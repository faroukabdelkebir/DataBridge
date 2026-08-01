"""
Doublures de test partagees : base de donnees (pyodbc / PyMySQL) et client Ollama.

Le projet parle a SQL Server via `get_connection()` et a MySQL via `_connect()`.
Ces deux points d'entree sont les seules coutures a doubler : `FakeDB` rejoue des
resultats en fonction du SQL execute, ce qui permet de tester la logique des
tools sans base.
"""

import copy
import json
import re

from core.ollama_client import AgentResponse, ToolCall


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Base de donnees simulee                                         ║
# ╚══════════════════════════════════════════════════════════════════╝

def normalize_sql(sql: str) -> str:
    """Ecrase les sauts de ligne / indentations pour rendre le matching lisible."""
    return " ".join(str(sql).split())


class FakeResult:
    """Resultat d'un execute : des lignes et, optionnellement, des noms de colonnes."""

    def __init__(self, rows=None, columns=None):
        self.rows = list(rows or [])
        self.columns = columns


class FakeCursor:
    def __init__(self, db):
        self._db = db
        self._result = FakeResult()

    # pyodbc expose `description` apres execute ; utilise par get_batch_config.
    @property
    def description(self):
        if self._result.columns is None:
            return None
        return [(c, None, None, None, None, None, None) for c in self._result.columns]

    @property
    def lastrowid(self):
        return self._db.lastrowid

    def execute(self, sql, *params):
        self._db.executed.append((normalize_sql(sql), params))
        self._result = self._db.resolve(sql, params)
        return self

    def fetchone(self):
        return self._result.rows[0] if self._result.rows else None

    def fetchall(self):
        return list(self._result.rows)

    def close(self):
        pass

    # PyMySQL s'utilise en `with conn.cursor() as cur:`
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class FakeConnection:
    def __init__(self, db):
        self._db = db
        self.closed = False

    def cursor(self):
        return FakeCursor(self._db)

    def close(self):
        self.closed = True
        self._db.closed_connections += 1


class FakeDB:
    """
    Base simulee pilotee par des regles `(matcher, resultat)`.

    `matcher` : sous-chaine, regex compilee, ou callable(sql_normalise) -> bool.
    `resultat`: liste de lignes, `FakeResult`, ou callable(sql, params) -> l'un des deux.

    La premiere regle qui matche gagne ; un SQL non couvert leve une AssertionError
    (un test qui oublie une requete le voit tout de suite au lieu de recevoir []).
    """

    def __init__(self, rules=()):
        self.rules = list(rules)
        self.executed = []
        self.closed_connections = 0
        self.lastrowid = 1

    def add(self, matcher, result):
        self.rules.append((matcher, result))
        return self

    def connect(self, *args, **kwargs):
        return FakeConnection(self)

    def resolve(self, sql, params):
        norm = normalize_sql(sql)
        for matcher, result in self.rules:
            if self._matches(matcher, norm):
                value = result(norm, params) if callable(result) else result
                return value if isinstance(value, FakeResult) else FakeResult(value)
        raise AssertionError(f"FakeDB : aucune regle pour la requete -> {norm}")

    @staticmethod
    def _matches(matcher, norm_sql):
        if callable(matcher) and not isinstance(matcher, re.Pattern):
            return bool(matcher(norm_sql))
        if isinstance(matcher, re.Pattern):
            return bool(matcher.search(norm_sql))
        return str(matcher) in norm_sql

    def queries_matching(self, fragment):
        return [sql for sql, _ in self.executed if fragment in sql]


# ╔══════════════════════════════════════════════════════════════════╗
# ║  Client Ollama simule                                            ║
# ╚══════════════════════════════════════════════════════════════════╝

class FakeOllamaClient:
    """
    Rejoue une file de reponses SLM. Chaque `chat()` depile la suivante ;
    une Exception dans la file est levee (simulation de panne Ollama).

    `repeat_last=True` : la derniere reponse est resservie indefiniment
    (utile pour tester l'atteinte de max_iterations).
    """

    def __init__(self, responses=(), repeat_last=False, model="qwen2.5:7b"):
        self.responses = list(responses)
        self.repeat_last = repeat_last
        self.model = model
        self.base_url = "http://ollama.invalid:11434"
        self.calls = []

    def chat(self, messages, tools=None, temperature=0.1):
        self.calls.append({
            "messages": copy.deepcopy(messages),
            "tools": tools,
            "temperature": temperature,
        })
        if not self.responses:
            raise AssertionError(
                f"FakeOllamaClient : appel chat n°{len(self.calls)} inattendu "
                "(file de reponses vide)"
            )
        response = self.responses[0] if (self.repeat_last and len(self.responses) == 1) \
            else self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    def is_available(self):
        return True

    def list_models(self):
        return [self.model]

    @property
    def last_messages(self):
        return self.calls[-1]["messages"] if self.calls else []


def slm_text(content, prompt_tokens=10, completion_tokens=5):
    """Reponse finale du SLM (pas de tool call)."""
    return AgentResponse(
        content=content, tool_calls=[], done=True,
        prompt_tokens=prompt_tokens, completion_tokens=completion_tokens,
    )


def slm_tool_call(name, arguments=None, content="", prompt_tokens=10, completion_tokens=5):
    """Reponse du SLM demandant l'execution d'un tool."""
    return AgentResponse(
        content=content,
        tool_calls=[ToolCall(name=name, arguments=arguments or {})],
        done=False,
        prompt_tokens=prompt_tokens, completion_tokens=completion_tokens,
    )


def make_agent(agent_cls, responses=(), repeat_last=False, **attrs):
    """Instancie un agent en remplacant son client Ollama par la doublure."""
    agent = agent_cls(model="qwen2.5:7b")
    agent.client = FakeOllamaClient(responses, repeat_last=repeat_last)
    for key, value in attrs.items():
        setattr(agent, key, value)
    return agent


def tool_output_step(step_cls, iteration, tool_name, payload):
    """Fabrique un AgentStep porteur d'une sortie de tool serialisee."""
    return step_cls(
        iteration=iteration, thought=None, tool_name=tool_name,
        tool_input={}, tool_output=json.dumps(payload, ensure_ascii=False),
    )
