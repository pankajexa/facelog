from sqlalchemy import Column, String, Float, DateTime, Enum, ForeignKey, Index
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.db.database import Base
import enum


class AttendanceType(str, enum.Enum):
    CHECK_IN = "CHECK_IN"
    CHECK_OUT = "CHECK_OUT"


class AttendanceRecord(Base):
    __tablename__ = "attendance_records"

    id = Column(String(36), primary_key=True)  # UUID from device
    employee_id = Column(String(36), ForeignKey("employees.id"), nullable=False, index=True)
    org_id = Column(String(36), ForeignKey("organizations.id"), nullable=False, index=True)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=False)
    timestamp = Column(DateTime(timezone=True), nullable=False, index=True)
    type = Column(Enum(AttendanceType), nullable=False)
    confidence = Column(Float, nullable=False)
    liveness_score = Column(Float, nullable=False)
    latitude = Column(Float)
    longitude = Column(Float)
    snapshot_url = Column(String(500))
    received_at = Column(DateTime(timezone=True), server_default=func.now())

    employee = relationship("Employee", back_populates="attendance_records")

    __table_args__ = (
        Index("ix_attendance_org_date", "org_id", "timestamp"),
        Index("ix_attendance_employee_date", "employee_id", "timestamp"),
    )
