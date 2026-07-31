"""
Endpoints de consultation de l'historique des rapports d'agents (MySQL).

    GET /api/reports/             → liste des rapports récents (filtres: agent, type, limit)
    GET /api/reports/<id>/        → un rapport complet (avec son corps JSON)
"""

from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status

from core import reports_store


class ReportListView(APIView):
    required_scopes = ["ai:read"]

    def get(self, request):
        agent = request.query_params.get("agent")
        report_type = request.query_params.get("type")
        try:
            limit = int(request.query_params.get("limit", 50))
        except (TypeError, ValueError):
            limit = 50
        try:
            rows = reports_store.list_reports(agent=agent, report_type=report_type, limit=limit)
            return Response({"count": len(rows), "results": rows})
        except Exception as e:  # noqa: BLE001
            return Response({"error": str(e)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


class ReportDetailView(APIView):
    required_scopes = ["ai:read"]

    def get(self, request, report_id: int):
        try:
            row = reports_store.get_report(report_id)
        except Exception as e:  # noqa: BLE001
            return Response({"error": str(e)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)
        if not row:
            return Response({"error": "Rapport introuvable"}, status=status.HTTP_404_NOT_FOUND)
        return Response(row)
