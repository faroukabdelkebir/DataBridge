"""
Tests unitaires des tools de l'agent M1 (qualite + anomalies).

SQL Server est remplace par `FakeDB` : chaque test declare les resultats que la
base renverrait, et verifie le calcul fait par le tool.
"""

from datetime import datetime, timedelta

import pytest

from agent_anomaly_quality.tools import anomaly_quality_tools as tools


def _exact(sql):
    """Matcher FakeDB strict : evite qu'une requete courte capte ses variantes."""
    return lambda executed: executed == sql


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Liste blanche des tables                                        ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestValidateTable:

    @pytest.mark.parametrize("entree,attendu", [
        ("CUSTOMER", "CUSTOMER"),
        ("customer", "CUSTOMER"),
        ("  CUSTOMER  ", "CUSTOMER"),
        ("AA_BILL_DETAILS", "AA_BILL_DETAILS"),
        ("FUNDS_TRANSFER_HIS", "FUNDS_TRANSFER#HIS"),
        ("FUNDS_TRANSFER#HIS", "FUNDS_TRANSFER#HIS"),
    ])
    def test_variantes_acceptees_renvoient_le_nom_physique(self, entree, attendu):
        assert tools._validate(entree) == attendu

    @pytest.mark.parametrize("table", [
        "UTILISATEURS",
        "CUSTOMER; DROP TABLE CUSTOMER",
        "sys.databases",
        "",
    ])
    def test_table_hors_liste_blanche_est_refusee(self, table):
        # Le nom de table est reinjecte dans le SQL : la liste blanche est la
        # seule protection contre l'injection via un argument du SLM.
        with pytest.raises(ValueError, match="Table non autorisée"):
            tools._validate(table)


# ╔══════════════════════════════════════════════════════════════════╗
# ║  D1 — Completude                                                 ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestMeasureCompleteness:

    @pytest.fixture
    def db(self, patch_quality_db):
        nulls = {"_id": 0, "NAME": 50, "AML_CHECK": 25}
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER]", [(100,)])
        patch_quality_db.add("INFORMATION_SCHEMA.COLUMNS", [("_id",), ("NAME",), ("AML_CHECK",)])
        patch_quality_db.add(
            "CASE WHEN",
            lambda sql, params: [(next(n for c, n in nulls.items() if f"[{c}]" in sql),)],
        )
        return patch_quality_db

    def test_score_et_repartition_des_colonnes(self, db):
        out = tools.measure_completeness("CUSTOMER")

        assert out["dimension"] == "D1_COMPLETENESS"
        assert out["total_rows"] == 100
        # 75 nulls sur 300 cellules -> 75 % de remplissage
        assert out["score"] == 75.0
        assert out["columns_filled"] == ["_id"]
        assert out["columns_empty"] == []
        assert out["columns_partial"] == [
            {"column": "NAME", "null_pct": 50.0},
            {"column": "AML_CHECK", "null_pct": 25.0},
        ]

    def test_colonnes_partielles_triees_par_gravite(self, db):
        pcts = [c["null_pct"] for c in tools.measure_completeness("CUSTOMER")["columns_partial"]]
        assert pcts == sorted(pcts, reverse=True)

    def test_colonne_entierement_vide_est_isolee(self, patch_quality_db):
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER]", [(10,)])
        patch_quality_db.add("INFORMATION_SCHEMA.COLUMNS", [("VIDE",)])
        patch_quality_db.add("CASE WHEN", [(10,)])

        out = tools.measure_completeness("CUSTOMER")
        assert out["columns_empty"] == ["VIDE"]
        assert out["score"] == 0.0

    def test_table_vide_ne_divise_pas_par_zero(self, patch_quality_db):
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER]", [(0,)])
        patch_quality_db.add("INFORMATION_SCHEMA.COLUMNS", [("_id",)])
        patch_quality_db.add("CASE WHEN", [(0,)])

        assert tools.measure_completeness("CUSTOMER")["score"] == 0

    def test_connexion_fermee_meme_en_cas_d_erreur(self, patch_quality_db):
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER]", lambda *_: 1 / 0)
        with pytest.raises(ZeroDivisionError):
            tools.measure_completeness("CUSTOMER")
        assert patch_quality_db.closed_connections == 1


# ╔══════════════════════════════════════════════════════════════════╗
# ║  D2 — Unicite                                                    ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestMeasureUniqueness:

    def _prepare(self, db, total, unique, dups=()):
        db.add("SELECT COUNT(*) FROM [CUSTOMER]", [(total,)])
        db.add("COUNT(DISTINCT", [(unique,)])
        db.add("HAVING COUNT(*)>1", list(dups))

    def test_doublons_massifs_sont_critiques(self, patch_quality_db):
        self._prepare(patch_quality_db, 2600, 50, dups=[("C1", 52), ("C2", 52)])
        out = tools.measure_uniqueness("CUSTOMER")

        assert out["dimension"] == "D2_UNIQUENESS"
        assert out["score"] == 1.9
        assert out["duplication_ratio"] == "52.0x"
        assert out["severity"] == "CRITICAL"
        assert out["top_duplicates"][0] == {"key": "C1", "count": 52}

    @pytest.mark.parametrize("total,unique,severite", [
        (100, 100, "OK"),
        (100, 60, "OK"),
        (100, 30, "HIGH"),
        (100, 5, "CRITICAL"),
    ])
    def test_paliers_de_severite(self, patch_quality_db, total, unique, severite):
        self._prepare(patch_quality_db, total, unique)
        assert tools.measure_uniqueness("CUSTOMER")["severity"] == severite

    def test_table_vide_est_consideree_parfaite(self, patch_quality_db):
        self._prepare(patch_quality_db, 0, 0)
        out = tools.measure_uniqueness("CUSTOMER")
        assert out["score"] == 100
        assert out["duplication_ratio"] == "∞"

    def test_colonne_cle_personnalisee(self, patch_quality_db):
        self._prepare(patch_quality_db, 10, 10)
        tools.measure_uniqueness("CUSTOMER", key_column="MNEMONIC")
        assert any("[MNEMONIC]" in sql for sql in patch_quality_db.queries_matching("DISTINCT"))


# ╔══════════════════════════════════════════════════════════════════╗
# ║  D3 — Couverture schema                                          ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestMeasureSchemaCoverage:

    def test_compte_les_colonnes_activees_et_les_gains_faciles(self, patch_quality_db):
        patch_quality_db.add("SCHEMA_SOURCE", [
            ("_id", 1, "S"), ("NAME", 1, "S"),
            ("AML_CHECK", 0, "S"), ("ADDRESS", 0, "M"),
        ])
        out = tools.measure_schema_coverage("CUSTOMER")

        assert out["dimension"] == "D3_SCHEMA_COVERAGE"
        assert out["score"] == 50.0
        assert (out["oracle_total"], out["enabled"], out["disabled"]) == (4, 2, 2)
        assert out["easy_wins_count"] == 1
        assert out["easy_wins_sample"] == ["AML_CHECK"]

    def test_schema_absent_donne_un_score_nul(self, patch_quality_db):
        patch_quality_db.add("SCHEMA_SOURCE", [])
        assert tools.measure_schema_coverage("CUSTOMER")["score"] == 0

    def test_nom_logique_t24_est_utilise_pour_la_recherche(self, patch_quality_db):
        patch_quality_db.add("SCHEMA_SOURCE", [])
        tools.measure_schema_coverage("FUNDS_TRANSFER_HIS")
        _, params = patch_quality_db.executed[-1]
        assert params[0] == "FUNDS_TRANSFER#HIS"

    def test_echantillon_de_gains_faciles_plafonne_a_dix(self, patch_quality_db):
        patch_quality_db.add("SCHEMA_SOURCE", [(f"C{i}", 0, "S") for i in range(25)])
        out = tools.measure_schema_coverage("CUSTOMER")
        assert out["easy_wins_count"] == 25
        assert len(out["easy_wins_sample"]) == 10


# ╔══════════════════════════════════════════════════════════════════╗
# ║  D4 — Conformite                                                 ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestMeasureConformity:

    def test_colonnes_dates_et_nombres_sont_controlees(self, patch_quality_db):
        violations = {"BIRTH_INCORP_DATE": (20, 100), "AGE": (0, 100)}
        patch_quality_db.add("DATA_TYPE LIKE '%char%'",
                             [("BIRTH_INCORP_DATE",), ("AGE",), ("MNEMONIC",)])
        patch_quality_db.add(
            "SUM(CASE WHEN",
            lambda sql, _: [next(v for c, v in violations.items() if f"[{c}]" in sql)],
        )
        out = tools.measure_conformity("CUSTOMER")

        assert out["dimension"] == "D4_CONFORMITY"
        assert out["columns_checked"] == 2  # MNEMONIC n'a aucun indice de type
        assert out["violations_found"] == 20
        assert out["score"] == 90.0
        assert out["worst_columns"][0]["column"] == "BIRTH_INCORP_DATE"
        assert out["worst_columns"][0]["expected_type"] == "DATE"

    def test_isdate_utilise_pour_les_dates_isnumeric_pour_les_nombres(self, patch_quality_db):
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("OPENING_DATE",), ("AMOUNT",)])
        patch_quality_db.add("SUM(CASE WHEN", [(0, 10)])
        tools.measure_conformity("CUSTOMER")

        sqls = patch_quality_db.queries_matching("SUM(CASE WHEN")
        assert any("ISDATE([OPENING_DATE])" in s for s in sqls)
        assert any("ISNUMERIC([AMOUNT])" in s for s in sqls)

    def test_aucune_colonne_typable_donne_un_score_parfait(self, patch_quality_db):
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("MNEMONIC",)])
        out = tools.measure_conformity("CUSTOMER")
        assert out["score"] == 100.0
        assert out["columns_checked"] == 0

    def test_colonne_entierement_nulle_est_ignoree(self, patch_quality_db):
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("BIRTH_DATE",)])
        patch_quality_db.add("SUM(CASE WHEN", [(0, 0)])
        assert tools.measure_conformity("CUSTOMER")["columns_checked"] == 0

    def test_worst_columns_plafonne_a_cinq(self, patch_quality_db):
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [(f"DATE_{i}",) for i in range(8)])
        patch_quality_db.add("SUM(CASE WHEN", [(5, 10)])
        assert len(tools.measure_conformity("CUSTOMER")["worst_columns"]) == 5


# ╔══════════════════════════════════════════════════════════════════╗
# ║  D5 — Fraicheur                                                  ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestMeasureFreshness:

    def test_perte_de_cinq_points_par_jour(self, patch_quality_db):
        il_y_a_deux_jours = datetime.now() - timedelta(days=2)
        patch_quality_db.add("BATCH_JOB_EXECUTION", [(42, il_y_a_deux_jours.isoformat())])

        out = tools.measure_freshness()
        assert out["dimension"] == "D5_FRESHNESS"
        assert out["score"] == pytest.approx(90.0, abs=0.2)
        assert out["age_days"] == pytest.approx(2.0, abs=0.1)
        assert out["last_success"]["job_id"] == 42

    def test_score_plancher_a_zero(self, patch_quality_db):
        vieux = datetime.now() - timedelta(days=90)
        patch_quality_db.add("BATCH_JOB_EXECUTION", [(1, vieux.isoformat())])
        assert tools.measure_freshness()["score"] == 0

    def test_aucune_extraction_reussie(self, patch_quality_db):
        patch_quality_db.add("BATCH_JOB_EXECUTION", [])
        out = tools.measure_freshness()
        assert out["score"] == 0
        assert out["last_success"] is None
        assert out["age_days"] is None

    def test_dates_illisibles_sont_ignorees(self, patch_quality_db):
        recent = datetime.now() - timedelta(days=1)
        patch_quality_db.add("BATCH_JOB_EXECUTION",
                             [(1, "date-corrompue"), (2, recent.isoformat())])
        out = tools.measure_freshness()
        assert out["last_success"]["job_id"] == 2


# ╔══════════════════════════════════════════════════════════════════╗
# ║  D6 — Consistance                                                ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestMeasureConsistency:

    def test_detecte_le_bug_de_parsing_ull_et_les_chaines_vides(self, patch_quality_db):
        patch_quality_db.add(
            "='ULL'", lambda sql, _: [(30,)] if "[AML_CHECK]" in sql else [(0,)]
        )
        patch_quality_db.add(
            "OR [", lambda sql, _: [(0,)] if "[AML_CHECK]" in sql else [(20,)]
        )
        patch_quality_db.add(_exact("SELECT COUNT(*) FROM [CUSTOMER]"), [(1000,)])
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("AML_CHECK",), ("MNEMONIC",)])
        out = tools.measure_consistency("CUSTOMER")

        assert out["dimension"] == "D6_CONSISTENCY"
        assert out["total_issues"] == 50
        assert out["score"] == 95.0
        types = {(i["type"], i["column"], i["severity"]) for i in out["issues"]}
        assert ("PARSING_BUG", "AML_CHECK", "HIGH") in types
        assert ("EMPTY_STRING", "MNEMONIC", "MEDIUM") in types

    def test_table_saine_a_un_score_parfait(self, patch_quality_db):
        patch_quality_db.add("='ULL'", [(0,)])
        patch_quality_db.add("OR [", [(0,)])
        patch_quality_db.add(_exact("SELECT COUNT(*) FROM [CUSTOMER]"), [(100,)])
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("MNEMONIC",)])
        out = tools.measure_consistency("CUSTOMER")
        assert out["score"] == 100
        assert out["issues"] == []

    def test_score_plancher_a_zero(self, patch_quality_db):
        patch_quality_db.add("='ULL'", [(10,)])
        patch_quality_db.add("OR [", [(10,)])
        patch_quality_db.add(_exact("SELECT COUNT(*) FROM [CUSTOMER]"), [(10,)])
        patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("A",), ("B",)])
        assert tools.measure_consistency("CUSTOMER")["score"] == 0


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Score global                                                    ║
# ╚══════════════════════════════════════════════════════════════════╝

@pytest.fixture
def stub_dimensions(monkeypatch):
    """Fige les 6 mesures pour tester la seule ponderation."""

    def _apply(**scores):
        defaults = {"D1": 100.0, "D2": 100.0, "D3": 100.0,
                    "D4": 100.0, "D5": 100.0, "D6": 100.0}
        defaults.update(scores)
        mapping = {
            "measure_completeness": "D1", "measure_uniqueness": "D2",
            "measure_schema_coverage": "D3", "measure_conformity": "D4",
            "measure_consistency": "D6",
        }
        for name, dim in mapping.items():
            monkeypatch.setattr(
                tools, name,
                lambda *a, _d=dim, **k: {"score": defaults[_d]},
            )
        monkeypatch.setattr(tools, "measure_freshness", lambda: {"score": defaults["D5"]})
        return defaults

    return _apply


class TestComputeQualityScore:

    def test_ponderation_des_six_dimensions(self, stub_dimensions):
        stub_dimensions(D1=80.0, D2=60.0, D3=40.0, D4=50.0, D5=100.0, D6=20.0)
        out = tools.compute_quality_score("CUSTOMER")

        # 80*.25 + 60*.25 + 40*.15 + 50*.15 + 100*.10 + 20*.10 = 60.5
        assert out["global_score"] == 60.5
        assert out["table"] == "CUSTOMER"
        assert set(out["dimensions"]) == {"D1", "D2", "D3", "D4", "D5", "D6"}
        assert out["dimensions"]["D1"] == {"score": 80.0, "weight": 0.25}

    def test_somme_des_poids_vaut_un(self):
        assert sum(tools.WEIGHTS.values()) == pytest.approx(1.0)

    @pytest.mark.parametrize("score,grade", [
        (100.0, "A"), (80.0, "A"), (79.9, "B"), (60.0, "B"),
        (59.9, "C"), (40.0, "C"), (39.9, "D"), (20.0, "D"), (19.9, "F"), (0.0, "F"),
    ])
    def test_bareme_des_grades(self, score, grade):
        assert tools._grade_from_score(score) == grade

    def test_dimension_critique_cassee_plafonne_le_grade_a_f(self, stub_dimensions):
        # D2 a 5 % : meme avec un score global correct, la table est inexploitable.
        stub_dimensions(D2=5.0)
        out = tools.compute_quality_score("CUSTOMER")

        assert out["global_score"] == 76.2
        assert out["grade"] == "F"
        assert out["capped_to_F_by"] == ["D2"]

    def test_les_deux_dimensions_critiques_sont_signalees(self, stub_dimensions):
        stub_dimensions(D1=10.0, D2=10.0)
        assert tools.compute_quality_score("CUSTOMER")["capped_to_F_by"] == ["D1", "D2"]

    def test_pas_de_plafonnement_au_dessus_du_seuil(self, stub_dimensions):
        stub_dimensions(D1=40.0, D2=40.0)
        out = tools.compute_quality_score("CUSTOMER")
        assert out["capped_to_F_by"] == []
        assert out["grade"] == "B"

    def test_plan_d_amelioration_trie_par_gain(self, stub_dimensions):
        stub_dimensions(D1=50.0, D2=90.0, D3=0.0, D6=0.0)
        improvements = tools.compute_quality_score("CUSTOMER")["improvements_by_impact"]

        gains = [i["max_gain"] for i in improvements]
        assert gains == sorted(gains, reverse=True)
        # D3 (0 -> gain 15.0) passe devant D1 (50 -> gain 12.5) malgre son poids
        # plus faible : le plan est classe par gain reel, pas par ponderation.
        assert [i["dimension"] for i in improvements] == ["D3", "D1", "D6"]
        assert improvements[0]["max_gain"] == 15.0
        assert improvements[0]["action"]

    def test_dimension_au_dessus_de_80_sort_du_plan(self, stub_dimensions):
        stub_dimensions()
        assert tools.compute_quality_score("CUSTOMER")["improvements_by_impact"] == []

    def test_arguments_parasites_du_slm_sont_ignores(self, stub_dimensions):
        # Regression : le SLM continue d'envoyer d1..d6 (ancienne signature).
        stub_dimensions(D1=80.0)
        out = tools.compute_quality_score(
            table_name="CUSTOMER", d1=99, d2=99, d3=99, d4=99, d5=99, d6=99
        )
        assert out["dimensions"]["D1"]["score"] == 80.0

    def test_table_hors_liste_blanche_est_refusee(self, stub_dimensions):
        stub_dimensions()
        with pytest.raises(ValueError):
            tools.compute_quality_score("UTILISATEURS")

    def test_nom_physique_est_renvoye(self, stub_dimensions):
        stub_dimensions()
        assert tools.compute_quality_score("FUNDS_TRANSFER_HIS")["table"] == "FUNDS_TRANSFER#HIS"


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Phase 2 — anomalies                                             ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestCrossValidate:

    def test_combinaisons_rares_sont_isolees(self, patch_quality_db):
        patch_quality_db.add("GROUP BY", [
            ("100", "400", 900), ("200", "401", 90), ("300", "402", 10),
        ])
        out = tools.cross_validate("CUSTOMER", "ACCOUNT_OFFICER", "ACCOM_TYPE")

        assert out["total_combinations"] == 3
        assert out["rare_combinations"] == [{"col_a": "300", "col_b": "402", "count": 10}]

    def test_aucune_donnee_ne_divise_pas_par_zero(self, patch_quality_db):
        patch_quality_db.add("GROUP BY", [])
        out = tools.cross_validate("CUSTOMER", "A", "B")
        assert out["total_combinations"] == 0
        assert out["rare_combinations"] == []

    def test_echantillon_plafonne_a_vingt(self, patch_quality_db):
        patch_quality_db.add("GROUP BY", [(str(i), "x", 10) for i in range(30)])
        assert len(tools.cross_validate("CUSTOMER", "A", "B")["all_combinations"]) == 20


class TestCheckBusinessRules:

    def test_violations_et_exemples(self, patch_quality_db):
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER] WHERE", [(3,)])
        patch_quality_db.add("SELECT TOP 5 _id", [("C1",), ("C2",)])
        out = tools.check_business_rules("CUSTOMER", [
            {"name": "aml_invalide", "condition": "AML_CHECK NOT IN ('YES','NO')"},
        ])

        assert out["results"] == [
            {"rule": "aml_invalide", "violations": 3, "examples": ["C1", "C2"]}
        ]

    def test_aucune_violation_n_interroge_pas_les_exemples(self, patch_quality_db):
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER] WHERE", [(0,)])
        out = tools.check_business_rules("CUSTOMER", [{"name": "r", "condition": "AGE < 0"}])
        assert out["results"][0]["examples"] == []
        assert patch_quality_db.queries_matching("TOP 5 _id") == []

    @pytest.mark.parametrize("condition", [
        "1=1; DROP TABLE CUSTOMER",
        "1=1 -- commentaire",
        "EXISTS (SELECT 1) AND EXEC sp_who",
        "AGE < 0 DELETE",
        "1=1 UPDATE CUSTOMER SET AGE=0",
        "1=1 INSERT INTO x VALUES (1)",
        "1=1 ALTER TABLE x",
    ])
    def test_sql_dangereux_est_bloque(self, patch_quality_db, condition):
        # Regression : le SLM redige librement la clause WHERE.
        out = tools.check_business_rules("CUSTOMER", [{"name": "r", "condition": condition}])
        assert out["results"][0]["error"] == "SQL interdit"
        assert patch_quality_db.executed == []

    def test_regles_plafonnees_a_dix(self, patch_quality_db):
        patch_quality_db.add("SELECT COUNT(*) FROM [CUSTOMER] WHERE", [(0,)])
        rules = [{"name": f"r{i}", "condition": "AGE < 0"} for i in range(15)]
        assert len(tools.check_business_rules("CUSTOMER", rules)["results"]) == 10

    def test_regle_sans_condition_est_neutralisee(self, patch_quality_db):
        patch_quality_db.add("WHERE 1=0", [(0,)])
        out = tools.check_business_rules("CUSTOMER", [{}])
        assert out["results"][0]["rule"] == "unnamed"
        assert out["results"][0]["violations"] == 0


class TestGetColumnStats:

    def test_statistiques_et_distribution(self, patch_quality_db):
        patch_quality_db.add("as total", [(100, 25, 12, "400", "402")])
        patch_quality_db.add("TOP 10", [("400", 50), ("401", 25)])
        out = tools.get_column_stats("CUSTOMER", "ACCOM_TYPE")

        assert out["column"] == "ACCOM_TYPE"
        assert (out["total"], out["nulls"], out["null_pct"], out["distinct"]) == (100, 25, 25.0, 12)
        assert (out["min"], out["max"]) == ("400", "402")
        assert out["top_values"] == [{"value": "400", "count": 50}, {"value": "401", "count": 25}]

    def test_table_vide(self, patch_quality_db):
        patch_quality_db.add("as total", [(0, 0, 0, None, None)])
        patch_quality_db.add("TOP 10", [])
        out = tools.get_column_stats("CUSTOMER", "AGE")
        assert out["null_pct"] == 0
        assert out["min"] is None and out["max"] is None


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Registre des tools                                              ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestRegistre:

    def test_chaque_definition_a_une_implementation(self):
        declares = {t["function"]["name"] for t in tools.ALL_TOOL_DEFINITIONS}
        assert declares == set(tools.TOOL_REGISTRY)

    def test_definitions_conformes_a_la_spec_openai(self):
        for definition in tools.ALL_TOOL_DEFINITIONS:
            assert definition["type"] == "function"
            fn = definition["function"]
            assert fn["name"] and fn["description"]
            assert fn["parameters"]["type"] == "object"
            assert set(fn["parameters"]["required"]) <= set(fn["parameters"]["properties"])

    def test_les_dix_tools_sont_exposes(self):
        assert len(tools.ALL_TOOL_DEFINITIONS) == 10
        assert len(tools.QUALITY_TOOLS) == 7
        assert len(tools.ANOMALY_TOOLS) == 3
