"""API v1 router - aggregates all endpoint routers."""
from fastapi import APIRouter

from app.api.v1 import (
    attendance,
    auth,
    devices,
    employees,
    enrollment,
    organizations,
    reports,
    sync,
)

api_v1_router = APIRouter()

api_v1_router.include_router(auth.router, prefix="/auth", tags=["Authentication"])
api_v1_router.include_router(devices.router, prefix="/devices", tags=["Devices"])
api_v1_router.include_router(sync.router, prefix="/sync", tags=["Sync"])
api_v1_router.include_router(
    attendance.router, prefix="/attendance", tags=["Attendance"]
)
api_v1_router.include_router(
    enrollment.router, prefix="/enrollment", tags=["Enrollment"]
)
api_v1_router.include_router(
    employees.router, prefix="/employees", tags=["Employees"]
)
api_v1_router.include_router(
    organizations.router, prefix="/organizations", tags=["Organizations"]
)
api_v1_router.include_router(reports.router, prefix="/reports", tags=["Reports"])
