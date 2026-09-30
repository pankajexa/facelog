"""Standalone embedding extractor using ONNX Runtime."""
import logging
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)


class EmbeddingExtractor:
    """Extracts face embeddings using ONNX Runtime (no InsightFace dependency)."""

    def __init__(self, model_path: str = "models/mobilefacenet_arcface_int8.onnx"):
        self.model_path = model_path
        self.session = None

    def initialize(self):
        """Load ONNX model."""
        if self.session is not None:
            return

        import onnxruntime as ort

        self.session = ort.InferenceSession(
            self.model_path,
            providers=["CPUExecutionProvider"],
        )
        self.input_name = self.session.get_inputs()[0].name
        self.input_shape = self.session.get_inputs()[0].shape
        logger.info(
            f"EmbeddingExtractor loaded: input={self.input_shape}, "
            f"model={self.model_path}"
        )

    def extract(self, face_image: np.ndarray) -> Optional[np.ndarray]:
        """
        Extract embedding from aligned face image.
        Input: BGR image, any size (will be resized to 112x112).
        Output: 512-d normalized embedding.
        """
        self.initialize()

        import cv2

        if face_image is None or face_image.size == 0:
            return None

        img = cv2.resize(face_image, (112, 112))
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        img = img.astype(np.float32)
        img = (img - 127.5) / 128.0
        img = np.transpose(img, (2, 0, 1))
        img = np.expand_dims(img, axis=0)

        outputs = self.session.run(None, {self.input_name: img})
        embedding = outputs[0][0]

        norm = np.linalg.norm(embedding)
        if norm > 0:
            embedding = embedding / norm

        return embedding

    def extract_from_bytes(self, image_bytes: bytes) -> Optional[np.ndarray]:
        """Extract embedding from raw image bytes."""
        import cv2

        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return None
        return self.extract(img)
