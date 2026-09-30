"""Request/response logging middleware."""
import time
import logging
import uuid

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger("facelog.access")


class LoggingMiddleware(BaseHTTPMiddleware):
    """Logs request/response details for observability."""

    async def dispatch(self, request: Request, call_next):
        request_id = str(uuid.uuid4())[:8]
        start_time = time.time()

        request.state.request_id = request_id

        logger.info(
            f"[{request_id}] {request.method} {request.url.path} "
            f"client={request.client.host if request.client else 'unknown'}"
        )

        response = await call_next(request)

        duration_ms = (time.time() - start_time) * 1000
        logger.info(
            f"[{request_id}] {response.status_code} "
            f"duration={duration_ms:.1f}ms"
        )

        response.headers["X-Request-ID"] = request_id
        return response
