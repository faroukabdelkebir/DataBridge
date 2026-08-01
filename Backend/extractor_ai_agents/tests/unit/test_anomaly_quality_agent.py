"""
Tests unitaires de l'agent M1 (agent_anomaly_quality/agents/anomaly_quality_agent.py).

Point cle du design : le rapport chiffre vient TOUJOURS du code
(`compute_quality_score`), jamais du SLM. Le SLM ne produit que la trace de
raisonnement et l'interpretation en francais. Ces tests verrouillent cette
separation.
"""

import json

import pytest

from agent_anomaly_quality.agents import anomaly_quality_agent as m1
from agent_anomaly_quality.agents.anomaly_quality_agent import AnomalyQualityAgent
from core.base_agent import AgentResult, AgentStep
from tests.helpers import make_agent, slm_text


RAPPORT = {
    "table": "CUSTOMER",
    "global_score": 20.7,
    "grade": "F",
    "capped_to_F_by": ["D2"],
    "dimensions": {
        "D1": {"score": 22.1, "weight": 0.25}, "D2": {"score": 1.9, "weight": 0.25},
        "D3": {"score": 8.2, "weight": 0.15}, "D4": {"score": 50.0, "weight": 0.15},
        "D5": {"score": 45.0, "weight": 0.10}, "D6": {"score": 15.4, "weight": 0.10},
    },
    "improvements_by_impact": [
        {"dimension": "D2", "current": 1.9, "max_gain": 24.5, "action": "TRUNCATE"},
    ],
}


@pytest.fixture
def agent():
    return make_agent(AnomalyQualityAgent, [slm_text("interprétation métier")])


@pytest.fixture
def stub_score(monkeypatch):
    """Fige le rapport deterministe renvoye par les tools."""
    appels = []

    def _fake(table_name="", **_):
        appels.append(table_name)
        return json.loads(json.dumps(dict(RAPPORT, table=table_name)))

    monkeypatch.setattr(
        "agent_anomaly_quality.tools.anomaly_quality_tools.compute_quality_score", _fake)
    return appels


@pytest.fixture
def stub_run(monkeypatch):
    """Remplace la boucle ReAct par un resultat scenarise."""

    def _install(agent, steps=(), iterations=5, input_tokens=0, output_tokens=0):
        result = AgentResult(
            answer="peu importe : le rapport vient du code",
            steps=list(steps), total_iterations=iterations,
            input_tokens=input_tokens, output_tokens=output_tokens,
        )
        monkeypatch.setattr(agent, "run", lambda task: result)
        return result

    return _install


def tool_step(iteration, tool_name, tool_input):
    return AgentStep(iteration=iteration, thought=None, tool_name=tool_name,
                     tool_input=tool_input, tool_output="{}")


# ╔══════════════════════════════════════════════════════════════════╗
# ║  audit_table                                                     ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAuditTable:

    def test_le_rapport_chiffre_vient_du_code_pas_du_slm(self, agent, stub_score, stub_run):
        # Regression : le 7B inventait des scores. Seul compute_quality_score fait foi.
        stub_run(agent)
        agent.client.responses = [slm_text("interprétation")]

        report = agent.audit_table("CUSTOMER")
        assert report["global_score"] == 20.7
        assert report["grade"] == "F"
        assert stub_score == ["CUSTOMER"]

    def test_interpretation_en_francais_est_ajoutee(self, agent, stub_score, stub_run):
        stub_run(agent)
        agent.client.responses = [slm_text("La table est inexploitable en l'état.")]

        report = agent.audit_table("CUSTOMER")
        assert report["interpretation"] == "La table est inexploitable en l'état."

    def test_trace_de_raisonnement_ne_garde_que_les_tools(self, agent, stub_score, stub_run):
        stub_run(agent, steps=[
            tool_step(1, "measure_completeness", {"table_name": "CUSTOMER"}),
            AgentStep(iteration=2, thought="je conclus", tool_name=None,
                      tool_input=None, tool_output=None, is_final=True),
        ], iterations=2)
        agent.client.responses = [slm_text("ok")]

        report = agent.audit_table("CUSTOMER")
        assert report["reasoning_trace"] == [
            {"iteration": 1, "tool": "measure_completeness",
             "input": {"table_name": "CUSTOMER"}},
        ]
        assert report["total_iterations"] == 2

    def test_tokens_cumulent_raisonnement_et_interpretation(self, agent, stub_score, stub_run):
        stub_run(agent, input_tokens=1000, output_tokens=200)
        agent.client.responses = [slm_text("ok", prompt_tokens=300, completion_tokens=80)]

        report = agent.audit_table("CUSTOMER")
        assert report["input_tokens"] == 1300
        assert report["output_tokens"] == 280
        assert report["total_tokens"] == 1580

    def test_compteurs_d_interpretation_remis_a_zero_entre_deux_audits(
            self, agent, stub_score, stub_run):
        stub_run(agent, input_tokens=100, output_tokens=10)
        agent.client.responses = [
            slm_text("a", prompt_tokens=50, completion_tokens=5),
            slm_text("b", prompt_tokens=50, completion_tokens=5),
        ]

        premier = agent.audit_table("CUSTOMER")
        second = agent.audit_table("CUSTOMER")
        assert premier["total_tokens"] == second["total_tokens"]

    def test_tache_envoyee_au_slm_decrit_les_deux_phases(self, agent, stub_score, monkeypatch):
        taches = []
        monkeypatch.setattr(agent, "run", lambda task: (
            taches.append(task) or AgentResult(answer="", steps=[], total_iterations=1)))
        agent.client.responses = [slm_text("ok")]

        agent.audit_table("CUSTOMER")
        assert "PHASE 1" in taches[0] and "PHASE 2" in taches[0]
        assert "CUSTOMER" in taches[0]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  interpret_report                                                ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestInterpretReport:

    def test_aucun_tool_n_est_expose_a_l_interpretation(self, agent):
        # L'interpretation ne doit RIEN mesurer : elle ne fait que traduire.
        agent.interpret_report(RAPPORT)
        assert agent.client.calls[0]["tools"] is None

    def test_dimensions_sont_traduites_en_libelles_parlants(self, agent):
        agent.interpret_report(RAPPORT)
        faits = json.loads(agent.client.calls[0]["messages"][1]["content"]
                           .split("Rapport d'audit :\n", 1)[1])

        assert faits["dimensions"] == {
            "Complétude (cellules remplies)": 22.1,
            "Unicité (absence de doublons)": 1.9,
            "Couverture du schéma (colonnes activées)": 8.2,
            "Conformité (types/formats respectés)": 50.0,
            "Fraîcheur (récence de l'extraction)": 45.0,
            "Consistance (valeurs non corrompues)": 15.4,
        }

    def test_plafonnement_est_transmis_quand_il_est_actif(self, agent):
        agent.interpret_report(RAPPORT)
        contenu = agent.client.calls[0]["messages"][1]["content"]
        assert "plafonne_a_F_par" in contenu
        assert "Unicité (absence de doublons)" in contenu

    def test_plafonnement_absent_n_est_pas_expose(self, agent):
        agent.interpret_report(dict(RAPPORT, capped_to_F_by=[]))
        assert "plafonne_a_F_par" not in agent.client.calls[0]["messages"][1]["content"]

    def test_consigne_systeme_interdit_d_inventer_des_chiffres(self, agent):
        agent.interpret_report(RAPPORT)
        systeme = agent.client.calls[0]["messages"][0]["content"]
        assert "DÉFINITIFS" in systeme
        assert "n'en inventes aucun" in systeme

    def test_reponse_vide_du_slm_donne_une_chaine_vide(self, agent):
        agent.client.responses = [slm_text(None)]
        assert agent.interpret_report(RAPPORT) == ""

    def test_tokens_du_dernier_appel_sont_memorises(self, agent):
        agent.client.responses = [slm_text("x", prompt_tokens=42, completion_tokens=7)]
        agent.interpret_report(RAPPORT)
        assert agent._last_interpret_input_tokens == 42
        assert agent._last_interpret_output_tokens == 7


# ╔══════════════════════════════════════════════════════════════════╗
# ║  quick_score                                                     ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestQuickScore:

    def test_score_deterministe_sans_interpretation(self, agent, stub_score, stub_run):
        stub_run(agent, iterations=3)
        report = agent.quick_score("CUSTOMER")

        assert report["global_score"] == 20.7
        assert report["total_iterations"] == 3
        assert "interpretation" not in report
        assert agent.client.calls == []  # aucun appel SLM supplementaire

    def test_tache_limite_a_la_phase_1(self, agent, stub_score, monkeypatch):
        taches = []
        monkeypatch.setattr(agent, "run", lambda task: (
            taches.append(task) or AgentResult(answer="", steps=[], total_iterations=1)))

        agent.quick_score("CUSTOMER")
        assert "Phase 1 uniquement" in taches[0]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  audit_all_tables                                                ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAuditAllTables:

    @pytest.fixture
    def stub_score_par_table(self, monkeypatch):
        scores = {"CUSTOMER": 20.7, "AA_BILL_DETAILS": 65.0, "FUNDS_TRANSFER_HIS": 42.0}
        grades = {"CUSTOMER": "F", "AA_BILL_DETAILS": "B", "FUNDS_TRANSFER_HIS": "C"}
        monkeypatch.setattr(
            "agent_anomaly_quality.tools.anomaly_quality_tools.compute_quality_score",
            lambda table_name="", **_: {
                "table": table_name, "global_score": scores[table_name],
                "grade": grades[table_name],
            })

    def test_les_trois_tables_sont_auditees(self, agent, stub_score_par_table, stub_run):
        stub_run(agent)
        report = agent.audit_all_tables()
        assert set(report["reports"]) == {"CUSTOMER", "AA_BILL_DETAILS", "FUNDS_TRANSFER_HIS"}

    def test_classement_de_la_pire_a_la_meilleure(self, agent, stub_score_par_table, stub_run):
        stub_run(agent)
        report = agent.audit_all_tables()

        assert [r["table"] for r in report["ranking"]] == [
            "CUSTOMER", "FUNDS_TRANSFER_HIS", "AA_BILL_DETAILS"]
        assert report["worst_table"] == "CUSTOMER"

    def test_iterations_du_run_sont_reportees(self, agent, stub_score_par_table, stub_run):
        stub_run(agent, iterations=9)
        assert agent.audit_all_tables()["total_iterations"] == 9


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Configuration de l'agent                                        ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestConfigurationAgent:

    def test_les_dix_tools_sont_cables(self, agent):
        noms = {t["function"]["name"] for t in agent.tools}
        assert noms == set(agent.tool_registry)
        assert len(noms) == 10

    def test_prompt_systeme_decrit_les_six_dimensions(self):
        prompt = AnomalyQualityAgent.system_prompt
        for dimension in ("D1", "D2", "D3", "D4", "D5", "D6"):
            assert dimension in prompt
        assert "JSON valide" in prompt

    def test_libelles_couvrent_les_six_dimensions(self):
        assert set(m1.DIMENSION_LABELS) == {"D1", "D2", "D3", "D4", "D5", "D6"}
