"""Rate limiting middleware using in-memory sliding window."""
import time
import logging
from collections import defaultdict
from fastapi import Request, HTTPException, status
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger(__name__)


class RateLimiter(BaseHTTPMiddleware):
    """Simple in-memory rate limiter. Use Redis-backed for production."""

    def __init__(self, app, requests_per_minute: int = 60):
        super().__init__(app)
        self.requests_per_minute = requests_per_minute
        self.requests: dict[str, list[float]] = defaultdict(list)

    async def dispatch(self, request: Request, call_next):
        client_ip = request.client.host if request.client else "unknown"
        api_key = request.headers.get("X-API-Key", "")
        identifier = api_key or client_ip

        now = time.time()
        window_start = now - 60

        self.requests[identifier] = [
            t for t in self.requests[identifier] if t > window_start
        ]

        if len(self.requests[identifier]) >= self.requests_per_minute:
            logger.warning(f"Rate limit exceeded for {identifier}")
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Rate limit exceeded. Try again later.",
                headers={"Retry-After": "60"},
            )

        self.requests[identifier].append(now)
        response = await call_next(request)
        return response
