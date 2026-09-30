"""AWS S3 client for face image and report storage."""
import logging
from typing import Optional

import boto3
from botocore.exceptions import ClientError

from app.config import Settings

logger = logging.getLogger(__name__)


class S3Client:
    """Handles S3 operations for FaceLog."""

    def __init__(self, settings: Optional[Settings] = None):
        self.settings = settings or Settings()
        self.client = None
        self.bucket = self.settings.AWS_S3_BUCKET

    def _get_client(self):
        if self.client is None:
            self.client = boto3.client(
                "s3",
                region_name=self.settings.AWS_REGION,
                aws_access_key_id=self.settings.AWS_ACCESS_KEY_ID,
                aws_secret_access_key=self.settings.AWS_SECRET_ACCESS_KEY,
            )
        return self.client

    async def upload_face_image(
        self,
        organization_id: str,
        employee_id: str,
        image_bytes: bytes,
        filename: str,
    ) -> str:
        """Upload enrollment face image to S3."""
        key = f"faces/{organization_id}/{employee_id}/{filename}"
        try:
            self._get_client().put_object(
                Bucket=self.bucket,
                Key=key,
                Body=image_bytes,
                ContentType="image/jpeg",
                ServerSideEncryption="AES256",
            )
            logger.info(f"Uploaded face image: {key}")
            return key
        except ClientError as e:
            logger.error(f"Failed to upload face image: {e}")
            raise

    async def download_file(self, key: str) -> bytes:
        """Download a file from S3."""
        try:
            response = self._get_client().get_object(
                Bucket=self.bucket,
                Key=key,
            )
            return response["Body"].read()
        except ClientError as e:
            logger.error(f"Failed to download {key}: {e}")
            raise

    async def generate_presigned_url(
        self, key: str, expiration: int = 3600
    ) -> str:
        """Generate a presigned URL for temporary access."""
        try:
            url = self._get_client().generate_presigned_url(
                "get_object",
                Params={"Bucket": self.bucket, "Key": key},
                ExpiresIn=expiration,
            )
            return url
        except ClientError as e:
            logger.error(f"Failed to generate presigned URL for {key}: {e}")
            raise

    async def delete_file(self, key: str):
        """Delete a file from S3."""
        try:
            self._get_client().delete_object(
                Bucket=self.bucket,
                Key=key,
            )
            logger.info(f"Deleted: {key}")
        except ClientError as e:
            logger.error(f"Failed to delete {key}: {e}")
            raise
