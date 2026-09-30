from pydantic import BaseModel
from typing import List, Optional
from datetime import date, datetime


class AttendanceUploadRequest(BaseModel):
    employee_id: str
    attendance_type: str  # "CHECK_IN" or "CHECK_OUT"
    confidence_score: float
    liveness_score: float
    captured_at: datetime
    idempotency_key: Optional[str] = None


class AttendanceBulkUploadRequest(BaseModel):
    records: List[AttendanceUploadRequest]


class AttendanceBulkUploadResponse(BaseModel):
    created: int
    duplicates: int
    total: int


class AttendanceQueryParams(BaseModel):
    employee_id: Optional[str] = None
    date_from: Optional[date] = None
    date_to: Optional[date] = None
    attendance_type: Optional[str] = None
    offset: int = 0
    limit: int = 50


class AttendanceRecordResponse(BaseModel):
    id: str
    employee_id: str
    device_id: str
    attendance_type: str
    confidence_score: float
    liveness_score: float
    captured_at: datetime
    synced_at: Optional[datetime] = None


class DailySummaryResponse(BaseModel):
    date: str
    present: int
    absent: int
    total_employees: int
    total_records: int


class AttendanceRecordOut(BaseModel):
    id: str
    employee_id: str
    employee_name: str
    department: str
    timestamp: datetime
    type: str
    confidence: float
    liveness_score: float
    device_id: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class AttendanceReport(BaseModel):
    records: List[AttendanceRecordOut]
    total_count: int
    page: int
    page_size: int
