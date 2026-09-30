package com.exargen.facelog.ui.screen.groupscan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exargen.facelog.ui.theme.ScanningCyan
import com.exargen.facelog.ui.theme.SpoofAlert
import com.exargen.facelog.ui.theme.Success
import com.exargen.facelog.ui.theme.Warning

@Composable
fun GroupTrackingOverlay(
    trackedFaces: List<TrackedFace>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bracketLen = 30f

            for (face in trackedFaces) {
                val color = faceStatusColor(face.status)
                val left = face.box.left * w
                val top = face.box.top * h
                val right = face.box.right * w
                val bottom = face.box.bottom * h
                val boxWidth = right - left
                val boxHeight = bottom - top

                // Box border — thicker for confirmed
                val borderStroke = if (face.status == FaceTrackStatus.CONFIRMED) 3f else 2f
                drawRect(
                    color = color.copy(alpha = if (face.status == FaceTrackStatus.DETECTING) 0.4f else 0.7f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = borderStroke),
                )

                // Corner brackets
                val cornerStroke = if (face.status == FaceTrackStatus.CONFIRMED) 4f else 3f
                val bLen = bracketLen.coerceAtMost(boxWidth * 0.3f).coerceAtMost(boxHeight * 0.3f)

                drawLine(color, Offset(left, top), Offset(left + bLen, top), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(left, top), Offset(left, top + bLen), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(right, top), Offset(right - bLen, top), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(right, top), Offset(right, top + bLen), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(left, bottom), Offset(left + bLen, bottom), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(left, bottom), Offset(left, bottom - bLen), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(right, bottom), Offset(right - bLen, bottom), cornerStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(right, bottom), Offset(right, bottom - bLen), cornerStroke, cap = StrokeCap.Round)

                // Confirmation progress arc (shown during CONFIRMING)
                if (face.status == FaceTrackStatus.CONFIRMING && face.confirmProgress > 0f) {
                    val arcPadding = 6f
                    val arcRect = Size(boxWidth + arcPadding * 2, boxHeight + arcPadding * 2)
                    val arcTopLeft = Offset(left - arcPadding, top - arcPadding)
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * face.confirmProgress,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcRect,
                        style = Stroke(width = 3f, cap = StrokeCap.Round),
                    )
                }

                // Confirmed checkmark circle at bottom-right corner
                if (face.status == FaceTrackStatus.CONFIRMED) {
                    val checkRadius = 10f
                    val cx = right - 2f
                    val cy = bottom - 2f
                    drawCircle(
                        color = Success,
                        radius = checkRadius,
                        center = Offset(cx, cy),
                    )
                    // Simple check mark
                    drawLine(
                        Color.White, Offset(cx - 4f, cy), Offset(cx - 1f, cy + 3f),
                        strokeWidth = 2f, cap = StrokeCap.Round,
                    )
                    drawLine(
                        Color.White, Offset(cx - 1f, cy + 3f), Offset(cx + 5f, cy - 4f),
                        strokeWidth = 2f, cap = StrokeCap.Round,
                    )
                }
            }
        }

        // Name labels above boxes
        for (face in trackedFaces) {
            val label = when (face.status) {
                FaceTrackStatus.CONFIRMED -> face.name ?: "Confirmed"
                FaceTrackStatus.CONFIRMING -> face.name ?: "Identifying..."
                FaceTrackStatus.UNKNOWN -> "Unknown"
                FaceTrackStatus.SPOOF -> "Spoof"
                FaceTrackStatus.DETECTING -> null
            }
            if (label != null) {
                FaceStatusLabel(
                    box = face.box,
                    label = label,
                    color = faceStatusColor(face.status),
                    showCheck = face.attendanceRecorded,
                    showClock = face.alreadyRecorded,
                )
            }
        }
    }
}

private fun faceStatusColor(status: FaceTrackStatus): Color = when (status) {
    FaceTrackStatus.DETECTING -> ScanningCyan
    FaceTrackStatus.CONFIRMING -> Color(0xFFFFC107) // Amber
    FaceTrackStatus.CONFIRMED -> Success
    FaceTrackStatus.UNKNOWN -> Warning
    FaceTrackStatus.SPOOF -> SpoofAlert
}

@Composable
private fun FaceStatusLabel(
    box: com.exargen.facelog.ui.screen.attendance.NormalizedBox,
    label: String,
    color: Color,
    showCheck: Boolean,
    showClock: Boolean,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentWidthPx = with(density) { maxWidth.toPx() }
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val labelOffsetPx = with(density) { 26.dp.toPx() }

        val xPx = box.left * parentWidthPx
        val yPx = (box.top * parentHeightPx - labelOffsetPx).coerceAtLeast(0f)

        val xDp = with(density) { xPx.toDp() }
        val yDp = with(density) { yPx.toDp() }

        val suffix = when {
            showCheck -> " \u2713"
            showClock -> " \u23F0"
            else -> ""
        }

        Text(
            text = "$label$suffix",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .offset(x = xDp, y = yDp)
                .background(
                    color = color.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
