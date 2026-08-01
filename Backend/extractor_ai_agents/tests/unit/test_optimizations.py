"""Tests unitaires de core/optimizations.py (cache + reparation JSON + retry)."""

import time
from datetime import datetime, timedelta

import pytest

from core.optimizations import (CacheManager, FallbackScorer, JsonRepairer,
                                run_with_retry)


# ╔══════════════════════════════════════════════════════════════════╗
# ║  CacheManager                                                    ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestCacheManager:

    def test_make_key_est_stable_quel_que_soit_l_ordre_des_arguments(self):
        cache = CacheManager()
        assert cache.make_key("t", {"a": 1, "b": 2}) == cache.make_key("t", {"b": 2, "a": 1})

    def test_make_key_distingue_les_tools_et_les_arguments(self):
        cache = CacheManager()
        assert cache.make_key("t1", {"a": 1}) != cache.make_key("t2", {"a": 1})
        assert cache.make_key("t1", {"a": 1}) != cache.make_key("t1", {"a": 2})

    def test_get_retourne_none_sur_cle_absente(self):
        assert CacheManager().get("inconnue") is None

    def test_set_puis_get_restitue_la_valeur(self):
        cache = CacheManager()
        cache.set("k", "valeur")
        assert cache.get("k") == "valeur"

    def test_entree_expiree_est_purgee(self):
        cache = CacheManager(ttl_seconds=0)
        cache.set("k", "valeur")
        time.sleep(0.01)
        assert cache.get("k") is None
        assert cache.stats == {"cached_entries": 0}

    def test_clear_vide_le_cache(self):
        cache = CacheManager()
        cache.set("a", "1")
        cache.set("b", "2")
        assert cache.stats["cached_entries"] == 2
        cache.clear()
        assert cache.stats["cached_entries"] == 0


# ╔══════════════════════════════════════════════════════════════════╗
# ║  JsonRepairer                                                    ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestJsonRepairer:

    def test_json_valide_est_parse_directement(self):
        ok, data, err = JsonRepairer.repair('{"grade": "F", "score": 20.7}')
        assert ok is True
        assert data == {"grade": "F", "score": 20.7}
        assert err == ""

    def test_texte_avant_et_apres_le_json_est_ignore(self):
        ok, data, _ = JsonRepairer.repair('Voici le rapport :\n{"a": 1}\nMerci !')
        assert ok is True
        assert data == {"a": 1}

    def test_bloc_markdown_est_supporte(self):
        ok, data, _ = JsonRepairer.repair('```json\n{"a": 1}\n```')
        assert ok is True
        assert data == {"a": 1}

    def test_virgule_en_trop_est_reparee(self):
        ok, data, msg = JsonRepairer.repair('{"a": 1, "b": 2,}')
        assert ok is True
        assert data == {"a": 1, "b": 2}
        assert msg  # une reparation a bien ete signalee

    def test_commentaires_sont_retires(self):
        ok, data, _ = JsonRepairer.repair('{\n"a": 1, // commentaire\n"b": 2\n}')
        assert ok is True
        assert data == {"a": 1, "b": 2}

    def test_guillemets_simples_sur_les_cles_sont_normalises(self):
        ok, data, _ = JsonRepairer.repair("{'a': 1, 'b': 2}")
        assert ok is True
        assert data == {"a": 1, "b": 2}

    def test_json_tronque_est_referme(self):
        # Le SLM coupe sa reponse a num_predict : accolades/crochets non fermes.
        ok, data, msg = JsonRepairer.repair('{"dimensions": {"D1": {"score": 22.1}')
        assert ok is True
        assert data == {"dimensions": {"D1": {"score": 22.1}}}
        assert "fermeture" in msg

    def test_crochet_de_liste_non_ferme_est_referme(self):
        ok, data, _ = JsonRepairer.repair('{"anomalies": [{"id": "ANO-001"}')
        assert ok is True
        assert data == {"anomalies": [{"id": "ANO-001"}]}

    def test_troncature_sans_aucune_accolade_fermante_reste_irrecuperable(self):
        # Limite connue : l'extraction repose sur la presence d'un '}'.
        ok, data, msg = JsonRepairer.repair('{"a": {"b": [1, 2')
        assert ok is False
        assert data == {}
        assert "Aucun objet JSON" in msg

    def test_absence_de_json_est_signalee(self):
        ok, data, msg = JsonRepairer.repair("Le modele a repondu en texte libre.")
        assert ok is False
        assert data == {}
        assert "Aucun objet JSON" in msg

    def test_json_irreparable_ne_leve_pas_d_exception(self):
        ok, data, msg = JsonRepairer.repair('{"a": 1 "b" 2 ///}')
        assert ok is False
        assert data == {}
        assert msg == "JSON irréparable"

    def test_chaine_vide(self):
        ok, _, msg = JsonRepairer.repair("")
        assert ok is False
        assert "Aucun objet JSON" in msg


# ╔══════════════════════════════════════════════════════════════════╗
# ║  run_with_retry                                                  ║
# ╚══════════════════════════════════════════════════════════════════╝

class _StubResult:
    def __init__(self, answer, iterations=1):
        self.answer = answer
        self.total_iterations = iterations
        self.steps = []


class _StubAgent:
    system_prompt = "agent de test"

    def __init__(self, answers):
        self.answers = list(answers)
        self.tasks = []

    def run(self, task):
        self.tasks.append(task)
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        return _StubResult(answer)


class TestRunWithRetry:

    def test_succes_du_premier_coup(self):
        agent = _StubAgent(['{"ok": true}'])
        out = run_with_retry(agent, "audite CUSTOMER")
        assert out["method"] == "agent"
        assert out["attempt"] == 1
        assert out["report"] == {"ok": True}

    def test_seconde_tentative_durcit_la_consigne_json(self):
        agent = _StubAgent(["pas de json ici", '{"ok": true}'])
        out = run_with_retry(agent, "audite CUSTOMER")
        assert out["method"] == "retry"
        assert out["attempt"] == 2
        assert "UNIQUEMENT avec un objet JSON valide" in agent.tasks[1]

    def test_exception_agent_n_interrompt_pas_le_retry(self):
        agent = _StubAgent([RuntimeError("ollama down"), '{"ok": true}'])
        out = run_with_retry(agent, "audite CUSTOMER")
        assert out["method"] == "retry"

    def test_echec_total_sans_fallback(self):
        agent = _StubAgent(["texte", "texte"])
        out = run_with_retry(agent, "planifie", use_fallback=False)
        assert out["method"] == "error"
        assert "2 tentatives" in out["error"]

    def test_fallback_declenche_pour_un_agent_qualite(self, monkeypatch):
        calls = {}

        class _Scorer:
            def compute_quality(self, table):
                calls["table"] = table
                return {"table": table, "method": "FALLBACK_DETERMINISTIC"}

        monkeypatch.setattr("core.optimizations.FallbackScorer", _Scorer)
        agent = _StubAgent(["texte", "texte"])
        agent.system_prompt = "Agent d'audit de QUALITE des donnees"

        out = run_with_retry(agent, "audite")
        assert out["method"] == "fallback"
        assert out["report"]["method"] == "FALLBACK_DETERMINISTIC"
        assert calls["table"] == "CUSTOMER"

    def test_fallback_en_erreur_est_remonte_proprement(self, monkeypatch):
        class _Scorer:
            def compute_quality(self, table):
                raise RuntimeError("SQL Server injoignable")

        monkeypatch.setattr("core.optimizations.FallbackScorer", _Scorer)
        agent = _StubAgent(["texte", "texte"])
        agent.system_prompt = "audit qualite"

        out = run_with_retry(agent, "audite")
        assert out["method"] == "error"
        assert "SQL Server injoignable" in out["error"]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  FallbackScorer — scoring sans SLM                               ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestFallbackScorer:
    """
    Dernier recours quand le SLM ne produit rien d'exploitable : le score est
    recalcule en SQL pur. Il doit rester coherent avec le bareme de l'agent.
    """

    @pytest.fixture
    def db(self, monkeypatch, fake_db):
        recent = (datetime.now() - timedelta(days=2)).isoformat()

        fake_db.add("IS NULL THEN 1",
                    lambda sql, _: [(0,)] if "[_id]" in sql else [(20,)])
        fake_db.add("SELECT COUNT(*), SUM(CAST(_enabled AS INT))", [(4, 2)])
        fake_db.add("DATA_TYPE LIKE '%char%'", [("NAME",)])
        fake_db.add("INFORMATION_SCHEMA.COLUMNS", [("_id",), ("NAME",)])
        fake_db.add("COUNT(DISTINCT _id)", [(50,)])
        fake_db.add("MAX(END_TIME)", [(recent,)])
        fake_db.add("WHERE [NAME]='ULL'", [(5,)])
        fake_db.add(lambda sql: sql == "SELECT COUNT(*) FROM CUSTOMER", [(100,)])

        monkeypatch.setattr("pyodbc.connect", lambda *a, **k: fake_db.connect())
        return fake_db

    def test_score_calcule_sans_slm(self, db):
        report = FallbackScorer().compute_quality("CUSTOMER")

        assert report["table"] == "CUSTOMER"
        assert report["method"] == "FALLBACK_DETERMINISTIC"
        # D1 90 + D2 50 + D3 50 + D4 50 + D5 90 + D6 95, ponderes -> 68,5
        assert report["quality_score"]["global"] == pytest.approx(68.5, abs=0.2)
        assert report["quality_score"]["grade"] == "B"

    def test_les_six_dimensions_sont_presentes(self, db):
        dimensions = FallbackScorer().compute_quality("CUSTOMER")["dimensions"]
        assert set(dimensions) == {"D1", "D2", "D3", "D4", "D5", "D6"}
        assert dimensions["D1"]["score"] == 90.0
        assert dimensions["D2"]["score"] == 50.0
        assert dimensions["D6"]["score"] == 95.0

    def test_conformite_est_estimee_faute_de_donnees(self, db):
        assert FallbackScorer().compute_quality("CUSTOMER")["dimensions"]["D4"]["score"] == 50.0

    def test_note_signale_l_absence_d_analyse_d_anomalies(self, db):
        assert "sans SLM" in FallbackScorer().compute_quality("CUSTOMER")["note"]

    def test_aucune_extraction_reussie_annule_la_fraicheur(self, monkeypatch, fake_db):
        fake_db.add("IS NULL THEN 1", [(0,)])
        fake_db.add("SELECT COUNT(*), SUM(CAST(_enabled AS INT))", [(4, 4)])
        fake_db.add("DATA_TYPE LIKE '%char%'", [])
        fake_db.add("INFORMATION_SCHEMA.COLUMNS", [("_id",)])
        fake_db.add("COUNT(DISTINCT _id)", [(100,)])
        fake_db.add("MAX(END_TIME)", [(None,)])
        fake_db.add(lambda sql: sql == "SELECT COUNT(*) FROM CUSTOMER", [(100,)])
        monkeypatch.setattr("pyodbc.connect", lambda *a, **k: fake_db.connect())

        report = FallbackScorer().compute_quality("CUSTOMER")
        assert report["dimensions"]["D5"]["score"] == 0

    def test_connexion_fermee_meme_en_cas_d_erreur(self, monkeypatch, fake_db):
        fake_db.add(lambda sql: True, lambda *_: (_ for _ in ()).throw(OSError("KO")))
        monkeypatch.setattr("pyodbc.connect", lambda *a, **k: fake_db.connect())

        with pytest.raises(OSError):
            FallbackScorer().compute_quality("CUSTOMER")
        assert fake_db.closed_connections == 1
