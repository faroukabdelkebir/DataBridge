"""
Optimisations de performance et fiabilité pour les agents.

Problèmes courants avec les SLMs :
1. JSON invalide → JSON repair + retry
2. Tools mal appelés → validation + correction
3. Lenteur → cache des résultats SQL + réduction du prompt
4. Inconsistance → fallback déterministe si le SLM échoue

Intégrer dans base_agent.py :
    from core.optimizations import CacheManager, JsonRepairer, FallbackScorer
"""

import json
import re
import hashlib
import time
from typing import Optional


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  1. CACHE DES RÉSULTATS SQL                                     ║
# ╚═══════════════════════════════════════════════════════════════════╝

class CacheManager:
    """
    Cache en mémoire pour les résultats de tools.
    Évite de re-exécuter les mêmes requêtes SQL quand le SLM
    appelle le même tool 2 fois (ce qui arrive souvent).

    Usage dans base_agent._execute_tool() :
        cache_key = cache.make_key(tool_name, arguments)
        cached = cache.get(cache_key)
        if cached:
            return cached
        result = func(**arguments)
        cache.set(cache_key, result)
        return result
    """

    def __init__(self, ttl_seconds: int = 300):
        self._cache = {}
        self._ttl = ttl_seconds

    def make_key(self, tool_name: str, args: dict) -> str:
        raw = f"{tool_name}:{json.dumps(args, sort_keys=True)}"
        return hashlib.md5(raw.encode()).hexdigest()

    def get(self, key: str) -> Optional[str]:
        if key in self._cache:
            result, timestamp = self._cache[key]
            if time.time() - timestamp < self._ttl:
                return result
            else:
                del self._cache[key]
        return None

    def set(self, key: str, value: str):
        self._cache[key] = (value, time.time())

    def clear(self):
        self._cache.clear()

    @property
    def stats(self) -> dict:
        return {"cached_entries": len(self._cache)}


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  2. JSON REPAIR — répare les JSON cassés du SLM                  ║
# ╚═══════════════════════════════════════════════════════════════════╝

class JsonRepairer:
    """
    Les SLMs produisent souvent du JSON avec des erreurs mineures :
    - Virgule en trop avant }
    - Commentaires // dans le JSON
    - Guillemets simples au lieu de doubles
    - JSON tronqué (pas de } final)
    - Texte avant/après le JSON

    Cette classe tente de réparer ces erreurs courantes.
    """

    @staticmethod
    def repair(text: str) -> tuple[bool, dict, str]:
        """
        Tente de parser puis réparer le JSON.
        Returns: (success, parsed_dict, error_message)
        """
        # 1. Essai direct
        try:
            # Extraire le JSON du texte
            start = text.index("{")
            end = text.rindex("}") + 1
            candidate = text[start:end]
            return True, json.loads(candidate), ""
        except (ValueError, json.JSONDecodeError):
            pass

        # 2. Nettoyage progressif
        cleaned = text

        # Retirer le texte avant/après le JSON
        match = re.search(r'\{[\s\S]*\}', cleaned)
        if match:
            cleaned = match.group(0)
        else:
            return False, {}, "Aucun objet JSON trouvé dans la réponse"

        # Retirer les commentaires // et /* */
        cleaned = re.sub(r'//.*?\n', '\n', cleaned)
        cleaned = re.sub(r'/\*.*?\*/', '', cleaned, flags=re.DOTALL)

        # Remplacer les guillemets simples par doubles (attention aux apostrophes)
        # Ne remplace que quand c'est clairement une clé/valeur JSON
        cleaned = re.sub(r"'(\w+)'\s*:", r'"\1":', cleaned)

        # Retirer les virgules en trop avant } ou ]
        cleaned = re.sub(r',\s*([}\]])', r'\1', cleaned)

        # Essayer de parser le nettoyé
        try:
            return True, json.loads(cleaned), "Réparé (nettoyage)"
        except json.JSONDecodeError:
            pass

        # 3. JSON tronqué — fermer les accolades/crochets manquants
        open_braces = cleaned.count('{') - cleaned.count('}')
        open_brackets = cleaned.count('[') - cleaned.count(']')
        if open_braces > 0 or open_brackets > 0:
            cleaned += ']' * open_brackets + '}' * open_braces
            # Retirer la dernière virgule avant la fermeture
            cleaned = re.sub(r',\s*([}\]])', r'\1', cleaned)
            try:
                return True, json.loads(cleaned), "Réparé (fermeture manquante)"
            except json.JSONDecodeError:
                pass

        return False, {}, "JSON irréparable"


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  3. FALLBACK DÉTERMINISTE — quand le SLM échoue                 ║
# ╚═══════════════════════════════════════════════════════════════════╝

class FallbackScorer:
    """
    Si le SLM ne produit pas de résultat valide après N tentatives,
    on calcule le score de qualité de manière DÉTERMINISTE (sans IA).

    C'est moins intelligent (pas de Phase 2 anomalies) mais c'est
    garanti de fonctionner et de retourner un résultat cohérent.

    Usage :
        scorer = FallbackScorer()
        report = scorer.compute_quality("CUSTOMER")
    """

    def compute_quality(self, table_name: str) -> dict:
        """Calcule le score qualité sans SLM — purement SQL."""
        import pyodbc
        from django.conf import settings

        conn = pyodbc.connect(
            f"DRIVER={{ODBC Driver 17 for SQL Server}};"
            f"SERVER={settings.BI_HOST},{settings.BI_PORT};DATABASE={settings.BI_DB};"
            f"UID={settings.BI_USER};PWD={settings.BI_PASS};"
        )
        try:
            cur = conn.cursor()

            # D1 — Complétude
            cur.execute(f"SELECT COUNT(*) FROM {table_name}")
            total_rows = cur.fetchone()[0]
            cur.execute(f"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=?", table_name)
            cols = [r[0] for r in cur.fetchall()]
            total_nulls = 0
            for c in cols:
                cur.execute(f"SELECT SUM(CASE WHEN [{c}] IS NULL THEN 1 ELSE 0 END) FROM {table_name}")
                total_nulls += cur.fetchone()[0] or 0
            total_cells = total_rows * len(cols)
            d1 = round((1 - total_nulls / total_cells) * 100, 1) if total_cells else 0

            # D2 — Unicité
            cur.execute(f"SELECT COUNT(DISTINCT _id) FROM {table_name}")
            unique = cur.fetchone()[0]
            d2 = round(unique / total_rows * 100, 1) if total_rows else 100

            # D3 — Couverture schéma
            schema_map = {"CUSTOMER": "CUSTOMER", "AA_BILL_DETAILS": "AA_BILL_DETAILS",
                          "FUNDS_TRANSFER_HIS": "FUNDS_TRANSFER#HIS"}
            sname = schema_map.get(table_name, table_name)
            cur.execute("SELECT COUNT(*), SUM(CAST(_enabled AS INT)) FROM SCHEMA_SOURCE WHERE table_name=?", sname)
            r = cur.fetchone()
            d3 = round(r[1] / r[0] * 100, 1) if r[0] else 0

            # D4 — Conformité (défaut)
            d4 = 50.0

            # D5 — Fraîcheur
            cur.execute("SELECT MAX(END_TIME) FROM BATCH_JOB_EXECUTION WHERE STATUS='COMPLETED'")
            last = cur.fetchone()[0]
            if last:
                from datetime import datetime
                age_days = (datetime.now() - datetime.fromisoformat(str(last))).total_seconds() / 86400
                d5 = max(0, round(100 - age_days * 5, 1))
            else:
                d5 = 0

            # D6 — Consistance
            cur.execute(f"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? AND DATA_TYPE LIKE '%char%'", table_name)
            text_cols = [r[0] for r in cur.fetchall()]
            bad_count = 0
            for c in text_cols:
                cur.execute(f"SELECT COUNT(*) FROM {table_name} WHERE [{c}]='ULL'")
                bad_count += cur.fetchone()[0] or 0
            d6 = max(0, round(100 - (bad_count / total_rows * 100), 1)) if total_rows else 100

            # Score global
            weights = {"D1": 0.25, "D2": 0.25, "D3": 0.15, "D4": 0.15, "D5": 0.10, "D6": 0.10}
            scores = {"D1": d1, "D2": d2, "D3": d3, "D4": d4, "D5": d5, "D6": d6}
            total = round(sum(scores[d] * weights[d] for d in scores), 1)
            grade = "A" if total >= 80 else "B" if total >= 60 else "C" if total >= 40 else "D" if total >= 20 else "F"

            return {
                "table": table_name,
                "method": "FALLBACK_DETERMINISTIC",
                "quality_score": {"global": total, "grade": grade},
                "dimensions": {d: {"score": s} for d, s in scores.items()},
                "note": "Calculé sans SLM (fallback déterministe). Pas d'analyse d'anomalies Phase 2."
            }
        finally:
            conn.close()


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  4. RETRY INTELLIGENT                                           ║
# ╚═══════════════════════════════════════════════════════════════════╝

def run_with_retry(agent, task: str, max_retries: int = 2, use_fallback: bool = True) -> dict:
    """
    Lance un agent avec retry automatique et fallback.

    Tentative 1 : prompt complet
    Tentative 2 : prompt simplifié + "Réponds uniquement en JSON"
    Fallback    : calcul déterministe (si M1)

    Returns: {"method": "agent"|"retry"|"fallback", "report": {...}}
    """
    repairer = JsonRepairer()

    for attempt in range(1, max_retries + 1):
        try:
            if attempt > 1:
                # Prompt simplifié pour le retry
                task = task + "\n\nIMPORTANT : Réponds UNIQUEMENT avec un objet JSON valide. Pas de texte avant ou après."

            result = agent.run(task)

            # Essayer de parser (avec repair)
            success, parsed, error = repairer.repair(result.answer)

            if success:
                return {
                    "method": "agent" if attempt == 1 else "retry",
                    "attempt": attempt,
                    "report": parsed,
                    "iterations": result.total_iterations,
                    "trace": [
                        {"iteration": s.iteration, "tool": s.tool_name}
                        for s in result.steps if s.tool_name
                    ],
                }
        except Exception as e:
            print(f"  Tentative {attempt} échouée : {e}")

    # Fallback déterministe
    if use_fallback and hasattr(agent, 'system_prompt') and 'qualit' in agent.system_prompt.lower():
        try:
            scorer = FallbackScorer()
            report = scorer.compute_quality("CUSTOMER")
            return {"method": "fallback", "report": report}
        except Exception as e:
            return {"method": "error", "error": str(e)}

    return {"method": "error", "error": f"Échec après {max_retries} tentatives"}
