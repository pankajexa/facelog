from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime


class SyncRequest(BaseModel):
    last_sync_timestamp: Optional[datetime] = None


class EmployeeSync(BaseModel):
    id: str
    employee_code: str
    full_name: str
    department: Optional[str] = None
    face_embedding: Optional[List[float]] = None
    embedding_version: Optional[int] = None
    updated_at: Optional[datetime] = None


class SyncResponse(BaseModel):
    employees: List[EmployeeSync]
    deactivated_ids: List[str] = []
    server_timestamp: Optional[datetime] = None
    has_more: bool = False
