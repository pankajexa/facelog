"""Report endpoints."""
from datetime import date

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_admin
from app.services.report_service import ReportService

router = APIRouter()


@router.get("/daily/{target_date}")
async def daily_report(
    target_date: date,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Generate daily attendance report."""
    service = ReportService(db)
    return await service.daily_report(
        organization_id=admin.organization_id,
        target_date=target_date,
    )


@router.get("/monthly")
async def monthly_report(
    year: int = Query(..., ge=2020, le=2030),
    month: int = Query(..., ge=1, le=12),
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Generate monthly attendance summary."""
    service = ReportService(db)
    return await service.monthly_summary(
        organization_id=admin.organization_id,
        year=year,
        month=month,
    )
