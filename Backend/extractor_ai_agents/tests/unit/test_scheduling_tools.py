"""
Tests unitaires des tools de l'agent M3 (ordonnancement).

Les 6 tools ne font que de l'agregation sur BATCH_JOB_EXECUTION /
BATCH_STEP_EXECUTION / BATCH_CONFIG : SQL Server est double par `FakeDB`.
"""

import pytest

from agent_scheduler.tools import scheduling_tools as tools
from tests.helpers import FakeResult


def job_row(job_id, status, start, end, exit_code="", message="",
            total_read=0, step_count=1):
    """Ligne telle que la renvoie la requete de get_job_history (9 colonnes)."""
    return (job_id, status, exit_code, start, start, end, message, total_read, step_count)


# ╔══════════════════════════════════════════════════════════════════╗
# ║  get_job_history                                                 ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestGetJobHistory:

    @pytest.fixture
    def db(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_JOB_EXECUTION je", [
            job_row(3, "COMPLETED", "2025-06-10T21:00:00", "2025-06-10T21:02:00", total_read=500),
            job_row(2, "FAILED", "2025-06-10T11:00:00", "2025-06-10T11:00:05",
                    exit_code="COB_RUNNING", message="COB en cours"),
            job_row(1, "COMPLETED", "2025-06-09T21:00:00", "2025-06-09T21:04:00", total_read=700),
        ])
        return patch_scheduling_db

    def test_taux_de_succes_global(self, db):
        out = tools.get_job_history()
        assert out["total_jobs"] == 3
        assert out["completed"] == 2
        assert out["failed"] == 1
        assert out["success_rate_pct"] == 66.7

    def test_duree_moyenne_des_jobs_reussis(self, db):
        assert tools.get_job_history()["avg_duration_completed"] == 180.0

    def test_repartition_par_heure(self, db):
        assert tools.get_job_history()["by_hour"] == {
            "11h": {"ok": 0, "fail": 1},
            "21h": {"ok": 2, "fail": 0},
        }

    def test_echantillons_reussite_et_echec(self, db):
        out = tools.get_job_history()
        assert [j["job_id"] for j in out["sample_failed"]] == [2]
        assert out["sample_failed"][0]["exit_code"] == "COB_RUNNING"
        assert [j["job_id"] for j in out["sample_completed"]] == [3, 1]

    def test_echantillons_plafonnes_a_trois(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_JOB_EXECUTION je", [
            job_row(i, "FAILED", "2025-06-10T11:00:00", "2025-06-10T11:00:01")
            for i in range(10)
        ])
        assert len(tools.get_job_history()["sample_failed"]) == 3

    def test_parametre_last_n_est_transmis(self, db):
        tools.get_job_history(last_n=200)
        assert db.executed[-1][1] == (200,)

    def test_historique_vide(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_JOB_EXECUTION je", [])
        out = tools.get_job_history()
        assert out["success_rate_pct"] == 0
        assert out["avg_duration_completed"] == 0
        assert out["by_hour"] == {}

    def test_job_sans_date_de_debut_ne_fait_pas_planter(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_JOB_EXECUTION je",
                                [job_row(1, "FAILED", None, None)])
        out = tools.get_job_history()
        assert out["by_hour"] == {}
        assert out["sample_failed"][0]["duration_sec"] is None

    def test_message_de_sortie_est_tronque(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_JOB_EXECUTION je", [
            job_row(1, "FAILED", "2025-06-10T11:00:00", "2025-06-10T11:00:01",
                    message="x" * 500),
        ])
        assert len(tools.get_job_history()["sample_failed"][0]["exit_message"]) == 200


# ╔══════════════════════════════════════════════════════════════════╗
# ║  analyze_time_patterns                                           ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAnalyzeTimePatterns:

    @pytest.fixture
    def db(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT START_TIME, STATUS, END_TIME", [
            ("2025-06-09T21:00:00", "COMPLETED", "2025-06-09T21:01:00"),
            ("2025-06-10T21:00:00", "COMPLETED", "2025-06-10T21:03:00"),
            ("2025-06-10T11:00:00", "FAILED", "2025-06-10T11:00:05"),
            ("2025-06-11T11:00:00", "FAILED", "2025-06-11T11:00:05"),
        ])
        return patch_scheduling_db

    def test_taux_de_succes_par_heure(self, db):
        by_hour = tools.analyze_time_patterns()["by_hour"]
        assert by_hour["21h"]["success_rate_pct"] == 100.0
        assert by_hour["11h"]["success_rate_pct"] == 0.0
        assert by_hour["21h"]["avg_duration_sec"] == 120.0

    def test_heures_formatees_sur_deux_chiffres(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT START_TIME, STATUS, END_TIME", [
            ("2025-06-09T03:00:00", "COMPLETED", "2025-06-09T03:01:00"),
        ])
        assert list(tools.analyze_time_patterns()["by_hour"]) == ["03h"]

    def test_meilleure_et_pire_heure(self, db):
        out = tools.analyze_time_patterns()
        assert out["best_hour"] == "21h"
        assert out["worst_hour"] == "11h"
        assert "21h" in out["recommendation"] and "11h" in out["recommendation"]

    def test_repartition_par_periode(self, db):
        periods = tools.analyze_time_patterns()["by_period"]
        assert periods["evening_18h_23h"]["success_rate_pct"] == 100.0
        assert periods["morning_6h_12h"]["success_rate_pct"] == 0.0

    @pytest.mark.parametrize("heure,periode", [
        (6, "morning_6h_12h"), (11, "morning_6h_12h"),
        (12, "afternoon_12h_18h"), (17, "afternoon_12h_18h"),
        (18, "evening_18h_23h"), (22, "evening_18h_23h"),
        (23, "night_23h_6h"), (2, "night_23h_6h"),
    ])
    def test_decoupage_des_periodes(self, patch_scheduling_db, heure, periode):
        patch_scheduling_db.add("SELECT START_TIME, STATUS, END_TIME", [
            (f"2025-06-09T{heure:02d}:00:00", "COMPLETED", f"2025-06-09T{heure:02d}:01:00"),
        ])
        assert list(tools.analyze_time_patterns()["by_period"]) == [periode]

    def test_repartition_par_jour(self, db):
        by_day = tools.analyze_time_patterns()["by_day"]
        assert by_day["Tuesday"]["total"] == 2  # 10 juin 2025
        assert by_day["Tuesday"]["success_rate_pct"] == 50.0

    def test_lignes_corrompues_sont_ignorees(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT START_TIME, STATUS, END_TIME", [
            ("date-illisible", "FAILED", None),
            ("2025-06-09T21:00:00", "COMPLETED", "2025-06-09T21:01:00"),
        ])
        assert tools.analyze_time_patterns()["by_hour"]["21h"]["total"] == 1

    def test_historique_vide(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT START_TIME, STATUS, END_TIME", [])
        out = tools.analyze_time_patterns()
        assert out["by_hour"] == {}
        assert out["best_hour"] is None and out["worst_hour"] is None


# ╔══════════════════════════════════════════════════════════════════╗
# ║  get_failure_streaks                                             ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestGetFailureStreaks:

    def test_series_d_echecs_consecutifs(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [
            (1, "FAILED", "2025-06-10T10:00:00", "2025-06-10T10:00:05"),
            (2, "FAILED", "2025-06-10T10:30:00", "2025-06-10T10:30:05"),
            (3, "FAILED", "2025-06-10T11:00:00", "2025-06-10T11:00:05"),
            (4, "COMPLETED", "2025-06-10T21:00:00", "2025-06-10T21:02:00"),
            (5, "FAILED", "2025-06-11T11:00:00", "2025-06-11T11:00:05"),
            (6, "COMPLETED", "2025-06-11T21:00:00", "2025-06-11T21:02:00"),
        ])
        out = tools.get_failure_streaks()

        assert out["total_streaks"] == 2
        assert out["max_streak_length"] == 3
        assert out["streaks"][0]["length"] == 3
        assert out["streaks"][0]["job_ids"] == [1, 2, 3]
        assert out["streaks"][0]["duration_minutes"] == 60.0

    def test_series_triees_de_la_plus_longue_a_la_plus_courte(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [
            (1, "FAILED", "2025-06-10T10:00:00", None),
            (2, "COMPLETED", "2025-06-10T21:00:00", None),
            (3, "FAILED", "2025-06-11T10:00:00", None),
            (4, "FAILED", "2025-06-11T10:30:00", None),
            (5, "COMPLETED", "2025-06-11T21:00:00", None),
        ])
        assert [s["length"] for s in tools.get_failure_streaks()["streaks"]] == [2, 1]

    def test_serie_encore_ouverte_en_fin_d_historique(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [
            (1, "COMPLETED", "2025-06-10T21:00:00", None),
            (2, "FAILED", "2025-06-11T10:00:00", None),
            (3, "FAILED", "2025-06-11T10:30:00", None),
        ])
        out = tools.get_failure_streaks()
        assert out["total_streaks"] == 1
        assert out["streaks"][0]["job_ids"] == [2, 3]

    def test_temps_de_recuperation_moyen(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [
            (1, "FAILED", "2025-06-10T20:00:00", None),
            (2, "COMPLETED", "2025-06-10T21:00:00", None),
            (3, "FAILED", "2025-06-11T20:00:00", None),
            (4, "COMPLETED", "2025-06-11T20:30:00", None),
        ])
        out = tools.get_failure_streaks()
        assert out["recovery_times_min"] == [60.0, 30.0]
        assert out["avg_recovery_time_min"] == 45.0

    def test_aucun_echec(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [
            (1, "COMPLETED", "2025-06-10T21:00:00", None),
        ])
        out = tools.get_failure_streaks()
        assert out["total_streaks"] == 0
        assert out["max_streak_length"] == 0
        assert out["avg_recovery_time_min"] is None
        assert "0 échecs consécutifs" in out["insight"]

    def test_historique_vide(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [])
        assert tools.get_failure_streaks()["total_streaks"] == 0


# ╔══════════════════════════════════════════════════════════════════╗
# ║  get_batch_config                                                ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestGetBatchConfig:

    def test_lecture_de_la_configuration(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_CONFIG", FakeResult(
            rows=[("CUSTOMER", "CUSTOMER", "Full", 1, 1, "20:00", "2025-06-10")],
            columns=["id", "TableName", "chargement", "enabled",
                     "scheduler", "scheduler_time", "last_date"],
        ))
        out = tools.get_batch_config()

        assert out["total_batches"] == 1
        assert out["batch_configs"][0] == {
            "batch_id": "CUSTOMER", "table_name": "CUSTOMER", "load_type": "Full",
            "enabled": True, "scheduler_enabled": True,
            "scheduler_time": "20:00", "last_date": "2025-06-10",
        }

    def test_colonnes_absentes_donnent_des_valeurs_neutres(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_CONFIG", FakeResult(rows=[("X",)], columns=["id"]))
        config = tools.get_batch_config()["batch_configs"][0]
        assert config["table_name"] == ""
        assert config["enabled"] is False

    def test_configuration_vide(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_CONFIG", FakeResult(rows=[], columns=["id"]))
        assert tools.get_batch_config() == {"batch_configs": [], "total_batches": 0}


# ╔══════════════════════════════════════════════════════════════════╗
# ║  get_step_timeline                                               ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestGetStepTimeline:

    STEPS = [
        ("COB_STATUS", "FAILED", "2025-06-10T11:00:00", "2025-06-10T11:00:01",
         0, 0, "COB_RUNNING"),
        ("EXTRACT", "COMPLETED", "2025-06-10T11:00:01", "2025-06-10T11:00:31", 500, 500, ""),
    ]

    def test_detail_des_steps_et_goulot(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_STEP_EXECUTION", self.STEPS)
        out = tools.get_step_timeline(job_execution_id=42)

        assert out["job_execution_id"] == 42
        assert out["total_steps"] == 2
        assert out["bottleneck_step"] == "EXTRACT"
        assert out["bottleneck_duration_sec"] == 30.0
        assert out["total_duration_sec"] == 31.0

    def test_blocage_cob_est_signale(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_STEP_EXECUTION", self.STEPS)
        assert tools.get_step_timeline(job_execution_id=42)["cob_blocked"] is True

    def test_absence_de_step_cob(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_STEP_EXECUTION", self.STEPS[1:])
        assert tools.get_step_timeline(job_execution_id=42)["cob_blocked"] is False

    @pytest.mark.parametrize("alias", ["job_id", "jobs", "id"])
    def test_alias_d_argument_du_slm_sont_acceptes(self, patch_scheduling_db, alias):
        # Regression : le 7B nomme rarement l'argument job_execution_id.
        patch_scheduling_db.add("BATCH_STEP_EXECUTION", self.STEPS)
        assert tools.get_step_timeline(**{alias: 7})["job_execution_id"] == 7

    def test_selection_automatique_du_dernier_job_echoue(self, patch_scheduling_db):
        patch_scheduling_db.add("WHERE STATUS = 'FAILED'", [(99,)])
        patch_scheduling_db.add("BATCH_STEP_EXECUTION WHERE", self.STEPS)
        assert tools.get_step_timeline()["job_execution_id"] == 99

    def test_aucun_job_echoue_disponible(self, patch_scheduling_db):
        patch_scheduling_db.add("WHERE STATUS = 'FAILED'", [])
        assert tools.get_step_timeline() == {"error": "Aucun job trouvé"}

    def test_erreur_de_selection_est_encapsulee(self, patch_scheduling_db):
        patch_scheduling_db.add("WHERE STATUS = 'FAILED'",
                                lambda *_: (_ for _ in ()).throw(RuntimeError("timeout")))
        out = tools.get_step_timeline()
        assert "timeout" in out["error"]

    def test_job_sans_step(self, patch_scheduling_db):
        patch_scheduling_db.add("BATCH_STEP_EXECUTION", [])
        out = tools.get_step_timeline(job_execution_id=1)
        assert out["total_steps"] == 0
        assert out["bottleneck_step"] is None
        assert out["total_duration_sec"] == 0


# ╔══════════════════════════════════════════════════════════════════╗
# ║  estimate_optimal_window                                         ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestEstimateOptimalWindow:

    @pytest.fixture
    def db(self, patch_scheduling_db):
        rows = []
        for _ in range(6):  # 21h : que des succes rapides
            rows.append(("2025-06-10T21:00:00", "2025-06-10T21:00:02", "COMPLETED"))
        for _ in range(6):  # 11h : que des echecs
            rows.append(("2025-06-10T11:00:00", "2025-06-10T11:00:05", "FAILED"))
        patch_scheduling_db.add("SELECT START_TIME, END_TIME, STATUS", rows)
        return patch_scheduling_db

    def test_les_24_heures_sont_notees(self, db):
        assert len(tools.estimate_optimal_window()["all_hours"]) == 24

    def test_creneau_du_soir_est_recommande(self, db):
        out = tools.estimate_optimal_window()
        assert out["best_hour"] == "21:00"
        assert out["all_hours"]["21:00"]["success_rate_pct"] == 100.0
        assert out["all_hours"]["11:00"]["success_rate_pct"] == 0.0

    def test_creneau_le_pire_est_signale(self, db):
        assert tools.estimate_optimal_window()["worst_hour"] == "11:00"

    def test_top_et_flop_contiennent_trois_creneaux(self, db):
        out = tools.estimate_optimal_window()
        assert len(out["recommended_windows"]) == 3
        assert len(out["avoid_windows"]) == 3

    def test_heure_sans_donnee_recoit_un_score_neutre(self, db):
        creneau = tools.estimate_optimal_window()["all_hours"]["04:00"]
        assert creneau == {"score": 50, "confidence": "no_data",
                           "success_rate": None, "avg_duration": None, "sample_size": 0}

    @pytest.mark.parametrize("nb,confiance", [(6, "high"), (3, "medium"), (1, "low")])
    def test_niveau_de_confiance_selon_l_echantillon(self, patch_scheduling_db, nb, confiance):
        patch_scheduling_db.add("SELECT START_TIME, END_TIME, STATUS", [
            ("2025-06-10T21:00:00", "2025-06-10T21:00:02", "COMPLETED")
        ] * nb)
        assert tools.estimate_optimal_window()["all_hours"]["21:00"]["confidence"] == confiance

    def test_lignes_corrompues_sont_ignorees(self, patch_scheduling_db):
        patch_scheduling_db.add("SELECT START_TIME, END_TIME, STATUS", [
            ("date-illisible", None, "FAILED"),
            ("2025-06-10T21:00:00", "2025-06-10T21:00:02", "COMPLETED"),
        ])
        assert tools.estimate_optimal_window()["all_hours"]["21:00"]["sample_size"] == 1


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Registre                                                        ║
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

    def test_les_tools_d_analyse_ne_demandent_aucun_argument(self):
        # Le prompt M3 promet au SLM que ces outils se lancent sans parametre.
        sans_argument = {"analyze_time_patterns", "get_failure_streaks",
                         "get_batch_config", "estimate_optimal_window"}
        for definition in tools.ALL_TOOL_DEFINITIONS:
            if definition["function"]["name"] in sans_argument:
                assert definition["function"]["parameters"]["properties"] == {}

    def test_aucun_tool_n_a_de_parametre_obligatoire(self):
        for definition in tools.ALL_TOOL_DEFINITIONS:
            assert definition["function"]["parameters"]["required"] == []
