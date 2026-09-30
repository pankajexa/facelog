"""Face image quality assessment for enrollment."""
import logging
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)


class QualityChecker:
    """Checks face image quality for enrollment suitability."""

    def __init__(
        self,
        min_face_size: int = 80,
        min_sharpness: float = 50.0,
        min_brightness: float = 40.0,
        max_brightness: float = 220.0,
    ):
        self.min_face_size = min_face_size
        self.min_sharpness = min_sharpness
        self.min_brightness = min_brightness
        self.max_brightness = max_brightness

    def check(self, image_bytes: bytes, bbox: Optional[list] = None) -> dict:
        """
        Assess image quality.
        Returns quality metrics and overall pass/fail.
        """
        import cv2

        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return {"passed": False, "reason": "Failed to decode image"}

        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        if bbox:
            x1, y1, x2, y2 = [int(c) for c in bbox]
            face_region = gray[y1:y2, x1:x2]
            face_w = x2 - x1
            face_h = y2 - y1
        else:
            face_region = gray
            face_h, face_w = gray.shape

        issues = []

        if face_w < self.min_face_size or face_h < self.min_face_size:
            issues.append(f"Face too small: {face_w}x{face_h}")

        sharpness = cv2.Laplacian(face_region, cv2.CV_64F).var()
        if sharpness < self.min_sharpness:
            issues.append(f"Image too blurry: {sharpness:.1f}")

        brightness = np.mean(face_region)
        if brightness < self.min_brightness:
            issues.append(f"Image too dark: {brightness:.1f}")
        elif brightness > self.max_brightness:
            issues.append(f"Image too bright: {brightness:.1f}")

        contrast = np.std(face_region)
        if contrast < 20:
            issues.append(f"Low contrast: {contrast:.1f}")

        quality_score = self._compute_score(
            face_size=min(face_w, face_h),
            sharpness=sharpness,
            brightness=brightness,
            contrast=contrast,
        )

        return {
            "passed": len(issues) == 0,
            "quality_score": quality_score,
            "sharpness": float(sharpness),
            "brightness": float(brightness),
            "contrast": float(contrast),
            "face_size": (face_w, face_h),
            "issues": issues,
        }

    def _compute_score(
        self,
        face_size: int,
        sharpness: float,
        brightness: float,
        contrast: float,
    ) -> float:
        """Compute overall quality score 0-1."""
        size_score = min(1.0, face_size / 200.0)
        sharp_score = min(1.0, sharpness / 200.0)

        if brightness < self.min_brightness:
            bright_score = brightness / self.min_brightness
        elif brightness > self.max_brightness:
            bright_score = (255 - brightness) / (255 - self.max_brightness)
        else:
            bright_score = 1.0

        contrast_score = min(1.0, contrast / 60.0)

        return (
            size_score * 0.2
            + sharp_score * 0.3
            + bright_score * 0.3
            + contrast_score * 0.2
        )
