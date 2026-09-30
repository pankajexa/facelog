"""Device service - handles device registration and management."""
from datetime import datetime
from typing import Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.device import Device
from app.db.models.organization import Organization
from app.core.security import generate_api_key, hash_api_key


class DeviceService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def register_device(
        self,
        registration_code: str,
        device_name: str,
        device_model: Optional[str] = None,
        android_version: Optional[str] = None,
    ) -> dict:
        """
        Register a new device using a registration code.
        Returns device_id and api_key for the device to use.
        """
        org = await self.db.execute(
            select(Organization).where(
                Organization.registration_code == registration_code,
                Organization.is_active == True,
            )
        )
        organization = org.scalar_one_or_none()
        if not organization:
            raise ValueError("Invalid registration code")

        api_key = generate_api_key()
        api_key_hash = hash_api_key(api_key)

        device = Device(
            organization_id=organization.id,
            device_name=device_name,
            device_model=device_model,
            android_version=android_version,
            api_key_hash=api_key_hash,
            is_active=True,
            registered_at=datetime.utcnow(),
        )
        self.db.add(device)
        await self.db.commit()
        await self.db.refresh(device)

        return {
            "device_id": str(device.id),
            "api_key": api_key,
            "organization_name": organization.name,
            "organization_id": str(organization.id),
        }

    async def heartbeat(
        self,
        device_id: UUID,
        battery_level: Optional[int] = None,
        app_version: Optional[str] = None,
    ) -> Device:
        """Update device heartbeat."""
        result = await self.db.execute(
            select(Device).where(Device.id == device_id)
        )
        device = result.scalar_one_or_none()
        if not device:
            raise ValueError(f"Device {device_id} not found")

        device.last_heartbeat_at = datetime.utcnow()
        if battery_level is not None:
            device.battery_level = battery_level
        if app_version is not None:
            device.app_version = app_version

        await self.db.commit()
        await self.db.refresh(device)
        return device

    async def get_device(self, device_id: UUID) -> Optional[Device]:
        result = await self.db.execute(
            select(Device).where(Device.id == device_id)
        )
        return result.scalar_one_or_none()

    async def list_devices(self, organization_id: UUID) -> list[Device]:
        result = await self.db.execute(
            select(Device).where(
                Device.organization_id == organization_id,
                Device.is_active == True,
            )
        )
        return list(result.scalars().all())

    async def deactivate_device(self, device_id: UUID) -> Device:
        result = await self.db.execute(
            select(Device).where(Device.id == device_id)
        )
        device = result.scalar_one_or_none()
        if not device:
            raise ValueError(f"Device {device_id} not found")

        device.is_active = False
        await self.db.commit()
        await self.db.refresh(device)
        return device
