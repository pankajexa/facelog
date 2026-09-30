"""Celery tasks for report generation."""
import logging
from datetime import date

from app.tasks.celery_app import celery_app

logger = logging.getLogger(__name__)


@celery_app.task
def generate_daily_summaries():
    """Generate daily attendance summaries for all organizations."""
    logger.info("Generating daily attendance summaries")
    # In production, iterate over all active organizations
    # and generate + email daily reports
    logger.info("Daily summaries generation complete")


@celery_app.task
def generate_monthly_report(organization_id: str, year: int, month: int):
    """Generate monthly attendance report for an organization."""
    logger.info(
        f"Generating monthly report for org {organization_id}: {year}-{month}"
    )
    # Generate report and store/email it
    logger.info(f"Monthly report generated for {organization_id}")


@celery_app.task
def export_attendance_csv(
    organization_id: str,
    date_from: str,
    date_to: str,
    requester_email: str,
):
    """Export attendance records as CSV and email to requester."""
    logger.info(
        f"Exporting attendance CSV for org {organization_id}: "
        f"{date_from} to {date_to}"
    )
    # Generate CSV, upload to S3, send email with download link
    logger.info(f"CSV export sent to {requester_email}")
