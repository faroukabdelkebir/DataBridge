"""
Tools unifiés : détection d'anomalies + scoring qualité.
10 tools organisés en 2 phases :
  Phase 1 — Quality Assessment (6 dimensions, hérité de M5)
  Phase 2 — Deep Anomaly Detection (investigation détaillée, hérité de M1)
"""

import pyodbc
from datetime import datetime
from collections import defaultdict
from typing import Optional
from django.conf import settings


def get_connection():
    return pyodbc.connect(
        f"DRIVER={{{os.getenv('ODBC_DRIVER', 'ODBC Driver 18 for SQL Server')}}};"
        f"TrustServerCertificate=yes;"
        f"SERVER={settings.BI_HOST},{settings.BI_PORT};"
        f"DATABASE={settings.BI_DB};"
        f"UID={settings.BI_USER};"
        f"PWD={settings.BI_PASS};"
    )


# Noms physiques réels des tables en base (T24 : le nom garde le '#',
# ex. FUNDS_TRANSFER#HIS). Ces noms sont réinjectés tels quels dans le SQL,
# encadrés par des crochets à cause du '#'.
ALLOWED_TABLES = {"CUSTOMER", "AA_BILL_DETAILS", "FUNDS_TRANSFER#HIS",
                  "BATCH_JOB_EXECUTION", "BATCH_STEP_EXECUTION",
                  "BATCH_CONFIG", "SCHEMA_SOURCE", "stats"}

def _validate(t):
    # Les appelants passent tantôt la variante '#' (FUNDS_TRANSFER#HIS, depuis le
    # schéma), tantôt la variante '_' (FUNDS_TRANSFER_HIS, en dur dans audit_all).
    # On accepte les deux et on renvoie TOUJOURS le nom physique de la liste blanche.
    clean = t.strip().upper().replace("#", "_")
    for allowed in ALLOWED_TABLES:
        if allowed.upper().replace("#", "_") == clean: return allowed
    raise ValueError(f"Table non autorisée: {t}")


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  PHASE 1 — QUALITY ASSESSMENT (6 dimensions)                    ║
# ╚═══════════════════════════════════════════════════════════════════╝

# ------- D1 : Complétude -------

def measure_completeness(table_name: str) -> dict:
    """D1 — % de cellules non-null, détail par colonne."""
    table = _validate(table_name)
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(f"SELECT COUNT(*) FROM [{table}]")
        total = cur.fetchone()[0]
        cur.execute(f"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? ORDER BY ORDINAL_POSITION", table)
        cols = [r[0] for r in cur.fetchall()]

        full_null, full_filled, partial = [], [], []
        total_nulls = 0
        for c in cols:
            cur.execute(f"SELECT SUM(CASE WHEN [{c}] IS NULL THEN 1 ELSE 0 END) FROM [{table}]")
            n = cur.fetchone()[0] or 0
            total_nulls += n
            pct = round(n / total * 100, 1) if total else 0
            if pct == 0: full_filled.append(c)
            elif pct == 100: full_null.append(c)
            else: partial.append({"column": c, "null_pct": pct})

        score = round((1 - total_nulls / (total * len(cols))) * 100, 1) if total and cols else 0
        return {"dimension": "D1_COMPLETENESS", "score": score, "total_rows": total,
                "columns_filled": full_filled, "columns_empty": full_null,
                "columns_partial": sorted(partial, key=lambda p: -p["null_pct"])}
    finally: conn.close()

MEASURE_COMPLETENESS_TOOL = {"type": "function", "function": {
    "name": "measure_completeness",
    "description": "D1 — Mesure la complétude : % cellules non-null. Détaille colonnes remplies, vides et partielles.",
    "parameters": {"type": "object", "properties": {"table_name": {"type": "string"}}, "required": ["table_name"]}
}}


# ------- D2 : Unicité -------

def measure_uniqueness(table_name: str, key_column: str = "_id") -> dict:
    """D2 — Ratio IDs uniques / total, détection doublons."""
    table = _validate(table_name)
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(f"SELECT COUNT(*) FROM [{table}]")
        total = cur.fetchone()[0]
        cur.execute(f"SELECT COUNT(DISTINCT [{key_column}]) FROM [{table}]")
        unique = cur.fetchone()[0]
        cur.execute(f"SELECT TOP 10 [{key_column}], COUNT(*) c FROM [{table}] GROUP BY [{key_column}] HAVING COUNT(*)>1 ORDER BY c DESC")
        dups = [{"key": str(r[0]), "count": r[1]} for r in cur.fetchall()]

        score = round(unique / total * 100, 1) if total else 100
        return {"dimension": "D2_UNIQUENESS", "score": score, "total_rows": total,
                "unique_keys": unique, "duplication_ratio": f"{round(total/unique,1)}x" if unique else "∞",
                "top_duplicates": dups,
                "severity": "CRITICAL" if score < 10 else "HIGH" if score < 50 else "OK"}
    finally: conn.close()

MEASURE_UNIQUENESS_TOOL = {"type": "function", "function": {
    "name": "measure_uniqueness",
    "description": "D2 — Ratio IDs uniques / lignes totales. Détecte les doublons massifs. <10% = CRITICAL.",
    "parameters": {"type": "object", "properties": {
        "table_name": {"type": "string"},
        "key_column": {"type": "string", "description": "Colonne clé (défaut: _id)"}
    }, "required": ["table_name"]}
}}


# ------- D3 : Couverture schéma -------

def measure_schema_coverage(table_name: str) -> dict:
    """D3 — % colonnes Oracle activées dans SCHEMA_SOURCE."""
    conn = get_connection()
    schema_map = {"CUSTOMER": "CUSTOMER", "AA_BILL_DETAILS": "AA_BILL_DETAILS", "FUNDS_TRANSFER_HIS": "FUNDS_TRANSFER#HIS"}
    schema_name = schema_map.get(table_name.upper(), table_name)
    try:
        cur = conn.cursor()
        cur.execute("SELECT column_name, _enabled, single_mult FROM SCHEMA_SOURCE WHERE table_name=?", schema_name)
        total, enabled, easy_wins = 0, 0, []
        for r in cur.fetchall():
            total += 1
            if r[1]: enabled += 1
            elif r[2] == "S": easy_wins.append(r[0])

        score = round(enabled / total * 100, 1) if total else 0
        return {"dimension": "D3_SCHEMA_COVERAGE", "score": score,
                "oracle_total": total, "enabled": enabled, "disabled": total - enabled,
                "easy_wins_count": len(easy_wins), "easy_wins_sample": easy_wins[:10]}
    finally: conn.close()

MEASURE_SCHEMA_COVERAGE_TOOL = {"type": "function", "function": {
    "name": "measure_schema_coverage",
    "description": "D3 — % colonnes Oracle activées vs total dans SCHEMA_SOURCE. Liste les colonnes single-value faciles à activer.",
    "parameters": {"type": "object", "properties": {"table_name": {"type": "string"}}, "required": ["table_name"]}
}}

# ------- D4 : Conformité -------

def measure_conformity(table_name: str) -> dict:
    """D4 — % de valeurs conformes au type attendu de leur colonne.
    Les valeurs Oracle/XML arrivent en varchar : on vérifie que les colonnes
    qui *devraient* être des dates/nombres le sont réellement."""
    table = _validate(table_name)
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? AND DATA_TYPE LIKE '%char%'", table)
        text_cols = [r[0] for r in cur.fetchall()]

        DATE_HINTS = ("DATE", "_DT", "DOB", "BIRTH", "EXPIRY", "OPENING", "INCORP")
        NUM_HINTS  = ("AGE", "AMOUNT", "AMT", "BALANCE", "NUMBER", "COUNT", "RATE", "LIMIT")

        checks = []
        for col in text_cols:
            name = col.upper()
            if any(h in name for h in DATE_HINTS):
                expected, fn = "DATE", "ISDATE"
            elif any(h in name for h in NUM_HINTS):
                expected, fn = "NUMERIC", "ISNUMERIC"
            else:
                continue
            cur.execute(
                f"SELECT SUM(CASE WHEN [{col}] IS NOT NULL AND {fn}([{col}])=0 THEN 1 ELSE 0 END), "
                f"       SUM(CASE WHEN [{col}] IS NOT NULL THEN 1 ELSE 0 END) FROM [{table}]")
            row = cur.fetchone()
            violations, non_null = (row[0] or 0), (row[1] or 0)
            if non_null > 0:
                checks.append({"column": col, "expected_type": expected,
                               "non_null": non_null, "violations": violations,
                               "conformity_pct": round((1 - violations / non_null) * 100, 1)})

        if checks:
            checked = sum(c["non_null"] for c in checks)
            viol = sum(c["violations"] for c in checks)
            score = round((1 - viol / checked) * 100, 1) if checked else 100.0
        else:
            score = 100.0  # aucune colonne typable détectée → rien à invalider

        worst = sorted([c for c in checks if c["violations"] > 0],
                       key=lambda c: c["conformity_pct"])[:5]
        return {"dimension": "D4_CONFORMITY", "score": score,
                "columns_checked": len(checks),
                "violations_found": sum(c["violations"] for c in checks),
                "worst_columns": worst}
    finally: conn.close()

MEASURE_CONFORMITY_TOOL = {"type": "function", "function": {
    "name": "measure_conformity",
    "description": "D4 — Vérifie que les valeurs respectent le type attendu de leur colonne (dates réelles, nombres réels). Détecte les colonnes texte qui devraient être typées.",
    "parameters": {"type": "object", "properties": {"table_name": {"type": "string"}}, "required": ["table_name"]}
}}
# ------- D5 : Fraîcheur -------

def measure_freshness() -> dict:
    """D5 — Temps depuis dernière extraction réussie."""
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute("SELECT TOP 5 JOB_EXECUTION_ID, END_TIME FROM BATCH_JOB_EXECUTION WHERE STATUS='COMPLETED' ORDER BY END_TIME DESC")
        jobs = []
        for r in cur.fetchall():
            try: jobs.append({"job_id": r[0], "end_time": str(datetime.fromisoformat(str(r[1])))})
            except: pass

        if jobs:
            last = datetime.fromisoformat(jobs[0]["end_time"])
            age_days = (datetime.now() - last).total_seconds() / 86400
            score = max(0, round(100 - age_days * 5, 1))
        else:
            age_days = None; score = 0

        return {"dimension": "D5_FRESHNESS", "score": score,
                "last_success": jobs[0] if jobs else None,
                "age_days": round(age_days, 1) if age_days else None}
    finally: conn.close()

MEASURE_FRESHNESS_TOOL = {"type": "function", "function": {
    "name": "measure_freshness",
    "description": "D5 — Fraîcheur : temps depuis la dernière extraction réussie. Perd 5 points/jour.",
    "parameters": {"type": "object", "properties": {}, "required": []}
}}


# ------- D6 : Consistance -------

def measure_consistency(table_name: str) -> dict:
    """D6 — Détecte parsing bugs ('ULL'), chaînes vides, types incorrects."""
    table = _validate(table_name)
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(f"SELECT COUNT(*) FROM [{table}]")
        total = cur.fetchone()[0]
        cur.execute(f"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? AND DATA_TYPE LIKE '%char%'", table)
        text_cols = [r[0] for r in cur.fetchall()]

        issues = []
        for col in text_cols:
            cur.execute(f"SELECT COUNT(*) FROM [{table}] WHERE [{col}]='ULL'")
            c = cur.fetchone()[0]
            if c > 0:
                issues.append({"type": "PARSING_BUG", "column": col, "value": "ULL", "count": c, "severity": "HIGH"})
            cur.execute(f"SELECT COUNT(*) FROM [{table}] WHERE [{col}]='' OR [{col}]=' '")
            c2 = cur.fetchone()[0]
            if c2 > 0:
                issues.append({"type": "EMPTY_STRING", "column": col, "count": c2, "severity": "MEDIUM"})

        total_bad = sum(i["count"] for i in issues)
        score = max(0, round(100 - (total_bad / total * 100), 1)) if total else 100
        return {"dimension": "D6_CONSISTENCY", "score": score, "issues": issues, "total_issues": total_bad}
    finally: conn.close()

MEASURE_CONSISTENCY_TOOL = {"type": "function", "function": {
    "name": "measure_consistency",
    "description": "D6 — Consistance : détecte les valeurs 'ULL' (bug parsing XML), chaînes vides, types incorrects.",
    "parameters": {"type": "object", "properties": {"table_name": {"type": "string"}}, "required": ["table_name"]}
}}


# ------- Score global -------

# def compute_quality_score(d1: float, d2: float, d3: float, d4: float, d5: float, d6: float, table_name: str = "") -> dict:
#     """Calcule le score global pondéré + grade + plan d'amélioration."""
#     weights = {"D1": 0.25, "D2": 0.25, "D3": 0.15, "D4": 0.15, "D5": 0.10, "D6": 0.10}
#     scores = {"D1": d1, "D2": d2, "D3": d3, "D4": d4, "D5": d5, "D6": d6}
#     total = round(sum(scores[d] * weights[d] for d in scores), 1)
#     grade = "A" if total >= 80 else "B" if total >= 60 else "C" if total >= 40 else "D" if total >= 20 else "F"

#     actions = {
#         "D1": "Activer plus de colonnes Oracle ou supprimer les colonnes vides",
#         "D2": "TRUNCATE avant chaque extraction Full ou implémenter MERGE/UPSERT",
#         "D3": "Activer les colonnes single-value dans SCHEMA_SOURCE",
#         "D4": "Vérifier oracle_count vs sql_count après extraction",
#         "D5": "Fiabiliser le scheduler pour extraction quotidienne",
#         "D6": "Corriger le parsing XML (N'NULL' → NULL) dans XMLProcessor",
#     }
#     improvements = sorted([
#         {"dimension": d, "current": s, "max_gain": round((100 - s) * weights[d], 1), "action": actions[d]}
#         for d, s in scores.items() if s < 80
#     ], key=lambda x: -x["max_gain"])

#     return {"table": table_name, "global_score": total, "grade": grade,
#             "dimensions": {d: {"score": s, "weight": weights[d]} for d, s in scores.items()},
#             "improvements_by_impact": improvements}

# COMPUTE_QUALITY_SCORE_TOOL = {"type": "function", "function": {
#     "name": "compute_quality_score",
#     "description": "Calcule le score global pondéré (D1×25% + D2×25% + D3×15% + D4×15% + D5×10% + D6×10%). Retourne grade A-F et plan d'amélioration classé par impact.",
#     "parameters": {"type": "object", "properties": {
#         "d1": {"type": "number"}, "d2": {"type": "number"}, "d3": {"type": "number"},
#         "d4": {"type": "number"}, "d5": {"type": "number"}, "d6": {"type": "number"},
#         "table_name": {"type": "string"}
#     }, "required": ["d1", "d2", "d3", "d4", "d5", "d6"]}
# }}
CRITICAL_FLOORS = {"D1": 40.0, "D2": 40.0}
WEIGHTS = {"D1": 0.25, "D2": 0.25, "D3": 0.15, "D4": 0.15, "D5": 0.10, "D6": 0.10}

def _grade_from_score(s):
    return "A" if s >= 80 else "B" if s >= 60 else "C" if s >= 40 else "D" if s >= 20 else "F"

def compute_quality_score(table_name: str = "", **_ignored) -> dict:
    """Score global + grade. Mesure les 6 dimensions LUI-MÊME : le SLM ne fournit
    aucun nombre (les kwargs hérités sont ignorés), il déclenche seulement le calcul."""
    table = _validate(table_name)

    # 1. Mesures déterministes — aucune valeur ne transite par le SLM.
    scores = {
        "D1": measure_completeness(table)["score"],
        "D2": measure_uniqueness(table)["score"],
        "D3": measure_schema_coverage(table)["score"],
        "D4": measure_conformity(table)["score"],
        "D5": measure_freshness()["score"],
        "D6": measure_consistency(table)["score"],
    }

    weighted = round(sum(scores[d] * WEIGHTS[d] for d in scores), 1)
    grade = _grade_from_score(weighted)

    # 2. Plafond : une dimension critique cassée force le F.
    capped_by = [d for d, floor in CRITICAL_FLOORS.items() if scores[d] < floor]
    if capped_by:
        grade = "F"

    actions = {
        "D1": "Activer plus de colonnes Oracle ou supprimer les colonnes vides",
        "D2": "TRUNCATE avant chaque extraction Full ou implémenter MERGE/UPSERT",
        "D3": "Activer les colonnes single-value dans SCHEMA_SOURCE",
        "D4": "Corriger le typage des colonnes date/numériques à l'extraction",
        "D5": "Fiabiliser le scheduler pour extraction quotidienne",
        "D6": "Corriger le parsing XML ('ULL' → NULL) dans XMLProcessor",
    }
    improvements = sorted(
        [{"dimension": d, "current": s, "max_gain": round((100 - s) * WEIGHTS[d], 1), "action": actions[d]}
         for d, s in scores.items() if s < 80],
        key=lambda x: -x["max_gain"])

    return {"table": table, "global_score": weighted, "grade": grade,
            "capped_to_F_by": capped_by,
            "dimensions": {d: {"score": scores[d], "weight": WEIGHTS[d]} for d in WEIGHTS},
            "improvements_by_impact": improvements}

COMPUTE_QUALITY_SCORE_TOOL = {"type": "function", "function": {
    "name": "compute_quality_score",
    "description": "Calcule le score qualité global et le grade A-F d'une table. Mesure lui-même les 6 dimensions : ne fournis aucun score, seulement le nom de la table.",
    "parameters": {"type": "object", "properties": {"table_name": {"type": "string"}}, "required": ["table_name"]}
}}

# ╔═══════════════════════════════════════════════════════════════════╗
# ║  PHASE 2 — DEEP ANOMALY DETECTION                               ║
# ╚═══════════════════════════════════════════════════════════════════╝

# ------- Cross-validation -------

def cross_validate(table_name: str, column_a: str, column_b: str) -> dict:
    """Croise deux colonnes pour trouver les combinaisons rares (<2%)."""
    table = _validate(table_name)
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(f"""
            SELECT [{column_a}], [{column_b}], COUNT(*) c
            FROM [{table}] WHERE [{column_a}] IS NOT NULL AND [{column_b}] IS NOT NULL
            GROUP BY [{column_a}], [{column_b}] ORDER BY COUNT(*) DESC
        """)
        combos = [{"col_a": str(r[0]), "col_b": str(r[1]), "count": r[2]} for r in cur.fetchall()]
        total = sum(c["count"] for c in combos)
        rare = [c for c in combos if c["count"] / total < 0.02] if total else []
        return {"column_a": column_a, "column_b": column_b,
                "total_combinations": len(combos), "all_combinations": combos[:20],
                "rare_combinations": rare}
    finally: conn.close()

CROSS_VALIDATE_TOOL = {"type": "function", "function": {
    "name": "cross_validate",
    "description": "Croise deux colonnes pour détecter les combinaisons inhabituelles (<2% du total). Utile pour trouver des incohérences entre champs liés (ex: ACCOUNT_OFFICER × ACCOM_TYPE).",
    "parameters": {"type": "object", "properties": {
        "table_name": {"type": "string"}, "column_a": {"type": "string"}, "column_b": {"type": "string"}
    }, "required": ["table_name", "column_a", "column_b"]}
}}


# ------- Règles de consistance métier -------

def check_business_rules(table_name: str, rules: list[dict]) -> dict:
    """Vérifie des règles métier (conditions SQL qui ne devraient pas être vraies)."""
    table = _validate(table_name)
    conn = get_connection()
    results = []
    try:
        cur = conn.cursor()
        for rule in rules[:10]:
            name = rule.get("name", "unnamed")
            cond = rule.get("condition", "1=0")
            forbidden = ["DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "EXEC", "--", ";"]
            if any(kw.upper() in cond.upper() for kw in forbidden):
                results.append({"rule": name, "error": "SQL interdit"}); continue
            cur.execute(f"SELECT COUNT(*) FROM [{table}] WHERE {cond}")
            violations = cur.fetchone()[0]
            examples = []
            if violations > 0:
                cur.execute(f"SELECT TOP 5 _id FROM [{table}] WHERE {cond}")
                examples = [str(r[0]) for r in cur.fetchall()]
            results.append({"rule": name, "violations": violations, "examples": examples})
        return {"table": table, "results": results}
    finally: conn.close()

CHECK_BUSINESS_RULES_TOOL = {"type": "function", "function": {
    "name": "check_business_rules",
    "description": "Vérifie des règles métier bancaires T24. Chaque règle est une condition SQL qui identifie des violations (ex: AGE < 0, AML_CHECK NOT IN ('YES','NO')). Retourne le nombre de violations et des exemples.",
    "parameters": {"type": "object", "properties": {
        "table_name": {"type": "string"},
        "rules": {"type": "array", "items": {"type": "object", "properties": {
            "name": {"type": "string"}, "condition": {"type": "string"}
        }, "required": ["name", "condition"]}}
    }, "required": ["table_name", "rules"]}
}}


# ------- Column stats (deep dive) -------

def get_column_stats(table_name: str, column_name: str) -> dict:
    """Statistiques détaillées d'une colonne : min, max, distribution top 10."""
    table = _validate(table_name)
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(f"""
            SELECT COUNT(*) as total,
                   SUM(CASE WHEN [{column_name}] IS NULL THEN 1 ELSE 0 END) as nulls,
                   COUNT(DISTINCT [{column_name}]) as distinct_count,
                   MIN([{column_name}]) as mn, MAX([{column_name}]) as mx
            FROM [{table}]
        """)
        r = cur.fetchone()
        stats = {"column": column_name, "total": r[0], "nulls": r[1],
                 "null_pct": round(r[1]/r[0]*100, 1) if r[0] else 0,
                 "distinct": r[2], "min": str(r[3]) if r[3] else None, "max": str(r[4]) if r[4] else None}
        cur.execute(f"SELECT TOP 10 [{column_name}], COUNT(*) c FROM [{table}] WHERE [{column_name}] IS NOT NULL GROUP BY [{column_name}] ORDER BY c DESC")
        stats["top_values"] = [{"value": str(r[0]), "count": r[1]} for r in cur.fetchall()]
        return stats
    finally: conn.close()

GET_COLUMN_STATS_TOOL = {"type": "function", "function": {
    "name": "get_column_stats",
    "description": "Statistiques détaillées d'une colonne : nulls, distinct, min, max, distribution top 10. Pour le deep dive après la phase qualité.",
    "parameters": {"type": "object", "properties": {
        "table_name": {"type": "string"}, "column_name": {"type": "string"}
    }, "required": ["table_name", "column_name"]}
}}


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  REGISTRE                                                        ║
# ╚═══════════════════════════════════════════════════════════════════╝

TOOL_REGISTRY = {
    # Phase 1 — Quality
    "measure_completeness": measure_completeness,
    "measure_uniqueness": measure_uniqueness,
    "measure_schema_coverage": measure_schema_coverage,
    "measure_conformity": measure_conformity,
    "measure_freshness": measure_freshness,
    "measure_consistency": measure_consistency,
    "compute_quality_score": compute_quality_score,
    # Phase 2 — Anomalies
    "cross_validate": cross_validate,
    "check_business_rules": check_business_rules,
    "get_column_stats": get_column_stats,
}

QUALITY_TOOLS = [MEASURE_COMPLETENESS_TOOL, MEASURE_UNIQUENESS_TOOL,
                 MEASURE_SCHEMA_COVERAGE_TOOL, MEASURE_CONFORMITY_TOOL,
                 MEASURE_FRESHNESS_TOOL, MEASURE_CONSISTENCY_TOOL, COMPUTE_QUALITY_SCORE_TOOL]

ANOMALY_TOOLS = [CROSS_VALIDATE_TOOL, CHECK_BUSINESS_RULES_TOOL, GET_COLUMN_STATS_TOOL]

ALL_TOOL_DEFINITIONS = QUALITY_TOOLS + ANOMALY_TOOLS
