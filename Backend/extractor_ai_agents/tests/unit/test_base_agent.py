"""
Tests unitaires de la boucle ReAct (core/base_agent.py).

Le SLM est entierement double : chaque test scenarise la suite de reponses
d'Ollama et verifie la mecanique de l'agent (execution des tools, anti-boucle,
cache, comptage de tokens, gestion d'erreur).
"""

import json

import pytest

from core.base_agent import AgentResult, AgentStep, BaseAgent
from tests.helpers import FakeOllamaClient, make_agent, slm_text, slm_tool_call


def _tool_def(name):
    return {"type": "function", "function": {"name": name, "parameters": {}}}


class _Agent(BaseAgent):
    """Agent minimal cable sur des tools Python purs."""
    system_prompt = "prompt systeme de test"


@pytest.fixture
def calls():
    return []


@pytest.fixture
def agent_factory(calls):
    def _echo(table_name="CUSTOMER"):
        calls.append(("echo", table_name))
        return {"table": table_name, "score": 42}

    def _boom():
        calls.append(("boom", None))
        raise RuntimeError("SQL Server injoignable")

    def _kwargs_tool(**kwargs):
        calls.append(("kwargs", kwargs))
        return {"received": sorted(kwargs)}

    registry = {"echo": _echo, "boom": _boom, "kwargs_tool": _kwargs_tool}

    def factory(responses, **attrs):
        attrs.setdefault("tools", [_tool_def(n) for n in registry])
        attrs.setdefault("tool_registry", registry)
        return make_agent(_Agent, responses, **attrs)

    factory.registry = registry
    return factory


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Dataclasses                                                     ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAgentResult:

    def test_total_tokens_somme_entree_et_sortie(self):
        assert AgentResult(answer="", input_tokens=120, output_tokens=30).total_tokens == 150

    def test_valeurs_par_defaut(self):
        result = AgentResult(answer="ok")
        assert result.steps == []
        assert result.total_iterations == 0
        assert result.error is None
        assert result.total_tokens == 0


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Chemin nominal                                                  ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestRunNominal:

    def test_reponse_finale_immediate(self, agent_factory):
        agent = agent_factory([slm_text('{"grade": "F"}')])
        result = agent.run("audite CUSTOMER")

        assert result.answer == '{"grade": "F"}'
        assert result.error is None
        assert result.total_iterations == 1
        assert len(result.steps) == 1
        assert result.steps[0].is_final is True

    def test_prompt_systeme_et_tache_sont_transmis(self, agent_factory):
        agent = agent_factory([slm_text("{}")])
        agent.run("audite CUSTOMER")

        messages = agent.client.calls[0]["messages"]
        assert messages[0] == {"role": "system", "content": "prompt systeme de test"}
        assert messages[1] == {"role": "user", "content": "audite CUSTOMER"}

    def test_tool_call_puis_conclusion(self, agent_factory, calls):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}, content="je mesure"),
            slm_text('{"grade": "C"}'),
        ])
        result = agent.run("audite CUSTOMER")

        assert calls == [("echo", "CUSTOMER")]
        assert result.total_iterations == 2
        tool_steps = [s for s in result.steps if s.tool_name]
        assert len(tool_steps) == 1
        assert tool_steps[0].tool_name == "echo"
        assert tool_steps[0].tool_input == {"table_name": "CUSTOMER"}
        assert json.loads(tool_steps[0].tool_output) == {"table": "CUSTOMER", "score": 42}

    def test_transcript_contient_le_message_assistant_puis_le_role_tool(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_text("{}"),
        ])
        agent.run("audite")

        messages = agent.client.calls[1]["messages"]
        assert messages[2]["role"] == "assistant"
        assert messages[2]["tool_calls"][0]["function"]["name"] == "echo"
        assert messages[3]["role"] == "tool"
        assert "CUSTOMER" in messages[3]["content"]

    def test_tokens_cumules_sur_toutes_les_iterations(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {}, prompt_tokens=100, completion_tokens=20),
            slm_text("{}", prompt_tokens=250, completion_tokens=40),
        ])
        result = agent.run("audite")

        assert result.input_tokens == 350
        assert result.output_tokens == 60
        assert result.total_tokens == 410

    def test_finalize_est_appele_avant_de_rendre_le_resultat(self, agent_factory):
        agent = agent_factory([slm_text('{"a": 1}')])
        agent.finalize = lambda result: AgentResult(answer="remplace par finalize")

        assert agent.run("audite").answer == "remplace par finalize"

    def test_finalize_par_defaut_est_l_identite(self, agent_factory):
        agent = agent_factory([slm_text("x")])
        result = AgentResult(answer="inchange")
        assert agent.finalize(result) is result


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Execution des tools                                             ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestExecuteTool:

    def test_tool_inconnu_renvoie_une_erreur_json(self, agent_factory):
        agent = agent_factory([])
        out = json.loads(agent._execute_tool("tool_fantome", {}))
        assert out["error"] == "Tool inconnu : tool_fantome"

    def test_exception_du_tool_est_encapsulee(self, agent_factory):
        agent = agent_factory([])
        out = json.loads(agent._execute_tool("boom", {}))
        assert out["error"] == "SQL Server injoignable"

    def test_arguments_inconnus_sont_filtres(self, agent_factory, calls):
        # Regression : le SLM 7B reinjecte la sortie d'un tool comme arguments du
        # suivant. Sans filtrage, func(**arguments) levait un TypeError.
        agent = agent_factory([])
        out = json.loads(agent._execute_tool(
            "echo", {"table_name": "CUSTOMER", "AA.BILL.DETAILS": {"score": 1}}
        ))
        assert out == {"table": "CUSTOMER", "score": 42}
        assert calls == [("echo", "CUSTOMER")]

    def test_tool_acceptant_var_keyword_recoit_tout(self, agent_factory, calls):
        agent = agent_factory([])
        agent._execute_tool("kwargs_tool", {"a": 1, "b": 2})
        assert calls == [("kwargs", {"a": 1, "b": 2})]

    def test_arguments_none_sont_normalises(self, agent_factory, calls):
        agent = agent_factory([])
        agent._execute_tool("echo", None)
        assert calls == [("echo", "CUSTOMER")]  # valeur par defaut du tool

    def test_sortie_volumineuse_est_tronquee(self, agent_factory):
        agent = agent_factory([])
        agent.tool_registry = dict(agent.tool_registry, gros=lambda: {"x": "a" * 5000})
        out = agent._execute_tool("gros", {})
        assert len(out) == 3000 + len("... [tronqué]")
        assert out.endswith("... [tronqué]")

    def test_cache_evite_de_rejouer_la_meme_requete(self, agent_factory, calls):
        agent = agent_factory([])
        first = agent._execute_tool("echo", {"table_name": "CUSTOMER"})
        second = agent._execute_tool("echo", {"table_name": "CUSTOMER"})
        assert first == second
        assert calls == [("echo", "CUSTOMER")]  # un seul appel reel

    def test_cache_ne_confond_pas_deux_jeux_d_arguments(self, agent_factory, calls):
        agent = agent_factory([])
        agent._execute_tool("echo", {"table_name": "CUSTOMER"})
        agent._execute_tool("echo", {"table_name": "AA_BILL_DETAILS"})
        assert len(calls) == 2

    def test_serialisation_gere_les_types_non_json(self, agent_factory):
        from datetime import datetime

        agent = agent_factory([])
        agent.tool_registry = dict(
            agent.tool_registry, dated=lambda: {"end": datetime(2025, 1, 2, 3, 4)}
        )
        assert "2025-01-02 03:04:00" in agent._execute_tool("dated", {})


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Anti-boucle                                                     ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAntiBoucle:

    def test_appel_repete_declenche_un_recadrage_vers_un_tool_inutilise(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),  # meme appel
            slm_text('{"grade": "F"}'),
        ], conclusion_prompt="Conclus en JSON.")
        result = agent.run("audite")

        nudges = [
            m for m in agent.client.calls[2]["messages"]
            if m["role"] == "user" and "Outils encore disponibles" in m.get("content", "")
        ]
        assert len(nudges) == 1
        assert "boom" in nudges[0]["content"] and "kwargs_tool" in nudges[0]["content"]
        assert result.answer == '{"grade": "F"}'

    def test_appel_repete_recoit_quand_meme_sa_reponse_tool(self, agent_factory):
        # Regression : l'appel repete n'etait jamais "repondu" dans le transcript,
        # le modele le re-emettait donc a l'infini.
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_text("{}"),
        ], conclusion_prompt="Conclus.")
        agent.run("audite")

        roles = [m["role"] for m in agent.client.calls[2]["messages"]]
        assert roles.count("tool") == 2

    def test_quota_de_recadrage_epuise_force_la_conclusion(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_text('{"grade": "F"}'),
        ], conclusion_prompt="Donne ta conclusion en JSON.", max_loop_nudges=0)
        agent.run("audite")

        messages = agent.client.calls[2]["messages"]
        assert len(messages) == 2  # transcript remis a plat : system + consigne
        assert "Donne ta conclusion en JSON." in messages[1]["content"]
        assert "Résultats des outils" in messages[1]["content"]

    def test_conclusion_forcee_par_defaut_si_pas_de_conclusion_prompt(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_text("{}"),
        ], max_loop_nudges=0)
        agent.run("audite")

        consigne = agent.client.calls[2]["messages"][1]["content"]
        assert "JSON UNIQUEMENT" in consigne

    def test_appels_avec_arguments_differents_ne_sont_pas_des_boucles(self, agent_factory, calls):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_tool_call("echo", {"table_name": "AA_BILL_DETAILS"}),
            slm_text("{}"),
        ])
        agent.run("audite")

        assert len(calls) == 2
        assert all(
            "Outils encore disponibles" not in m.get("content", "")
            for m in agent.client.calls[2]["messages"]
        )


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Fausse reponse finale                                           ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestFausseReponseFinale:

    def _agent_avec_trois_tools_utilises(self, agent_factory, final_responses):
        return agent_factory(
            [
                slm_tool_call("echo", {"table_name": "CUSTOMER"}),
                slm_tool_call("boom", {}),
                slm_tool_call("kwargs_tool", {"a": 1}),
            ] + final_responses,
            conclusion_prompt="Conclus en JSON.",
        )

    def test_texte_sans_json_apres_trois_tools_relance_la_conclusion(self, agent_factory):
        agent = self._agent_avec_trois_tools_utilises(
            agent_factory,
            [slm_text("Je vais maintenant calculer le score."), slm_text('{"grade": "F"}')],
        )
        result = agent.run("audite")

        relance = agent.client.calls[4]["messages"]
        assert len(relance) == 2
        assert "Conclus en JSON." in relance[1]["content"]
        assert result.answer == '{"grade": "F"}'

    def test_texte_contenant_du_json_est_accepte_immediatement(self, agent_factory):
        agent = self._agent_avec_trois_tools_utilises(
            agent_factory, [slm_text('Voici : {"grade": "F"}')]
        )
        result = agent.run("audite")

        assert result.steps[-1].is_final is True
        assert result.answer == 'Voici : {"grade": "F"}'

    def test_texte_sans_json_est_accepte_si_moins_de_trois_tools(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {"table_name": "CUSTOMER"}),
            slm_text("Analyse terminee."),
        ], conclusion_prompt="Conclus en JSON.")
        result = agent.run("audite")

        assert result.answer == "Analyse terminee."
        assert result.total_iterations == 2


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Erreurs et garde-fous                                           ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestErreurs:

    def test_panne_ollama_renvoie_un_resultat_en_erreur(self, agent_factory):
        agent = agent_factory([ConnectionError("Impossible de se connecter a Ollama.")])
        result = agent.run("audite")

        assert result.answer == ""
        assert result.error == "Impossible de se connecter a Ollama."
        assert result.total_iterations == 1

    def test_panne_ollama_conserve_les_tokens_deja_consommes(self, agent_factory):
        agent = agent_factory([
            slm_tool_call("echo", {}, prompt_tokens=90, completion_tokens=10),
            TimeoutError("trop long"),
        ])
        result = agent.run("audite")

        assert result.error == "trop long"
        assert result.input_tokens == 90
        assert result.output_tokens == 10

    def test_max_iterations_atteint(self, agent_factory):
        agent = agent_factory(
            [slm_tool_call("echo", {"table_name": "CUSTOMER"})],
            max_iterations=3,
        )
        agent.client.repeat_last = True
        agent.max_loop_nudges = 99  # on veut boucler jusqu'au plafond
        result = agent.run("audite")

        assert result.error == "max_iterations_reached"
        assert result.total_iterations == 3
        assert "nombre maximum d'itérations" in result.answer


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Trace lisible                                                   ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestTraceMarkdown:

    def test_rend_pensees_actions_observations_et_reponse_finale(self, agent_factory):
        agent = agent_factory([])
        result = AgentResult(answer="fini", steps=[
            AgentStep(iteration=1, thought="je mesure", tool_name="echo",
                      tool_input={"table_name": "CUSTOMER"}, tool_output='{"score": 42}'),
            AgentStep(iteration=2, thought="conclusion", tool_name=None,
                      tool_input=None, tool_output=None, is_final=True),
        ])
        md = agent.get_trace_markdown(result)

        assert "## Trace de raisonnement de l'agent" in md
        assert "### Itération 1" in md
        assert "**Pensée** : je mesure" in md
        assert "`echo({\"table_name\": \"CUSTOMER\"})`" in md
        assert '{"score": 42}' in md
        assert "**→ Réponse finale**" in md

    def test_trace_vide_reste_valide(self, agent_factory):
        agent = agent_factory([])
        assert agent.get_trace_markdown(AgentResult(answer="")) == \
            "## Trace de raisonnement de l'agent\n\n"
