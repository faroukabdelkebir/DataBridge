"""
Base Agent — boucle ReAct (Reason → Act → Observe → Repeat).
Tous les agents héritent de cette classe.
"""

import json
import inspect
import logging
from dataclasses import dataclass, field
from typing import Optional

from core.ollama_client import OllamaClient, AgentResponse
from core.optimizations import CacheManager

logger = logging.getLogger("agents")


@dataclass
class AgentStep:
    """Un pas dans la trace de raisonnement de l'agent."""
    iteration: int
    thought: Optional[str]
    tool_name: Optional[str]
    tool_input: Optional[dict]
    tool_output: Optional[str]
    is_final: bool = False


@dataclass
class AgentResult:
    """Résultat complet d'un run d'agent."""
    answer: str
    steps: list[AgentStep] = field(default_factory=list)
    total_iterations: int = 0
    error: Optional[str] = None
    # Consommation cumulée de tokens sur tous les appels SLM du run.
    input_tokens: int = 0
    output_tokens: int = 0

    @property
    def total_tokens(self) -> int:
        return self.input_tokens + self.output_tokens


class BaseAgent:
    """
    Agent ReAct générique.
    """

    system_prompt: str = ""
    tools: list[dict] = []
    tool_registry: dict = {}
    max_iterations: int = 15
    conclusion_prompt: str = ""
    # Nombre de fois où on tolère de "recadrer" le SLM vers un outil inutilisé
    # avant de forcer la conclusion. Empêche la boucle infinie quand le modèle
    # rappelle indéfiniment le même outil sans jamais conclure.
    max_loop_nudges: int = 2

    def __init__(self, model: str = "qwen2.5:7b"):
        self.client = OllamaClient(model=model)
        self._cache = CacheManager(ttl_seconds=300)

    def run(self, task: str) -> AgentResult:
        messages = [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": task},
        ]
        steps = []

        # --- Anti-boucle : compter les appels par (tool, args) ---
        tool_call_history = {}  # clé = (name, args_json) → count
        tools_already_used = set()  # noms de tools déjà appelés
        loop_nudges = 0  # nb de recadrages déjà tentés avant conclusion forcée

        # --- Tokens : cumul sur tous les appels au SLM de ce run ---
        input_tokens = 0
        output_tokens = 0

        for i in range(self.max_iterations):
            logger.info(f"[Agent] Iteration {i+1}/{self.max_iterations}")

            # 1. Appel au SLM
            try:
                response: AgentResponse = self.client.chat(
                    messages=messages,
                    tools=self.tools,
                )
            except Exception as e:
                logger.error(f"[Agent] Erreur Ollama: {e}")
                return AgentResult(
                    answer="", steps=steps,
                    total_iterations=i + 1,
                    error=str(e),
                    input_tokens=input_tokens,
                    output_tokens=output_tokens,
                )

            input_tokens += response.prompt_tokens
            output_tokens += response.completion_tokens

            # 2. Réponse finale
            # 2. Si pas de tool calls → potentiellement réponse finale
            if response.done or not response.tool_calls:
                content = response.content or ""
                # Vérifier si c'est une VRAIE réponse finale (contient du JSON)
                # ou une fausse (le SLM "annonce" une action sans la faire)
                has_json = "{" in content and "}" in content

                tools_used_count = len(tools_already_used)
                if (not has_json and self.conclusion_prompt
                        and tools_used_count >= 3 and i < self.max_iterations - 1):                    # Fausse réponse finale → pousser vers la conclusion JSON
                    logger.warning("[Agent] Réponse sans JSON, on force la conclusion")
                    tool_results_summary = ""
                    for step in steps:
                        if step.tool_name and step.tool_output:
                            tool_results_summary += f"\n{step.tool_name}: {step.tool_output[:600]}\n"

                    messages = [
                        {"role": "system", "content": self.system_prompt},
                        {"role": "user", "content": (
                            f"{self.conclusion_prompt}\n"
                            f"Résultats des outils :\n{tool_results_summary}"
                        )},
                    ]
                    continue

                steps.append(AgentStep(
                    iteration=i + 1,
                    thought=content,
                    tool_name=None, tool_input=None, tool_output=None,
                    is_final=True,
                ))
                return self.finalize(AgentResult(
                    answer=content,
                    steps=steps,
                    total_iterations=i + 1,
                    input_tokens=input_tokens,
                    output_tokens=output_tokens,
                ))

            # 3. Vérifier les boucles AVANT d'exécuter les tools
            repeated_calls = []
            for tc in response.tool_calls:
                call_key = (tc.name, json.dumps(tc.arguments, sort_keys=True))
                tool_call_history[call_key] = tool_call_history.get(call_key, 0) + 1
                if tool_call_history[call_key] >= 2:
                    repeated_calls.append(tc)
                    logger.warning(
                        f"[Agent] Tool '{tc.name}' déjà appelé avec les mêmes args "
                        f"({tool_call_history[call_key]}x)"
                    )

            if repeated_calls:
                loop_nudges += 1
                all_tool_names = {t["function"]["name"] for t in self.tools}
                unused_tools = all_tool_names - tools_already_used

                # IMPORTANT : on garde un transcript cohérent. Avant, l'appel répété
                # du SLM n'était jamais "répondu" → le modèle le ré-émettait à
                # l'infini. Ici on répond à chaque appel avec le résultat DÉJÀ calculé
                # (servi par le cache, aucune requête SQL refaite). Le modèle voit donc
                # qu'il a la donnée et n'a plus de raison de boucler.
                assistant_msg = {
                    "role": "assistant",
                    "content": response.content or "",
                    "tool_calls": [
                        {"function": {"name": tc.name, "arguments": tc.arguments}}
                        for tc in response.tool_calls
                    ],
                }
                messages.append(assistant_msg)
                for tc in response.tool_calls:
                    messages.append({
                        "role": "tool",
                        "content": self._execute_tool(tc.name, tc.arguments),
                    })

                # Tant qu'il reste des outils utiles ET qu'on n'a pas épuisé le quota
                # de recadrage → on oriente le modèle vers un outil inexploité.
                if unused_tools and loop_nudges <= self.max_loop_nudges:
                    nudge = (
                        "Tu as déjà ces résultats ci-dessus, ne rappelle pas les mêmes "
                        "outils. Outils encore disponibles : "
                        f"{', '.join(sorted(unused_tools))}. Utilise-en un SEULEMENT "
                        "s'il apporte une information vraiment nouvelle ; sinon donne "
                        "directement ta réponse finale en JSON."
                    )
                    messages.append({"role": "user", "content": nudge})
                    continue

                # Sinon → on force la conclusion (sans aucun outil).
                tool_results_summary = ""
                for step in steps:
                    if step.tool_name and step.tool_output:
                        tool_results_summary += (
                            f"\n{step.tool_name}: {step.tool_output[:600]}\n"
                        )
                conclusion = self.conclusion_prompt or (
                    "Tu as assez d'informations. Donne maintenant ta réponse FINALE en "
                    "JSON UNIQUEMENT, sans appeler aucun outil."
                )
                messages = [
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": (
                        f"{conclusion}\nRésultats des outils :\n{tool_results_summary}"
                    )},
                ]
                logger.info("[Agent] Conclusion forcée après détection de boucle")
                continue

            # 4. Exécuter les tool calls
            assistant_msg = {"role": "assistant", "content": response.content or ""}
            if response.tool_calls:
                assistant_msg["tool_calls"] = [
                    {
                        "function": {
                            "name": tc.name,
                            "arguments": tc.arguments,
                        }
                    }
                    for tc in response.tool_calls
                ]
            messages.append(assistant_msg)

            for tc in response.tool_calls:
                logger.info(f"[Agent] Tool call: {tc.name}({json.dumps(tc.arguments, ensure_ascii=False)[:200]})")

                tool_output = self._execute_tool(tc.name, tc.arguments)
                tools_already_used.add(tc.name)

                steps.append(AgentStep(
                    iteration=i + 1,
                    thought=response.content,
                    tool_name=tc.name,
                    tool_input=tc.arguments,
                    tool_output=tool_output,
                ))

                messages.append({
                    "role": "tool",
                    "content": tool_output,
                })

        # Max iterations
        return self.finalize(AgentResult(
            answer="L'agent a atteint le nombre maximum d'itérations sans conclure.",
            steps=steps,
            total_iterations=self.max_iterations,
            error="max_iterations_reached",
            input_tokens=input_tokens,
            output_tokens=output_tokens,
        ))

    def finalize(self, result: AgentResult) -> AgentResult:
        """
        Hook de post-traitement appelé avant de retourner le résultat.

        Par défaut, ne fait rien (identité). Les agents peuvent le surcharger
        pour garantir un contrat de sortie (JSON complet, champs obligatoires)
        à partir des données réellement collectées dans result.steps, même si
        le SLM a produit une réponse incomplète ou paraphrasée.
        """
        return result

    def _execute_tool(self, tool_name: str, arguments: dict) -> str:
        func = self.tool_registry.get(tool_name)

        if func is None:
            return json.dumps({"error": f"Tool inconnu : {tool_name}"})

        arguments = arguments or {}

        # --- Robustesse : le SLM 7B réinjecte parfois la sortie d'un tool
        # comme arguments du suivant (ex: get_job_history(**{"AA.BILL.DETAILS": ...})).
        # On filtre les kwargs inconnus au lieu de planter avec un TypeError. ---
        sig = inspect.signature(func)
        accepts_var_kw = any(
            p.kind == inspect.Parameter.VAR_KEYWORD
            for p in sig.parameters.values()
        )
        if not accepts_var_kw:
            valid = set(sig.parameters.keys())
            dropped = [k for k in arguments if k not in valid]
            if dropped:
                logger.warning(
                    f"[Agent] Args inconnus ignorés pour {tool_name}: {dropped}"
                )
            arguments = {k: v for k, v in arguments.items() if k in valid}

        # --- Cache : évite de ré-exécuter la même requête SQL quand le SLM
        # rappelle le même tool avec les mêmes args (fréquent). ---
        cache_key = self._cache.make_key(tool_name, arguments)
        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached

        try:
            result = func(**arguments)
            result_json = json.dumps(result, default=str, ensure_ascii=False)
            if len(result_json) > 3000:
                result_json = result_json[:3000] + '... [tronqué]'
            self._cache.set(cache_key, result_json)
            return result_json
        except Exception as e:
            logger.error(f"[Agent] Erreur tool {tool_name}: {e}")
            return json.dumps({"error": str(e)})

    def get_trace_markdown(self, result: AgentResult) -> str:
        md = "## Trace de raisonnement de l'agent\n\n"
        for step in result.steps:
            md += f"### Itération {step.iteration}\n"
            if step.thought:
                md += f"**Pensée** : {step.thought}\n\n"
            if step.tool_name:
                md += f"**Action** : `{step.tool_name}({json.dumps(step.tool_input, ensure_ascii=False)[:150]})`\n\n"
                md += f"**Observation** : ```\n{step.tool_output[:500]}\n```\n\n"
            if step.is_final:
                md += "**→ Réponse finale**\n\n"
            md += "---\n\n"
        return md