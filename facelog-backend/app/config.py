from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    # App
    ENVIRONMENT: str = "development"
    DEBUG: bool = True
    SECRET_KEY: str = "change-me-in-production"
    API_KEY_HEADER: str = "X-API-Key"

    # Database
    DATABASE_URL: str = "postgresql+asyncpg://facelog:facelog@localhost:5432/facelog"
    DB_POOL_SIZE: int = 20
    DB_MAX_OVERFLOW: int = 10

    # Redis
    REDIS_URL: str = "redis://localhost:6379/0"

    # AWS
    AWS_ACCESS_KEY_ID: str = ""
    AWS_SECRET_ACCESS_KEY: str = ""
    AWS_REGION: str = "ap-south-1"
    S3_BUCKET_NAME: str = "facelog-assets"
    S3_ENROLLMENT_PREFIX: str = "enrollments/"
    S3_MODEL_PREFIX: str = "models/"

    # JWT
    JWT_SECRET_KEY: str = "change-me-in-production"
    JWT_ALGORITHM: str = "HS256"
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    JWT_REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # ML
    EMBEDDING_DIM: int = 512
    MATCH_THRESHOLD: float = 0.45
    LIVENESS_THRESHOLD: float = 0.65
    MIN_FACE_QUALITY_SCORE: float = 0.7

    # Celery
    CELERY_BROKER_URL: str = "redis://localhost:6379/1"
    CELERY_RESULT_BACKEND: str = "redis://localhost:6379/2"

    # CORS
    CORS_ORIGINS: List[str] = ["http://localhost:3000"]

    # Rate Limiting
    RATE_LIMIT_PER_MINUTE: int = 60
    SYNC_RATE_LIMIT_PER_MINUTE: int = 10

    class Config:
        env_file = ".env"


settings = Settings()
