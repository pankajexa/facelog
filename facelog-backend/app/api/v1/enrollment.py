"""Employee enrollment endpoints."""
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.dependencies import get_current_admin
from app.services.enrollment_service import EnrollmentService
from app.core.exceptions import (
    DuplicateEmployeeError,
    FaceNotDetectedError,
    LowQualityImageError,
)
from app.schemas.enrollment import EnrollmentResponse

router = APIRouter()


@router.post("/enroll", response_model=EnrollmentResponse)
async def enroll_employee(
    employee_code: str = Form(...),
    full_name: str = Form(...),
    department: Optional[str] = Form(None),
    face_images: list[UploadFile] = File(..., description="3-5 face images"),
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_session),
):
    """
    Enroll a new employee with face images.
    Requires 3-5 face images for robust embedding generation.
    """
    if len(face_images) < 3:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="At least 3 face images required for enrollment",
        )
    if len(face_images) > 10:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Maximum 10 face images allowed",
        )

    image_bytes = []
    for img in face_images:
        content = await img.read()
        if len(content) > 10 * 1024 * 1024:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Image {img.filename} exceeds 10MB limit",
            )
        image_bytes.append(content)

    service = EnrollmentService(db)
    try:
        employee = await service.enroll_employee(
            organization_id=admin.organization_id,
            employee_code=employee_code,
            full_name=full_name,
            department=department,
            face_images=image_bytes,
        )
        return EnrollmentResponse(
            employee_id=str(employee.id),
            employee_code=employee.employee_code,
            full_name=employee.full_name,
            status="enrolled",
        )
    except DuplicateEmployeeError as e:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(e),
        )
    except FaceNotDetectedError as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(e),
        )
    except LowQualityImageError as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(e),
        )
