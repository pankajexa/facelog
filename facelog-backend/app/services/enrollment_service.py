"""Enrollment service - handles employee face enrollment."""
import numpy as np
from typing import Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.employee import Employee
from app.core.exceptions import (
    DuplicateEmployeeError,
    FaceNotDetectedError,
    LowQualityImageError,
)


class EnrollmentService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def enroll_employee(
        self,
        organization_id: UUID,
        employee_code: str,
        full_name: str,
        department: Optional[str],
        face_images: list[bytes],
        face_processor=None,
    ) -> Employee:
        """
        Enroll a new employee with face images.
        1. Check for duplicate employee code
        2. Process each face image to extract embeddings
        3. Average embeddings for robust template
        4. Check for duplicate faces in gallery
        5. Store employee with embedding
        """
        existing = await self.db.execute(
            select(Employee).where(
                Employee.organization_id == organization_id,
                Employee.employee_code == employee_code,
            )
        )
        if existing.scalar_one_or_none():
            raise DuplicateEmployeeError(
                f"Employee with code {employee_code} already exists"
            )

        embeddings = []
        for img_bytes in face_images:
            if face_processor is None:
                raise RuntimeError("Face processor not available")

            result = face_processor.process(img_bytes)
            if result is None or result.get("embedding") is None:
                raise FaceNotDetectedError("No face detected in one or more images")

            if result.get("quality_score", 1.0) < 0.5:
                raise LowQualityImageError("Image quality too low for enrollment")

            embeddings.append(np.array(result["embedding"]))

        if len(embeddings) < 1:
            raise FaceNotDetectedError("No valid face embeddings extracted")

        avg_embedding = np.mean(embeddings, axis=0)
        avg_embedding = avg_embedding / np.linalg.norm(avg_embedding)

        await self._check_duplicate_face(
            organization_id, avg_embedding, threshold=0.6
        )

        employee = Employee(
            organization_id=organization_id,
            employee_code=employee_code,
            full_name=full_name,
            department=department,
            face_embedding=avg_embedding.tolist(),
            embedding_version=1,
            is_active=True,
        )
        self.db.add(employee)
        await self.db.commit()
        await self.db.refresh(employee)
        return employee

    async def update_embedding(
        self,
        employee_id: UUID,
        face_images: list[bytes],
        face_processor=None,
    ) -> Employee:
        """Re-enroll employee with new face images."""
        result = await self.db.execute(
            select(Employee).where(Employee.id == employee_id)
        )
        employee = result.scalar_one_or_none()
        if not employee:
            raise ValueError(f"Employee {employee_id} not found")

        embeddings = []
        for img_bytes in face_images:
            if face_processor is None:
                raise RuntimeError("Face processor not available")

            proc_result = face_processor.process(img_bytes)
            if proc_result is None or proc_result.get("embedding") is None:
                raise FaceNotDetectedError("No face detected in image")
            embeddings.append(np.array(proc_result["embedding"]))

        avg_embedding = np.mean(embeddings, axis=0)
        avg_embedding = avg_embedding / np.linalg.norm(avg_embedding)

        employee.face_embedding = avg_embedding.tolist()
        employee.embedding_version += 1
        await self.db.commit()
        await self.db.refresh(employee)
        return employee

    async def _check_duplicate_face(
        self,
        organization_id: UUID,
        embedding: np.ndarray,
        threshold: float = 0.6,
    ):
        """Check if a similar face already exists in the organization."""
        result = await self.db.execute(
            select(Employee).where(
                Employee.organization_id == organization_id,
                Employee.is_active == True,
                Employee.face_embedding.isnot(None),
            )
        )
        employees = result.scalars().all()

        for emp in employees:
            if emp.face_embedding is not None:
                existing = np.array(emp.face_embedding)
                similarity = float(np.dot(embedding, existing))
                if similarity > threshold:
                    raise DuplicateEmployeeError(
                        f"Face is too similar to existing employee: {emp.full_name} "
                        f"(similarity: {similarity:.3f})"
                    )
