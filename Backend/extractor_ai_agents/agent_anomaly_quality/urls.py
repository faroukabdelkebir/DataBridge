from django.urls import path
from .views import AuditTableView, AuditAllView, QuickScoreView

urlpatterns = [
    path('audit/', AuditTableView.as_view()),
    path('audit-all/', AuditAllView.as_view()),
    path('quick-score/', QuickScoreView.as_view()),
]
