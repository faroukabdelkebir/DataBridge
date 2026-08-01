"""
Tests de non-regression de la couche HTTP.

Ils verrouillent le contrat expose aux clients (le microservice Java et le front) :
routes, codes de retour, forme des reponses et politique d'acces "deny by default".
Toute rupture ici casse un appelant en production.
"""

import json

import pytest
from django.urls import Resolver404, resolve
from rest_framework.test import APIClient, APIRequestFactory, force_authenticate

from agent_anomaly_quality.views import AuditAllView, AuditTableView, QuickScoreView
from agent_scheduler.views import (BatchConfigView, SchedulerRecommendView,
                                   ShouldLaunchView)
from core.oauth2_auth import OAuth2Principal
from reports_views import ReportDetailView, ReportListView


ROUTES = [
    ("/", "get"),
    ("/api/m1/audit/", "post"),
    ("/api/m1/audit-all/", "post"),
    ("/api/m1/quick-score/", "post"),
    ("/api/m3/scheduler/recommend/", "post"),
    ("/api/m3/scheduler/should-launch/", "post"),
    ("/api/m3/scheduler/config/", "get"),
    ("/api/reports/", "get"),
    ("/api/reports/42/", "get"),
]

PROTEGEES = [(url, verbe) for url, verbe in ROUTES if url != "/"]


def appel(view, methode, chemin, data=None, principal=None, **kwargs):
    """Invoque une vue DRF hors routage, avec ou sans authentification forcee."""
    factory = APIRequestFactory()
    request = getattr(factory, methode)(chemin, data, format="json")
    if principal is not None:
        force_authenticate(request, user=principal)
    return view.as_view()(request, **kwargs)


@pytest.fixture
def lecteur():
    """Principal VISITOR : lecture seule."""
    return OAuth2Principal({"client_id": "front", "scope": "ai:read"})


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Routage                                                         ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestRoutage:

    @pytest.mark.parametrize("url,_verbe", ROUTES)
    def test_toutes_les_routes_publiees_resolvent(self, url, _verbe):
        assert resolve(url) is not None

    def test_identifiant_de_rapport_est_typé_entier(self):
        assert resolve("/api/reports/42/").kwargs == {"report_id": 42}
        with pytest.raises(Resolver404):
            resolve("/api/reports/abc/")

    @pytest.mark.parametrize("url,vue", [
        ("/api/m1/audit/", AuditTableView),
        ("/api/m1/audit-all/", AuditAllView),
        ("/api/m1/quick-score/", QuickScoreView),
        ("/api/m3/scheduler/recommend/", SchedulerRecommendView),
        ("/api/m3/scheduler/should-launch/", ShouldLaunchView),
        ("/api/m3/scheduler/config/", BatchConfigView),
        ("/api/reports/", ReportListView),
        ("/api/reports/1/", ReportDetailView),
    ])
    def test_chaque_url_pointe_sur_la_bonne_vue(self, url, vue):
        assert resolve(url).func.cls is vue


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Securite : deny by default                                      ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestSecurite:

    @pytest.mark.parametrize("url,verbe", PROTEGEES)
    def test_aucun_endpoint_metier_n_est_ouvert_sans_token(self, url, verbe):
        response = getattr(APIClient(), verbe)(url, {}, format="json")
        assert response.status_code == 401

    @pytest.mark.parametrize("url,verbe", PROTEGEES)
    def test_token_illisible_est_refuse(self, url, verbe):
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION="Bearer pas-un-jwt")
        assert getattr(client, verbe)(url, {}, format="json").status_code == 401

    def test_challenge_bearer_est_renvoye(self):
        response = APIClient().get("/api/reports/")
        assert response["WWW-Authenticate"] == 'Bearer realm="ai-agents"'

    @pytest.mark.parametrize("vue,chemin", [
        (AuditTableView, "/api/m1/audit/"),
        (AuditAllView, "/api/m1/audit-all/"),
    ])
    def test_scope_lecture_seule_ne_peut_pas_declencher_un_audit(self, vue, chemin, lecteur):
        # Un audit consomme ~5 min de SLM : reserve au scope ai:execute.
        assert appel(vue, "post", chemin, {}, principal=lecteur).status_code == 403

    def test_scope_lecture_suffit_pour_le_score_rapide(self, lecteur, monkeypatch):
        monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent",
                            lambda model: type("A", (), {"quick_score": lambda s, t: {"g": "F"}})())
        monkeypatch.setattr("core.reports_store.save_report", lambda **kw: 1)
        response = appel(QuickScoreView, "post", "/api/m1/quick-score/", {}, principal=lecteur)
        assert response.status_code == 200

    def test_scope_lecture_ne_peut_pas_lancer_un_calcul_de_planning(
            self, lecteur, reset_reco_cache):
        response = appel(SchedulerRecommendView, "post",
                         "/api/m3/scheduler/recommend/", {}, principal=lecteur)
        assert response.status_code == 403

    def test_scope_lecture_peut_consulter_le_planning(self, lecteur, reset_reco_cache):
        response = appel(SchedulerRecommendView, "get",
                         "/api/m3/scheduler/recommend/", principal=lecteur)
        assert response.status_code == 200


# ╔══════════════════════════════════════════════════════════════════╗
# ║  GET / — sante du service                                        ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestHealth:

    @pytest.fixture
    def stub_ollama(self, monkeypatch):
        def _install(available=True, models=("qwen2.5:7b",)):
            class _Client:
                def is_available(self):
                    return available

                def list_models(self):
                    return list(models)

            monkeypatch.setattr("core.ollama_client.OllamaClient", _Client)

        return _install

    def test_accessible_sans_authentification(self, stub_ollama):
        # La sonde de liveness du conteneur ne porte pas de token.
        stub_ollama()
        assert APIClient().get("/").status_code == 200

    def test_forme_de_la_reponse(self, stub_ollama):
        stub_ollama()
        data = APIClient().get("/").json()

        assert data["service"] == "AI Agents"
        assert data["status"] == "running"
        assert data["ollama"] == {"available": True, "models": ["qwen2.5:7b"],
                                  "active_model": "qwen2.5:7b"}
        assert set(data["agents"]) == {"M1", "M3"}

    def test_ollama_eteint_ne_fait_pas_echouer_la_sonde(self, stub_ollama):
        stub_ollama(available=False)
        response = APIClient().get("/")

        assert response.status_code == 200
        assert response.json()["ollama"] == {
            "available": False, "models": [], "active_model": "qwen2.5:7b"}


# ╔══════════════════════════════════════════════════════════════════╗
# ║  API M1 — audit qualite                                          ║
# ╚══════════════════════════════════════════════════════════════════╝

class _AgentM1Stub:
    instances = []

    def __init__(self, model="qwen2.5:7b"):
        self.model = model
        self.appels = []
        _AgentM1Stub.instances.append(self)

    def audit_table(self, table):
        self.appels.append(("audit_table", table))
        return {"table": table, "grade": "F"}

    def audit_all_tables(self):
        self.appels.append(("audit_all_tables", None))
        return {"worst_table": "CUSTOMER"}

    def quick_score(self, table):
        self.appels.append(("quick_score", table))
        return {"table": table, "global_score": 20.7}


@pytest.fixture
def stub_agent_m1(monkeypatch):
    _AgentM1Stub.instances = []
    monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent", _AgentM1Stub)
    return _AgentM1Stub


@pytest.fixture
def stub_save_report(monkeypatch):
    enregistres = []

    def _save(**kwargs):
        enregistres.append(kwargs)
        return len(enregistres)

    monkeypatch.setattr("core.reports_store.save_report", _save)
    return enregistres


class TestAuditTableView:

    def test_audit_reussi(self, api_principal, stub_agent_m1, stub_save_report):
        response = appel(AuditTableView, "post", "/api/m1/audit/",
                         {"table_name": "CUSTOMER"}, principal=api_principal)

        assert response.status_code == 200
        assert response.data["status"] == "completed"
        assert response.data["report"] == {"table": "CUSTOMER", "grade": "F"}
        assert response.data["report_id"] == 1

    def test_table_par_defaut(self, api_principal, stub_agent_m1, stub_save_report):
        appel(AuditTableView, "post", "/api/m1/audit/", {}, principal=api_principal)
        assert stub_agent_m1.instances[0].appels == [("audit_table", "CUSTOMER")]

    def test_modele_choisi_par_l_appelant(self, api_principal, stub_agent_m1, stub_save_report):
        appel(AuditTableView, "post", "/api/m1/audit/",
              {"model": "llama3.1:8b"}, principal=api_principal)
        assert stub_agent_m1.instances[0].model == "llama3.1:8b"

    def test_rapport_est_persiste_avec_ses_metadonnees(
            self, api_principal, stub_agent_m1, stub_save_report):
        appel(AuditTableView, "post", "/api/m1/audit/",
              {"table_name": "AA_BILL_DETAILS"}, principal=api_principal)

        enregistre = stub_save_report[0]
        assert enregistre["agent"] == "anomaly_quality"
        assert enregistre["report_type"] == "audit_table"
        assert enregistre["target"] == "AA_BILL_DETAILS"
        assert isinstance(enregistre["duration_sec"], float)

    def test_table_hors_liste_blanche_donne_400(self, api_principal, monkeypatch):
        monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent",
                            lambda model: type("A", (), {
                                "audit_table": lambda s, t: (_ for _ in ()).throw(
                                    ValueError("Table non autorisée: UTILISATEURS"))})())
        response = appel(AuditTableView, "post", "/api/m1/audit/",
                         {"table_name": "UTILISATEURS"}, principal=api_principal)

        assert response.status_code == 400
        assert "Table non autorisée" in response.data["error"]

    def test_ollama_injoignable_donne_503(self, api_principal, monkeypatch):
        monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent",
                            lambda model: type("A", (), {
                                "audit_table": lambda s, t: (_ for _ in ()).throw(
                                    ConnectionError("Impossible de se connecter à Ollama."))})())
        response = appel(AuditTableView, "post", "/api/m1/audit/", {},
                         principal=api_principal)

        assert response.status_code == 503
        assert "Ollama" in response.data["error"]

    def test_persistance_indisponible_ne_casse_pas_la_reponse(
            self, api_principal, stub_agent_m1, monkeypatch):
        # Regression : une panne MySQL faisait tomber l'audit en 500.
        monkeypatch.setattr("core.reports_store.save_report", lambda **kw: None)
        response = appel(AuditTableView, "post", "/api/m1/audit/", {},
                         principal=api_principal)

        assert response.status_code == 200
        assert response.data["report_id"] is None


class TestAuditAllView:

    def test_audit_comparatif(self, api_principal, stub_agent_m1, stub_save_report):
        response = appel(AuditAllView, "post", "/api/m1/audit-all/", {},
                         principal=api_principal)

        assert response.status_code == 200
        assert response.data["report"]["worst_table"] == "CUSTOMER"
        assert stub_save_report[0]["report_type"] == "audit_all"
        assert stub_save_report[0].get("target") is None

    @pytest.mark.parametrize("exception,code", [
        (ValueError("Table non autorisée: X"), 400),
        (ConnectionError("Ollama injoignable"), 503),
    ])
    def test_erreurs_metier_sont_traduites_en_http(self, api_principal, monkeypatch,
                                                   exception, code):
        monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent",
                            lambda model: type("A", (), {
                                "audit_all_tables": lambda s: (_ for _ in ()).throw(exception)})())
        response = appel(AuditAllView, "post", "/api/m1/audit-all/", {},
                         principal=api_principal)
        assert response.status_code == code


class TestQuickScoreView:

    @pytest.mark.parametrize("exception,code", [
        (ValueError("Table non autorisée: X"), 400),
        (ConnectionError("Ollama injoignable"), 503),
    ])
    def test_erreurs_metier_sont_traduites_en_http(self, api_principal, monkeypatch,
                                                   exception, code):
        monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent",
                            lambda model: type("A", (), {
                                "quick_score": lambda s, t: (_ for _ in ()).throw(exception)})())
        response = appel(QuickScoreView, "post", "/api/m1/quick-score/", {},
                         principal=api_principal)
        assert response.status_code == code

    def test_le_rapport_est_renvoye_a_plat(self, api_principal, stub_agent_m1,
                                           stub_save_report):
        # Contrat different des autres endpoints : pas d'enveloppe status/report.
        response = appel(QuickScoreView, "post", "/api/m1/quick-score/",
                         {"table_name": "CUSTOMER"}, principal=api_principal)

        assert response.status_code == 200
        assert response.data == {"table": "CUSTOMER", "global_score": 20.7}

    def test_le_score_rapide_est_aussi_historise(self, api_principal, stub_agent_m1,
                                                 stub_save_report):
        appel(QuickScoreView, "post", "/api/m1/quick-score/", {}, principal=api_principal)
        assert stub_save_report[0]["report_type"] == "quick_score"


# ╔══════════════════════════════════════════════════════════════════╗
# ║  API M3 — ordonnancement                                         ║
# ╚══════════════════════════════════════════════════════════════════╝

class _ThreadStub:
    """Execute la cible immediatement : le calcul de fond devient synchrone."""
    lances = []

    def __init__(self, target=None, args=(), kwargs=None, daemon=None):
        self._target, self._args, self._kwargs = target, args, kwargs or {}
        type(self).lances.append(self)

    def start(self):
        self._target(*self._args, **self._kwargs)


class _ThreadInerte(_ThreadStub):
    """Enregistre le lancement sans jamais executer la cible."""

    def start(self):
        pass


@pytest.fixture
def stub_thread(monkeypatch):
    def _install(cls=_ThreadStub):
        cls.lances = []
        monkeypatch.setattr("agent_scheduler.views.threading.Thread", cls)
        return cls

    return _install


@pytest.fixture
def stub_agent_m3(monkeypatch):
    def _install(report=None, exception=None, decision=None):
        class _Agent:
            def __init__(self, model="qwen2.5:7b"):
                self.model = model

            def recommend_schedule(self):
                if exception:
                    raise exception
                return report or {"recommended_schedule": {"CUSTOMER": {}}}

            def should_launch_now(self, batch_id):
                if exception:
                    raise exception
                return decision or {"launch": True, "reason": "ok",
                                    "recommended_time": None, "batch_id": batch_id}

        monkeypatch.setattr("agent_scheduler.views.SchedulerAgent", _Agent)

    return _install


class TestSchedulerRecommendView:

    def test_get_initial_renvoie_l_etat_au_repos(self, api_principal, reset_reco_cache):
        response = appel(SchedulerRecommendView, "get", "/api/m3/scheduler/recommend/",
                         principal=api_principal)

        assert response.status_code == 200
        assert response.data["status"] == "idle"
        assert response.data["report"] is None

    def test_post_lance_le_calcul_en_arriere_plan(self, api_principal, reset_reco_cache,
                                                  stub_thread, stub_agent_m3):
        # Le calcul dure ~5 min : la requete HTTP ne doit jamais le porter.
        stub_agent_m3()
        threads = stub_thread(_ThreadInerte)
        response = appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/",
                         {}, principal=api_principal)

        assert response.status_code == 202
        assert response.data["status"] == "computing"
        assert response.data["started_at"]
        assert len(threads.lances) == 1

    def test_calcul_termine_est_consultable_en_get(self, api_principal, reset_reco_cache,
                                                   stub_thread, stub_agent_m3,
                                                   stub_save_report):
        stub_agent_m3(report={"recommended_schedule": {"CUSTOMER": {"recommended_time": "21:00"}}})
        stub_thread()
        appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/", {},
              principal=api_principal)

        snapshot = appel(SchedulerRecommendView, "get", "/api/m3/scheduler/recommend/",
                         principal=api_principal).data
        assert snapshot["status"] == "completed"
        assert snapshot["report"]["recommended_schedule"]["CUSTOMER"]["recommended_time"] == "21:00"
        assert snapshot["computed_at"] and snapshot["duration_sec"] is not None
        assert stub_save_report[0]["report_type"] == "scheduler_recommend"

    def test_post_suivant_ressert_le_cache(self, api_principal, reset_reco_cache,
                                           stub_thread, stub_agent_m3, stub_save_report):
        stub_agent_m3()
        threads = stub_thread()
        appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/", {},
              principal=api_principal)
        response = appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/",
                         {}, principal=api_principal)

        assert response.status_code == 200
        assert response.data["cached"] is True
        assert len(threads.lances) == 1  # aucun recalcul

    def test_refresh_force_un_recalcul(self, api_principal, reset_reco_cache,
                                       stub_thread, stub_agent_m3, stub_save_report):
        stub_agent_m3()
        threads = stub_thread()
        appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/", {},
              principal=api_principal)
        response = appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/",
                         {"refresh": True}, principal=api_principal)

        assert response.status_code == 202
        assert len(threads.lances) == 2

    def test_calcul_en_cours_ne_declenche_pas_de_doublon(self, api_principal,
                                                         reset_reco_cache, stub_thread,
                                                         stub_agent_m3):
        stub_agent_m3()
        threads = stub_thread(_ThreadInerte)
        appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/", {},
              principal=api_principal)
        response = appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/",
                         {}, principal=api_principal)

        assert response.status_code == 202
        assert "déjà en cours" in response.data["message"]
        assert len(threads.lances) == 1

    def test_echec_du_calcul_est_expose_en_get(self, api_principal, reset_reco_cache,
                                               stub_thread, stub_agent_m3,
                                               stub_save_report):
        stub_agent_m3(exception=ConnectionError("Ollama injoignable"))
        stub_thread()
        appel(SchedulerRecommendView, "post", "/api/m3/scheduler/recommend/", {},
              principal=api_principal)

        snapshot = appel(SchedulerRecommendView, "get", "/api/m3/scheduler/recommend/",
                         principal=api_principal).data
        assert snapshot["status"] == "error"
        assert "Ollama injoignable" in snapshot["error"]
        assert stub_save_report[0]["status"] == "error"


class TestShouldLaunchView:

    def test_decision_positive(self, api_principal, stub_agent_m3):
        stub_agent_m3(decision={"launch": True, "reason": "créneau favorable",
                                "recommended_time": None, "batch_id": "CUSTOMER"})
        response = appel(ShouldLaunchView, "post", "/api/m3/scheduler/should-launch/",
                         {"batch_id": "CUSTOMER"}, principal=api_principal)

        assert response.status_code == 200
        assert response.data["launch"] is True

    def test_batch_id_obligatoire(self, api_principal, stub_agent_m3):
        stub_agent_m3()
        response = appel(ShouldLaunchView, "post", "/api/m3/scheduler/should-launch/",
                         {}, principal=api_principal)

        assert response.status_code == 400
        assert "batch_id" in response.data["error"]

    def test_panne_interne_reporte_le_batch_sans_erreur_http(self, api_principal,
                                                             stub_agent_m3):
        # Regression : le scheduler Java doit toujours recevoir une decision
        # exploitable, jamais une 500.
        stub_agent_m3(exception=RuntimeError("SQL Server injoignable"))
        response = appel(ShouldLaunchView, "post", "/api/m3/scheduler/should-launch/",
                         {"batch_id": "CUSTOMER"}, principal=api_principal)

        assert response.status_code == 200
        assert response.data["launch"] is False
        assert response.data["recommended_time"] == "20:55"
        assert response.data["batch_id"] == "CUSTOMER"
        assert "SQL Server injoignable" in response.data["reason"]

    def test_contrat_de_sortie_stable(self, api_principal, stub_agent_m3):
        stub_agent_m3()
        response = appel(ShouldLaunchView, "post", "/api/m3/scheduler/should-launch/",
                         {"batch_id": "CUSTOMER"}, principal=api_principal)
        assert set(response.data) == {"launch", "reason", "recommended_time", "batch_id"}


class TestBatchConfigView:

    def test_configuration_renvoyee(self, api_principal, monkeypatch):
        monkeypatch.setattr("agent_scheduler.views.get_batch_config",
                            lambda: {"batch_configs": [], "total_batches": 0})
        response = appel(BatchConfigView, "get", "/api/m3/scheduler/config/",
                         principal=api_principal)

        assert response.status_code == 200
        assert response.data == {"batch_configs": [], "total_batches": 0}

    def test_base_injoignable_donne_500(self, api_principal, monkeypatch):
        monkeypatch.setattr("agent_scheduler.views.get_batch_config",
                            lambda: (_ for _ in ()).throw(RuntimeError("SQL Server KO")))
        response = appel(BatchConfigView, "get", "/api/m3/scheduler/config/",
                         principal=api_principal)

        assert response.status_code == 500
        assert "SQL Server KO" in response.data["error"]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  API rapports                                                    ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestReportListView:

    def test_liste_paginee_par_limite(self, api_principal, monkeypatch):
        appels = {}

        def _list(agent=None, report_type=None, limit=50):
            appels.update(agent=agent, report_type=report_type, limit=limit)
            return [{"id": 2}, {"id": 1}]

        monkeypatch.setattr("core.reports_store.list_reports", _list)
        response = appel(ReportListView, "get",
                         "/api/reports/?agent=scheduler&type=audit_table&limit=10",
                         principal=api_principal)

        assert response.status_code == 200
        assert response.data == {"count": 2, "results": [{"id": 2}, {"id": 1}]}
        assert appels == {"agent": "scheduler", "report_type": "audit_table", "limit": 10}

    @pytest.mark.parametrize("brut", ["abc", "", "1.5"])
    def test_limite_illisible_retombe_sur_la_valeur_par_defaut(self, api_principal,
                                                               monkeypatch, brut):
        vues = {}
        monkeypatch.setattr("core.reports_store.list_reports",
                            lambda **kw: vues.update(kw) or [])
        appel(ReportListView, "get", f"/api/reports/?limit={brut}", principal=api_principal)
        assert vues["limit"] == 50

    def test_mysql_injoignable_donne_503(self, api_principal, monkeypatch):
        monkeypatch.setattr("core.reports_store.list_reports",
                            lambda **kw: (_ for _ in ()).throw(OSError("mysql down")))
        response = appel(ReportListView, "get", "/api/reports/", principal=api_principal)

        assert response.status_code == 503
        assert "mysql down" in response.data["error"]


class TestReportDetailView:

    def test_rapport_complet(self, api_principal, monkeypatch):
        monkeypatch.setattr("core.reports_store.get_report",
                            lambda rid: {"id": rid, "report": {"grade": "F"}})
        response = appel(ReportDetailView, "get", "/api/reports/7/",
                         principal=api_principal, report_id=7)

        assert response.status_code == 200
        assert response.data["report"] == {"grade": "F"}

    def test_rapport_inexistant_donne_404(self, api_principal, monkeypatch):
        monkeypatch.setattr("core.reports_store.get_report", lambda rid: None)
        response = appel(ReportDetailView, "get", "/api/reports/404/",
                         principal=api_principal, report_id=404)

        assert response.status_code == 404
        assert response.data["error"] == "Rapport introuvable"

    def test_mysql_injoignable_donne_503(self, api_principal, monkeypatch):
        monkeypatch.setattr("core.reports_store.get_report",
                            lambda rid: (_ for _ in ()).throw(OSError("mysql down")))
        response = appel(ReportDetailView, "get", "/api/reports/1/",
                         principal=api_principal, report_id=1)
        assert response.status_code == 503


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Serialisation                                                   ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestSerialisation:

    def test_les_reponses_sont_du_json(self, api_principal, stub_agent_m1,
                                       stub_save_report):
        response = appel(AuditTableView, "post", "/api/m1/audit/", {},
                         principal=api_principal)
        response.render()

        assert response["Content-Type"] == "application/json"
        assert json.loads(response.content)["status"] == "completed"

    def test_accents_survivent_a_la_serialisation(self, api_principal, monkeypatch):
        monkeypatch.setattr("agent_anomaly_quality.views.AnomalyQualityAgent",
                            lambda model: type("A", (), {
                                "quick_score": lambda s, t: {"note": "qualité dégradée"}})())
        monkeypatch.setattr("core.reports_store.save_report", lambda **kw: 1)
        response = appel(QuickScoreView, "post", "/api/m1/quick-score/", {},
                         principal=api_principal)
        response.render()

        assert json.loads(response.content)["note"] == "qualité dégradée"
