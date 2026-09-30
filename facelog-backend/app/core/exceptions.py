from fastapi import HTTPException


class FaceLogException(HTTPException):
    def __init__(self, status_code: int, detail: str):
        super().__init__(status_code=status_code, detail=detail)


class DuplicateEmployeeError(FaceLogException):
    def __init__(self, detail: str = "Employee already exists"):
        super().__init__(status_code=409, detail=detail)


class FaceNotDetectedError(FaceLogException):
    def __init__(self, detail: str = "No face detected in image"):
        super().__init__(status_code=400, detail=detail)


class LowQualityImageError(FaceLogException):
    def __init__(self, detail: str = "Image quality too low"):
        super().__init__(status_code=400, detail=detail)


class DeviceNotFoundError(FaceLogException):
    def __init__(self, detail: str = "Device not found"):
        super().__init__(status_code=404, detail=detail)


class OrganizationNotFoundError(FaceLogException):
    def __init__(self, detail: str = "Organization not found"):
        super().__init__(status_code=404, detail=detail)


class UnauthorizedError(FaceLogException):
    def __init__(self, detail: str = "Unauthorized"):
        super().__init__(status_code=401, detail=detail)
