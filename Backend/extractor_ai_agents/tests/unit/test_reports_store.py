"""
Tests unitaires du stockage MySQL des rapports (core/reports_store.py).

PyMySQL est double par `FakeDB`. Regle metier centrale verifiee ici : la
persistance est best-effort et ne doit JAMAIS casser la reponse HTTP d'un agent.
"""

import json

import pytest

from tests.helpers import FakeDB


@pytest.fixture
def store(reset_reports_store):
    return reset_reports_store


@pytest.fixture
def patch_mysql(monkeypatch, store):
    """Branche la base simulee sur `_connect` et memorise les appels."""
    connexions = []

    def _install(db=None):
        db = db or FakeDB()
        db.add("CREATE DATABASE", [])
        db.add("USE `", [])
        db.add("CREATE TABLE", [])

        def _connect(with_db=True):
            connexions.append(with_db)
            return db.connect()

        monkeypatch.setattr(store, "_connect", _connect)
        return db

    _install.connexions = connexions
    return _install


# ╔══════════════════════════════════════════════════════════════════╗
# ║  init_schema                                                     ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestInitSchema:

    def test_cree_la_base_puis_la_table(self, store, patch_mysql):
        db = patch_mysql()
        store.init_schema()

        sqls = " | ".join(sql for sql, _ in db.executed)
        assert "CREATE DATABASE IF NOT EXISTS `ai_agents_test`" in sqls
        assert "CREATE TABLE IF NOT EXISTS agent_reports" in sqls

    def test_se_connecte_sans_selectionner_la_base(self, store, patch_mysql):
        # La base peut ne pas exister encore : impossible de la selectionner.
        patch_mysql()
        store.init_schema()
        assert patch_mysql.connexions == [False]

    def test_idempotent_sur_la_duree_du_process(self, store, patch_mysql):
        db = patch_mysql()
        store.init_schema()
        store.init_schema()
        store.init_schema()
        assert len([s for s, _ in db.executed if "CREATE DATABASE" in s]) == 1

    def test_connexion_fermee(self, store, patch_mysql):
        db = patch_mysql()
        store.init_schema()
        assert db.closed_connections == 1

    def test_echec_ne_marque_pas_le_schema_comme_pret(self, store, monkeypatch):
        monkeypatch.setattr(store, "_connect",
                            lambda with_db=True: (_ for _ in ()).throw(OSError("mysql down")))
        with pytest.raises(OSError):
            store.init_schema()
        assert store._schema_ready is False


# ╔══════════════════════════════════════════════════════════════════╗
# ║  save_report                                                     ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestSaveReport:

    def test_insertion_complete(self, store, patch_mysql):
        db = patch_mysql()
        db.add("INSERT INTO agent_reports", [])
        db.lastrowid = 77

        report_id = store.save_report(
            agent="anomaly_quality", report_type="audit_table",
            report={"grade": "F"}, target="CUSTOMER",
            model="qwen2.5:7b", duration_sec=12.4,
        )

        assert report_id == 77
        _, params = next((s, p) for s, p in db.executed if "INSERT INTO" in s)
        valeurs = params[0]
        assert valeurs[:5] == ("anomaly_quality", "audit_table", "CUSTOMER",
                               "qwen2.5:7b", "completed")
        assert json.loads(valeurs[5]) == {"grade": "F"}
        assert valeurs[7] == 12.4

    def test_rapport_absent_est_stocke_a_null(self, store, patch_mysql):
        db = patch_mysql()
        db.add("INSERT INTO agent_reports", [])

        store.save_report(agent="scheduler", report_type="scheduler_recommend",
                          report=None, status="error", error="Ollama injoignable")
        _, params = next((s, p) for s, p in db.executed if "INSERT INTO" in s)
        assert params[0][5] is None
        assert params[0][4] == "error"
        assert params[0][6] == "Ollama injoignable"

    def test_objets_non_serialisables_sont_convertis(self, store, patch_mysql):
        from datetime import datetime

        db = patch_mysql()
        db.add("INSERT INTO agent_reports", [])
        store.save_report(agent="a", report_type="t", report={"date": datetime(2025, 6, 1)})

        _, params = next((s, p) for s, p in db.executed if "INSERT INTO" in s)
        assert "2025-06-01" in params[0][5]

    def test_accents_ne_sont_pas_echappes(self, store, patch_mysql):
        db = patch_mysql()
        db.add("INSERT INTO agent_reports", [])
        store.save_report(agent="a", report_type="t", report={"note": "qualité dégradée"})

        _, params = next((s, p) for s, p in db.executed if "INSERT INTO" in s)
        assert "qualité dégradée" in params[0][5]

    def test_panne_mysql_ne_casse_pas_la_reponse_de_l_agent(self, store, monkeypatch, caplog):
        # Regression : un MySQL indisponible faisait echouer POST /api/m1/audit/.
        monkeypatch.setattr(store, "init_schema", lambda: None)
        monkeypatch.setattr(store, "_connect",
                            lambda with_db=True: (_ for _ in ()).throw(OSError("mysql down")))

        assert store.save_report(agent="a", report_type="t", report={"x": 1}) is None
        assert "Échec de sauvegarde" in caplog.text

    def test_connexion_fermee_meme_en_cas_d_erreur_sql(self, store, patch_mysql):
        db = patch_mysql()
        db.add("INSERT INTO agent_reports", lambda *_: 1 / 0)
        assert store.save_report(agent="a", report_type="t", report={}) is None
        assert db.closed_connections == 2  # init_schema + save_report


# ╔══════════════════════════════════════════════════════════════════╗
# ║  list_reports                                                    ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestListReports:

    def test_liste_sans_filtre(self, store, patch_mysql):
        db = patch_mysql()
        db.add("FROM agent_reports", [{"id": 2, "agent": "scheduler"},
                                      {"id": 1, "agent": "anomaly_quality"}])

        rows = store.list_reports()
        assert [r["id"] for r in rows] == [2, 1]
        sql, params = next((s, p) for s, p in db.executed if "FROM agent_reports" in s)
        assert "WHERE" not in sql
        assert params[0] == [50]

    def test_le_corps_du_rapport_n_est_pas_charge(self, store, patch_mysql):
        db = patch_mysql()
        db.add("FROM agent_reports", [])
        store.list_reports()

        sql, _ = next((s, p) for s, p in db.executed if "FROM agent_reports" in s)
        assert "SELECT id, agent, report_type" in sql
        assert " report," not in sql

    def test_filtres_agent_et_type(self, store, patch_mysql):
        db = patch_mysql()
        db.add("FROM agent_reports", [])
        store.list_reports(agent="scheduler", report_type="scheduler_recommend")

        sql, params = next((s, p) for s, p in db.executed if "FROM agent_reports" in s)
        assert "WHERE agent = %s AND report_type = %s" in sql
        assert params[0][:2] == ["scheduler", "scheduler_recommend"]

    @pytest.mark.parametrize("demande,applique", [(10, 10), (0, 1), (-5, 1), (5000, 500)])
    def test_limite_est_bornee(self, store, patch_mysql, demande, applique):
        db = patch_mysql()
        db.add("FROM agent_reports", [])
        store.list_reports(limit=demande)

        _, params = next((s, p) for s, p in db.executed if "FROM agent_reports" in s)
        assert params[0][-1] == applique

    def test_tri_du_plus_recent_au_plus_ancien(self, store, patch_mysql):
        db = patch_mysql()
        db.add("FROM agent_reports", [])
        store.list_reports()
        sql, _ = next((s, p) for s, p in db.executed if "FROM agent_reports" in s)
        assert "ORDER BY id DESC" in sql


# ╔══════════════════════════════════════════════════════════════════╗
# ║  get_report                                                      ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestGetReport:

    def test_corps_json_est_deserialise(self, store, patch_mysql):
        db = patch_mysql()
        db.add("WHERE id = %s", [{"id": 5, "report": '{"grade": "F"}'}])

        row = store.get_report(5)
        assert row["report"] == {"grade": "F"}

    def test_rapport_introuvable(self, store, patch_mysql):
        db = patch_mysql()
        db.add("WHERE id = %s", [])
        assert store.get_report(404) is None

    def test_corps_illisible_est_laisse_tel_quel(self, store, patch_mysql):
        db = patch_mysql()
        db.add("WHERE id = %s", [{"id": 5, "report": "pas du json"}])
        assert store.get_report(5)["report"] == "pas du json"

    def test_corps_null(self, store, patch_mysql):
        db = patch_mysql()
        db.add("WHERE id = %s", [{"id": 5, "report": None}])
        assert store.get_report(5)["report"] is None

    def test_identifiant_passe_en_parametre_lie(self, store, patch_mysql):
        # Pas de concatenation : la requete reste parametree.
        db = patch_mysql()
        db.add("WHERE id = %s", [])
        store.get_report(42)

        sql, params = next((s, p) for s, p in db.executed if "WHERE id" in s)
        assert sql == "SELECT * FROM agent_reports WHERE id = %s"
        assert params[0] == (42,)
