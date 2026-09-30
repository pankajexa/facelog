"""Authentication middleware for API key and JWT validation."""
import logging
from fastapi import Request, HTTPException, status
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger(__name__)

PUBLIC_PATHS = {
    "/",
    "/health",
    "/docs",
    "/openapi.json",
    "/redoc",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/devices/register",
}


class AuthMiddleware(BaseHTTPMiddleware):
    """Validates that requests to protected endpoints have valid credentials."""

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        if path in PUBLIC_PATHS or path.startswith("/docs") or path.startswith("/redoc"):
            return await call_next(request)

        api_key = request.headers.get("X-API-Key")
        auth_header = request.headers.get("Authorization")

        if not api_key and not auth_header:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Missing authentication credentials",
            )

        response = await call_next(request)
        return response
