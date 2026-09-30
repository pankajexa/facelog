from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime


class EmployeeResponse(BaseModel):
    id: str
    employee_code: str
    full_name: str
    department: Optional[str] = None
    is_active: bool
    has_embedding: bool = False
    created_at: datetime

    class Config:
        from_attributes = True


class EmployeeListResponse(BaseModel):
    employees: List[EmployeeResponse]
    total: int
    offset: int
    limit: int


class EmployeeOut(BaseModel):
    id: str
    org_id: str
    employee_code: str
    name: str
    department: Optional[str] = None
    designation: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    photo_url: Optional[str] = None
    is_active: bool
    enrolled_at: Optional[datetime] = None
    enrollment_quality_score: Optional[float] = None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
