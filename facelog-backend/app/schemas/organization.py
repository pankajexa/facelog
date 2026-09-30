from pydantic import BaseModel
from typing import Optional, Dict, Any
from datetime import datetime


class OrganizationResponse(BaseModel):
    id: str
    name: str
    code: str
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True


class OrganizationOut(BaseModel):
    id: str
    name: str
    code: str
    type: Optional[str] = None
    address: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    config: Optional[Dict[str, Any]] = None
    is_active: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class OrgConfigUpdate(BaseModel):
    recognition_threshold: Optional[float] = None
    liveness_threshold: Optional[float] = None
    sync_interval_minutes: Optional[int] = None
    allow_manual_checkin: Optional[bool] = None


class OrgConfigResponse(BaseModel):
    recognition_threshold: float = 0.6
    liveness_threshold: float = 0.5
    sync_interval_minutes: int = 30
    allow_manual_checkin: bool = False
