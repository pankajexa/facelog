package com.exargen.facelog.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts camera frames (YUV_420_888) to Bitmap for ML pipeline processing.
 */
@Singleton
class ImagePreprocessor @Inject constructor() {

    /**
     * Convert CameraX ImageProxy (YUV_420_888) to Bitmap.
     */
    fun imageToBitmap(image: Image, rotationDegrees: Int): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null,
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            90,
            out,
        )

        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        return if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    /**
     * Crop a face region from bitmap with padding.
     */
    fun cropFace(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paddingPercent: Float = 0.2f,
    ): Bitmap {
        val w = right - left
        val h = bottom - top
        val padX = w * paddingPercent
        val padY = h * paddingPercent

        val cropLeft = (left - padX).toInt().coerceAtLeast(0)
        val cropTop = (top - padY).toInt().coerceAtLeast(0)
        val cropRight = (right + padX).toInt().coerceAtMost(bitmap.width)
        val cropBottom = (bottom + padY).toInt().coerceAtMost(bitmap.height)

        return Bitmap.createBitmap(
            bitmap,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop,
        )
    }
}
