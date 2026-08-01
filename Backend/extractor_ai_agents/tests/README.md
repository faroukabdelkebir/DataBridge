# Tests — SEO7 AI Agents

Suite de tests **unitaires** et de **non-régression** du microservice d'agents IA.

Aucun test ne touche SQL Server, Oracle, MySQL, Ollama ni AuthMS : les quatre
frontières externes sont doublées. La suite s'exécute donc partout, en moins de
2 secondes, sans infrastructure.

## Lancer les tests

```bash
pip install -r requirements.txt -r requirements-dev.txt

pytest                       # toute la suite
pytest -m unit               # tests unitaires seulement
pytest -m regression         # non-régression seulement
pytest tests/unit/test_base_agent.py -v
pytest --cov=core --cov=agent_anomaly_quality --cov=agent_scheduler \
       --cov=reports_views --cov=urls --cov-report=term-missing
```

Valider sur l'interpréteur de production (3.12, pas le 3.14 du venv local) :

```bash
docker build -t seo7-ai-agents:test .
docker run --rm --user root --entrypoint sh seo7-ai-agents:test \
  -c 'pip install -q pytest pytest-django && cd /app && python -m pytest -q'
```

### Sélection des settings de test

`pytest.ini` déclare `DJANGO_SETTINGS_MODULE = tests.settings_test`, **mais une
variable d'environnement du même nom lui est prioritaire** — et l'image Docker
exporte `DJANGO_SETTINGS_MODULE=settings`. Sans précaution, la suite tournerait
sur les settings de production, donc sur les vrais hôtes du `.env`.

Deux protections :

- `tests_bootstrap.py`, chargé via `-p` dans `pytest.ini`, force la variable
  avant que pytest-django ne résolve les settings (un `conftest.py` est importé
  trop tard pour ça) ;
- une fixture `autouse` de session dans `tests/conftest.py` vérifie que
  `BI_HOST`, `MYSQL_HOST`, `ORACLE_HOST`, `OLLAMA_BASE_URL` et `OAUTH2_ISSUER`
  pointent bien sur le domaine `.invalid`, et fait échouer la suite sinon.

## Organisation

| Chemin | Contenu |
|---|---|
| `tests/settings_test.py` | Settings Django de test : toutes les cibles externes pointent sur des hôtes `.invalid` |
| `tests/helpers.py` | Doublures partagées : `FakeDB` (pyodbc/PyMySQL), `FakeOllamaClient` |
| `tests/conftest.py` | Fixtures : bases simulées, gel de l'heure, réinitialisation des caches, principal OAuth2 |
| `tests/unit/` | Une fonction/classe isolée à la fois |
| `tests/regression/` | Contrats externes : HTTP et sorties d'agents de bout en bout |

### Tests unitaires

| Fichier | Couvre |
|---|---|
| `test_base_agent.py` | Boucle ReAct : exécution des tools, filtrage des arguments inventés, cache, anti-boucle, comptage des tokens, erreurs Ollama |
| `test_ollama_client.py` | Payload envoyé, parsing des `tool_calls`, traduction des pannes réseau |
| `test_optimizations.py` | `CacheManager`, `JsonRepairer`, `run_with_retry`, `FallbackScorer` |
| `test_oauth2_auth.py` | Validation RS256 réelle (paire de clés générée à la volée), contrôle par scope |
| `test_quality_tools.py` | Les 10 tools M1 : les 6 dimensions, pondération, plafonnement au grade F, liste blanche des tables |
| `test_scheduling_tools.py` | Les 6 tools M3 : historique, patterns horaires, streaks, créneau optimal |
| `test_anomaly_quality_agent.py` | Agent M1 : rapport chiffré déterministe, interprétation, trace |
| `test_scheduler_agent.py` | Agent M3 : helpers, filet `finalize()`, décision temps réel |
| `test_reports_store.py` | Persistance MySQL best-effort |

### Tests de non-régression

| Fichier | Verrouille |
|---|---|
| `test_api_endpoints.py` | Routage, *deny by default* (401/403), codes 400/404/500/503, forme des réponses, calcul M3 en arrière-plan |
| `test_agent_pipelines.py` | Les deux agents de bout en bout (vrais tools, vraie boucle ReAct) face à un SLM 7B défaillant |

## Écrire un nouveau test

`FakeDB` répond en fonction du SQL exécuté ; la **première règle qui correspond
gagne**, et une requête non couverte lève une `AssertionError` explicite.

```python
def test_ma_mesure(patch_quality_db):
    patch_quality_db.add("COUNT(DISTINCT", [(50,)])          # sous-chaîne
    patch_quality_db.add(lambda sql: sql.endswith("X"), [])  # ou prédicat
    ...
```

`FakeOllamaClient` rejoue une file de réponses SLM (`slm_text`,
`slm_tool_call`) ; une exception dans la file simule une panne d'Ollama.

```python
agent = make_agent(SchedulerAgent, [
    slm_tool_call("get_job_history", {}),
    slm_text('{"recommended_schedule": {...}}'),
])
```

Utilisez `repeat_last=True` quand l'agent peut relancer le SLM pour forcer une
conclusion : sinon la file se vide et le test échoue pour une mauvaise raison.

## Points d'attention connus

- Les scripts `test_agents.py`, `test_conn.py` et `test_tool_calling.py` de la
  racine sont des **utilitaires CLI**, pas des tests : ils ouvrent des
  connexions dès l'import et sont exclus de la collecte par le `conftest.py`
  racine.
- `test_ollama_eteint_remonte_une_reponse_lisible`
  (`tests/regression/test_agent_pipelines.py`) fige un comportement actuel et
  non souhaitable : quand Ollama lève, `BaseAgent.run()` sort **sans passer par
  `finalize()`**, donc le filet déterministe de M3 ne joue pas et l'API renvoie
  `{"raw_answer": "", "parse_error": true}` au lieu d'un planning. À corriger
  dans `core/base_agent.py` — le test devra alors être mis à jour.
