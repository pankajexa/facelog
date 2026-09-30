from fastapi import Depends, HTTPException, Header
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.db.database import async_session
from app.db.models.device import Device
from app.db.models.user import AdminUser
from app.core.security import verify_jwt_token, verify_api_key_hash
from app.config import settings


async def get_db():
    async with async_session() as session:
        try:
            yield session
        finally:
            await session.close()


async def get_current_device(
    x_api_key: str = Header(...),
    x_device_id: str = Header(...),
    db: AsyncSession = Depends(get_db),
) -> Device:
    result = await db.execute(
        select(Device).where(Device.id == x_device_id, Device.is_active == True)
    )
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=401, detail="Invalid device")

    if not verify_api_key_hash(x_api_key, device.api_key_hash):
        raise HTTPException(status_code=401, detail="Invalid API key")

    return device


async def get_current_admin(
    authorization: str = Header(...),
    db: AsyncSession = Depends(get_db),
) -> AdminUser:
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization header")

    token = authorization[7:]
    payload = verify_jwt_token(token)
    if not payload:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    result = await db.execute(
        select(AdminUser).where(
            AdminUser.id == payload.get("sub"), AdminUser.is_active == True
        )
    )
    admin = result.scalars().first()
    if not admin:
        raise HTTPException(status_code=401, detail="Admin user not found")

    return admin
