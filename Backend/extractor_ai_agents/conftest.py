"""
Configuration pytest a la racine.

Les scripts `test_agents.py`, `test_conn.py` et `test_tool_calling.py` de la racine
sont des utilitaires CLI (ils ouvrent des connexions SQL Server / Ollama des
l'import) et non des tests : on les exclut explicitement de la collecte pour que
`pytest` reste executable sans base ni SLM.

Le choix des settings de test est force en amont par `tests_bootstrap.py`
(charge via `-p` dans pytest.ini), car un `conftest.py` est importe trop tard
pour prendre le pas sur la variable d'environnement DJANGO_SETTINGS_MODULE.
"""

collect_ignore = [
    "test_agents.py",
    "test_conn.py",
    "test_tool_calling.py",
    "eval_agents.py",
    "tests_bootstrap.py",
    "venv",
]
