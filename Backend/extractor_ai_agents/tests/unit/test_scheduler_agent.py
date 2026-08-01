"""
Tests unitaires de l'agent M3 (agent_scheduler/agents/scheduler_agent.py).

Couvre les helpers deterministes, le filet de securite `finalize()` (SLM-first
avec correction ciblee) et la decision temps reel `should_launch_now()`.
"""

import json

import pytest

from agent_scheduler.agents import scheduler_agent as m3
from agent_scheduler.agents.scheduler_agent import SchedulerAgent
from core.base_agent import AgentResult, AgentStep
from tests.helpers import make_agent, tool_output_step


def step(tool_name, payload, iteration=1):
    return tool_output_step(AgentStep, iteration, tool_name, payload)


HISTORY = {"success_rate_pct": 36.0, "by_hour": {"11h": {"ok": 0, "fail": 5}}}
PATTERNS = {"by_hour": {"21h": {"success_rate_pct": 92.0},
                        "11h": {"success_rate_pct": 0.0}},
            "best_hour": "21h", "worst_hour": "11h"}
WINDOW = {"all_hours": {"21:00": {"success_rate_pct": 92.0}}}
STREAKS = {"max_streak_length": 4, "avg_recovery_time_min": 65.0}
CONFIG = {"batch_configs": [
    {"batch_id": "CUSTOMER", "load_type": "Full", "scheduler_time": "10:00"},
    {"batch_id": "AA.BILL.DETAILS", "load_type": "Incremental", "scheduler_time": "10:30"},
    {"batch_id": "FUNDS.TRANSFER$HIS", "load_type": "Incremental", "scheduler_time": "11:00"},
]}


@pytest.fixture
def steps_complets():
    return [
        step("get_batch_config", CONFIG),
        step("get_job_history", HISTORY),
        step("analyze_time_patterns", PATTERNS),
        step("get_failure_streaks", STREAKS),
        step("estimate_optimal_window", WINDOW),
    ]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Helpers deterministes                                           ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestHelpers:

    @pytest.mark.parametrize("valeur,attendu", [
        ("20:00", (20, 0)), ("21:35", (21, 35)), ("9", (9, 0)), ("00:00", (0, 0)),
    ])
    def test_parse_hhmm_valide(self, valeur, attendu):
        assert m3._parse_hhmm(valeur) == attendu

    @pytest.mark.parametrize("valeur", ["25:00", "20:99", "-1:00", "apres le COB", None, ""])
    def test_parse_hhmm_invalide(self, valeur):
        assert m3._parse_hhmm(valeur) is None

    @pytest.mark.parametrize("valeur,attendu", [
        ("20h", 20), ("20:00", 20), (" 21 ", 21), ("bad", None), (None, None),
    ])
    def test_hour_int(self, valeur, attendu):
        assert m3._hour_int(valeur) == attendu

    def test_safe_slot_espace_les_batchs(self):
        assert [m3._safe_slot(21, i) for i in range(3)] == ["21:00", "21:15", "21:25"]

    def test_safe_slot_espacement_minimal_de_dix_minutes(self):
        creneaux = [m3._parse_hhmm(m3._safe_slot(21, i)) for i in range(6)]
        minutes = [h * 60 + m for h, m in creneaux]
        assert all(b - a >= 10 for a, b in zip(minutes, minutes[1:]))

    @pytest.mark.parametrize("heure", sorted(m3.DANGER_HOURS))
    def test_safe_slot_refuse_les_heures_a_risque(self, heure):
        creneau = m3._safe_slot(heure, 0)
        assert m3._parse_hhmm(creneau)[0] == m3.DEFAULT_BASE_HOUR
        assert m3._parse_hhmm(creneau)[0] not in m3.DANGER_HOURS

    def test_safe_load_tolere_le_texte_libre(self):
        assert m3._safe_load('{"a": 1}') == {"a": 1}
        assert m3._safe_load("pas du json") is None

    def test_collect_tool_outputs_garde_le_dernier_appel_valide(self):
        outputs = m3._collect_tool_outputs([
            step("get_job_history", {"success_rate_pct": 10}),
            step("get_job_history", {"success_rate_pct": 36}, iteration=2),
        ])
        assert outputs["get_job_history"]["success_rate_pct"] == 36

    def test_collect_tool_outputs_ecarte_les_erreurs(self):
        outputs = m3._collect_tool_outputs([
            step("get_job_history", {"error": "SQL Server injoignable"}),
            step("analyze_time_patterns", PATTERNS),
        ])
        assert "get_job_history" not in outputs
        assert "analyze_time_patterns" in outputs

    def test_collect_tool_outputs_ignore_les_etapes_sans_tool(self):
        etape = AgentStep(iteration=1, thought="reflexion", tool_name=None,
                          tool_input=None, tool_output=None, is_final=True)
        assert m3._collect_tool_outputs([etape]) == {}

    def test_hour_success_map_fusionne_les_trois_sources(self):
        scores = m3._hour_success_map(PATTERNS, WINDOW, HISTORY)
        assert scores[21] == 92.0
        assert scores[11] == 0.0

    def test_hour_success_map_calcule_le_taux_depuis_ok_fail(self):
        scores = m3._hour_success_map({}, {}, {"by_hour": {"22h": {"ok": 3, "fail": 1}}})
        assert scores[22] == 75.0

    def test_hour_success_map_premiere_source_prioritaire(self):
        scores = m3._hour_success_map(
            {"by_hour": {"21h": {"success_rate_pct": 92.0}}},
            {"all_hours": {"21:00": {"success_rate_pct": 10.0}}},
            {},
        )
        assert scores[21] == 92.0

    def test_hour_success_map_ignore_les_cles_illisibles(self):
        assert m3._hour_success_map({"by_hour": {"nuit": {"success_rate_pct": 50}}}, {}, {}) == {}

    def test_heures_a_risque_couvrent_le_cob_et_la_journee_business(self):
        assert m3.DANGER_HOURS == {10, 11, 12, 18, 19}
        assert m3.DEFAULT_BASE_HOUR not in m3.DANGER_HOURS


# ╔══════════════════════════════════════════════════════════════════╗
# ║  build_scheduling_report (filet de securite complet)             ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestBuildSchedulingReport:

    def test_planning_complet_a_partir_des_donnees(self, steps_complets):
        report = m3.build_scheduling_report(steps_complets)

        assert set(report["recommended_schedule"]) == {
            "CUSTOMER", "AA.BILL.DETAILS", "FUNDS.TRANSFER$HIS"}
        assert report["estimated_improvement"]["current_success_rate"] == 36.0
        assert report["estimated_improvement"]["projected_success_rate"] > 60
        assert report["_source"] == "finalizer_deterministe"

    def test_creneaux_hors_zone_cob_et_espaces(self, steps_complets):
        schedule = m3.build_scheduling_report(steps_complets)["recommended_schedule"]
        heures = [m3._parse_hhmm(c["recommended_time"]) for c in schedule.values()]

        assert all(h[0] not in m3.DANGER_HOURS for h in heures)
        minutes = sorted(h[0] * 60 + h[1] for h in heures)
        assert all(b - a >= 10 for a, b in zip(minutes, minutes[1:]))

    def test_resume_cite_le_cob_et_le_backoff(self, steps_complets):
        report = m3.build_scheduling_report(steps_complets)
        texte = json.dumps(report, ensure_ascii=False).lower()
        assert "cob" in texte and "backoff" in texte

    def test_types_de_chargement_repris_de_la_configuration(self, steps_complets):
        schedule = m3.build_scheduling_report(steps_complets)["recommended_schedule"]
        assert schedule["CUSTOMER"]["load_type"] == "Full"
        assert schedule["AA.BILL.DETAILS"]["load_type"] == "Incremental"

    def test_sans_donnee_le_rapport_reste_exploitable(self):
        report = m3.build_scheduling_report([])

        assert set(report["recommended_schedule"]) == {
            "CUSTOMER", "AA.BILL.DETAILS", "FUNDS.TRANSFER$HIS"}
        assert report["estimated_improvement"]["current_success_rate"] == 36.0
        assert report["estimated_improvement"]["projected_success_rate"] == 90.0
        assert m3._parse_hhmm(
            report["recommended_schedule"]["CUSTOMER"]["recommended_time"]
        )[0] == m3.DEFAULT_BASE_HOUR

    def test_heure_de_base_repliee_si_seule_une_heure_a_risque_est_bonne(self):
        steps = [step("analyze_time_patterns", {"by_hour": {"11h": {"success_rate_pct": 99.0}}})]
        report = m3.build_scheduling_report(steps)
        heures = [m3._parse_hhmm(c["recommended_time"])[0]
                  for c in report["recommended_schedule"].values()]
        assert all(h not in m3.DANGER_HOURS for h in heures)

    def test_creneau_de_nuit_retenu_a_defaut_de_soiree(self):
        # Aucune heure entre 20h et 23h : on retient la meilleure heure sure
        # au-dessus de 50 % plutot que de retomber aveuglement sur 20h.
        steps = [step("analyze_time_patterns", {"by_hour": {
            "03h": {"success_rate_pct": 88.0},
            "05h": {"success_rate_pct": 62.0},
            "11h": {"success_rate_pct": 99.0},  # ignoree : zone COB
        }})]
        report = m3.build_scheduling_report(steps)

        assert report["recommended_schedule"]["CUSTOMER"]["recommended_time"] == "03:00"
        assert report["estimated_improvement"]["projected_success_rate"] == 88.0

    def test_taux_projete_plafonne_a_95(self):
        steps = [step("analyze_time_patterns", {"by_hour": {"21h": {"success_rate_pct": 100.0}}})]
        report = m3.build_scheduling_report(steps)
        assert report["estimated_improvement"]["projected_success_rate"] == 95.0

    def test_taux_projete_plancher_a_85_quand_l_historique_est_moyen(self):
        steps = [step("analyze_time_patterns", {"by_hour": {"21h": {"success_rate_pct": 70.0}}})]
        report = m3.build_scheduling_report(steps)
        assert report["estimated_improvement"]["projected_success_rate"] == 85.0

    def test_serie_d_echecs_justifie_le_backoff(self, steps_complets):
        patterns = m3.build_scheduling_report(steps_complets)["patterns_detected"]
        assert any("4" in p["pattern"] for p in patterns)
        assert any("65.0" in p["evidence"] for p in patterns)

    def test_resume_du_slm_conserve_s_il_est_argumente(self, steps_complets):
        resume = ("Le COB de T24 provoque la majorite des echecs ; on decale les "
                  "extractions apres 21h avec un backoff exponentiel.")
        report = m3.build_scheduling_report(steps_complets, {"analysis_summary": resume})
        assert report["analysis_summary"] == resume
        assert report["_source"] == "slm_validated"

    @pytest.mark.parametrize("resume", [
        "Trop court.",
        "Un resume long mais qui ne parle jamais du traitement de fin de journee ni du reste.",
        "Le COB est la cause principale des echecs constates sur les extractions T24.",
    ])
    def test_resume_du_slm_ecarte_s_il_est_incomplet(self, steps_complets, resume):
        report = m3.build_scheduling_report(steps_complets, {"analysis_summary": resume})
        assert report["_source"] == "finalizer_deterministe"

    def test_slm_parsed_non_dict_est_tolere(self, steps_complets):
        assert m3.build_scheduling_report(steps_complets, "pas un dict")["_source"] \
            == "finalizer_deterministe"


# ╔══════════════════════════════════════════════════════════════════╗
# ║  validate_slm_report                                             ║
# ╚══════════════════════════════════════════════════════════════════╝

def rapport_slm(**overrides):
    base = {
        "analysis_summary": "Le COB bloque les jobs ; on ajoute un backoff exponentiel.",
        "recommended_schedule": {
            "CUSTOMER": {"recommended_time": "21:00"},
            "AA.BILL.DETAILS": {"recommended_time": "21:15"},
        },
        "estimated_improvement": {"current_success_rate": 36, "projected_success_rate": 90},
    }
    base.update(overrides)
    return base


class TestValidateSlmReport:

    def test_rapport_conforme_n_a_aucun_probleme(self):
        rapport = rapport_slm()
        checks, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert problems == []
        assert all(checks.values())

    def test_planning_manquant_est_detecte(self):
        rapport = rapport_slm(recommended_schedule={})
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "has_schedule" in problems

    def test_horaire_en_zone_cob_est_detecte(self):
        rapport = rapport_slm(recommended_schedule={"CUSTOMER": {"recommended_time": "11:00"}})
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "times_safe" in problems
        assert "times_valid" not in problems

    def test_horaire_illisible_est_detecte(self):
        rapport = rapport_slm(
            recommended_schedule={"CUSTOMER": {"recommended_time": "apres le COB"}})
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "times_valid" in problems

    def test_horaire_donne_en_valeur_directe_est_accepte(self):
        rapport = rapport_slm(recommended_schedule={"CUSTOMER": "21:00"})
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "times_valid" not in problems

    def test_concepts_metier_absents_sont_detectes(self):
        rapport = rapport_slm(analysis_summary="Extraire le soir.")
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "mentions_cob" in problems and "mentions_backoff" in problems

    @pytest.mark.parametrize("projete", [None, 60, 55, "90", {}])
    def test_taux_projete_irrealiste_est_detecte(self, projete):
        rapport = rapport_slm(estimated_improvement={"projected_success_rate": projete})
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "projected_ok" in problems

    def test_amelioration_absente_est_detectee(self):
        rapport = rapport_slm(estimated_improvement=None)
        _, problems = m3.validate_slm_report(rapport, json.dumps(rapport))
        assert "projected_ok" in problems


# ╔══════════════════════════════════════════════════════════════════╗
# ║  patch_slm_report (correction ciblee)                            ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestPatchSlmReport:

    def test_seul_l_horaire_fautif_est_deplace(self, steps_complets):
        rapport = rapport_slm(recommended_schedule={
            "CUSTOMER": {"recommended_time": "21:00", "reason": "raison du SLM"},
            "AA.BILL.DETAILS": {"recommended_time": "11:00"},
        })
        corrige = m3.patch_slm_report(rapport, ["times_safe"], steps_complets)

        schedule = corrige["recommended_schedule"]
        assert schedule["CUSTOMER"] == {"recommended_time": "21:00", "reason": "raison du SLM"}
        assert m3._parse_hhmm(schedule["AA.BILL.DETAILS"]["recommended_time"])[0] \
            not in m3.DANGER_HOURS
        assert any("AA.BILL.DETAILS" in c for c in corrige["_corrections"])

    def test_mention_cob_est_ajoutee_sans_ecraser_le_resume(self, steps_complets):
        rapport = rapport_slm(analysis_summary="Extraire le soir.")
        corrige = m3.patch_slm_report(rapport, ["mentions_cob"], steps_complets)

        assert corrige["analysis_summary"].startswith("Extraire le soir.")
        assert "COB" in corrige["analysis_summary"]

    def test_regle_backoff_est_ajoutee_a_la_liste_existante(self, steps_complets):
        rapport = rapport_slm(adaptive_rules=["espacer les batchs de 10 min"])
        corrige = m3.patch_slm_report(rapport, ["mentions_backoff"], steps_complets)

        assert corrige["adaptive_rules"][0] == "espacer les batchs de 10 min"
        assert any("backoff" in r for r in corrige["adaptive_rules"])

    def test_regle_backoff_cree_la_liste_si_absente(self, steps_complets):
        corrige = m3.patch_slm_report(rapport_slm(), ["mentions_backoff"], steps_complets)
        assert isinstance(corrige["adaptive_rules"], list)

    def test_taux_projete_est_recalcule_depuis_les_donnees(self, steps_complets):
        rapport = rapport_slm(estimated_improvement={"projected_success_rate": 5})
        corrige = m3.patch_slm_report(rapport, ["projected_ok"], steps_complets)

        assert corrige["estimated_improvement"]["projected_success_rate"] > 60
        assert corrige["estimated_improvement"]["current_success_rate"] == 36.0

    def test_source_et_journal_de_corrections(self, steps_complets):
        corrige = m3.patch_slm_report(rapport_slm(), ["mentions_cob"], steps_complets)
        assert corrige["_source"] == "slm_corrige"
        assert corrige["_corrections"] == ["mention COB ajoutée"]

    def test_rapport_d_origine_n_est_pas_mute(self, steps_complets):
        rapport = rapport_slm()
        m3.patch_slm_report(rapport, ["mentions_cob"], steps_complets)
        assert "_source" not in rapport

    def test_correction_s_appuie_sur_la_meilleure_heure_sure_hors_soiree(self):
        steps = [step("analyze_time_patterns", {"by_hour": {
            "03h": {"success_rate_pct": 88.0}, "11h": {"success_rate_pct": 99.0}}})]
        rapport = rapport_slm(
            recommended_schedule={"CUSTOMER": {"recommended_time": "11:00"}})
        corrige = m3.patch_slm_report(rapport, ["times_safe"], steps)

        assert corrige["recommended_schedule"]["CUSTOMER"]["recommended_time"] == "03:00"

    def test_correction_retombe_sur_l_heure_par_defaut_sans_donnees(self):
        rapport = rapport_slm(
            recommended_schedule={"CUSTOMER": {"recommended_time": "11:00"}})
        corrige = m3.patch_slm_report(rapport, ["times_safe"], [])

        heure = m3._parse_hhmm(corrige["recommended_schedule"]["CUSTOMER"]["recommended_time"])
        assert heure[0] == m3.DEFAULT_BASE_HOUR


# ╔══════════════════════════════════════════════════════════════════╗
# ║  finalize — arbitrage SLM / correction / repli                   ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestFinalize:

    def _finalise(self, answer, steps=()):
        agent = make_agent(SchedulerAgent)
        result = agent.finalize(AgentResult(answer=answer, steps=list(steps)))
        return json.loads(result.answer)

    def test_reponse_slm_conforme_est_conservee(self):
        rapport = rapport_slm()
        sortie = self._finalise(json.dumps(rapport))

        assert sortie["_source"] == "slm"
        assert sortie["recommended_schedule"] == rapport["recommended_schedule"]

    def test_reponse_slm_partiellement_fautive_est_corrigee(self, steps_complets):
        rapport = rapport_slm(analysis_summary="Extraire le soir apres la fin du COB.")
        sortie = self._finalise(json.dumps(rapport), steps_complets)

        assert sortie["_source"] == "slm_corrige"
        assert any("backoff" in r for r in sortie["adaptive_rules"])

    def test_json_illisible_bascule_sur_le_filet_deterministe(self, steps_complets):
        sortie = self._finalise("Le modele a repondu en texte libre.", steps_complets)

        assert sortie["_source"] == "fallback_deterministe"
        assert "n'a pas produit de JSON" in sortie["_reason"]
        assert sortie["recommended_schedule"]

    def test_planning_absent_bascule_sur_le_filet_deterministe(self, steps_complets):
        sortie = self._finalise(json.dumps({"analysis_summary": "..."}), steps_complets)

        assert sortie["_source"] == "fallback_deterministe"
        assert "Planning du SLM absent" in sortie["_reason"]
        assert set(sortie["recommended_schedule"]) == {
            "CUSTOMER", "AA.BILL.DETAILS", "FUNDS.TRANSFER$HIS"}

    def test_finalize_renvoie_toujours_du_json_valide(self, steps_complets):
        for answer in ["", "texte", "{}", json.dumps(rapport_slm())]:
            sortie = self._finalise(answer, steps_complets)
            assert isinstance(sortie, dict)
            assert sortie["recommended_schedule"]

    def test_finalize_desactivable(self):
        agent = make_agent(SchedulerAgent, _finalize_enabled=False)
        result = agent.finalize(AgentResult(answer="brut"))
        assert result.answer == "brut"


# ╔══════════════════════════════════════════════════════════════════╗
# ║  recommend_schedule                                              ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestRecommendSchedule:

    def test_rapport_enrichi_de_la_trace(self, monkeypatch):
        agent = make_agent(SchedulerAgent)
        steps = [step("get_job_history", HISTORY), step("analyze_time_patterns", PATTERNS, 2)]
        monkeypatch.setattr(agent, "run", lambda task: AgentResult(
            answer=json.dumps(rapport_slm()), steps=steps, total_iterations=4))

        report = agent.recommend_schedule()
        assert report["total_iterations"] == 4
        assert report["reasoning_trace"] == [
            {"iteration": 1, "tool": "get_job_history", "input": {}},
            {"iteration": 2, "tool": "analyze_time_patterns", "input": {}},
        ]

    def test_reponse_illisible_est_remontee_brute(self, monkeypatch):
        agent = make_agent(SchedulerAgent)
        monkeypatch.setattr(agent, "run", lambda task: AgentResult(
            answer="reponse non json", steps=[], total_iterations=1))

        report = agent.recommend_schedule()
        assert report["parse_error"] is True
        assert report["raw_answer"] == "reponse non json"


# ╔══════════════════════════════════════════════════════════════════╗
# ║  should_launch_now — decision temps reel                         ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestShouldLaunchNow:

    @pytest.fixture
    def agent(self):
        return make_agent(SchedulerAgent)

    @pytest.fixture
    def stub_patterns(self, monkeypatch):
        def _install(patterns=None, exception=None):
            def _fake():
                if exception:
                    raise exception
                return patterns or {}

            monkeypatch.setattr(
                "agent_scheduler.tools.scheduling_tools.analyze_time_patterns", _fake)

        return _install

    @pytest.mark.parametrize("heure", sorted(m3.DANGER_HOURS))
    def test_heure_a_risque_reporte_le_lancement(self, agent, freeze_hour, heure):
        with freeze_hour(heure):
            decision = agent.should_launch_now("CUSTOMER")

        assert decision["launch"] is False
        assert "COB" in decision["reason"]
        assert m3._parse_hhmm(decision["recommended_time"])[0] == m3.DEFAULT_BASE_HOUR
        assert decision["batch_id"] == "CUSTOMER"

    def test_heure_a_risque_ne_consulte_pas_la_base(self, agent, freeze_hour, stub_patterns):
        stub_patterns(exception=AssertionError("la base ne doit pas etre interrogee"))
        with freeze_hour(11):
            assert agent.should_launch_now("CUSTOMER")["launch"] is False

    def test_creneau_favorable_autorise_le_lancement(self, agent, freeze_hour, stub_patterns):
        stub_patterns({"by_hour": {"21h": {"success_rate_pct": 92.0}}, "best_hour": "21h"})
        with freeze_hour(21):
            decision = agent.should_launch_now("CUSTOMER")

        assert decision["launch"] is True
        assert "92.0%" in decision["reason"]
        assert decision["recommended_time"] is None

    def test_taux_historique_faible_reporte_vers_la_meilleure_heure(
            self, agent, freeze_hour, stub_patterns):
        stub_patterns({"by_hour": {"03h": {"success_rate_pct": 20.0}}, "best_hour": "21h"})
        with freeze_hour(3):
            decision = agent.should_launch_now("CUSTOMER")

        assert decision["launch"] is False
        assert "20.0%" in decision["reason"]
        assert decision["recommended_time"] == "21:00"

    def test_repli_si_la_meilleure_heure_est_a_risque(self, agent, freeze_hour, stub_patterns):
        stub_patterns({"by_hour": {"03h": {"success_rate_pct": 20.0}}, "best_hour": "11h"})
        with freeze_hour(3):
            decision = agent.should_launch_now("CUSTOMER")
        assert m3._parse_hhmm(decision["recommended_time"])[0] == m3.DEFAULT_BASE_HOUR

    def test_historique_indisponible_ne_bloque_pas_le_scheduler(
            self, agent, freeze_hour, stub_patterns):
        # Hors zone COB, une base injoignable ne doit pas empecher l'extraction.
        stub_patterns(exception=RuntimeError("SQL Server injoignable"))
        with freeze_hour(21):
            decision = agent.should_launch_now("CUSTOMER")

        assert decision["launch"] is True
        assert "SQL Server injoignable" in decision["reason"]

    def test_heure_sans_historique_est_autorisee(self, agent, freeze_hour, stub_patterns):
        stub_patterns({"by_hour": {}, "best_hour": None})
        with freeze_hour(2):
            decision = agent.should_launch_now("CUSTOMER")

        assert decision["launch"] is True
        assert "hors zone COB" in decision["reason"]

    def test_contrat_de_sortie_toujours_respecte(self, agent, freeze_hour, stub_patterns):
        stub_patterns({"by_hour": {"21h": {"success_rate_pct": 92.0}}, "best_hour": "21h"})
        for heure in range(24):
            with freeze_hour(heure):
                decision = agent.should_launch_now("CUSTOMER")
            assert set(decision) == {"launch", "reason", "recommended_time", "batch_id"}
            assert isinstance(decision["launch"], bool)

    def test_decision_n_appelle_jamais_le_slm(self, agent, freeze_hour, stub_patterns):
        # ~5 min d'inference bloqueraient le AutoExtractionScheduler cote Java.
        stub_patterns({"by_hour": {"21h": {"success_rate_pct": 92.0}}, "best_hour": "21h"})
        with freeze_hour(21):
            agent.should_launch_now("CUSTOMER")
        assert agent.client.calls == []


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Configuration de l'agent                                        ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestConfigurationAgent:

    def test_les_six_tools_sont_cables(self):
        agent = make_agent(SchedulerAgent)
        noms = {t["function"]["name"] for t in agent.tools}
        assert noms == set(agent.tool_registry)
        assert len(noms) == 6

    def test_prompt_systeme_expose_le_cob_et_le_backoff(self):
        prompt = SchedulerAgent.system_prompt.lower()
        assert "cob" in prompt and "backoff" in prompt

    def test_conclusion_prompt_montre_un_gabarit_json_valide(self):
        from core.optimizations import JsonRepairer

        ok, gabarit, _ = JsonRepairer.repair(SchedulerAgent.conclusion_prompt)
        assert ok is True
        assert set(gabarit["recommended_schedule"]) == {
            "CUSTOMER", "AA.BILL.DETAILS", "FUNDS.TRANSFER$HIS"}
