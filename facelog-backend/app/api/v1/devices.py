"""Device management endpoints."""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_device, get_current_admin
from app.services.device_service import DeviceService
from app.schemas.device import (
    DeviceRegistrationRequest,
    DeviceRegistrationResponse,
    DeviceHeartbeatRequest,
    DeviceHeartbeatResponse,
)

router = APIRouter()


@router.post("/register", response_model=DeviceRegistrationResponse)
async def register_device(
    request: DeviceRegistrationRequest,
    db: AsyncSession = Depends(get_session),
):
    """Register a new device with a registration code."""
    service = DeviceService(db)
    try:
        result = await service.register_device(
            registration_code=request.registration_code,
            device_name=request.device_name,
            device_model=request.device_model,
            android_version=request.android_version,
        )
        return DeviceRegistrationResponse(**result)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e),
        )


@router.post("/heartbeat", response_model=DeviceHeartbeatResponse)
async def device_heartbeat(
    request: DeviceHeartbeatRequest,
    device=Depends(get_current_device),
    db: AsyncSession = Depends(get_session),
):
    """Update device heartbeat status."""
    service = DeviceService(db)
    device = await service.heartbeat(
        device_id=device.id,
        battery_level=request.battery_level,
        app_version=request.app_version,
    )
    return DeviceHeartbeatResponse(
        status="ok",
        server_time=device.last_heartbeat_at,
    )
