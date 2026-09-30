from sqlalchemy import Column, String, Boolean, DateTime, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.db.database import Base


class Organization(Base):
    __tablename__ = "organizations"

    id = Column(String(36), primary_key=True)
    name = Column(String(255), nullable=False)
    code = Column(String(50), unique=True, nullable=False, index=True)
    type = Column(String(50))  # "private", "government", "psu"
    address = Column(Text)
    city = Column(String(100))
    state = Column(String(100))
    config = Column(Text)  # JSON: thresholds, features, retention policy
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    employees = relationship("Employee", back_populates="organization")
    devices = relationship("Device", back_populates="organization")
