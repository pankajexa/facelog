"""Server-side face processing using InsightFace."""
import logging
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)


class FaceProcessor:
    """Processes face images for enrollment and verification on the server."""

    def __init__(self, model_path: Optional[str] = None):
        self.app = None
        self._initialized = False
        self.model_path = model_path

    def initialize(self):
        """Lazy-load InsightFace models."""
        if self._initialized:
            return

        try:
            from insightface.app import FaceAnalysis

            self.app = FaceAnalysis(
                name="buffalo_sc",
                providers=["CPUExecutionProvider"],
            )
            self.app.prepare(ctx_id=-1, det_size=(640, 640))
            self._initialized = True
            logger.info("FaceProcessor initialized with InsightFace buffalo_sc")
        except ImportError:
            logger.error("InsightFace not installed. Run: pip install insightface")
            raise
        except Exception as e:
            logger.error(f"Failed to initialize FaceProcessor: {e}")
            raise

    def process(self, image_bytes: bytes) -> Optional[dict]:
        """
        Process a single face image.
        Returns dict with embedding, bbox, quality_score, or None if no face found.
        """
        self.initialize()

        import cv2
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            logger.warning("Failed to decode image")
            return None

        faces = self.app.get(img)
        if not faces:
            logger.warning("No face detected in image")
            return None

        face = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))

        embedding = face.embedding
        if embedding is not None:
            embedding = embedding / np.linalg.norm(embedding)

        bbox = face.bbox.tolist()
        det_score = float(face.det_score)

        img_h, img_w = img.shape[:2]
        face_w = bbox[2] - bbox[0]
        face_h = bbox[3] - bbox[1]
        face_area_ratio = (face_w * face_h) / (img_w * img_h)
        quality_score = min(1.0, det_score * (1.0 if face_area_ratio > 0.05 else 0.5))

        return {
            "embedding": embedding.tolist() if embedding is not None else None,
            "bbox": bbox,
            "det_score": det_score,
            "quality_score": quality_score,
        }

    def compute_similarity(
        self, embedding1: list[float], embedding2: list[float]
    ) -> float:
        """Compute cosine similarity between two embeddings."""
        e1 = np.array(embedding1)
        e2 = np.array(embedding2)
        return float(np.dot(e1, e2) / (np.linalg.norm(e1) * np.linalg.norm(e2)))
