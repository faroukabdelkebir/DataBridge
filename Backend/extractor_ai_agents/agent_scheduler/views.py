"""
API endpoints pour l'agent M3 — Ordonnancement intelligent.

Endpoints :
    POST /api/agent/scheduler/recommend/      → Lance (en arrière-plan) le calcul du plan
    GET  /api/agent/scheduler/recommend/      → Récupère l'état / le dernier plan calculé
    POST /api/agent/scheduler/should-launch/  → Décision temps réel (rapide) : lancer ou attendre ?
    GET  /api/agent/scheduler/config/         → Configuration actuelle des batchs
"""

import threading
from datetime import datetime

from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status

from agent_scheduler.agents.scheduler_agent import SchedulerAgent
from agent_scheduler.tools.scheduling_tools import get_batch_config
from core import reports_store


# ============================================================================
#  Cache + calcul en arrière-plan pour /recommend/
#  La reco prend ~5 min (SLM 7B sur CPU) : on ne bloque PAS la requête HTTP.
#  Le front fait POST pour lancer, puis GET pour récupérer le résultat.
#  (Pour la prod : remplacer le thread par Celery/RQ. Suffisant pour le dev.)
# ============================================================================

class _RecoCache:
    def __init__(self):
        self.lock = threading.Lock()
        self.status = "idle"        # idle | computing | completed | error
        self.report = None
        self.error = None
        self.started_at = None
        self.computed_at = None
        self.duration_sec = None

    def snapshot(self):
        return {
            "status": self.status,
            "started_at": self.started_at,
            "computed_at": self.computed_at,
            "duration_sec": self.duration_sec,
            "error": self.error,
            "report": self.report,
        }


_reco_cache = _RecoCache()


def _compute_reco(model: str):
    """Exécuté dans un thread : calcule la reco et remplit le cache."""
    t0 = datetime.now()
    try:
        agent = SchedulerAgent(model=model)
        report = agent.recommend_schedule()
        duration = round((datetime.now() - t0).total_seconds(), 1)
        with _reco_cache.lock:
            _reco_cache.report = report
            _reco_cache.status = "completed"
            _reco_cache.error = None
            _reco_cache.computed_at = datetime.now().isoformat(timespec="seconds")
            _reco_cache.duration_sec = duration
        reports_store.save_report(
            agent="scheduler", report_type="scheduler_recommend",
            report=report, model=model, duration_sec=duration,
        )
    except Exception as e:
        with _reco_cache.lock:
            _reco_cache.status = "error"
            _reco_cache.error = str(e)
            _reco_cache.computed_at = datetime.now().isoformat(timespec="seconds")
        reports_store.save_report(
            agent="scheduler", report_type="scheduler_recommend",
            report=None, model=model, status="error", error=str(e),
        )


class SchedulerRecommendView(APIView):
    required_scopes = {"GET": ["ai:read"], "POST": ["ai:execute"]}
    """
    POST /api/agent/scheduler/recommend/   Body: {"model": "qwen2.5:7b", "refresh": false}
        - Renvoie le plan en cache si déjà calculé (sauf refresh=true).
        - Sinon lance le calcul en arrière-plan et renvoie 202 {status: "computing"}.
    GET  /api/agent/scheduler/recommend/
        - Renvoie l'état courant + le dernier plan calculé (polling du front).
    """

    def get(self, request):
        return Response(_reco_cache.snapshot())

    def post(self, request):
        model = request.data.get("model", "qwen2.5:7b")
        refresh = bool(request.data.get("refresh", False))

        with _reco_cache.lock:
            if _reco_cache.status == "computing":
                return Response({
                    "status": "computing",
                    "started_at": _reco_cache.started_at,
                    "message": "Calcul déjà en cours (~5 min). Interrogez ce même endpoint en GET.",
                }, status=status.HTTP_202_ACCEPTED)

            if _reco_cache.status == "completed" and not refresh:
                return Response({
                    "status": "completed",
                    "cached": True,
                    "computed_at": _reco_cache.computed_at,
                    "report": _reco_cache.report,
                })

            # Démarrer un nouveau calcul en arrière-plan
            _reco_cache.status = "computing"
            _reco_cache.started_at = datetime.now().isoformat(timespec="seconds")

        threading.Thread(target=_compute_reco, args=(model,), daemon=True).start()
        return Response({
            "status": "computing",
            "started_at": _reco_cache.started_at,
            "message": "Calcul lancé (~5 min). Interrogez GET /recommend/ pour le résultat.",
        }, status=status.HTTP_202_ACCEPTED)


class ShouldLaunchView(APIView):
    required_scopes = ["ai:read"]
    """
    POST /api/agent/scheduler/should-launch/
    Body: {"batch_id": "CUSTOMER"}

    Décision temps réel (rapide, déterministe) : lancer le batch maintenant ou reporter ?
    Appelée par AutoExtractionScheduler (Java) avant chaque lancement.

    Réponse : {"launch": true/false, "reason": "...", "recommended_time": "20:55" | null}
    """

    def post(self, request):
        batch_id = request.data.get("batch_id")
        if not batch_id:
            return Response(
                {"error": "Le champ 'batch_id' est requis"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            # Décision déterministe -> pas besoin de charger un modèle SLM ici
            decision = SchedulerAgent().should_launch_now(batch_id)
            return Response(decision)
        except Exception as e:
            # Ne jamais bloquer le scheduler : par sécurité on reporte.
            return Response({
                "launch": False,
                "reason": f"Décision indisponible ({e}) ; report par sécurité.",
                "recommended_time": "20:55",
                "batch_id": batch_id,
            })


class BatchConfigView(APIView):
    required_scopes = ["ai:read"]
    """GET /api/agent/scheduler/config/ — Config actuelle (rapide, sans SLM)."""

    def get(self, request):
        try:
            config = get_batch_config()
            return Response(config)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)