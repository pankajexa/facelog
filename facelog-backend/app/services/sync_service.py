"""Sync service - handles gallery sync between server and devices."""
from datetime import datetime
from typing import Optional
from uuid import UUID

from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.employee import Employee
from app.db.models.device import Device
from app.schemas.sync import SyncRequest, SyncResponse, EmployeeSync


class SyncService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_gallery_delta(
        self,
        device_id: UUID,
        last_sync_timestamp: Optional[datetime] = None,
    ) -> SyncResponse:
        """
        Get delta gallery update for device.
        Returns only employees modified since last_sync_timestamp.
        """
        device = await self._get_device(device_id)
        if not device:
            raise ValueError(f"Device {device_id} not found")

        query = select(Employee).where(
            Employee.organization_id == device.organization_id,
            Employee.is_active == True,
            Employee.face_embedding.isnot(None),
        )

        if last_sync_timestamp:
            query = query.where(Employee.updated_at > last_sync_timestamp)

        result = await self.db.execute(query)
        employees = result.scalars().all()

        deactivated = []
        if last_sync_timestamp:
            deactivated_result = await self.db.execute(
                select(Employee.id).where(
                    and_(
                        Employee.organization_id == device.organization_id,
                        Employee.is_active == False,
                        Employee.updated_at > last_sync_timestamp,
                    )
                )
            )
            deactivated = [str(eid) for eid in deactivated_result.scalars().all()]

        employee_syncs = [
            EmployeeSync(
                id=str(emp.id),
                employee_code=emp.employee_code,
                full_name=emp.full_name,
                department=emp.department,
                face_embedding=emp.face_embedding,
                embedding_version=emp.embedding_version,
                updated_at=emp.updated_at,
            )
            for emp in employees
        ]

        now = datetime.utcnow()
        device.last_sync_at = now
        await self.db.commit()

        return SyncResponse(
            employees=employee_syncs,
            deactivated_ids=deactivated,
            server_timestamp=now,
            has_more=False,
        )

    async def process_sync_request(
        self,
        device_id: UUID,
        request: SyncRequest,
    ) -> SyncResponse:
        """Process a full sync request from device."""
        return await self.get_gallery_delta(
            device_id=device_id,
            last_sync_timestamp=request.last_sync_timestamp,
        )

    async def _get_device(self, device_id: UUID) -> Optional[Device]:
        result = await self.db.execute(
            select(Device).where(Device.id == device_id)
        )
        return result.scalar_one_or_none()

    async def get_full_gallery(self, device_id: UUID) -> SyncResponse:
        """Get complete gallery for initial device sync."""
        return await self.get_gallery_delta(device_id=device_id)
