"""Tests unitaires du client Ollama (core/ollama_client.py). `requests` est double."""

import pytest
import requests

from core.ollama_client import AgentResponse, OllamaClient, ToolCall


class _FakeResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self.status_code = status_code

    def json(self):
        return self._payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.exceptions.HTTPError(f"HTTP {self.status_code}")


@pytest.fixture
def post_spy(monkeypatch):
    """Intercepte requests.post et rejoue une reponse Ollama scenarisee."""
    captured = {}

    def _install(payload, status_code=200, exception=None):
        def _post(url, json=None, timeout=None):
            captured["url"] = url
            captured["payload"] = json
            captured["timeout"] = timeout
            if exception:
                raise exception
            return _FakeResponse(payload, status_code)

        monkeypatch.setattr("core.ollama_client.requests.post", _post)
        return captured

    return _install


class TestChat:

    def test_reponse_texte_est_marquee_terminee(self, post_spy):
        post_spy({
            "message": {"content": '{"grade": "F"}'},
            "prompt_eval_count": 312,
            "eval_count": 48,
        })
        response = OllamaClient().chat([{"role": "user", "content": "audite"}])

        assert isinstance(response, AgentResponse)
        assert response.done is True
        assert response.content == '{"grade": "F"}'
        assert response.tool_calls == []
        assert response.prompt_tokens == 312
        assert response.completion_tokens == 48

    def test_tool_calls_sont_convertis_en_dataclass(self, post_spy):
        post_spy({"message": {
            "content": "je mesure",
            "tool_calls": [
                {"function": {"name": "measure_completeness",
                              "arguments": {"table_name": "CUSTOMER"}}},
                {"function": {"name": "measure_freshness", "arguments": {}}},
            ],
        }})
        response = OllamaClient().chat([{"role": "user", "content": "audite"}])

        assert response.done is False
        assert response.content == "je mesure"
        assert response.tool_calls == [
            ToolCall(name="measure_completeness", arguments={"table_name": "CUSTOMER"}),
            ToolCall(name="measure_freshness", arguments={}),
        ]

    def test_tool_call_malforme_ne_fait_pas_planter(self, post_spy):
        post_spy({"message": {"tool_calls": [{"function": {}}]}})
        response = OllamaClient().chat([])
        assert response.tool_calls == [ToolCall(name="", arguments={})]

    def test_compteurs_de_tokens_absents_valent_zero(self, post_spy):
        post_spy({"message": {"content": "ok"}})
        response = OllamaClient().chat([])
        assert (response.prompt_tokens, response.completion_tokens) == (0, 0)

    def test_compteurs_de_tokens_null_valent_zero(self, post_spy):
        post_spy({"message": {"content": "ok"},
                  "prompt_eval_count": None, "eval_count": None})
        response = OllamaClient().chat([])
        assert (response.prompt_tokens, response.completion_tokens) == (0, 0)

    def test_payload_envoye_a_ollama(self, post_spy):
        captured = post_spy({"message": {"content": "ok"}})
        tools = [{"type": "function", "function": {"name": "t"}}]
        client = OllamaClient(base_url="http://slm.invalid:11434", model="qwen2.5:7b")
        client.chat([{"role": "user", "content": "hello"}], tools=tools, temperature=0.4)

        assert captured["url"] == "http://slm.invalid:11434/api/chat"
        assert captured["payload"]["model"] == "qwen2.5:7b"
        assert captured["payload"]["stream"] is False
        assert captured["payload"]["tools"] == tools
        assert captured["payload"]["options"]["temperature"] == 0.4
        assert captured["payload"]["options"]["num_ctx"] == 4096

    def test_cle_tools_absente_quand_aucun_tool(self, post_spy):
        captured = post_spy({"message": {"content": "ok"}})
        OllamaClient().chat([{"role": "user", "content": "hello"}])
        assert "tools" not in captured["payload"]

    def test_liste_de_tools_vide_est_ignoree(self, post_spy):
        captured = post_spy({"message": {"content": "ok"}})
        OllamaClient().chat([], tools=[])
        assert "tools" not in captured["payload"]

    def test_temperature_par_defaut_est_basse(self, post_spy):
        captured = post_spy({"message": {"content": "ok"}})
        OllamaClient().chat([])
        assert captured["payload"]["options"]["temperature"] == 0.1


class TestChatErreurs:

    def test_serveur_injoignable_donne_un_message_actionnable(self, post_spy):
        post_spy(None, exception=requests.exceptions.ConnectionError())
        with pytest.raises(ConnectionError, match="ollama serve"):
            OllamaClient().chat([])

    def test_timeout_est_traduit(self, post_spy):
        post_spy(None, exception=requests.exceptions.Timeout())
        with pytest.raises(TimeoutError, match="trop de temps"):
            OllamaClient().chat([])

    def test_erreur_http_est_propagee(self, post_spy):
        post_spy({}, status_code=500)
        with pytest.raises(requests.exceptions.HTTPError):
            OllamaClient().chat([])


class TestDisponibilite:

    @pytest.fixture
    def get_spy(self, monkeypatch):
        def _install(payload=None, status_code=200, exception=None):
            def _get(url, timeout=None):
                if exception:
                    raise exception
                return _FakeResponse(payload or {}, status_code)

            monkeypatch.setattr("core.ollama_client.requests.get", _get)

        return _install

    def test_is_available_vrai_sur_200(self, get_spy):
        get_spy({"models": []})
        assert OllamaClient().is_available() is True

    def test_is_available_faux_sur_erreur_http(self, get_spy):
        get_spy({}, status_code=503)
        assert OllamaClient().is_available() is False

    def test_is_available_faux_si_ollama_est_eteint(self, get_spy):
        get_spy(exception=requests.exceptions.ConnectionError())
        assert OllamaClient().is_available() is False

    def test_list_models_extrait_les_noms(self, get_spy):
        get_spy({"models": [{"name": "qwen2.5:7b"}, {"name": "llama3.1:8b"}]})
        assert OllamaClient().list_models() == ["qwen2.5:7b", "llama3.1:8b"]

    def test_list_models_vide_si_ollama_est_eteint(self, get_spy):
        get_spy(exception=requests.exceptions.ConnectionError())
        assert OllamaClient().list_models() == []

    def test_list_models_vide_si_reponse_inattendue(self, get_spy):
        get_spy({})
        assert OllamaClient().list_models() == []
