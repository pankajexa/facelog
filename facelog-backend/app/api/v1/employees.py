"""Employee management endpoints."""
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_admin
from app.db.models.employee import Employee
from app.schemas.employee import EmployeeResponse, EmployeeListResponse

router = APIRouter()


@router.get("/", response_model=EmployeeListResponse)
async def list_employees(
    department: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    is_active: bool = Query(True),
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """List employees with optional filters."""
    query = select(Employee).where(
        Employee.organization_id == admin.organization_id,
        Employee.is_active == is_active,
    )

    if department:
        query = query.where(Employee.department == department)
    if search:
        query = query.where(
            Employee.full_name.ilike(f"%{search}%")
            | Employee.employee_code.ilike(f"%{search}%")
        )

    query = query.order_by(Employee.full_name)
    query = query.offset(offset).limit(limit)

    result = await db.execute(query)
    employees = result.scalars().all()

    return EmployeeListResponse(
        employees=[
            EmployeeResponse(
                id=str(e.id),
                employee_code=e.employee_code,
                full_name=e.full_name,
                department=e.department,
                is_active=e.is_active,
                has_embedding=e.face_embedding is not None,
                created_at=e.created_at,
            )
            for e in employees
        ],
        total=len(employees),
        offset=offset,
        limit=limit,
    )


@router.get("/{employee_id}", response_model=EmployeeResponse)
async def get_employee(
    employee_id: UUID,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Get single employee details."""
    result = await db.execute(
        select(Employee).where(
            Employee.id == employee_id,
            Employee.organization_id == admin.organization_id,
        )
    )
    employee = result.scalar_one_or_none()
    if not employee:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Employee not found",
        )

    return EmployeeResponse(
        id=str(employee.id),
        employee_code=employee.employee_code,
        full_name=employee.full_name,
        department=employee.department,
        is_active=employee.is_active,
        has_embedding=employee.face_embedding is not None,
        created_at=employee.created_at,
    )


@router.patch("/{employee_id}/deactivate")
async def deactivate_employee(
    employee_id: UUID,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """Deactivate an employee (soft delete)."""
    result = await db.execute(
        select(Employee).where(
            Employee.id == employee_id,
            Employee.organization_id == admin.organization_id,
        )
    )
    employee = result.scalar_one_or_none()
    if not employee:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Employee not found",
        )

    employee.is_active = False
    await db.commit()
    return {"status": "deactivated", "employee_id": str(employee_id)}
