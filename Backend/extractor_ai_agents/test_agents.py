#!/usr/bin/env python3
"""
============================================
AI Agents - Test rapide en CLI
============================================

Usage :
    python test_agents.py status          -> verifie Ollama + SQL Server
    python test_agents.py m1 CUSTOMER     -> audit qualite + anomalies
    python test_agents.py m2 CUSTOMER     -> prediction champs manquants
    python test_agents.py m3              -> recommandation scheduling
"""

import os, sys, json

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "settings")
import django
django.setup()


def check_status():
    from django.conf import settings
    from core.ollama_client import OllamaClient

    print("=" * 50)
    print("  Verification de l'environnement")
    print("=" * 50)

    # Ollama
    client = OllamaClient()
    if client.is_available():
        models = client.list_models()
        print(f"\n[OK] Ollama disponible ({client.base_url})")
        print(f"     Modeles : {models}")
    else:
        print(f"\n[KO] Ollama non disponible ({client.base_url})")
        print("     Lancez : ollama serve")

    # SQL Server
    try:
        import pyodbc
        conn_str = (
            f"DRIVER={{ODBC Driver 17 for SQL Server}};"
            f"SERVER={settings.BI_HOST},{settings.BI_PORT};"
            f"DATABASE={settings.BI_DB};"
            f"UID={settings.BI_USER};"
            f"PWD={settings.BI_PASS};"
        )
        conn = pyodbc.connect(conn_str, timeout=5)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES")
        nb = cursor.fetchone()[0]
        conn.close()
        print(f"\n[OK] SQL Server accessible ({settings.BI_HOST},{settings.BI_PORT})")
        print(f"     Base : {settings.BI_DB} — {nb} tables")
    except Exception as e:
        print(f"\n[KO] SQL Server non accessible : {e}")
        print(f"     BI_HOST={settings.BI_HOST}, PORT={settings.BI_PORT}, DB={settings.BI_DB}")

    print()


def run_m1(table_name="CUSTOMER"):
    from agent_anomaly_quality.agents.anomaly_quality_agent import AnomalyQualityAgent
    from agent_anomaly_quality.tools.anomaly_quality_tools import compute_quality_score

    print(f"\n[M1] Audit de {table_name} (qualite + anomalies)...\n")
    agent = AnomalyQualityAgent()
    result = agent.run(
        f"Fais un audit complet de la table {table_name}. "
        f"Phase 1 : mesure les 6 dimensions de qualite. "
        f"Phase 2 : investigue les dimensions faibles (< 50)."
    )
    authoritative = compute_quality_score(table_name)
    result.answer = json.dumps(authoritative, ensure_ascii=False, indent=2)
    print_trace(result)
    return result


def run_m3():
    from agent_scheduler.agents.scheduler_agent import SchedulerAgent

    print("\n[M3] Ordonnancement intelligent...\n")
    agent = SchedulerAgent()
    result = agent.run(
        "Analyse l'historique des jobs et recommande un planning optimal "
        "pour les 3 batchs. Inclus des regles adaptatives."
    )
    print_trace(result)
    return result


def print_trace(result):
    print("=" * 60)
    print("  TRACE DE RAISONNEMENT")
    print("=" * 60)

    for step in result.steps:
        if step.is_final:
            print(f"\n{'='*60}")
            print("  REPONSE FINALE")
            print(f"{'='*60}")
            print(result.answer[:2000])
            if len(result.answer) > 2000:
                print(f"\n... ({len(result.answer)} caracteres au total)")
        else:
            print(f"\n--- Iteration {step.iteration} ---")
            if step.thought:
                print(f"[Thought] {step.thought[:200]}")
            if step.tool_name:
                args = json.dumps(step.tool_input, ensure_ascii=False)[:120]
                print(f"[Tool]    {step.tool_name}({args})")
                if step.tool_output:
                    print(f"[Result]  {step.tool_output[:200]}...")

    print(f"\n[Stats] Iterations : {result.total_iterations}")
    if result.error:
        print(f"[Erreur] {result.error}")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return

    cmd = sys.argv[1].lower()
    if cmd == "status":
        check_status()
    elif cmd == "m1":
        table = sys.argv[2] if len(sys.argv) > 2 else "CUSTOMER"
        run_m1(table)
    elif cmd == "m3":
        run_m3()
    else:
        print(f"Commande inconnue : {cmd}")
        print(__doc__)


if __name__ == "__main__":
    main()
