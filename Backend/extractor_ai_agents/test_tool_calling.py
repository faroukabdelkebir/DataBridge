# test_tool_calling.py
import requests, json

# 1 seul tool, 1 seule instruction, le plus simple possible
response = requests.post("http://localhost:11434/api/chat", json={
    "model": "qwen2.5:7b",
    "stream": False,
    "messages": [
        {"role": "user", "content": "Quelle heure est-il ?"}
    ],
    "tools": [{
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "Retourne l'heure actuelle",
            "parameters": {
                "type": "object",
                "properties": {
                    "timezone": {"type": "string", "description": "Fuseau horaire"}
                },
                "required": []
            }
        }
    }]
}, timeout=300)

data = response.json()
msg = data.get("message", {})

print("=== RÉPONSE BRUTE ===")
print(json.dumps(msg, indent=2, ensure_ascii=False))
print()

if msg.get("tool_calls"):
    print("✅ TOOL CALLING FONCTIONNE")
    for tc in msg["tool_calls"]:
        print(f"   Tool: {tc['function']['name']}")
        print(f"   Args: {tc['function']['arguments']}")
else:
    print("❌ PAS DE TOOL CALL — le modèle a juste répondu en texte :")
    print(f"   {msg.get('content', '')[:200]}")
    print()
    print("Solutions :")
    print("  1. Essayer qwen2.5:7b  ")
    print("  3. Essayer llama3.1:8b")