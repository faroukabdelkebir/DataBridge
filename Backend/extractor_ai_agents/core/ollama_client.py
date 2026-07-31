"""
Ollama API client avec support natif du tool calling.
Communique avec Ollama sur localhost:11434.
"""

import json
import requests
from typing import Optional
from dataclasses import dataclass, field

OLLAMA_BASE_URL = "http://localhost:11434"
DEFAULT_MODEL = "qwen2.5:7b"


@dataclass
class ToolCall:
    """Représente un appel de tool demandé par le SLM."""
    name: str
    arguments: dict


@dataclass
class AgentResponse:
    """Réponse du SLM — soit du texte, soit des appels de tools."""
    content: Optional[str] = None
    tool_calls: list[ToolCall] = field(default_factory=list)
    done: bool = False  # True si le SLM a terminé (pas de tool calls)
    # Consommation de tokens rapportée par Ollama pour CET appel.
    prompt_tokens: int = 0      # tokens en entrée (prompt_eval_count)
    completion_tokens: int = 0  # tokens générés (eval_count)


class OllamaClient:
    """
    Client pour l'API Ollama /api/chat avec tool calling.
    
    Ollama supporte le tool calling nativement depuis v0.4+.
    Le format des tools suit la spec OpenAI-compatible :
    {
        "type": "function",
        "function": {
            "name": "...",
            "description": "...",
            "parameters": { JSON Schema }
        }
    }
    """

    def __init__(self, base_url: str = OLLAMA_BASE_URL, model: str = DEFAULT_MODEL):
        self.base_url = base_url
        self.model = model

    def chat(
        self,
        messages: list[dict],
        tools: Optional[list[dict]] = None,
        temperature: float = 0.1,
    ) -> AgentResponse:
        """
        Envoie une conversation à Ollama et retourne la réponse.
        
        Args:
            messages: historique [{"role": "system"|"user"|"assistant"|"tool", "content": "..."}]
            tools: liste de définitions de tools (format OpenAI-compatible)
            temperature: 0.0-1.0, basse = plus déterministe
        
        Returns:
            AgentResponse avec soit du texte (done=True) soit des tool_calls (done=False)
        """
        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            "options": {
                "temperature": temperature,
                "num_ctx": 4096,
                "num_predict": 1024,
            },
        }

        if tools:
            payload["tools"] = tools

        try:
            resp = requests.post(
                f"{self.base_url}/api/chat",
                json=payload,
                timeout=600,
            )
            resp.raise_for_status()
            data = resp.json()
        except requests.exceptions.ConnectionError:
            raise ConnectionError(
                "Impossible de se connecter à Ollama. "
                "Vérifiez que le serveur tourne : ollama serve"
            )
        except requests.exceptions.Timeout:
            raise TimeoutError("Ollama a mis trop de temps à répondre (>120s)")

        message = data.get("message", {})

        # Tokens consommés par cet appel (rapportés par Ollama).
        prompt_tokens = data.get("prompt_eval_count", 0) or 0
        completion_tokens = data.get("eval_count", 0) or 0

        # Extraire les tool calls si présents
        raw_tool_calls = message.get("tool_calls", [])

        if raw_tool_calls:
            tool_calls = []
            for tc in raw_tool_calls:
                func = tc.get("function", {})
                tool_calls.append(ToolCall(
                    name=func.get("name", ""),
                    arguments=func.get("arguments", {}),
                ))
            return AgentResponse(
                content=message.get("content"),
                tool_calls=tool_calls,
                done=False,
                prompt_tokens=prompt_tokens,
                completion_tokens=completion_tokens,
            )

        # Pas de tool calls → réponse finale
        return AgentResponse(
            content=message.get("content", ""),
            tool_calls=[],
            done=True,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
        )

    def is_available(self) -> bool:
        """Vérifie si Ollama est accessible."""
        try:
            resp = requests.get(f"{self.base_url}/api/tags", timeout=5)
            return resp.status_code == 200
        except Exception:
            return False

    def list_models(self) -> list[str]:
        """Liste les modèles installés localement."""
        try:
            resp = requests.get(f"{self.base_url}/api/tags", timeout=5)
            data = resp.json()
            return [m["name"] for m in data.get("models", [])]
        except Exception:
            return []
