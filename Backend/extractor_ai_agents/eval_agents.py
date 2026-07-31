"""
============================================================================
Framework d'évaluation des agents — Métriques, benchmarks, optimisations
============================================================================

Ce module mesure la QUALITÉ et la PERFORMANCE de chaque agent :
- Taux de réussite (JSON valide, tools appelés correctement)
- Précision des résultats (comparaison avec la vérité terrain)
- Temps de réponse et nombre d'itérations
- Consistance entre les runs (même input → même output ?)

Usage :
    python eval_agents.py m1          → évalue M1 sur CUSTOMER
    python eval_agents.py m2          → évalue M2 (prédictions vs réalité)
    python eval_agents.py m3          → évalue M3 (recommandations vs historique)
    python eval_agents.py all         → benchmark complet
    python eval_agents.py all --runs 5  → 5 runs pour mesurer la consistance
"""

import os, sys, json, time
from datetime import datetime
from dataclasses import dataclass, field

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "settings")
import django
django.setup()

from core.base_agent import AgentResult


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  MÉTRIQUES COMMUNES                                              ║
# ╚═══════════════════════════════════════════════════════════════════╝

@dataclass
class EvalResult:
    """Résultat d'une évaluation."""
    agent: str
    run_id: int
    timestamp: str = ""

    # Performance
    duration_sec: float = 0
    iterations: int = 0
    tool_calls: int = 0

    # Consommation tokens (rapportée par Ollama)
    input_tokens: int = 0
    output_tokens: int = 0

    @property
    def total_tokens(self) -> int:
        return self.input_tokens + self.output_tokens

    # Fiabilité
    json_valid: bool = False           # Le SLM a-t-il produit du JSON valide ?
    json_parse_error: str = ""
    tools_called_correctly: bool = False  # Tous les tools ont-ils été appelés ?
    expected_tools: list = field(default_factory=list)
    actual_tools: list = field(default_factory=list)
    missing_tools: list = field(default_factory=list)

    # Précision (spécifique à chaque agent)
    accuracy_metrics: dict = field(default_factory=dict)

    # Résultat brut
    raw_answer: str = ""
    parsed_answer: dict = field(default_factory=dict)


def parse_agent_json(answer: str) -> tuple[bool, dict, str]:
    """Parse le JSON de la réponse avec réparation automatique."""
    from core.optimizations import JsonRepairer
    return JsonRepairer.repair(answer)


def extract_tools_called(result: AgentResult) -> list[str]:
    """Extrait la liste des tools appelés depuis la trace."""
    return [s.tool_name for s in result.steps if s.tool_name]


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  ÉVALUATION M1 — Anomalies + Qualité                            ║
# ╚═══════════════════════════════════════════════════════════════════╝

# Vérité terrain calculée manuellement depuis les données réelles
M1_GROUND_TRUTH = {
    "CUSTOMER": {
        "expected_dimensions": {
            "D1": {"min": 18, "max": 26},     # complétude ~22.3
            "D2": {"min": 0,  "max": 5},      # unicité ~1.8
            "D3": {"min": 5,  "max": 12},     # couverture ~8.2
            "D4": {"min": 90, "max": 100},    # conformité ~100
            "D5": {"min": 0,  "max": 100},    # fraîcheur variable → plage large
            "D6": {"min": 90, "max": 100},    # consistance ~100 (pas de 'ULL' actuel)
        },
        "expected_global_score": {"min": 38, "max": 46},   # ~42.0
        "expected_grade": ["F"],
        "critical_dims_expected": ["D1", "D2"],            # capped_to_F_by attendu
        "expected_tools": [                                # les 6 mesures (trace SLM)
            "measure_completeness", "measure_uniqueness",
            "measure_schema_coverage", "measure_conformity",
            "measure_freshness", "measure_consistency",
        ],
    }
}

def eval_m1(table_name: str = "CUSTOMER") -> EvalResult:
    """Évalue M1 via audit_table() — le même chemin que les endpoints."""
    from agent_anomaly_quality.agents.anomaly_quality_agent import AnomalyQualityAgent

    gt = M1_GROUND_TRUTH.get(table_name, {})
    eval_res = EvalResult(agent="M1", run_id=0, timestamp=datetime.now().isoformat())

    start = time.time()
    report = AnomalyQualityAgent().audit_table(table_name)   # ← chemin déterministe
    eval_res.duration_sec = round(time.time() - start, 2)
    eval_res.iterations = report.get("total_iterations", 0)
    eval_res.input_tokens = report.get("input_tokens", 0)
    eval_res.output_tokens = report.get("output_tokens", 0)
    eval_res.parsed_answer = report
    eval_res.raw_answer = json.dumps(report, ensure_ascii=False)
    eval_res.json_valid = isinstance(report, dict) and "global_score" in report
    eval_res.json_parse_error = "" if eval_res.json_valid else "global_score absent du rapport"

    # Tools réellement déclenchés par le SLM (qualité de la trace, pas du score)
    tools_called = [s["tool"] for s in report.get("reasoning_trace", []) if s.get("tool")]
    eval_res.actual_tools = tools_called
    eval_res.tool_calls = len(tools_called)
    eval_res.expected_tools = gt.get("expected_tools", [])
    eval_res.missing_tools = [t for t in eval_res.expected_tools if t not in tools_called]
    eval_res.tools_called_correctly = len(eval_res.missing_tools) == 0

    acc = {}

    rng = gt.get("expected_global_score", {})
    gs = report.get("global_score")
    acc["global_score"] = gs
    acc["global_score_in_range"] = bool(rng) and gs is not None and rng["min"] <= gs <= rng["max"]

    grade = report.get("grade")
    acc["grade"] = grade
    acc["grade_correct"] = grade in gt.get("expected_grade", [])

    dims = report.get("dimensions", {})
    dim_checks = {}
    for dk, r in gt.get("expected_dimensions", {}).items():
        v = dims.get(dk)
        s = v.get("score") if isinstance(v, dict) else v
        dim_checks[dk] = {"score": s, "expected": f"{r['min']}-{r['max']}",
                          "in_range": s is not None and r["min"] <= s <= r["max"]}
    acc["dimensions"] = dim_checks
    ok = sum(1 for d in dim_checks.values() if d["in_range"])
    acc["dimensions_accuracy_pct"] = round(ok / len(dim_checks) * 100, 1) if dim_checks else 0

    capped = sorted(report.get("capped_to_F_by", []))
    acc["capped_to_F_by"] = capped
    acc["capped_correct"] = capped == sorted(gt.get("critical_dims_expected", []))

    checks = [
        acc["global_score_in_range"],
        acc["grade_correct"],
        acc["dimensions_accuracy_pct"] >= 80,
        acc["capped_correct"],
    ]
    acc["overall_accuracy_pct"] = round(sum(checks) / len(checks) * 100, 1)

    eval_res.accuracy_metrics = acc
    return eval_res


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  ÉVALUATION M3 — Scheduling                                     ║
# ╚═══════════════════════════════════════════════════════════════════╝

M3_GROUND_TRUTH = {
    "avoid_hours": [10, 11, 12],      # Heures à éviter (COB en cours)
    "good_hours": [19, 20, 21, 14],    # Heures avec bon taux de succès
    "expected_tools": [
        "get_batch_config", "get_job_history",
        "analyze_time_patterns",
    ],
    "must_mention": ["COB", "backoff"],  # Concepts clés
    "current_success_rate": 36,          # Taux actuel
    "min_projected_improvement": 60,     # Le taux projeté doit être > 60%
}


def eval_m3() -> EvalResult:
    """Évalue l'agent M3 — vérifie que les recommandations sont cohérentes avec l'historique."""
    from agent_scheduler.agents.scheduler_agent import SchedulerAgent

    gt = M3_GROUND_TRUTH
    eval_res = EvalResult(agent="M3", run_id=0, timestamp=datetime.now().isoformat())

    start_time = time.time()
    agent = SchedulerAgent()
    result = agent.run(
        "Analyse l'historique des jobs et recommande un planning optimal. "
        "Inclus des règles adaptatives."
    )
    eval_res.duration_sec = round(time.time() - start_time, 2)
    eval_res.iterations = result.total_iterations
    eval_res.input_tokens = result.input_tokens
    eval_res.output_tokens = result.output_tokens
    eval_res.raw_answer = result.answer

    tools_called = extract_tools_called(result)
    eval_res.actual_tools = tools_called
    eval_res.tool_calls = len(tools_called)
    eval_res.expected_tools = gt["expected_tools"]
    eval_res.missing_tools = [t for t in eval_res.expected_tools if t not in tools_called]
    eval_res.tools_called_correctly = len(eval_res.missing_tools) == 0

    valid, parsed, error = parse_agent_json(result.answer)
    eval_res.json_valid = valid
    eval_res.json_parse_error = error
    eval_res.parsed_answer = parsed

    accuracy = {}
    answer_lower = result.answer.lower()

    # Vérifier les concepts clés mentionnés
    for concept in gt["must_mention"]:
        accuracy[f"mentions_{concept}"] = concept.lower() in answer_lower

    # Vérifier que les heures dangereuses sont identifiées
    avoid_mentioned = any(f"{h}" in answer_lower or f"{h}h" in answer_lower for h in gt["avoid_hours"])
    accuracy["avoid_hours_identified"] = avoid_mentioned

    # Vérifier que les bonnes heures sont recommandées
    good_mentioned = any(f"{h}" in answer_lower for h in gt["good_hours"])
    accuracy["good_hours_recommended"] = good_mentioned

    if valid:
        # Vérifier le schedule recommandé
        schedule = parsed.get("recommended_schedule", {})
        if schedule:
            for batch_name, config in schedule.items():
                time_str = str(config.get("recommended_time", config.get("time", "")))
                try:
                    hour = int(time_str.split(":")[0])
                    accuracy[f"{batch_name}_not_in_danger_zone"] = hour not in gt["avoid_hours"]
                except (ValueError, IndexError):
                    accuracy[f"{batch_name}_not_in_danger_zone"] = None

        # Vérifier l'amélioration projetée
        improvement = parsed.get("estimated_improvement", {})
        projected = improvement.get("projected_success_rate", 0)
        accuracy["projected_rate"] = projected
        accuracy["projected_improvement_realistic"] = projected > gt["min_projected_improvement"]

    checks = [
        accuracy.get("mentions_COB", False),
        accuracy.get("avoid_hours_identified", False),
        accuracy.get("good_hours_recommended", False),
        accuracy.get("mentions_backoff", False),
        eval_res.tools_called_correctly,
    ]
    accuracy["overall_accuracy_pct"] = round(sum(checks) / len(checks) * 100, 1)

    eval_res.accuracy_metrics = accuracy
    return eval_res


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  BENCHMARK MULTI-RUNS                                           ║
# ╚═══════════════════════════════════════════════════════════════════╝

def benchmark(agent_name: str, n_runs: int = 3) -> dict:
    """Lance N runs d'un agent et calcule les statistiques de consistance."""
    eval_funcs = {"m1": eval_m1, "m3": eval_m3}
    func = eval_funcs.get(agent_name.lower())
    if not func:
        print(f"Agent inconnu : {agent_name}")
        return {}

    results = []
    for i in range(n_runs):
        print(f"\n{'='*50}")
        print(f"  Run {i+1}/{n_runs} — Agent {agent_name.upper()}")
        print(f"{'='*50}")

        eval_res = func()
        eval_res.run_id = i + 1
        results.append(eval_res)
        _print_eval_summary(eval_res)

    # Statistiques agrégées
    print(f"\n{'='*60}")
    print(f"  RÉSUMÉ — {n_runs} runs de {agent_name.upper()}")
    print(f"{'='*60}")

    json_success_rate = sum(1 for r in results if r.json_valid) / n_runs * 100
    tools_success_rate = sum(1 for r in results if r.tools_called_correctly) / n_runs * 100
    avg_duration = sum(r.duration_sec for r in results) / n_runs
    avg_iterations = sum(r.iterations for r in results) / n_runs
    avg_accuracy = sum(r.accuracy_metrics.get("overall_accuracy_pct", 0) for r in results) / n_runs
    avg_input_tokens = sum(r.input_tokens for r in results) / n_runs
    avg_output_tokens = sum(r.output_tokens for r in results) / n_runs

    # Consistance du score (écart-type si M1)
    if agent_name.lower() == "m1":
        scores = [r.accuracy_metrics.get("global_score") for r in results if r.accuracy_metrics.get("global_score")]
        if len(scores) > 1:
            mean_score = sum(scores) / len(scores)
            std_score = (sum((s - mean_score)**2 for s in scores) / len(scores)) ** 0.5
        else:
            mean_score = scores[0] if scores else 0
            std_score = 0
    else:
        mean_score = std_score = None

    summary = {
        "agent": agent_name.upper(),
        "total_runs": n_runs,
        "json_success_rate_pct": round(json_success_rate, 1),
        "tools_success_rate_pct": round(tools_success_rate, 1),
        "avg_duration_sec": round(avg_duration, 1),
        "avg_iterations": round(avg_iterations, 1),
        "avg_accuracy_pct": round(avg_accuracy, 1),
        "avg_input_tokens": round(avg_input_tokens, 1),
        "avg_output_tokens": round(avg_output_tokens, 1),
        "avg_total_tokens": round(avg_input_tokens + avg_output_tokens, 1),
        "score_consistency": {
            "mean": round(mean_score, 1) if mean_score else None,
            "std": round(std_score, 1) if std_score is not None else None,
        } if mean_score is not None else None,
    }

    print(f"\n  JSON valide       : {json_success_rate:.0f}%")
    print(f"  Tools corrects    : {tools_success_rate:.0f}%")
    print(f"  Durée moyenne     : {avg_duration:.1f}s")
    print(f"  Itérations moy.   : {avg_iterations:.1f}")
    print(f"  Tokens in moy.    : {avg_input_tokens:.0f}")
    print(f"  Tokens out moy.   : {avg_output_tokens:.0f}")
    print(f"  Tokens total moy. : {avg_input_tokens + avg_output_tokens:.0f}")
    print(f"  Accuracy moyenne  : {avg_accuracy:.1f}%")
    if mean_score is not None:
        print(f"  Score M1 moyen    : {mean_score:.1f} ± {std_score:.1f}")

    return summary


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  HELPERS                                                         ║
# ╚═══════════════════════════════════════════════════════════════════╝

def _extract_nested(d: dict, keys: list):
    """Extrait une valeur d'un dict imbriqué."""
    current = d
    for k in keys:
        if isinstance(current, dict) and k in current:
            current = current[k]
        else:
            return None
    return current


def _print_eval_summary(eval_res: EvalResult):
    """Affiche un résumé d'une évaluation."""
    print(f"\n  Agent            : {eval_res.agent}")
    print(f"  Durée            : {eval_res.duration_sec}s")
    print(f"  Itérations       : {eval_res.iterations}")
    print(f"  Tool calls       : {eval_res.tool_calls}")
    print(f"  Tokens (in/out)  : {eval_res.input_tokens} / {eval_res.output_tokens} "
          f"(total {eval_res.total_tokens})")
    print(f"  JSON valide      : {'✅' if eval_res.json_valid else '❌'} {eval_res.json_parse_error}")
    print(f"  Tools corrects   : {'✅' if eval_res.tools_called_correctly else '❌'}")
    if eval_res.missing_tools:
        print(f"    Manquants      : {eval_res.missing_tools}")
    print(f"  Accuracy         : {eval_res.accuracy_metrics.get('overall_accuracy_pct', '?')}%")

    # Détail des métriques
    for k, v in eval_res.accuracy_metrics.items():
        if k != "overall_accuracy_pct" and not isinstance(v, (dict, list)):
            icon = "✅" if v is True else "❌" if v is False else "  "
            print(f"    {icon} {k}: {v}")


# ╔═══════════════════════════════════════════════════════════════════╗
# ║  MAIN                                                            ║
# ╚═══════════════════════════════════════════════════════════════════╝

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return

    agent = sys.argv[1].lower()
    n_runs = 1

    # Check for --runs flag
    if "--runs" in sys.argv:
        idx = sys.argv.index("--runs")
        if idx + 1 < len(sys.argv):
            n_runs = int(sys.argv[idx + 1])

    if agent == "all":
        for a in ["m1", "m3"]:
            benchmark(a, n_runs)
    elif agent in ["m1", "m3"]:
        if n_runs > 1:
            benchmark(agent, n_runs)
        else:
            funcs = {"m1": eval_m1, "m3": eval_m3}
            result = funcs[agent]()
            _print_eval_summary(result)
    else:
        print(f"Agent inconnu : {agent}")
        print("Usage : python eval_agents.py [m1|m3|all] [--runs N]")


if __name__ == "__main__":
    main()
