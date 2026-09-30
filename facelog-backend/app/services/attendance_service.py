"""Attendance service - handles check-in/check-out logic."""
from datetime import datetime, date, timedelta
from typing import Optional
from uuid import UUID

from sqlalchemy import select, and_, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.attendance import AttendanceRecord, AttendanceType
from app.db.models.employee import Employee
from app.schemas.attendance import AttendanceUploadRequest, AttendanceQueryParams


class AttendanceService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def record_attendance(
        self,
        employee_id: UUID,
        device_id: UUID,
        attendance_type: AttendanceType,
        confidence: float,
        liveness_score: float,
        captured_at: datetime,
        idempotency_key: Optional[str] = None,
    ) -> AttendanceRecord:
        """Record a single attendance event with idempotency."""
        if idempotency_key:
            existing = await self.db.execute(
                select(AttendanceRecord).where(
                    AttendanceRecord.idempotency_key == idempotency_key
                )
            )
            existing_record = existing.scalar_one_or_none()
            if existing_record:
                return existing_record

        record = AttendanceRecord(
            employee_id=employee_id,
            device_id=device_id,
            attendance_type=attendance_type,
            confidence_score=confidence,
            liveness_score=liveness_score,
            captured_at=captured_at,
            synced_at=datetime.utcnow(),
            idempotency_key=idempotency_key,
        )
        self.db.add(record)
        await self.db.commit()
        await self.db.refresh(record)
        return record

    async def bulk_upload(
        self,
        records: list[AttendanceUploadRequest],
        device_id: UUID,
    ) -> dict:
        """Upload batch of attendance records from device."""
        created = 0
        duplicates = 0

        for req in records:
            if req.idempotency_key:
                existing = await self.db.execute(
                    select(AttendanceRecord).where(
                        AttendanceRecord.idempotency_key == req.idempotency_key
                    )
                )
                if existing.scalar_one_or_none():
                    duplicates += 1
                    continue

            record = AttendanceRecord(
                employee_id=req.employee_id,
                device_id=device_id,
                attendance_type=req.attendance_type,
                confidence_score=req.confidence_score,
                liveness_score=req.liveness_score,
                captured_at=req.captured_at,
                synced_at=datetime.utcnow(),
                idempotency_key=req.idempotency_key,
            )
            self.db.add(record)
            created += 1

        await self.db.commit()
        return {"created": created, "duplicates": duplicates}

    async def get_attendance(
        self,
        organization_id: UUID,
        params: AttendanceQueryParams,
    ) -> list[AttendanceRecord]:
        """Query attendance records with filters."""
        query = (
            select(AttendanceRecord)
            .join(Employee, AttendanceRecord.employee_id == Employee.id)
            .where(Employee.organization_id == organization_id)
        )

        if params.employee_id:
            query = query.where(AttendanceRecord.employee_id == params.employee_id)
        if params.date_from:
            query = query.where(AttendanceRecord.captured_at >= params.date_from)
        if params.date_to:
            query = query.where(AttendanceRecord.captured_at <= params.date_to)
        if params.attendance_type:
            query = query.where(
                AttendanceRecord.attendance_type == params.attendance_type
            )

        query = query.order_by(AttendanceRecord.captured_at.desc())
        query = query.offset(params.offset).limit(params.limit)

        result = await self.db.execute(query)
        return list(result.scalars().all())

    async def get_daily_summary(
        self,
        organization_id: UUID,
        target_date: date,
    ) -> dict:
        """Get daily attendance summary for an organization."""
        start = datetime.combine(target_date, datetime.min.time())
        end = start + timedelta(days=1)

        result = await self.db.execute(
            select(
                func.count(func.distinct(AttendanceRecord.employee_id)).label(
                    "unique_employees"
                ),
                func.count(AttendanceRecord.id).label("total_records"),
            )
            .join(Employee, AttendanceRecord.employee_id == Employee.id)
            .where(
                and_(
                    Employee.organization_id == organization_id,
                    AttendanceRecord.captured_at >= start,
                    AttendanceRecord.captured_at < end,
                )
            )
        )
        row = result.one()

        total_employees = await self.db.execute(
            select(func.count(Employee.id)).where(
                and_(
                    Employee.organization_id == organization_id,
                    Employee.is_active == True,
                )
            )
        )
        total = total_employees.scalar()

        return {
            "date": target_date.isoformat(),
            "present": row.unique_employees,
            "absent": total - row.unique_employees,
            "total_employees": total,
            "total_records": row.total_records,
        }
