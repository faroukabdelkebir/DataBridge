from datetime import datetime

from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from agent_anomaly_quality.agents.anomaly_quality_agent import AnomalyQualityAgent
from core import reports_store


class AuditTableView(APIView):
    required_scopes = ["ai:execute"]
    """POST /api/agent/audit/ — Audit complet (qualité + anomalies)."""
    def post(self, request):
        table = request.data.get("table_name", "CUSTOMER")
        model = request.data.get("model", "qwen2.5:7b")
        try:
            agent = AnomalyQualityAgent(model=model)
            t0 = datetime.now()
            report = agent.audit_table(table)
            duration = round((datetime.now() - t0).total_seconds(), 1)
            report_id = reports_store.save_report(
                agent="anomaly_quality", report_type="audit_table",
                report=report, target=table, model=model, duration_sec=duration,
            )
            return Response({"status": "completed", "report_id": report_id, "report": report})
        except ValueError as e:
            return Response({"error": str(e)}, status=status.HTTP_400_BAD_REQUEST)
        except ConnectionError as e:
            return Response({"error": str(e)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


class AuditAllView(APIView):
    required_scopes = ["ai:execute"]
    """POST /api/agent/audit-all/ — Audit comparatif des 3 tables."""
    def post(self, request):
        model = request.data.get("model", "qwen2.5:7b")
        try:
            agent = AnomalyQualityAgent(model=model)
            t0 = datetime.now()
            report = agent.audit_all_tables()
            duration = round((datetime.now() - t0).total_seconds(), 1)
            report_id = reports_store.save_report(
                agent="anomaly_quality", report_type="audit_all",
                report=report, model=model, duration_sec=duration,
            )
            return Response({"status": "completed", "report_id": report_id, "report": report})
        except ValueError as e:
            return Response({"error": str(e)}, status=status.HTTP_400_BAD_REQUEST)
        except ConnectionError as e:
            return Response({"error": str(e)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)

class QuickScoreView(APIView):
    required_scopes = ["ai:read"]
    """POST /api/agent/quick-score/ — Score rapide (Phase 1 seulement)."""
    def post(self, request):
        table = request.data.get("table_name", "CUSTOMER")
        model = request.data.get("model", "qwen2.5:7b")
        try:
            agent = AnomalyQualityAgent(model=model)
            t0 = datetime.now()
            report = agent.quick_score(table)
            duration = round((datetime.now() - t0).total_seconds(), 1)
            reports_store.save_report(
                agent="anomaly_quality", report_type="quick_score",
                report=report, target=table, model=model, duration_sec=duration,
            )
            return Response(report)
        except ValueError as e:
            return Response({"error": str(e)}, status=status.HTTP_400_BAD_REQUEST)
        except ConnectionError as e:
            return Response({"error": str(e)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)
