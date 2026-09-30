package com.exargen.facelog.ui.screen.attendance

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.exargen.facelog.ui.theme.ConfirmingGreen
import com.exargen.facelog.ui.theme.ScanningCyan

/**
 * Scanning overlay for attendance.
 * - Detecting: cyan bbox with sweep line + "Scanning..."
 * - Confirming: cyan->green transition, progress arc, "Verifying..."
 */
@Composable
fun ScanningOverlay(
    box: NormalizedBox,
    isConfirming: Boolean,
    confirmProgress: Float, // 0-1 for confirming
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")

    // Sweep line animation (800ms)
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    // Border pulse for confirming
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "borderPulse",
    )

    val boxColor = if (isConfirming) ConfirmingGreen else ScanningCyan

    Canvas(modifier = modifier.fillMaxSize()) {
        val left = box.left * size.width
        val top = box.top * size.height
        val right = box.right * size.width
        val bottom = box.bottom * size.height
        val boxWidth = right - left
        val boxHeight = bottom - top

        val strokeWidth = if (isConfirming) 4f * borderPulse else 3f

        // Rounded bbox
        drawRect(
            color = boxColor,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            style = Stroke(width = strokeWidth),
        )

        // Corner accents
        val cornerLength = 24f
        val cornerWidth = 5f
        // Top-left
        drawLine(boxColor, Offset(left, top), Offset(left + cornerLength, top), cornerWidth)
        drawLine(boxColor, Offset(left, top), Offset(left, top + cornerLength), cornerWidth)
        // Top-right
        drawLine(boxColor, Offset(right, top), Offset(right - cornerLength, top), cornerWidth)
        drawLine(boxColor, Offset(right, top), Offset(right, top + cornerLength), cornerWidth)
        // Bottom-left
        drawLine(boxColor, Offset(left, bottom), Offset(left + cornerLength, bottom), cornerWidth)
        drawLine(boxColor, Offset(left, bottom), Offset(left, bottom - cornerLength), cornerWidth)
        // Bottom-right
        drawLine(boxColor, Offset(right, bottom), Offset(right - cornerLength, bottom), cornerWidth)
        drawLine(boxColor, Offset(right, bottom), Offset(right, bottom - cornerLength), cornerWidth)

        if (!isConfirming) {
            // Sweep line scanning effect
            val sweepY = top + boxHeight * sweepProgress
            drawLine(
                color = ScanningCyan.copy(alpha = 0.6f),
                start = Offset(left, sweepY),
                end = Offset(right, sweepY),
                strokeWidth = 2f,
            )
        }

        if (isConfirming) {
            // Progress arc around the box
            val arcPad = 8f
            drawArc(
                color = ConfirmingGreen,
                startAngle = -90f,
                sweepAngle = 360f * confirmProgress,
                useCenter = false,
                topLeft = Offset(left - arcPad, top - arcPad),
                size = Size(boxWidth + arcPad * 2, boxHeight + arcPad * 2),
                style = Stroke(width = 4f),
            )
        }
    }
}
