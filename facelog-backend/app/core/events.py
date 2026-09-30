import logging

from app.db.database import engine

logger = logging.getLogger(__name__)


async def startup_handler():
    logger.info("FaceLog API starting up...")
    logger.info("Database engine initialized")


async def shutdown_handler():
    logger.info("FaceLog API shutting down...")
    await engine.dispose()
    logger.info("Database connections closed")
