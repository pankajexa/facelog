from pydantic import BaseModel
from typing import Optional, Dict, Any
from datetime import datetime


class DeviceRegistrationRequest(BaseModel):
    registration_code: str
    device_name: str
    device_model: Optional[str] = None
    android_version: Optional[str] = None
    app_version: Optional[str] = None
    public_key: Optional[str] = None


class DeviceRegistrationResponse(BaseModel):
    device_id: str
    api_key: str  # One-time display
    organization_name: str
    organization_id: str


class DeviceHeartbeatRequest(BaseModel):
    battery_level: Optional[int] = None
    app_version: Optional[str] = None


class DeviceHeartbeatResponse(BaseModel):
    status: str = "ok"
    server_time: Optional[datetime] = None


class HeartbeatRequest(BaseModel):
    device_id: str
    battery_level: Optional[int] = None
    gallery_count: Optional[int] = None
    pending_sync_count: Optional[int] = None
    app_version: str
    uptime_seconds: Optional[int] = None


class HeartbeatResponse(BaseModel):
    status: str = "ok"
    server_timestamp: int
    commands: list = []  # Pending commands for the device
    config_updated: bool = False
