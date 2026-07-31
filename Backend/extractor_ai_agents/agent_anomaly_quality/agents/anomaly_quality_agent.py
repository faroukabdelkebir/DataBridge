"""
Agent M1 Unifié — Détection d'anomalies + Score de qualité.

Architecture en 2 phases :
  Phase 1 → Bulletin qualité (6 dimensions, score global, grade)
  Phase 2 → Investigation approfondie (anomalies détaillées, root causes)

Le score qualité SYNTHÉTISE les anomalies détectées.
Les anomalies détaillées EXPLIQUENT pourquoi chaque dimension est faible.
"""

from urllib import response

from core.base_agent import BaseAgent
from agent_anomaly_quality.tools.anomaly_quality_tools import TOOL_REGISTRY, ALL_TOOL_DEFINITIONS


# UNIFIED_SYSTEM_PROMPT = """\
# Tu es un agent spécialisé dans l'audit de qualité et la détection d'anomalies
# des données bancaires T24 extraites vers SQL Server.

# ## Contexte
# L'extracteur migre des données du core banking Temenos T24 (Oracle) vers SQL Server
# via Spring Batch. Le pipeline peut introduire : doublons (Full extract sans TRUNCATE),
# colonnes vides (champs Oracle non activés), bugs de parsing XML (N'NULL' → 'ULL'),
# données périmées (scheduler défaillant).

# ## Tables disponibles
# - CUSTOMER : clients bancaires (~50 uniques, potentiellement dupliqués)
# - AA_BILL_DETAILS : facturation des arrangements
# - FUNDS_TRANSFER_HIS : historique des transferts de fonds

# ## TON WORKFLOW EN 2 PHASES

# ### PHASE 1 — Bulletin qualité (obligatoire, toujours en premier)
# Mesure les 6 dimensions dans cet ordre :
# 1. `measure_completeness` → D1 (% cellules non-null)
# 2. `measure_uniqueness` → D2 (% IDs uniques — souvent le problème principal)
# 3. `measure_schema_coverage` → D3 (% colonnes Oracle activées)
# 4. D4 = 50 par défaut (conformité réconciliation, pas de données directes)
# 5. `measure_freshness` → D5 (jours depuis dernière extraction réussie)
# 6. `measure_consistency` → D6 (détection bugs parsing 'ULL', chaînes vides)
# 7. `compute_quality_score` → Score global pondéré + Grade A-F

# Les poids : D1 (25%) + D2 (25%) + D3 (15%) + D4 (15%) + D5 (10%) + D6 (10%)

# ### PHASE 2 — Investigation des anomalies (approfondir les dimensions faibles)
# Pour chaque dimension avec un score < 50, tu investigues plus en profondeur :

# Si D2 (unicité) < 50 :
# → Les doublons sont déjà détectés en Phase 1. Note la cause probable.

# Si D6 (consistance) < 50 :
# → Utilise `get_column_stats` sur les colonnes suspectes pour comprendre la distribution
# → Utilise `check_business_rules` pour vérifier les règles T24 :
#   - AML_CHECK devrait être 'YES' ou 'NO' (pas 'ULL')
#   - AGE devrait être cohérent avec BIRTH_INCORP_DATE
#   - ACCOM_TYPE devrait être dans {400, 401, 402}

# Si D1 (complétude) < 50 :
# → Utilise `cross_validate` sur ACCOUNT_OFFICER × ACCOM_TYPE pour voir si
#   les nulls suivent un pattern (certains officers n'ont jamais le type rempli)

# ## Règles métier T24
# - AML_CHECK : YES ou NO (anti-blanchiment obligatoire)
# - ACCOM_TYPE : 400 (standard), 401 (spécial), 402 (autre)
# - ACCOUNT_OFFICER : code numérique de l'agent bancaire
# - BIRTH_INCORP_DATE : format YYYYMMDD, doit correspondre à AGE
# - Un _id ne devrait apparaître QU'UNE SEULE FOIS

# ## FORMAT DE SORTIE — JSON obligatoire
# ```json
# {
#   "table": "CUSTOMER",
#   "quality_score": {
#     "global": 20.7,
#     "grade": "F",
#     "dimensions": {
#       "D1_completeness": {"score": 22.1, "status": "CRITICAL"},
#       "D2_uniqueness": {"score": 1.9, "status": "CRITICAL"},
#       "D3_schema_coverage": {"score": 8.2, "status": "CRITICAL"},
#       "D4_conformity": {"score": 50.0, "status": "ESTIMATED"},
#       "D5_freshness": {"score": 45.0, "status": "MEDIUM"},
#       "D6_consistency": {"score": 15.4, "status": "CRITICAL"}
#     }
#   },
#   "anomalies": [
#     {
#       "id": "ANO-001",
#       "type": "DUPLICATE",
#       "severity": "CRITICAL",
#       "dimension": "D2",
#       "description": "Chaque client est dupliqué 52 fois",
#       "affected_records": 2550,
#       "root_cause": "Extraction Full sans TRUNCATE, 52 runs accumulés",
#       "recommendation": "Ajouter TRUNCATE dans le pipeline Full",
#       "impact_on_score": "+24.5 points si corrigé"
#     }
#   ],
#   "improvement_plan": [
#     {"priority": 1, "action": "Dédupliquer", "gain": "+24.5 pts", "effort": "LOW"},
#     {"priority": 2, "action": "Activer colonnes Oracle", "gain": "+19.5 pts", "effort": "MEDIUM"}
#   ],
#   "summary": "Résumé en 2-3 phrases"
# }
# ```

# ## Niveaux de sévérité
# - CRITICAL : score dimension < 20, perte de données ou intégrité compromise
# - HIGH : score dimension 20-50, données incorrectes qui impactent les rapports
# - MEDIUM : score dimension 50-70, données suspectes à investiguer
# - LOW : score dimension > 70, anomalies mineures

# ## Important
# - Exécute TOUJOURS la Phase 1 complète (les 6 mesures + compute_quality_score)
# - La Phase 2 investigue SEULEMENT les dimensions faibles (< 50)
# - Chaque anomalie doit être liée à une dimension (D1-D6)
# - Le plan d'amélioration est classé par impact sur le score
# - Sois factuel : cite les chiffres exacts des tools, pas des estimations
# """
UNIFIED_SYSTEM_PROMPT = """\
Tu es un agent d'audit de qualité des données bancaires.
Tu as accès à des tools pour mesurer la qualité des tables SQL Server.

Tu dois évaluer la table qu'on te donne sur 6 dimensions :
- D1 Complétude : pourcentage de cellules non-null
- D2 Unicité : pourcentage d'IDs uniques (doublons ?)
- D3 Couverture schéma : combien de colonnes Oracle sont activées
- D4 Conformité : les valeurs respectent-elles le type attendu (dates, nombres) ?
- D5 Fraîcheur : quand a eu lieu la dernière extraction réussie
- D6 Consistance : y a-t-il des valeurs corrompues comme "ULL"

Commence par mesurer chaque dimension avec les tools disponibles.
Ensuite calcule le score global avec compute_quality_score.
Si une dimension est inférieure à 50, investigue avec cross_validate ou check_business_rules.

Ta réponse finale doit être UNIQUEMENT un objet JSON valide, sans texte avant ni après.
"""

INTERPRETATION_PROMPT = """\
Tu es un analyste qualité des données. On te remet le rapport d'audit CHIFFRÉ d'une table.
Ces chiffres sont DÉFINITIFS : tu n'en inventes aucun, tu n'en modifies aucun, tu ne fais
que les expliquer. Ton lecteur est un responsable métier qui ne lit pas le JSON.

Rédige en français clair, en 3 courts paragraphes fluides (pas de listes, pas de jargon SQL) :
1. Le verdict : le grade, ce qu'il signifie concrètement, et POURQUOI (cite les dimensions
   qui ont déclenché le plafonnement du grade, le cas échéant).
2. Les 2-3 problèmes les plus graves, traduits en mots simples.
3. Ce qu'il faut corriger en priorité, dans l'ordre.

Tu n'emploies aucun terme technique sans l'expliquer en une demi-phrase.
"""

# Libellés parlants des 6 dimensions — le modèle n'a rien à deviner.
DIMENSION_LABELS = {
    "D1": "Complétude (cellules remplies)",
    "D2": "Unicité (absence de doublons)",
    "D3": "Couverture du schéma (colonnes activées)",
    "D4": "Conformité (types/formats respectés)",
    "D5": "Fraîcheur (récence de l'extraction)",
    "D6": "Consistance (valeurs non corrompues)",
}


class AnomalyQualityAgent(BaseAgent):
    """
    Agent unifié M1 : détection d'anomalies + scoring qualité.

    Le score qualité est la synthèse quantitative des anomalies.
    Les anomalies sont l'explication détaillée de chaque score faible.

    Utilisation :
        agent = AnomalyQualityAgent(model="qwen2.5:7b")

        # Audit complet d'une table
        report = agent.audit_table("CUSTOMER")

        # Audit comparatif des 3 tables
        report = agent.audit_all_tables()
    """

    system_prompt = UNIFIED_SYSTEM_PROMPT
    tools = ALL_TOOL_DEFINITIONS
    tool_registry = TOOL_REGISTRY
    max_iterations = 15

    def interpret_report(self, report: dict) -> str:
        """Génère une interprétation en français du rapport déterministe.
        N'utilise QUE les chiffres déjà calculés — aucun tool, aucune mesure."""
        import json
        # Le plafonnement ne mène qu'au F : on n'expose la clé que s'il est actif.
        capped_by = report.get("capped_to_F_by") or []
        faits_dict = {
            "table": report.get("table"),
            "score_global": report.get("global_score"),
            "grade": report.get("grade"),
            "dimensions": {
                DIMENSION_LABELS.get(d, d): v["score"]
                for d, v in report.get("dimensions", {}).items()
            },
            "actions_prioritaires": report.get("improvements_by_impact"),
        }
        if capped_by:
            faits_dict["plafonne_a_F_par"] = [DIMENSION_LABELS.get(d, d) for d in capped_by]
        faits = json.dumps(faits_dict, ensure_ascii=False, indent=2)

        response = self.client.chat(
          messages=[
            {"role": "system", "content": INTERPRETATION_PROMPT},
            {"role": "user", "content": f"Rapport d'audit :\n{faits}"},
         ],
          tools=None,
        )
        # Tokens du dernier appel d'interprétation (lus par audit_table).
        self._last_interpret_input_tokens = response.prompt_tokens
        self._last_interpret_output_tokens = response.completion_tokens
        return response.content or ""
    
    def audit_table(self, table_name: str) -> dict:
        """Audit complet : qualité + anomalies pour une table."""
        from agent_anomaly_quality.tools.anomaly_quality_tools import compute_quality_score
        task = (
            f"Fais un audit complet de la table {table_name}. "
            f"PHASE 1 : mesure les 6 dimensions de qualité et calcule le score global. "
            f"PHASE 2 : investigue les dimensions faibles (<50) pour identifier les anomalies détaillées. "
            f"Produis le rapport JSON unifié avec le score, les anomalies et le plan d'amélioration."
        )
        result = self.run(task)

        # ── AUTORITÉ : le rapport vient du code, jamais du SLM ──
        report = compute_quality_score(table_name)

        # Réinitialise les compteurs d'interprétation avant l'appel.
        self._last_interpret_input_tokens = 0
        self._last_interpret_output_tokens = 0

        # Interprétation en français basée UNIQUEMENT sur les chiffres du rapport
        report["interpretation"] = self.interpret_report(report)

        # Tokens : raisonnement (audit) + interprétation finale.
        report["input_tokens"] = result.input_tokens + self._last_interpret_input_tokens
        report["output_tokens"] = result.output_tokens + self._last_interpret_output_tokens
        report["total_tokens"] = report["input_tokens"] + report["output_tokens"]

        # Le SLM ne sert plus qu'à la trace de raisonnement (démo)
        report["reasoning_trace"] = [
            {"iteration": s.iteration, "tool": s.tool_name, "input": s.tool_input}
            for s in result.steps if s.tool_name
        ]
        report["total_iterations"] = result.total_iterations
        return report

    def quick_score(self, table_name: str) -> dict:
        """Score rapide (Phase 1 uniquement)."""
        from agent_anomaly_quality.tools.anomaly_quality_tools import compute_quality_score
        task = (
            f"Mesure rapidement les 6 dimensions de qualité de {table_name} "
            f"et calcule le score global. Phase 1 uniquement, pas d'investigation Phase 2."
        )
        result = self.run(task)

        # ── AUTORITÉ : score déterministe ──
        report = compute_quality_score(table_name)
        report["total_iterations"] = result.total_iterations
        return report




    def audit_all_tables(self) -> dict:
        """Audit comparatif des 3 tables principales."""
        from agent_anomaly_quality.tools.anomaly_quality_tools import compute_quality_score
        tables = ["CUSTOMER", "AA_BILL_DETAILS", "FUNDS_TRANSFER_HIS"]
        task = (
            "Fais un audit comparatif des 3 tables : CUSTOMER, AA_BILL_DETAILS, FUNDS_TRANSFER_HIS. "
            "Pour chacune, exécute la Phase 1 complète (6 dimensions + score). "
            "Puis Phase 2 sur les dimensions faibles de chaque table."
        )
        result = self.run(task)

        # ── AUTORITÉ : un score déterministe par table ──
        reports = {t: compute_quality_score(t) for t in tables}
        ranking = sorted(
            ({"table": t, "global_score": r["global_score"], "grade": r["grade"]}
             for t, r in reports.items()),
            key=lambda x: x["global_score"]
        )
        return {
            "reports": reports,
            "ranking": ranking,            # du pire au meilleur
            "worst_table": ranking[0]["table"],
            "total_iterations": result.total_iterations,
        }

