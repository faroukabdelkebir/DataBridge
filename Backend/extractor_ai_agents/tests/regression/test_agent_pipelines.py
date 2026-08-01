"""
Tests de non-regression de bout en bout des deux agents.

Ici rien n'est stubbe a l'interieur du projet : la vraie boucle ReAct execute les
vrais tools. Seules les deux frontieres externes sont doublees — SQL Server
(`FakeDB`) et Ollama (`FakeOllamaClient`). Ces tests verrouillent donc le contrat
de sortie des agents face a un SLM 7B qui se comporte mal (paraphrase, boucle,
arguments inventes, JSON tronque), ce qui est le mode de defaillance courant.
"""

import json
from datetime import datetime, timedelta

import pytest

from agent_anomaly_quality.agents.anomaly_quality_agent import AnomalyQualityAgent
from agent_scheduler.agents import scheduler_agent as m3
from agent_scheduler.agents.scheduler_agent import SchedulerAgent
from tests.helpers import FakeResult, make_agent, slm_text, slm_tool_call


def exact(sql):
    return lambda execute: execute == sql


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Base simulee : table CUSTOMER degradee (cas reel du projet)      ║
# ╚══════════════════════════════════════════════════════════════════╝

@pytest.fixture
def db_customer(patch_quality_db):
    """2600 lignes pour 50 clients : extraction Full rejouee sans TRUNCATE."""
    recent = (datetime.now() - timedelta(days=2)).isoformat()

    patch_quality_db.add("IS NOT NULL AND ISDATE", [(0, 2600)])
    patch_quality_db.add("IS NULL THEN 1", [(650,)])
    patch_quality_db.add("ORDER BY ORDINAL_POSITION",
                         [("_id",), ("MNEMONIC",), ("BIRTH_INCORP_DATE",)])
    patch_quality_db.add("DATA_TYPE LIKE '%char%'", [("MNEMONIC",), ("BIRTH_INCORP_DATE",)])
    patch_quality_db.add("COUNT(DISTINCT", [(50,)])
    patch_quality_db.add("HAVING COUNT(*)>1", [("C1", 52), ("C2", 52)])
    patch_quality_db.add("SCHEMA_SOURCE", [("_id", 1, "S"), ("AML_CHECK", 0, "S")])
    patch_quality_db.add("BATCH_JOB_EXECUTION", [(88, recent)])
    patch_quality_db.add("='ULL'", [(0,)])
    patch_quality_db.add("OR [", [(0,)])
    patch_quality_db.add(exact("SELECT COUNT(*) FROM [CUSTOMER]"), [(2600,)])
    return patch_quality_db


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Base simulee : historique Spring Batch                          ║
# ╚══════════════════════════════════════════════════════════════════╝

@pytest.fixture
def db_batch(patch_scheduling_db):
    """3 succes le soir, 6 echecs COB_RUNNING en pleine journee."""
    soir = [("2025-06-%02dT21:00:00" % j, "2025-06-%02dT21:02:00" % j, "COMPLETED")
            for j in (9, 10, 11)]
    jour = [("2025-06-%02dT11:00:00" % j, "2025-06-%02dT11:00:05" % j, "FAILED")
            for j in (9, 10, 11, 12, 13, 14)]

    patch_scheduling_db.add("BATCH_JOB_EXECUTION je", [
        (i, statut, "COB_RUNNING" if statut == "FAILED" else "", debut, debut, fin, "", 0, 2)
        for i, (debut, fin, statut) in enumerate(soir + jour, start=1)
    ])
    patch_scheduling_db.add("SELECT START_TIME, STATUS, END_TIME",
                            [(d, s, f) for d, f, s in soir + jour])
    patch_scheduling_db.add("SELECT START_TIME, END_TIME, STATUS", soir + jour)
    patch_scheduling_db.add("SELECT JOB_EXECUTION_ID, STATUS", [
        (i, statut, debut, fin) for i, (debut, fin, statut) in enumerate(soir + jour, 1)
    ])
    patch_scheduling_db.add("BATCH_CONFIG", FakeResult(
        rows=[("CUSTOMER", "CUSTOMER", "Full", 1, 1, "10:00", "2025-06-14"),
              ("AA.BILL.DETAILS", "AA.BILL.DETAILS", "Incremental", 1, 1, "10:30", "2025-06-14"),
              ("FUNDS.TRANSFER$HIS", "FUNDS.TRANSFER#HIS", "Incremental", 1, 1, "11:00",
               "2025-06-14")],
        columns=["id", "TableName", "chargement", "enabled", "scheduler",
                 "scheduler_time", "last_date"],
    ))
    patch_scheduling_db.add("WHERE STATUS = 'FAILED'", [(4,)])
    patch_scheduling_db.add("BATCH_STEP_EXECUTION WHERE", [
        ("COB_STATUS", "FAILED", "2025-06-12T11:00:00", "2025-06-12T11:00:01",
         0, 0, "COB_RUNNING"),
    ])
    return patch_scheduling_db


# ╔══════════════════════════════════════════════════════════════════╗
# ║  M1 — audit de qualite de bout en bout                           ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAuditQualiteBoutEnBout:

    def _agent(self, reponses_slm):
        return make_agent(AnomalyQualityAgent, reponses_slm)

    def test_rapport_chiffre_reste_deterministe_malgre_un_slm_bavard(self, db_customer):
        # Le SLM annonce un grade "A" : le rapport rendu doit l'ignorer.
        agent = self._agent([
            slm_tool_call("measure_completeness", {"table_name": "CUSTOMER"}),
            slm_tool_call("measure_uniqueness", {"table_name": "CUSTOMER"}),
            slm_text('{"quality_score": {"global": 98, "grade": "A"}}'),
            slm_text("La table contient de nombreux doublons."),
        ])
        report = agent.audit_table("CUSTOMER")

        assert report["grade"] == "F"
        assert report["global_score"] == pytest.approx(60.7, abs=0.2)
        assert report["dimensions"]["D1"]["score"] == 75.0
        assert report["dimensions"]["D2"]["score"] == 1.9
        assert report["dimensions"]["D3"]["score"] == 50.0

    def test_unicite_effondree_plafonne_le_grade_a_f(self, db_customer):
        # Regression : un score global "moyen" masquait une table inexploitable.
        agent = self._agent([slm_text('{"fait"}'), slm_text("interprétation")])
        report = agent.audit_table("CUSTOMER")

        assert report["global_score"] > 40  # note ponderee correcte...
        assert report["grade"] == "F"       # ...mais D2 a 1,9 % la disqualifie
        assert report["capped_to_F_by"] == ["D2"]

    def test_plan_d_amelioration_priorise_les_doublons(self, db_customer):
        agent = self._agent([slm_text("{}"), slm_text("interprétation")])
        report = agent.audit_table("CUSTOMER")

        assert report["improvements_by_impact"][0]["dimension"] == "D2"
        assert "TRUNCATE" in report["improvements_by_impact"][0]["action"]

    def test_interpretation_et_trace_accompagnent_le_rapport(self, db_customer):
        agent = self._agent([
            slm_tool_call("measure_completeness", {"table_name": "CUSTOMER"}),
            slm_text("{}"),
            slm_text("La table est inexploitable en l'état."),
        ])
        report = agent.audit_table("CUSTOMER")

        assert report["interpretation"] == "La table est inexploitable en l'état."
        assert report["reasoning_trace"] == [
            {"iteration": 1, "tool": "measure_completeness",
             "input": {"table_name": "CUSTOMER"}}
        ]
        assert report["total_tokens"] > 0

    def test_contrat_de_sortie_du_rapport(self, db_customer):
        agent = self._agent([slm_text("{}"), slm_text("interprétation")])
        report = agent.audit_table("CUSTOMER")

        assert set(report) >= {
            "table", "global_score", "grade", "capped_to_F_by", "dimensions",
            "improvements_by_impact", "interpretation", "reasoning_trace",
            "total_iterations", "input_tokens", "output_tokens", "total_tokens",
        }
        assert set(report["dimensions"]) == {"D1", "D2", "D3", "D4", "D5", "D6"}
        json.dumps(report)  # le rapport doit rester serialisable pour l'API

    def test_tool_hallucine_par_le_slm_n_interrompt_pas_l_audit(self, db_customer):
        # Regression : un nom de tool invente faisait remonter une 500.
        agent = self._agent([
            slm_tool_call("measure_data_quality_v2", {"table": "CUSTOMER"}),
            slm_text("{}"),
            slm_text("interprétation"),
        ])
        report = agent.audit_table("CUSTOMER")
        assert report["grade"] == "F"

    def test_arguments_inventes_par_le_slm_sont_absorbes(self, db_customer):
        # Regression : le 7B reinjecte la sortie d'un tool comme arguments du suivant.
        agent = self._agent([
            slm_tool_call("measure_uniqueness",
                          {"table_name": "CUSTOMER", "D1_COMPLETENESS": {"score": 75}}),
            slm_text("{}"),
            slm_text("interprétation"),
        ])
        report = agent.audit_table("CUSTOMER")
        assert report["dimensions"]["D2"]["score"] == 1.9

    def test_table_hors_liste_blanche_remonte_une_valueerror(self, db_customer):
        # Contrat attendu par la vue, qui la traduit en HTTP 400.
        agent = self._agent([slm_text("{}")])
        with pytest.raises(ValueError, match="Table non autorisée"):
            agent.audit_table("UTILISATEURS")

    def test_score_rapide_donne_les_memes_chiffres_que_l_audit(self, db_customer):
        rapide = self._agent([slm_text("{}")]).quick_score("CUSTOMER")
        complet = self._agent([slm_text("{}"), slm_text("i")]).audit_table("CUSTOMER")

        assert rapide["global_score"] == complet["global_score"]
        assert rapide["grade"] == complet["grade"]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  M3 — recommandation d'ordonnancement de bout en bout            ║
# ╚══════════════════════════════════════════════════════════════════╝

PLAN_SLM_CORRECT = {
    "analysis_summary": ("Le COB de T24 fait echouer les extractions lancees en "
                         "journee ; on les decale apres 21h avec un backoff exponentiel."),
    "recommended_schedule": {
        "CUSTOMER": {"recommended_time": "21:00", "reason": "apres le COB"},
        "AA.BILL.DETAILS": {"recommended_time": "21:15", "reason": "apres CUSTOMER"},
        "FUNDS.TRANSFER$HIS": {"recommended_time": "21:30", "reason": "apres AA.BILL"},
    },
    "risk_windows": [{"window": "10:00-12:00", "risk": "CRITICAL", "reason": "COB"}],
    "adaptive_rules": ["backoff exponentiel apres 3 echecs"],
    "estimated_improvement": {"current_success_rate": 33, "projected_success_rate": 90},
}

APPELS_TOOLS = [
    slm_tool_call("get_batch_config", {}),
    slm_tool_call("get_job_history", {}),
    slm_tool_call("analyze_time_patterns", {}),
    slm_tool_call("get_failure_streaks", {}),
    slm_tool_call("estimate_optimal_window", {}),
]


class TestOrdonnancementBoutEnBout:

    def _plan(self, db_batch, reponses_finales, appels=APPELS_TOOLS):
        # repeat_last : quand l'agent relance le SLM pour forcer une conclusion,
        # un vrai Ollama repondrait a nouveau. La boucle s'arrete alors sur
        # max_iterations, exactement comme en production.
        agent = make_agent(SchedulerAgent, list(appels) + list(reponses_finales),
                           repeat_last=True)
        return agent, agent.recommend_schedule()

    def test_plan_du_slm_conserve_quand_il_est_conforme(self, db_batch):
        _, plan = self._plan(db_batch, [slm_text(json.dumps(PLAN_SLM_CORRECT))])

        assert plan["_source"] == "slm"
        assert plan["recommended_schedule"]["CUSTOMER"]["recommended_time"] == "21:00"

    def test_horaire_en_pleine_zone_cob_est_corrige(self, db_batch):
        # Regression : le 7B recommandait parfois 11h, l'heure la plus mortelle.
        fautif = json.loads(json.dumps(PLAN_SLM_CORRECT))
        fautif["recommended_schedule"]["CUSTOMER"]["recommended_time"] = "11:00"
        _, plan = self._plan(db_batch, [slm_text(json.dumps(fautif))])

        assert plan["_source"] == "slm_corrige"
        corrige = plan["recommended_schedule"]["CUSTOMER"]["recommended_time"]
        assert m3._parse_hhmm(corrige)[0] not in m3.DANGER_HOURS
        # Les creneaux deja sains ne sont pas touches.
        assert plan["recommended_schedule"]["AA.BILL.DETAILS"]["recommended_time"] == "21:15"

    def test_json_tronque_bascule_sur_le_filet_deterministe(self, db_batch):
        _, plan = self._plan(db_batch, [
            slm_text("Voici mon analyse detaillee des creneaux, sans JSON exploitable."),
        ])

        assert plan["_source"] == "fallback_deterministe"
        assert set(plan["recommended_schedule"]) == {
            "CUSTOMER", "AA.BILL.DETAILS", "FUNDS.TRANSFER$HIS"}

    def test_filet_deterministe_reprend_les_chiffres_mesures(self, db_batch):
        # 3 succes sur 9 executions -> 33,3 % de taux de succes constate.
        _, plan = self._plan(db_batch, [slm_text("pas de json")])
        assert plan["estimated_improvement"]["current_success_rate"] == 33.3
        assert plan["estimated_improvement"]["projected_success_rate"] > 60

    def test_filet_deterministe_reprend_la_configuration_reelle(self, db_batch):
        _, plan = self._plan(db_batch, [slm_text("pas de json")])
        assert plan["current_config"]["CUSTOMER"] == {"current_time": "10:00", "type": "Full"}
        assert plan["recommended_schedule"]["CUSTOMER"]["load_type"] == "Full"

    @pytest.mark.parametrize("reponse", [
        "",
        "texte libre sans structure",
        "{}",
        '{"recommended_schedule": {}}',
        '{"recommended_schedule": {"CUSTOMER": {"recommended_time": "midi"}}}',
        '{"recommended_schedule": {"CUSTOMER": {"recommended_time": "11:00"}}',
        json.dumps(PLAN_SLM_CORRECT),
    ])
    def test_le_plan_rendu_est_toujours_exploitable(self, db_batch, reponse):
        # Contrat non negociable : quoi que produise le SLM, l'API renvoie un
        # planning complet dont aucun creneau ne tombe en zone COB.
        _, plan = self._plan(db_batch, [slm_text(reponse)])

        assert plan["recommended_schedule"]
        for creneau in plan["recommended_schedule"].values():
            heure = creneau["recommended_time"] if isinstance(creneau, dict) else creneau
            hhmm = m3._parse_hhmm(heure)
            assert hhmm is not None
            assert hhmm[0] not in m3.DANGER_HOURS
        assert plan["estimated_improvement"]["projected_success_rate"] > 60
        json.dumps(plan)

    def test_trace_de_raisonnement_liste_les_tools_appeles(self, db_batch):
        _, plan = self._plan(db_batch, [slm_text(json.dumps(PLAN_SLM_CORRECT))])

        assert [e["tool"] for e in plan["reasoning_trace"]] == [
            "get_batch_config", "get_job_history", "analyze_time_patterns",
            "get_failure_streaks", "estimate_optimal_window",
        ]
        assert plan["total_iterations"] == 6

    def test_boucle_du_slm_ne_bloque_pas_la_recommandation(self, db_batch):
        # Regression : le 7B rappelait le meme outil jusqu'a max_iterations.
        agent, plan = self._plan(
            db_batch,
            [slm_text(json.dumps(PLAN_SLM_CORRECT))],
            appels=[slm_tool_call("get_job_history", {})] * 3,
        )

        assert plan["recommended_schedule"]
        assert plan.get("parse_error") is None
        assert len(db_batch.queries_matching("BATCH_JOB_EXECUTION je")) == 1  # cache

    def test_panne_sql_pendant_l_analyse_ne_casse_pas_le_plan(self, patch_scheduling_db):
        # Aucun tool ne peut repondre : le filet doit quand meme produire un plan.
        patch_scheduling_db.add(
            lambda sql: True,
            lambda *_: (_ for _ in ()).throw(RuntimeError("SQL Server injoignable")),
        )
        agent = make_agent(SchedulerAgent, APPELS_TOOLS + [slm_text("pas de json")],
                           repeat_last=True)
        plan = agent.recommend_schedule()

        assert plan["_source"] == "fallback_deterministe"
        assert set(plan["recommended_schedule"]) == {
            "CUSTOMER", "AA.BILL.DETAILS", "FUNDS.TRANSFER$HIS"}

    def test_ollama_eteint_remonte_une_reponse_lisible(self, db_batch):
        # ATTENTION — comportement actuel, volontairement fige ici :
        # `BaseAgent.run()` sort par `return AgentResult(error=...)` SANS passer
        # par `finalize()` quand Ollama leve. Le filet deterministe ne joue donc
        # pas, et l'API renvoie un plan brut au lieu d'un planning. Le seul
        # contrat garanti est l'absence d'exception.
        agent = make_agent(SchedulerAgent, [ConnectionError("Ollama injoignable")])
        plan = agent.recommend_schedule()

        assert plan["parse_error"] is True
        assert plan["raw_answer"] == ""
        assert "recommended_schedule" not in plan

    def test_decision_temps_reel_reste_independante_du_slm(self, db_batch, freeze_hour):
        # Elle est appelee avant CHAQUE lancement par le scheduler Java.
        agent = make_agent(SchedulerAgent, [])
        with freeze_hour(21):
            decision = agent.should_launch_now("CUSTOMER")

        assert decision["launch"] is True
        assert agent.client.calls == []
