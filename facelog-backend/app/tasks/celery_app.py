"""Celery application configuration."""
from celery import Celery

from app.config import Settings

settings = Settings()

celery_app = Celery(
    "facelog",
    broker=settings.CELERY_BROKER_URL,
    backend=settings.CELERY_RESULT_BACKEND,
    include=[
        "app.tasks.enrollment_tasks",
        "app.tasks.report_tasks",
        "app.tasks.notification_tasks",
    ],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Kolkata",
    enable_utc=True,
    task_track_started=True,
    task_time_limit=300,
    task_soft_time_limit=240,
    worker_prefetch_multiplier=1,
    worker_max_tasks_per_child=100,
)

celery_app.conf.beat_schedule = {
    "check-offline-devices": {
        "task": "app.tasks.notification_tasks.check_offline_devices",
        "schedule": 300.0,  # Every 5 minutes
    },
    "daily-attendance-summary": {
        "task": "app.tasks.report_tasks.generate_daily_summaries",
        "schedule": {
            "hour": 20,
            "minute": 0,
        },
    },
}
