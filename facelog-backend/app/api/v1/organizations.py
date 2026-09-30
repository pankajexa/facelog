"""Organization management endpoints."""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_admin
from app.db.models.organization import Organization
from app.schemas.organization import OrganizationResponse, OrgConfigUpdate, OrgConfigResponse

router = APIRouter()


@router.get("/me", response_model=OrganizationResponse)
async def get_my_organization(
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Get current admin's organization."""
    result = await db.execute(
        select(Organization).where(Organization.id == admin.organization_id)
    )
    org = result.scalar_one_or_none()
    if not org:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Organization not found",
        )
    return OrganizationResponse(
        id=str(org.id),
        name=org.name,
        code=org.code,
        is_active=org.is_active,
        created_at=org.created_at,
    )


@router.get("/config", response_model=OrgConfigResponse)
async def get_org_config(
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Get organization configuration."""
    result = await db.execute(
        select(Organization).where(Organization.id == admin.organization_id)
    )
    org = result.scalar_one_or_none()
    if not org:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Organization not found",
        )

    return OrgConfigResponse(
        recognition_threshold=org.recognition_threshold or 0.6,
        liveness_threshold=org.liveness_threshold or 0.5,
        sync_interval_minutes=org.sync_interval_minutes or 30,
        allow_manual_checkin=org.allow_manual_checkin or False,
    )


@router.put("/config", response_model=OrgConfigResponse)
async def update_org_config(
    config: OrgConfigUpdate,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Update organization configuration."""
    result = await db.execute(
        select(Organization).where(Organization.id == admin.organization_id)
    )
    org = result.scalar_one_or_none()
    if not org:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Organization not found",
        )

    if config.recognition_threshold is not None:
        org.recognition_threshold = config.recognition_threshold
    if config.liveness_threshold is not None:
        org.liveness_threshold = config.liveness_threshold
    if config.sync_interval_minutes is not None:
        org.sync_interval_minutes = config.sync_interval_minutes
    if config.allow_manual_checkin is not None:
        org.allow_manual_checkin = config.allow_manual_checkin

    await db.commit()

    return OrgConfigResponse(
        recognition_threshold=org.recognition_threshold,
        liveness_threshold=org.liveness_threshold,
        sync_interval_minutes=org.sync_interval_minutes,
        allow_manual_checkin=org.allow_manual_checkin,
    )
