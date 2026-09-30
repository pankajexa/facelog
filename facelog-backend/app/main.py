from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.config import settings
from app.api.v1.router import api_v1_router
from app.api.middleware.rate_limiter import RateLimiter
from app.api.middleware.logging_middleware import LoggingMiddleware
from app.core.events import startup_handler, shutdown_handler
from app.db.database import engine

@asynccontextmanager
async def lifespan(app: FastAPI):
    await startup_handler()
    yield
    await shutdown_handler()

app = FastAPI(
    title="FaceLog API",
    description="Enterprise Facial Recognition Attendance System",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.ENVIRONMENT != "production" else None,
    redoc_url="/redoc" if settings.ENVIRONMENT != "production" else None,
)

# Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(LoggingMiddleware)
app.add_middleware(RateLimiter)

# Routes
app.include_router(api_v1_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "version": "1.0.0"}
