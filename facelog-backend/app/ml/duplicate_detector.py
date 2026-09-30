"""Duplicate face detection across organization gallery."""
import logging
from typing import Optional
from uuid import UUID

import numpy as np
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.employee import Employee

logger = logging.getLogger(__name__)


class DuplicateDetector:
    """Detects if a face embedding matches an existing employee."""

    def __init__(self, threshold: float = 0.6):
        self.threshold = threshold

    async def find_duplicates(
        self,
        db: AsyncSession,
        organization_id: UUID,
        embedding: list[float],
        exclude_employee_id: Optional[UUID] = None,
    ) -> list[dict]:
        """
        Find employees with similar face embeddings.
        Returns list of potential duplicates with similarity scores.
        """
        query = select(Employee).where(
            Employee.organization_id == organization_id,
            Employee.is_active == True,
            Employee.face_embedding.isnot(None),
        )
        if exclude_employee_id:
            query = query.where(Employee.id != exclude_employee_id)

        result = await db.execute(query)
        employees = result.scalars().all()

        target = np.array(embedding)
        if np.linalg.norm(target) > 0:
            target = target / np.linalg.norm(target)

        duplicates = []
        for emp in employees:
            if emp.face_embedding is None:
                continue

            existing = np.array(emp.face_embedding)
            if np.linalg.norm(existing) > 0:
                existing = existing / np.linalg.norm(existing)

            similarity = float(np.dot(target, existing))

            if similarity >= self.threshold:
                duplicates.append({
                    "employee_id": str(emp.id),
                    "employee_code": emp.employee_code,
                    "full_name": emp.full_name,
                    "similarity": similarity,
                })

        duplicates.sort(key=lambda x: x["similarity"], reverse=True)
        return duplicates
