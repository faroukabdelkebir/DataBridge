from django.urls import path
from .views import SchedulerRecommendView, ShouldLaunchView, BatchConfigView

urlpatterns = [
    path('scheduler/recommend/', SchedulerRecommendView.as_view()),
    path('scheduler/should-launch/', ShouldLaunchView.as_view()),
    path('scheduler/config/', BatchConfigView.as_view()),
]
