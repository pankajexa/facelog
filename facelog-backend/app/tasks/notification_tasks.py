"""Celery tasks for notifications and alerts."""
import logging
from datetime import datetime, timedelta

from app.tasks.celery_app import celery_app

logger = logging.getLogger(__name__)


@celery_app.task
def check_offline_devices():
    """Check for devices that haven't sent a heartbeat recently."""
    logger.info("Checking for offline devices")
    threshold = datetime.utcnow() - timedelta(minutes=10)
    # Query devices where last_heartbeat_at < threshold
    # Send alerts for newly-offline devices
    logger.info("Offline device check complete")


@celery_app.task
def send_spoof_alert(
    organization_id: str,
    device_id: str,
    timestamp: str,
    image_path: str = None,
):
    """Send alert about detected spoofing attempt."""
    logger.warning(
        f"SPOOF ALERT: org={organization_id}, device={device_id}, "
        f"time={timestamp}"
    )
    # Send notification to org admins (email, push, etc.)


@celery_app.task
def send_low_battery_alert(device_id: str, battery_level: int):
    """Alert when device battery is low."""
    logger.warning(
        f"LOW BATTERY: device={device_id}, level={battery_level}%"
    )
