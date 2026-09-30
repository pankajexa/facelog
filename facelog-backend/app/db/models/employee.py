from sqlalchemy import Column, String, Boolean, DateTime, Float, ForeignKey, Index, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from pgvector.sqlalchemy import Vector
from app.db.database import Base
from app.config import settings


class Employee(Base):
    __tablename__ = "employees"

    id = Column(String(36), primary_key=True)
    org_id = Column(String(36), ForeignKey("organizations.id"), nullable=False, index=True)
    employee_code = Column(String(100), nullable=False)
    name = Column(String(255), nullable=False)
    department = Column(String(255))
    designation = Column(String(255))
    email = Column(String(255))
    phone = Column(String(20))
    photo_url = Column(String(500))

    # Face embedding stored as pgvector
    embedding = Column(Vector(settings.EMBEDDING_DIM))

    # Multiple embeddings for robustness (JSON array of float arrays)
    enrollment_embeddings = Column(Text)  # JSON

    is_active = Column(Boolean, default=True, index=True)
    enrolled_at = Column(DateTime(timezone=True))
    enrollment_quality_score = Column(Float)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    organization = relationship("Organization", back_populates="employees")
    attendance_records = relationship("AttendanceRecord", back_populates="employee")

    __table_args__ = (
        Index("ix_employee_org_active", "org_id", "is_active"),
        Index("ix_employee_code_org", "employee_code", "org_id", unique=True),
    )
