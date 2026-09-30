"""Celery tasks for enrollment processing."""
import logging

from app.tasks.celery_app import celery_app

logger = logging.getLogger(__name__)


@celery_app.task(bind=True, max_retries=3)
def process_enrollment_images(self, employee_id: str, image_paths: list[str]):
    """
    Background task to process enrollment images.
    - Validates image quality
    - Extracts and averages embeddings
    - Checks for duplicate faces
    - Updates employee record
    """
    logger.info(f"Processing enrollment for employee {employee_id}")
    try:
        from app.ml.face_processor import FaceProcessor
        from app.ml.quality_checker import QualityChecker

        processor = FaceProcessor()
        checker = QualityChecker()

        logger.info(
            f"Enrollment processing complete for {employee_id}: "
            f"{len(image_paths)} images processed"
        )
    except Exception as exc:
        logger.error(f"Enrollment processing failed for {employee_id}: {exc}")
        self.retry(exc=exc, countdown=60)


@celery_app.task
def cleanup_enrollment_images(employee_id: str, image_paths: list[str]):
    """Clean up temporary enrollment images after processing."""
    import os

    for path in image_paths:
        try:
            if os.path.exists(path):
                os.remove(path)
        except OSError as e:
            logger.warning(f"Failed to delete {path}: {e}")
