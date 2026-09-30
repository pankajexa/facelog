"""Notification service - sends alerts and notifications."""
import logging
from typing import Optional

logger = logging.getLogger(__name__)


class NotificationService:
    """Handles sending notifications via various channels."""

    async def send_spoof_alert(
        self,
        organization_id: str,
        device_id: str,
        timestamp: str,
        details: Optional[str] = None,
    ):
        """Alert admins about spoofing attempt."""
        logger.warning(
            f"SPOOF ALERT: org={organization_id}, device={device_id}, "
            f"time={timestamp}, details={details}"
        )

    async def send_device_offline_alert(
        self,
        organization_id: str,
        device_id: str,
        device_name: str,
        last_seen: str,
    ):
        """Alert when a device goes offline."""
        logger.warning(
            f"DEVICE OFFLINE: org={organization_id}, device={device_name} "
            f"({device_id}), last_seen={last_seen}"
        )

    async def send_daily_summary(
        self,
        organization_id: str,
        date: str,
        present: int,
        absent: int,
        total: int,
    ):
        """Send daily attendance summary to admins."""
        logger.info(
            f"DAILY SUMMARY: org={organization_id}, date={date}, "
            f"present={present}, absent={absent}, total={total}"
        )

    async def send_enrollment_notification(
        self,
        organization_id: str,
        employee_name: str,
        employee_code: str,
    ):
        """Notify about new employee enrollment."""
        logger.info(
            f"NEW ENROLLMENT: org={organization_id}, "
            f"employee={employee_name} ({employee_code})"
        )
