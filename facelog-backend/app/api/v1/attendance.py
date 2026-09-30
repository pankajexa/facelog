"""Attendance endpoints."""
from datetime import date
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_device, get_current_admin
from app.services.attendance_service import AttendanceService
from app.schemas.attendance import (
    AttendanceUploadRequest,
    AttendanceBulkUploadRequest,
    AttendanceBulkUploadResponse,
    AttendanceRecordResponse,
    AttendanceQueryParams,
    DailySummaryResponse,
)

router = APIRouter()


@router.post("/upload", response_model=AttendanceBulkUploadResponse)
async def upload_attendance(
    request: AttendanceBulkUploadRequest,
    device=Depends(get_current_device),
    db: AsyncSession = Depends(get_session),
):
    """
    Bulk upload attendance records from device.
    Supports idempotent uploads via idempotency_key.
    """
    service = AttendanceService(db)
    result = await service.bulk_upload(
        records=request.records,
        device_id=device.id,
    )
    return AttendanceBulkUploadResponse(
        created=result["created"],
        duplicates=result["duplicates"],
        total=len(request.records),
    )


@router.get("/records", response_model=list[AttendanceRecordResponse])
async def get_attendance_records(
    employee_id: Optional[UUID] = Query(None),
    date_from: Optional[date] = Query(None),
    date_to: Optional[date] = Query(None),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Query attendance records (admin only)."""
    service = AttendanceService(db)
    params = AttendanceQueryParams(
        employee_id=employee_id,
        date_from=date_from,
        date_to=date_to,
        offset=offset,
        limit=limit,
    )
    records = await service.get_attendance(
        organization_id=admin.organization_id,
        params=params,
    )
    return [
        AttendanceRecordResponse(
            id=str(r.id),
            employee_id=str(r.employee_id),
            device_id=str(r.device_id),
            attendance_type=r.attendance_type.value,
            confidence_score=r.confidence_score,
            liveness_score=r.liveness_score,
            captured_at=r.captured_at,
            synced_at=r.synced_at,
        )
        for r in records
    ]


@router.get("/summary/{target_date}", response_model=DailySummaryResponse)
async def daily_summary(
    target_date: date,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Get daily attendance summary (admin only)."""
    service = AttendanceService(db)
    result = await service.get_daily_summary(
        organization_id=admin.organization_id,
        target_date=target_date,
    )
    return DailySummaryResponse(**result)
