"""
Tools de scheduling pour l'agent M3.
Analyse l'historique des jobs, patterns temporels, COB status,
et configurations de batch pour recommander les créneaux optimaux.
"""

import os

import pyodbc
from datetime import datetime, timedelta
from collections import defaultdict
from typing import Optional
from django.conf import settings


def get_connection():
    return pyodbc.connect(
        f"DRIVER={{{os.getenv('ODBC_DRIVER', 'ODBC Driver 18 for SQL Server')}}};"
        f"TrustServerCertificate=yes;"
        f"SERVER={settings.BI_HOST},{settings.BI_PORT};"
        f"DATABASE={settings.BI_DB};"
        f"UID={settings.BI_USER};"
        f"PWD={settings.BI_PASS};"
    )


# -------------------------------------------------------------------
# Tool 1 : get_job_history
# -------------------------------------------------------------------

def get_job_history(last_n: int = 50, batch_id: Optional[str] = None) -> dict:
    """
    Récupère l'historique des N dernières exécutions de jobs.
    Inclut : ID, status, heure de lancement, durée, nombre de lignes lues.
    """
    conn = get_connection()
    try:
        cursor = conn.cursor()
        query = """
            SELECT TOP (?)
                je.JOB_EXECUTION_ID,
                je.STATUS,
                je.EXIT_CODE,
                je.CREATE_TIME,
                je.START_TIME,
                je.END_TIME,
                je.EXIT_MESSAGE,
                (SELECT SUM(CAST(se.READ_COUNT AS BIGINT))
                 FROM BATCH_STEP_EXECUTION se
                 WHERE se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID) as total_read,
                (SELECT COUNT(*)
                 FROM BATCH_STEP_EXECUTION se
                 WHERE se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID) as step_count
            FROM BATCH_JOB_EXECUTION je
            ORDER BY je.START_TIME DESC
        """
        cursor.execute(query, last_n)

        jobs = []
        for r in cursor.fetchall():
            start = r[4]
            end = r[5]
            duration = None
            if start and end:
                try:
                    s = datetime.fromisoformat(str(start))
                    e = datetime.fromisoformat(str(end))
                    duration = round((e - s).total_seconds(), 2)
                except:
                    pass

            jobs.append({
                "job_id": r[0],
                "status": r[1],
                "exit_code": r[2],
                "start_time": str(r[4]) if r[4] else None,
                "end_time": str(r[5]) if r[5] else None,
                "duration_sec": duration,
                "exit_message": (str(r[6])[:200] if r[6] else ""),
                "total_rows_read": r[7] or 0,
                "hour": datetime.fromisoformat(str(r[4])).hour if r[4] else None,
            })

        completed = [j for j in jobs if j["status"] == "COMPLETED"]
        failed = [j for j in jobs if j["status"] == "FAILED"]

        # Résumé par heure (le SLM reçoit l'insight, pas les données brutes)
        hour_summary = defaultdict(lambda: {"ok": 0, "fail": 0})
        for j in jobs:
            if j["hour"] is not None:
                if j["status"] == "COMPLETED":
                    hour_summary[j["hour"]]["ok"] += 1
                else:
                    hour_summary[j["hour"]]["fail"] += 1

        # Garder quelques exemples de jobs récents pour l'analyse détaillée
        sample_failed = [j for j in jobs if j["status"] == "FAILED"][:3]
        sample_ok = [j for j in jobs if j["status"] == "COMPLETED"][:3]

        return {
            "total_jobs": len(jobs),
            "completed": len(completed),
            "failed": len(failed),
            "success_rate_pct": round(len(completed) / len(jobs) * 100, 1) if jobs else 0,
            "avg_duration_completed": round(
                sum(j["duration_sec"] for j in completed if j["duration_sec"]) /
                len(completed), 2
            ) if completed else 0,
            "by_hour": {f"{h}h": d for h, d in sorted(hour_summary.items())},
            "sample_failed": sample_failed,
            "sample_completed": sample_ok,
        }
    finally:
        conn.close()


GET_JOB_HISTORY_TOOL = {
    "type": "function",
    "function": {
        "name": "get_job_history",
        "description": (
            "Récupère l'historique des exécutions de jobs Spring Batch. "
            "Retourne pour chaque job : status (COMPLETED/FAILED), heure de lancement, "
            "durée, lignes lues, nombre de steps. Inclut aussi le taux de succès global."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "last_n": {
                    "type": "integer",
                    "description": "Nombre de jobs à récupérer (défaut: 50, max: 200)"
                },
            },
            "required": []
        }
    }
}


# -------------------------------------------------------------------
# Tool 2 : analyze_time_patterns
# -------------------------------------------------------------------

def analyze_time_patterns() -> dict:
    """
    Analyse les patterns temporels : taux de succès par heure,
    par jour de la semaine, et par période (matin/après-midi/soir/nuit).
    """
    conn = get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute("""
            SELECT START_TIME, STATUS, END_TIME
            FROM BATCH_JOB_EXECUTION
            WHERE START_TIME IS NOT NULL
            ORDER BY START_TIME
        """)

        hourly = defaultdict(lambda: {"total": 0, "ok": 0, "fail": 0, "durations": []})
        daily = defaultdict(lambda: {"total": 0, "ok": 0, "fail": 0})
        periods = defaultdict(lambda: {"total": 0, "ok": 0, "fail": 0})

        for r in cursor.fetchall():
            try:
                start = datetime.fromisoformat(str(r[0]))
                end = datetime.fromisoformat(str(r[2])) if r[2] else None
                status = r[1]
                hour = start.hour
                day = start.strftime("%A")
                dur = (end - start).total_seconds() if end else 0

                # Period mapping
                if 6 <= hour < 12:
                    period = "morning_6h_12h"
                elif 12 <= hour < 18:
                    period = "afternoon_12h_18h"
                elif 18 <= hour < 23:
                    period = "evening_18h_23h"
                else:
                    period = "night_23h_6h"

                for bucket, key in [(hourly, hour), (daily, day), (periods, period)]:
                    bucket[key]["total"] += 1
                    if status == "COMPLETED":
                        bucket[key]["ok"] += 1
                    else:
                        bucket[key]["fail"] += 1

                if status == "COMPLETED" and dur > 0:
                    hourly[hour]["durations"].append(dur)
            except:
                continue

        # Format results
        hour_results = {}
        for h in sorted(hourly):
            d = hourly[h]
            avg_dur = round(sum(d["durations"]) / len(d["durations"]), 2) if d["durations"] else None
            hour_results[f"{h:02d}h"] = {
                "total": d["total"], "completed": d["ok"], "failed": d["fail"],
                "success_rate_pct": round(d["ok"] / d["total"] * 100, 1) if d["total"] else 0,
                "avg_duration_sec": avg_dur,
            }

        day_results = {}
        for day in daily:
            d = daily[day]
            day_results[day] = {
                "total": d["total"], "completed": d["ok"], "failed": d["fail"],
                "success_rate_pct": round(d["ok"] / d["total"] * 100, 1) if d["total"] else 0,
            }

        period_results = {}
        for p in periods:
            d = periods[p]
            period_results[p] = {
                "total": d["total"], "completed": d["ok"], "failed": d["fail"],
                "success_rate_pct": round(d["ok"] / d["total"] * 100, 1) if d["total"] else 0,
            }

        # Best and worst hours
        best_hour = max(hour_results, key=lambda h: hour_results[h]["success_rate_pct"]) if hour_results else None
        worst_hour = min(hour_results, key=lambda h: hour_results[h]["success_rate_pct"]) if hour_results else None

        return {
            "by_hour": hour_results,
            "by_day": day_results,
            "by_period": period_results,
            "best_hour": best_hour,
            "worst_hour": worst_hour,
            "recommendation": f"Meilleur créneau : {best_hour}. Éviter : {worst_hour}.",
        }
    finally:
        conn.close()


ANALYZE_TIME_PATTERNS_TOOL = {
    "type": "function",
    "function": {
        "name": "analyze_time_patterns",
        "description": (
            "Analyse les patterns temporels des exécutions : taux de succès par heure "
            "(0h-23h), par jour de la semaine, et par période (matin/après-midi/soir/nuit). "
            "Identifie le meilleur et le pire créneau horaire."
        ),
        "parameters": {"type": "object", "properties": {}, "required": []}
    }
}


# -------------------------------------------------------------------
# Tool 3 : get_failure_streaks
# -------------------------------------------------------------------

def get_failure_streaks() -> dict:
    """
    Détecte les séquences d'échecs consécutifs et les patterns de retry.
    """
    conn = get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute("""
            SELECT JOB_EXECUTION_ID, STATUS, START_TIME, END_TIME
            FROM BATCH_JOB_EXECUTION
            WHERE START_TIME IS NOT NULL
            ORDER BY START_TIME ASC
        """)

        jobs = []
        for r in cursor.fetchall():
            try:
                start = datetime.fromisoformat(str(r[2]))
                end = datetime.fromisoformat(str(r[3])) if r[3] else None
                jobs.append({
                    "job_id": r[0], "status": r[1],
                    "start": start, "end": end,
                })
            except:
                continue

        # Detect streaks
        streaks = []
        current_streak = []
        for j in jobs:
            if j["status"] == "FAILED":
                current_streak.append(j)
            else:
                if current_streak:
                    streaks.append({
                        "length": len(current_streak),
                        "start_time": str(current_streak[0]["start"]),
                        "end_time": str(current_streak[-1]["start"]),
                        "duration_minutes": round(
                            (current_streak[-1]["start"] - current_streak[0]["start"]).total_seconds() / 60, 1
                        ),
                        "job_ids": [s["job_id"] for s in current_streak],
                    })
                current_streak = []
        if current_streak:
            streaks.append({
                "length": len(current_streak),
                "start_time": str(current_streak[0]["start"]),
                "end_time": str(current_streak[-1]["start"]),
                "duration_minutes": round(
                    (current_streak[-1]["start"] - current_streak[0]["start"]).total_seconds() / 60, 1
                ),
                "job_ids": [s["job_id"] for s in current_streak],
            })

        # Recovery time: time between end of failure streak and next success
        recovery_times = []
        for i, j in enumerate(jobs):
            if j["status"] == "COMPLETED" and i > 0 and jobs[i - 1]["status"] == "FAILED":
                gap = (j["start"] - jobs[i - 1]["start"]).total_seconds() / 60
                recovery_times.append(round(gap, 1))

        return {
            "total_streaks": len(streaks),
            "max_streak_length": max(s["length"] for s in streaks) if streaks else 0,
            "streaks": sorted(streaks, key=lambda s: -s["length"]),
            "avg_recovery_time_min": round(
                sum(recovery_times) / len(recovery_times), 1
            ) if recovery_times else None,
            "recovery_times_min": recovery_times,
            "insight": (
                f"{len(streaks)} séquences d'échecs détectées. "
                f"Plus longue : {max(s['length'] for s in streaks) if streaks else 0} échecs consécutifs. "
                f"Temps de récupération moyen : {round(sum(recovery_times) / len(recovery_times), 1) if recovery_times else '?'} min."
            ),
        }
    finally:
        conn.close()


GET_FAILURE_STREAKS_TOOL = {
    "type": "function",
    "function": {
        "name": "get_failure_streaks",
        "description": (
            "Détecte les séquences d'échecs consécutifs (streaks) dans l'historique. "
            "Retourne : nombre de streaks, longueur max, durée de chaque streak, "
            "et le temps moyen de récupération après un streak d'échecs."
        ),
        "parameters": {"type": "object", "properties": {}, "required": []}
    }
}


# -------------------------------------------------------------------
# Tool 4 : get_batch_config
# -------------------------------------------------------------------

def get_batch_config() -> dict:
    """Lit la configuration actuelle des batchs (tables, scheduling, type de chargement)."""
    conn = get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM BATCH_CONFIG")
        cols = [desc[0] for desc in cursor.description]
        rows = [dict(zip(cols, r)) for r in cursor.fetchall()]

        configs = []
        for r in rows:
            configs.append({
                "batch_id": str(r.get("id", "")),
                "table_name": str(r.get("TableName", "")),
                "load_type": str(r.get("chargement", "")),
                "enabled": bool(r.get("enabled", 0)),
                "scheduler_enabled": bool(r.get("scheduler", 0)),
                "scheduler_time": str(r.get("scheduler_time", "")),
                "last_date": str(r.get("last_date", "")),
            })

        return {"batch_configs": configs, "total_batches": len(configs)}
    finally:
        conn.close()


GET_BATCH_CONFIG_TOOL = {
    "type": "function",
    "function": {
        "name": "get_batch_config",
        "description": (
            "Lit la configuration actuelle des 3 batchs d'extraction : "
            "CUSTOMER, AA.BILL.DETAILS, FUNDS.TRANSFER$HIS. "
            "Retourne : table Oracle, type de chargement (Full/Incremental), "
            "scheduler activé, heure planifiée."
        ),
        "parameters": {"type": "object", "properties": {}, "required": []}
    }
}


# -------------------------------------------------------------------
# Tool 5 : get_step_timeline
# -------------------------------------------------------------------

def get_step_timeline(job_execution_id: int = None, **kwargs) -> dict:
    """Retourne le détail step-by-step d'un job spécifique."""
    conn = get_connection()

    # Le SLM envoie parfois job_id, jobs ou id au lieu de job_execution_id
    if job_execution_id is None:
        job_execution_id = kwargs.get("job_id") or kwargs.get("jobs") or kwargs.get("id")

    # Si toujours pas d'ID → sélection auto du dernier job FAILED
    if job_execution_id is None:
        try:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT TOP 1 JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION
                WHERE STATUS = 'FAILED' ORDER BY START_TIME DESC
            """)
            row = cursor.fetchone()
            if row:
                job_execution_id = row[0]
            else:
                conn.close()
                return {"error": "Aucun job trouvé"}
        except Exception as e:
            conn.close()
            return {"error": f"Impossible de sélectionner un job: {e}"}

    try:
        cursor = conn.cursor()
        cursor.execute("""
            SELECT STEP_NAME, STATUS, START_TIME, END_TIME,
                   READ_COUNT, WRITE_COUNT, EXIT_CODE
            FROM BATCH_STEP_EXECUTION
            WHERE JOB_EXECUTION_ID = ?
            ORDER BY START_TIME
        """, job_execution_id)

        steps = []
        for r in cursor.fetchall():
            dur = None
            try:
                s = datetime.fromisoformat(str(r[2]))
                e = datetime.fromisoformat(str(r[3]))
                dur = round((e - s).total_seconds(), 3)
            except:
                pass

            steps.append({
                "step_name": r[0],
                "status": r[1],
                "duration_sec": dur,
                "read_count": r[4] or 0,
                "write_count": r[5] or 0,
                "exit_code": r[6],
            })

        completed_steps = [s for s in steps if s["duration_sec"] and s["duration_sec"] > 0]
        bottleneck = max(completed_steps, key=lambda s: s["duration_sec"]) if completed_steps else None

        cob_step = next((s for s in steps if s["step_name"] == "COB_STATUS"), None)
        cob_blocked = cob_step and cob_step.get("exit_code") == "COB_RUNNING" if cob_step else False

        return {
            "job_execution_id": job_execution_id,
            "total_steps": len(steps),
            "steps": steps,
            "bottleneck_step": bottleneck["step_name"] if bottleneck else None,
            "bottleneck_duration_sec": bottleneck["duration_sec"] if bottleneck else None,
            "cob_blocked": cob_blocked,
            "total_duration_sec": sum(s["duration_sec"] for s in steps if s["duration_sec"]) if steps else 0,
        }
    finally:
        conn.close()


GET_STEP_TIMELINE_TOOL = {
    "type": "function",
    "function": {
        "name": "get_step_timeline",
        "description": (
            "Retourne le détail step-by-step d'une exécution de job spécifique. "
            "Montre chaque step avec sa durée, son status, les lignes lues/écrites. "
            "Identifie le goulot d'étranglement (step le plus lent) et si le COB a bloqué le job."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "job_execution_id": {
                    "type": "integer",
                    "description": "ID du job (récupéré via get_job_history)"
                }
            },
            "required": []
        }
    }
}


# -------------------------------------------------------------------
# Tool 6 : estimate_optimal_window
# -------------------------------------------------------------------

def estimate_optimal_window() -> dict:
    """
    Calcule le créneau optimal en combinant historique de succès et patterns COB.
    Score = success_rate × (1/avg_duration) pour chaque heure.
    """
    conn = get_connection()
    try:
        cursor = conn.cursor()

        # Get all jobs with timing
        cursor.execute("""
            SELECT START_TIME, END_TIME, STATUS
            FROM BATCH_JOB_EXECUTION
            WHERE START_TIME IS NOT NULL
        """)

        hourly = defaultdict(lambda: {"ok": 0, "fail": 0, "durations": []})
        for r in cursor.fetchall():
            try:
                start = datetime.fromisoformat(str(r[0]))
                end = datetime.fromisoformat(str(r[1])) if r[1] else None
                status = r[2]
                hour = start.hour

                if status == "COMPLETED":
                    hourly[hour]["ok"] += 1
                    if end:
                        dur = (end - start).total_seconds()
                        hourly[hour]["durations"].append(dur)
                else:
                    hourly[hour]["fail"] += 1
            except:
                continue

        # Score each hour
        scores = {}
        for h in range(24):
            d = hourly.get(h, {"ok": 0, "fail": 0, "durations": []})
            total = d["ok"] + d["fail"]
            if total == 0:
                # No data — neutral score
                scores[h] = {"score": 50, "confidence": "no_data",
                             "success_rate": None, "avg_duration": None, "sample_size": 0}
                continue

            success_rate = d["ok"] / total
            avg_dur = sum(d["durations"]) / len(d["durations"]) if d["durations"] else 10
            speed_factor = 1 / max(avg_dur, 0.1)

            # Composite score: 70% success rate + 30% speed
            score = round((0.7 * success_rate + 0.3 * min(speed_factor, 1)) * 100, 1)
            confidence = "high" if total >= 5 else "medium" if total >= 2 else "low"

            scores[h] = {
                "score": score,
                "confidence": confidence,
                "success_rate_pct": round(success_rate * 100, 1),
                "avg_duration_sec": round(avg_dur, 2),
                "sample_size": total,
            }

        # Rank hours
        ranked = sorted(scores.items(), key=lambda x: -x[1]["score"])
        top3 = [{"hour": f"{h:02d}:00", **s} for h, s in ranked[:3]]
        bottom3 = [{"hour": f"{h:02d}:00", **s} for h, s in ranked[-3:]]

        return {
            "all_hours": {f"{h:02d}:00": s for h, s in sorted(scores.items())},
            "recommended_windows": top3,
            "avoid_windows": bottom3,
            "best_hour": top3[0]["hour"] if top3 else None,
            "worst_hour": bottom3[-1]["hour"] if bottom3 else None,
        }
    finally:
        conn.close()


ESTIMATE_OPTIMAL_WINDOW_TOOL = {
    "type": "function",
    "function": {
        "name": "estimate_optimal_window",
        "description": (
            "Calcule un score d'optimisation pour chaque heure de la journée (0h-23h) "
            "basé sur : taux de succès historique (70%) et vitesse d'exécution (30%). "
            "Retourne le top 3 des meilleurs créneaux et le top 3 des créneaux à éviter, "
            "avec un niveau de confiance basé sur la taille de l'échantillon."
        ),
        "parameters": {"type": "object", "properties": {}, "required": []}
    }
}


# -------------------------------------------------------------------
# Registre
# -------------------------------------------------------------------

TOOL_REGISTRY = {
    "get_job_history": get_job_history,
    "analyze_time_patterns": analyze_time_patterns,
    "get_failure_streaks": get_failure_streaks,
    "get_batch_config": get_batch_config,
    "get_step_timeline": get_step_timeline,
    "estimate_optimal_window": estimate_optimal_window,
}

ALL_TOOL_DEFINITIONS = [
    GET_JOB_HISTORY_TOOL,
    ANALYZE_TIME_PATTERNS_TOOL,
    GET_FAILURE_STREAKS_TOOL,
    GET_BATCH_CONFIG_TOOL,
    GET_STEP_TIMELINE_TOOL,
    ESTIMATE_OPTIMAL_WINDOW_TOOL,
]
