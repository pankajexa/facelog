from pydantic import BaseModel
from typing import Optional


class EnrollmentRequest(BaseModel):
    org_id: str
    employee_code: str
    name: str
    department: str
    designation: str = ""
    email: str = ""
    phone: str = ""


class EnrollmentResponse(BaseModel):
    employee_id: str
    employee_code: str
    full_name: str
    status: str
    quality_score: Optional[float] = None
    photo_count: Optional[int] = None
    duplicate_warning: Optional[str] = None
