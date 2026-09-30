package com.exargen.facelog.ui.screen.enrollment

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.exargen.facelog.ml.FaceGuideState
import com.exargen.facelog.ui.theme.GuideCaptured
import com.exargen.facelog.ui.theme.GuideError
import com.exargen.facelog.ui.theme.GuideReady
import com.exargen.facelog.ui.theme.GuideWarning
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FaceScanOverlay(
    guideState: FaceGuideState,
    currentAngle: FaceScanAngle,
    currentAngleIndex: Int,
    capturedCount: Int,
    totalAngles: Int,
    alignProgress: Float,
    qualityScore: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanOverlay")

    // Breathing animation for NO_FACE state
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingAlpha",
    )

    // Pulsing arrow animation (1200ms cycle)
    val arrowPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "arrowPulse",
    )

    // Smooth border color transition
    val borderColor = when (guideState) {
        FaceGuideState.NO_FACE -> Color.White.copy(alpha = breathingAlpha)
        FaceGuideState.FACE_TOO_FAR, FaceGuideState.FACE_TOO_CLOSE -> GuideWarning
        FaceGuideState.FACE_OFF_CENTER -> GuideWarning
        FaceGuideState.WRONG_ANGLE -> GuideError.copy(alpha = 0.8f)
        FaceGuideState.LOW_QUALITY -> GuideWarning
        FaceGuideState.ALIGNING -> GuideReady
        FaceGuideState.CAPTURED -> GuideCaptured
    }

    val animatedBorderColor by animateFloatAsState(
        targetValue = if (guideState == FaceGuideState.CAPTURED) 1f else 0f,
        animationSpec = spring(stiffness = 300f),
        label = "capturedBounce",
    )

    // Scale bounce on capture (to 103%)
    val ovalScale = if (guideState == FaceGuideState.CAPTURED) 1.03f else 1.0f
    val animatedScale by animateFloatAsState(
        targetValue = ovalScale,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "ovalScale",
    )

    // Progress arc for aligning
    val animatedProgress by animateFloatAsState(
        targetValue = alignProgress,
        animationSpec = tween(200),
        label = "alignProgress",
    )

    // Animated quality score for smooth bar transitions
    val animatedQuality by animateFloatAsState(
        targetValue = qualityScore,
        animationSpec = tween(300),
        label = "qualityScore",
    )

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { alpha = 0.99f }) {
        val ovalWidth = size.width * 0.55f * animatedScale
        val ovalHeight = size.height * 0.45f * animatedScale
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val left = centerX - ovalWidth / 2f
        val top = centerY - ovalHeight / 2f

        // 1. Dark vignette outside oval cutout
        val ovalPath = Path().apply {
            addOval(Rect(left, top, left + ovalWidth, top + ovalHeight))
        }
        // Draw semi-transparent dark overlay
        drawRect(color = Color.Black.copy(alpha = 0.40f), size = size)
        // Cut out the oval
        clipPath(ovalPath, clipOp = androidx.compose.ui.graphics.ClipOp.Intersect) {
            drawRect(color = Color.Black, size = size, blendMode = BlendMode.Clear)
        }

        // 2. Oval border
        drawOval(
            color = borderColor,
            topLeft = Offset(left, top),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 4f),
        )

        // 3. Progress segments (5 segments around oval, 68 degrees each, 4 degree gaps)
        val segmentSweep = 68f
        val gapDegrees = 4f
        val startOffset = -90f // start at top
        for (i in 0 until totalAngles) {
            val segStart = startOffset + i * (segmentSweep + gapDegrees)
            val isCaptured = i < capturedCount
            val isCurrent = i == currentAngleIndex

            val segColor = when {
                isCaptured -> GuideCaptured
                isCurrent && guideState == FaceGuideState.ALIGNING -> GuideReady.copy(alpha = 0.8f)
                isCurrent -> Color.White.copy(alpha = 0.4f)
                else -> Color.White.copy(alpha = 0.15f)
            }

            // Draw segment arc slightly outside the oval
            val arcPad = 10f
            drawArc(
                color = segColor,
                startAngle = segStart,
                sweepAngle = if (isCurrent && guideState == FaceGuideState.ALIGNING)
                    segmentSweep * animatedProgress
                else
                    segmentSweep,
                useCenter = false,
                topLeft = Offset(left - arcPad, top - arcPad),
                size = Size(ovalWidth + arcPad * 2, ovalHeight + arcPad * 2),
                style = Stroke(width = if (isCaptured || isCurrent) 6f else 3f),
            )
        }

        // 4. Directional arrows for LEFT/RIGHT/UP/DOWN angles
        if (guideState == FaceGuideState.WRONG_ANGLE || guideState == FaceGuideState.NO_FACE) {
            drawDirectionalArrows(
                currentAngle = currentAngle,
                centerX = centerX,
                centerY = centerY,
                ovalWidth = ovalWidth,
                ovalHeight = ovalHeight,
                pulse = arrowPulse,
            )
        }

        // 5. Size guidance arrows (inward/outward)
        if (guideState == FaceGuideState.FACE_TOO_FAR) {
            drawSizeArrows(centerX, centerY, ovalWidth, ovalHeight, inward = true, pulse = arrowPulse)
        } else if (guideState == FaceGuideState.FACE_TOO_CLOSE) {
            drawSizeArrows(centerX, centerY, ovalWidth, ovalHeight, inward = false, pulse = arrowPulse)
        }

        // 6. Captured checkmark inside oval
        if (guideState == FaceGuideState.CAPTURED) {
            drawCheckmark(centerX, centerY)
        }

        // 7. Quality indicator bar below oval (only during ALIGNING or LOW_QUALITY)
        if (guideState == FaceGuideState.ALIGNING || guideState == FaceGuideState.LOW_QUALITY) {
            drawQualityBar(
                centerX = centerX,
                ovalBottom = top + ovalHeight,
                totalWidth = size.width,
                quality = animatedQuality,
            )
        }
    }
}

private fun DrawScope.drawQualityBar(
    centerX: Float,
    ovalBottom: Float,
    totalWidth: Float,
    quality: Float,
) {
    val barWidth = totalWidth * 0.40f
    val barHeight = 4f * density
    val barTop = ovalBottom + 16f * density
    val barLeft = centerX - barWidth / 2f
    val cornerRadius = barHeight / 2f

    // Background track
    val bgPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = barLeft,
                top = barTop,
                right = barLeft + barWidth,
                bottom = barTop + barHeight,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            )
        )
    }
    drawPath(bgPath, color = Color.White.copy(alpha = 0.2f))

    // Filled portion
    val fillWidth = barWidth * quality.coerceIn(0f, 1f)
    if (fillWidth > 0f) {
        val fillColor = when {
            quality < 0.30f -> GuideError
            quality < 0.60f -> GuideWarning
            else -> GuideReady
        }

        val fillPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = barLeft,
                    top = barTop,
                    right = barLeft + fillWidth,
                    bottom = barTop + barHeight,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            )
        }
        drawPath(fillPath, color = fillColor)
    }
}

private fun DrawScope.drawDirectionalArrows(
    currentAngle: FaceScanAngle,
    centerX: Float,
    centerY: Float,
    ovalWidth: Float,
    ovalHeight: Float,
    pulse: Float,
) {
    val arrowColor = Color.White.copy(alpha = 0.5f + pulse * 0.3f)
    val arrowSize = 24f
    val offset = 20f + pulse * 8f // pulsing offset

    when (currentAngle) {
        FaceScanAngle.LEFT -> {
            // Arrow pointing left near left edge of oval
            val x = centerX - ovalWidth / 2f - offset
            val y = centerY
            drawChevron(x, y, arrowSize, arrowColor, direction = 180f)
        }
        FaceScanAngle.RIGHT -> {
            val x = centerX + ovalWidth / 2f + offset
            val y = centerY
            drawChevron(x, y, arrowSize, arrowColor, direction = 0f)
        }
        FaceScanAngle.UP -> {
            val x = centerX
            val y = centerY - ovalHeight / 2f - offset
            drawChevron(x, y, arrowSize, arrowColor, direction = 270f)
        }
        FaceScanAngle.DOWN -> {
            val x = centerX
            val y = centerY + ovalHeight / 2f + offset
            drawChevron(x, y, arrowSize, arrowColor, direction = 90f)
        }
        FaceScanAngle.CENTER -> { /* no directional arrow for center */ }
    }
}

private fun DrawScope.drawChevron(
    x: Float,
    y: Float,
    size: Float,
    color: Color,
    direction: Float,
) {
    val rad = Math.toRadians(direction.toDouble())
    val perpRad = rad + Math.PI / 2

    // Chevron tip
    val tipX = x + (cos(rad) * size).toFloat()
    val tipY = y + (sin(rad) * size).toFloat()

    // Chevron wings
    val wingLen = size * 0.7f
    val w1x = x + (cos(perpRad) * wingLen).toFloat()
    val w1y = y + (sin(perpRad) * wingLen).toFloat()
    val w2x = x - (cos(perpRad) * wingLen).toFloat()
    val w2y = y - (sin(perpRad) * wingLen).toFloat()

    drawLine(color, Offset(w1x, w1y), Offset(tipX, tipY), strokeWidth = 3f)
    drawLine(color, Offset(w2x, w2y), Offset(tipX, tipY), strokeWidth = 3f)
}

private fun DrawScope.drawSizeArrows(
    centerX: Float,
    centerY: Float,
    ovalWidth: Float,
    ovalHeight: Float,
    inward: Boolean,
    pulse: Float,
) {
    val arrowColor = GuideWarning.copy(alpha = 0.6f + pulse * 0.3f)
    val offset = if (inward) 30f + pulse * 10f else 20f + pulse * 10f

    // 4 arrows at cardinal points
    val points = listOf(
        Pair(centerX - ovalWidth / 2f - offset, centerY), // left
        Pair(centerX + ovalWidth / 2f + offset, centerY), // right
        Pair(centerX, centerY - ovalHeight / 2f - offset), // top
        Pair(centerX, centerY + ovalHeight / 2f + offset), // bottom
    )
    val directions = if (inward) listOf(0f, 180f, 90f, 270f) else listOf(180f, 0f, 270f, 90f)

    points.forEachIndexed { i, (x, y) ->
        drawChevron(x, y, 16f, arrowColor, directions[i])
    }
}

private fun DrawScope.drawCheckmark(centerX: Float, centerY: Float) {
    val checkColor = GuideCaptured
    val path = Path().apply {
        moveTo(centerX - 20f, centerY)
        lineTo(centerX - 5f, centerY + 15f)
        lineTo(centerX + 25f, centerY - 15f)
    }
    drawPath(
        path = path,
        color = checkColor,
        style = Stroke(width = 5f),
    )
}
