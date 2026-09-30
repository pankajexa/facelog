from sqlalchemy import Column, String, Boolean, DateTime, Float, ForeignKey, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.db.database import Base


class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True)
    org_id = Column(String(36), ForeignKey("organizations.id"), nullable=False, index=True)
    name = Column(String(255))
    api_key_hash = Column(String(255), nullable=False)
    device_model = Column(String(255))
    android_version = Column(String(50))
    app_version = Column(String(50))
    location_name = Column(String(255))  # "Main Gate", "Building A"
    latitude = Column(Float)
    longitude = Column(Float)
    last_heartbeat = Column(DateTime(timezone=True))
    last_sync = Column(DateTime(timezone=True))
    gallery_version = Column(String(50))
    config = Column(Text)  # JSON: device-specific overrides
    is_active = Column(Boolean, default=True)
    registered_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    organization = relationship("Organization", back_populates="devices")
