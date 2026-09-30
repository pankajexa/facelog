"""Gallery sync endpoints for devices."""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_device
from app.services.sync_service import SyncService
from app.schemas.sync import SyncRequest, SyncResponse

router = APIRouter()


@router.post("/gallery", response_model=SyncResponse)
async def sync_gallery(
    request: SyncRequest,
    device=Depends(get_current_device),
    db: AsyncSession = Depends(get_session),
):
    """
    Delta gallery sync.
    Device sends its last_sync_timestamp, server returns only changed employees.
    """
    service = SyncService(db)
    return await service.process_sync_request(
        device_id=device.id,
        request=request,
    )


@router.get("/gallery/full", response_model=SyncResponse)
async def full_gallery_sync(
    device=Depends(get_current_device),
    db: AsyncSession = Depends(get_session),
):
    """Full gallery download for initial device setup."""
    service = SyncService(db)
    return await service.get_full_gallery(device_id=device.id)
