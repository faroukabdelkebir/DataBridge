"""
Plugin pytest charge AVANT tout le reste (voir `-p tests_bootstrap` dans pytest.ini).

Raison d'etre : `pytest.ini` declare `DJANGO_SETTINGS_MODULE = tests.settings_test`,
mais une variable d'environnement du meme nom lui est PRIORITAIRE — et l'image
Docker exporte `DJANGO_SETTINGS_MODULE=settings`. Sans ce forcage, lancer la suite
dans le conteneur la ferait tourner sur les settings de PRODUCTION, donc sur les
vrais hotes SQL Server / MySQL / Ollama du .env.

Un `conftest.py` ne suffit pas : pytest-django resout les settings pendant
`pytest_load_initial_conftests`, avant l'import des conftest du projet. Un module
passe via `-p` est importe plus tot, ce qui garantit l'ordre.

Le garde-fou de `tests/conftest.py` verifie ensuite que le forcage a bien pris.
"""

import os

os.environ["DJANGO_SETTINGS_MODULE"] = "tests.settings_test"
