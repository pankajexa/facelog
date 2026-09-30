"""Authentication endpoints for admin users."""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.db.models.user import AdminUser
from app.core.security import verify_password, create_access_token, create_refresh_token
from app.schemas.auth import LoginRequest, LoginResponse, RefreshRequest

router = APIRouter()


@router.post("/login", response_model=LoginResponse)
async def login(
    request: LoginRequest,
    db: AsyncSession = Depends(get_session),
):
    """Authenticate admin user and return tokens."""
    result = await db.execute(
        select(AdminUser).where(AdminUser.email == request.email)
    )
    user = result.scalar_one_or_none()

    if not user or not verify_password(request.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is deactivated",
        )

    access_token = create_access_token(
        data={
            "sub": str(user.id),
            "org": str(user.organization_id),
            "role": user.role,
        }
    )
    refresh_token = create_refresh_token(data={"sub": str(user.id)})

    return LoginResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        token_type="bearer",
        user_id=str(user.id),
        organization_id=str(user.organization_id),
        role=user.role,
    )


@router.post("/refresh", response_model=LoginResponse)
async def refresh_token(
    request: RefreshRequest,
    db: AsyncSession = Depends(get_session),
):
    """Refresh access token using refresh token."""
    from app.core.security import verify_token

    payload = verify_token(request.refresh_token)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid refresh token",
        )

    user_id = payload.get("sub")
    result = await db.execute(
        select(AdminUser).where(AdminUser.id == user_id)
    )
    user = result.scalar_one_or_none()

    if not user or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found or deactivated",
        )

    access_token = create_access_token(
        data={
            "sub": str(user.id),
            "org": str(user.organization_id),
            "role": user.role,
        }
    )

    return LoginResponse(
        access_token=access_token,
        refresh_token=request.refresh_token,
        token_type="bearer",
        user_id=str(user.id),
        organization_id=str(user.organization_id),
        role=user.role,
    )
