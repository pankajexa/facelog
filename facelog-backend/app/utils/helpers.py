"""Utility helper functions."""
import hashlib
import secrets
from datetime import datetime, date
from typing import Optional


def generate_registration_code(length: int = 8) -> str:
    """Generate a random alphanumeric registration code."""
    return secrets.token_urlsafe(length)[:length].upper()


def compute_file_hash(data: bytes) -> str:
    """Compute SHA256 hash of file data."""
    return hashlib.sha256(data).hexdigest()


def ist_now() -> datetime:
    """Get current time in IST (UTC+5:30)."""
    from datetime import timezone, timedelta

    ist = timezone(timedelta(hours=5, minutes=30))
    return datetime.now(ist)


def format_ist(dt: Optional[datetime]) -> Optional[str]:
    """Format datetime to IST string."""
    if dt is None:
        return None
    from datetime import timezone, timedelta

    ist = timezone(timedelta(hours=5, minutes=30))
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(ist).strftime("%Y-%m-%d %H:%M:%S IST")


def working_days_in_month(year: int, month: int) -> int:
    """Count working days (Mon-Fri) in a month."""
    from calendar import monthrange

    _, num_days = monthrange(year, month)
    count = 0
    for day in range(1, num_days + 1):
        d = date(year, month, day)
        if d.weekday() < 5:
            count += 1
    return count


def sanitize_filename(filename: str) -> str:
    """Sanitize a filename for safe storage."""
    import re

    name = re.sub(r"[^\w\s\-.]", "", filename)
    name = re.sub(r"\s+", "_", name)
    return name[:255]
