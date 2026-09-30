from sqlalchemy import Column, String, DateTime, Text, BigInteger
from sqlalchemy.sql import func
from app.db.database import Base


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    org_id = Column(String(36), index=True)
    device_id = Column(String(36), index=True)
    event_type = Column(String(50), nullable=False, index=True)
    details = Column(Text)  # JSON
    ip_address = Column(String(45))
    timestamp = Column(DateTime(timezone=True), server_default=func.now(), index=True)
