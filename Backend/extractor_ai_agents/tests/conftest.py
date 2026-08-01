"""Fixtures partagees par toute la suite."""

import contextlib
import datetime as _datetime_module
from unittest import mock

import pytest

from tests.helpers import FakeDB


@pytest.fixture(scope="session", autouse=True)
def garde_fou_settings_de_test():
    """
    Refuse de laisser la suite s'executer sur les settings de production.

    Un `DJANGO_SETTINGS_MODULE=settings` present dans l'environnement (c'est le
    cas dans l'image Docker) prend le pas sur `pytest.ini`. Les tests taperaient
    alors sur les vrais hotes du .env : mieux vaut echouer bruyamment.
    """
    from django.conf import settings

    cibles = {
        "BI_HOST": settings.BI_HOST,
        "MYSQL_HOST": settings.MYSQL_HOST,
        "ORACLE_HOST": settings.ORACLE_HOST,
        "OLLAMA_BASE_URL": settings.OLLAMA_BASE_URL,
        "OAUTH2_ISSUER": settings.OAUTH2_ISSUER,
    }
    fautives = [nom for nom, valeur in cibles.items() if ".invalid" not in str(valeur)]
    if fautives:
        raise RuntimeError(
            "La suite ne tourne pas sur tests/settings_test.py : "
            f"{', '.join(fautives)} pointe(nt) hors du domaine .invalid. "
            "Verifiez DJANGO_SETTINGS_MODULE (l'environnement prime sur pytest.ini)."
        )


def pytest_collection_modifyitems(items):
    """Marque automatiquement les tests selon leur dossier (`unit` / `regression`)."""
    for item in items:
        path = str(item.fspath).replace("\\", "/")
        if "/tests/unit/" in path:
            item.add_marker(pytest.mark.unit)
        elif "/tests/regression/" in path:
            item.add_marker(pytest.mark.regression)


@pytest.fixture
def fake_db():
    """Base SQL simulee, vierge : chaque test declare les requetes qu'il attend."""
    return FakeDB()


@pytest.fixture
def patch_quality_db(monkeypatch, fake_db):
    """Branche la base simulee sur les tools de l'agent M1."""
    monkeypatch.setattr(
        "agent_anomaly_quality.tools.anomaly_quality_tools.get_connection",
        fake_db.connect,
    )
    return fake_db


@pytest.fixture
def patch_scheduling_db(monkeypatch, fake_db):
    """Branche la base simulee sur les tools de l'agent M3."""
    monkeypatch.setattr(
        "agent_scheduler.tools.scheduling_tools.get_connection",
        fake_db.connect,
    )
    return fake_db


@pytest.fixture
def reset_reports_store():
    """Remet a zero le flag de creation de schema MySQL entre deux tests."""
    from core import reports_store

    reports_store._schema_ready = False
    yield reports_store
    reports_store._schema_ready = False


@pytest.fixture
def reset_reco_cache():
    """Remet le cache de recommandation M3 dans son etat initial."""
    from agent_scheduler import views

    def _reset():
        cache = views._reco_cache
        cache.status = "idle"
        cache.report = None
        cache.error = None
        cache.started_at = None
        cache.computed_at = None
        cache.duration_sec = None

    _reset()
    yield views._reco_cache
    _reset()


@pytest.fixture
def freeze_hour():
    """
    Fige l'heure vue par le code qui fait `from datetime import datetime` a
    l'interieur d'une fonction (cas de `SchedulerAgent.should_launch_now`).
    """

    @contextlib.contextmanager
    def _freeze(hour, minute=0):
        frozen = _datetime_module.datetime(2025, 6, 15, hour, minute)
        fake = mock.MagicMock(wraps=_datetime_module.datetime)
        fake.now.return_value = frozen
        fake.fromisoformat.side_effect = _datetime_module.datetime.fromisoformat
        with mock.patch.object(_datetime_module, "datetime", fake):
            yield frozen

    return _freeze


@pytest.fixture
def api_principal():
    """Principal OAuth2 authentifie disposant de tous les scopes."""
    from core.oauth2_auth import OAuth2Principal

    return OAuth2Principal({"client_id": "test-client", "scope": "ai:read ai:execute"})
