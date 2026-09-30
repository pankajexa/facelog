"""Report service - generates attendance reports."""
from datetime import date, datetime, timedelta
from typing import Optional
from uuid import UUID

from sqlalchemy import select, func, and_, case
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.attendance import AttendanceRecord, AttendanceType
from app.db.models.employee import Employee


class ReportService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def daily_report(
        self,
        organization_id: UUID,
        target_date: date,
    ) -> dict:
        """Generate daily attendance report."""
        start = datetime.combine(target_date, datetime.min.time())
        end = start + timedelta(days=1)

        records = await self.db.execute(
            select(
                Employee.id,
                Employee.employee_code,
                Employee.full_name,
                Employee.department,
                func.min(
                    case(
                        (
                            AttendanceRecord.attendance_type == AttendanceType.CHECK_IN,
                            AttendanceRecord.captured_at,
                        ),
                    )
                ).label("first_check_in"),
                func.max(
                    case(
                        (
                            AttendanceRecord.attendance_type == AttendanceType.CHECK_OUT,
                            AttendanceRecord.captured_at,
                        ),
                    )
                ).label("last_check_out"),
                func.count(AttendanceRecord.id).label("total_punches"),
            )
            .outerjoin(
                AttendanceRecord,
                and_(
                    Employee.id == AttendanceRecord.employee_id,
                    AttendanceRecord.captured_at >= start,
                    AttendanceRecord.captured_at < end,
                ),
            )
            .where(
                Employee.organization_id == organization_id,
                Employee.is_active == True,
            )
            .group_by(
                Employee.id,
                Employee.employee_code,
                Employee.full_name,
                Employee.department,
            )
        )

        rows = records.all()
        entries = []
        present_count = 0
        absent_count = 0

        for row in rows:
            is_present = row.total_punches > 0
            if is_present:
                present_count += 1
            else:
                absent_count += 1

            entries.append({
                "employee_id": str(row.id),
                "employee_code": row.employee_code,
                "full_name": row.full_name,
                "department": row.department,
                "first_check_in": row.first_check_in.isoformat() if row.first_check_in else None,
                "last_check_out": row.last_check_out.isoformat() if row.last_check_out else None,
                "total_punches": row.total_punches,
                "status": "present" if is_present else "absent",
            })

        return {
            "date": target_date.isoformat(),
            "organization_id": str(organization_id),
            "summary": {
                "total": len(entries),
                "present": present_count,
                "absent": absent_count,
                "attendance_rate": (
                    present_count / len(entries) * 100 if entries else 0
                ),
            },
            "entries": entries,
        }

    async def monthly_summary(
        self,
        organization_id: UUID,
        year: int,
        month: int,
    ) -> dict:
        """Generate monthly attendance summary."""
        start_date = date(year, month, 1)
        if month == 12:
            end_date = date(year + 1, 1, 1)
        else:
            end_date = date(year, month + 1, 1)

        start = datetime.combine(start_date, datetime.min.time())
        end = datetime.combine(end_date, datetime.min.time())

        result = await self.db.execute(
            select(
                Employee.id,
                Employee.employee_code,
                Employee.full_name,
                Employee.department,
                func.count(
                    func.distinct(func.date(AttendanceRecord.captured_at))
                ).label("days_present"),
            )
            .outerjoin(
                AttendanceRecord,
                and_(
                    Employee.id == AttendanceRecord.employee_id,
                    AttendanceRecord.captured_at >= start,
                    AttendanceRecord.captured_at < end,
                ),
            )
            .where(
                Employee.organization_id == organization_id,
                Employee.is_active == True,
            )
            .group_by(
                Employee.id,
                Employee.employee_code,
                Employee.full_name,
                Employee.department,
            )
        )

        rows = result.all()
        working_days = sum(
            1
            for d in range((end_date - start_date).days)
            if (start_date + timedelta(days=d)).weekday() < 5
        )

        entries = []
        for row in rows:
            entries.append({
                "employee_id": str(row.id),
                "employee_code": row.employee_code,
                "full_name": row.full_name,
                "department": row.department,
                "days_present": row.days_present,
                "working_days": working_days,
                "attendance_rate": (
                    row.days_present / working_days * 100 if working_days > 0 else 0
                ),
            })

        return {
            "year": year,
            "month": month,
            "organization_id": str(organization_id),
            "working_days": working_days,
            "entries": entries,
        }
