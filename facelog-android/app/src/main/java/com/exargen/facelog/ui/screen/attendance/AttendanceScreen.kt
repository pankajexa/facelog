package com.exargen.facelog.ui.screen.attendance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.exargen.facelog.domain.model.AttendanceType
import com.exargen.facelog.ui.component.FaceLogTopBar
import com.exargen.facelog.ui.theme.ConfirmingGreen
import com.exargen.facelog.ui.theme.GuideReady
import com.exargen.facelog.ui.theme.OnBackground
import com.exargen.facelog.ui.theme.ScanningCyan
import com.exargen.facelog.ui.theme.SpoofAlert
import com.exargen.facelog.ui.theme.Success
import com.exargen.facelog.ui.theme.SuccessLight
import com.exargen.facelog.ui.theme.TextSecondary
import com.exargen.facelog.ui.theme.Warning
import com.exargen.facelog.ui.theme.WarningLight
import kotlin.math.sin

@Composable
fun AttendanceScreen(
    attendanceType: String,
    onNavigateBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    val isCheckIn = uiState.attendanceType == AttendanceType.CHECK_IN
    val headerColor = if (isCheckIn) Color(0xFF4CAF50) else Color(0xFFFF9800)
    val headerText = if (isCheckIn) "Checking In" else "Checking Out"

    // Haptic feedback
    LaunchedEffect(Unit) {
        viewModel.hapticEvent.collect { event ->
            when (event) {
                HapticEvent.FACE_DETECTED -> hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.SUCCESS -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    kotlinx.coroutines.delay(130)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                HapticEvent.NOT_RECOGNIZED -> hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.SPOOF -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    kotlinx.coroutines.delay(100)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    kotlinx.coroutines.delay(100)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                HapticEvent.TAP_DISMISS -> hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        FaceLogTopBar(
            title = headerText,
            onNavigateBack = onNavigateBack,
            containerColor = headerColor,
            contentColor = Color.White,
        )

        // Camera + overlays
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CameraPreview(
                onFrameAvailable = viewModel::onFrameAvailable,
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
            )

            val scanState = uiState.scanState

            // Face guide when idle (no face detected, no result showing)
            if (scanState is ScanState.Idle) {
                FaceGuideOverlay(modifier = Modifier.fillMaxSize())
            }

            // Camera switch button
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                        CameraSelector.LENS_FACING_BACK
                    else
                        CameraSelector.LENS_FACING_FRONT
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Session counter pill
            if (uiState.scanCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "${uiState.scanCount} scanned",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Result cards
            androidx.compose.animation.AnimatedVisibility(
                visible = scanState is ScanState.Success,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                if (scanState is ScanState.Success) {
                    SuccessCard(
                        name = scanState.name,
                        label = scanState.label,
                        timestamp = scanState.timestamp,
                        confidence = scanState.confidence,
                        onDismiss = viewModel::onDismissResult,
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = scanState is ScanState.Rejected,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                RejectedCard(onDismiss = viewModel::onDismissResult)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = scanState is ScanState.AlreadyRecorded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                if (scanState is ScanState.AlreadyRecorded) {
                    AlreadyRecordedCard(
                        name = scanState.name,
                        label = scanState.label,
                        onDismiss = viewModel::onDismissResult,
                    )
                }
            }

            // Spoof alert
            androidx.compose.animation.AnimatedVisibility(
                visible = scanState is ScanState.Spoof,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                SpoofAlertOverlay()
            }
        }
    }
}

@Composable
private fun ScanLabel(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.padding(top = 60.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

// --- Result Cards ---

@Composable
fun SuccessCard(
    name: String,
    label: String,
    timestamp: String,
    confidence: Float,
    onDismiss: () -> Unit,
) {
    // Animated checkmark circle
    val checkScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        checkScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f))
    }

    // Countdown progress (2.5s)
    var countdownProgress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val duration = 2500L
        while (countdownProgress > 0f) {
            val elapsed = System.currentTimeMillis() - startTime
            countdownProgress = (1f - elapsed.toFloat() / duration).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(16) // ~60fps
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = Success,
                modifier = Modifier.size((48 * checkScale.value).dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                color = OnBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Success,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Countdown bar
            LinearProgressIndicator(
                progress = { countdownProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Success,
                trackColor = Success.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
fun RejectedCard(onDismiss: () -> Unit) {
    // Shake animation
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Decaying oscillation: 400ms
        val amplitude = 12f
        val duration = 400L
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < duration) {
            val elapsed = (System.currentTimeMillis() - startTime).toFloat()
            val progress = elapsed / duration
            val decay = 1f - progress
            val value = sin(progress * 6 * Math.PI).toFloat() * amplitude * decay
            shakeOffset.snapTo(value)
            kotlinx.coroutines.delay(16)
        }
        shakeOffset.snapTo(0f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .offset { IntOffset(shakeOffset.value.toInt(), 0) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarningLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = "Not recognized",
                tint = Warning,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Not Recognized",
                style = MaterialTheme.typography.headlineMedium,
                color = OnBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "This person is not enrolled",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AlreadyRecordedCard(
    name: String,
    label: String,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(32.dp),
                )
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$name",
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SpoofAlertOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpoofAlert.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Spoof detected",
                tint = Color.White,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Spoof Detected",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please present your real face to the camera",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun FaceGuideOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "faceGuide")

    // Pulsing oval border
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { alpha = 0.99f }) {
        val ovalWidth = size.width * 0.50f
        val ovalHeight = size.height * 0.38f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val left = centerX - ovalWidth / 2f
        val top = centerY - ovalHeight / 2f

        // Dark overlay with oval cutout (spotlight)
        val ovalPath = Path().apply {
            addOval(Rect(left, top, left + ovalWidth, top + ovalHeight))
        }
        drawRect(color = Color.Black.copy(alpha = 0.40f), size = size)
        clipPath(ovalPath, clipOp = androidx.compose.ui.graphics.ClipOp.Intersect) {
            drawRect(color = Color.Black, size = size, blendMode = BlendMode.Clear)
        }

        // Dashed pulsing white oval border
        drawOval(
            color = Color.White.copy(alpha = pulseAlpha),
            topLeft = Offset(left, top),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f),
            ),
        )

        // Four corner brackets (30dp = ~30 pixels)
        val bracketLen = 30f
        val bracketWidth = 3f
        val bracketColor = Color.White.copy(alpha = 0.8f)
        val pad = 4f

        // Top-left
        drawLine(bracketColor, Offset(left - pad, top - pad), Offset(left - pad + bracketLen, top - pad), bracketWidth)
        drawLine(bracketColor, Offset(left - pad, top - pad), Offset(left - pad, top - pad + bracketLen), bracketWidth)
        // Top-right
        drawLine(bracketColor, Offset(left + ovalWidth + pad, top - pad), Offset(left + ovalWidth + pad - bracketLen, top - pad), bracketWidth)
        drawLine(bracketColor, Offset(left + ovalWidth + pad, top - pad), Offset(left + ovalWidth + pad, top - pad + bracketLen), bracketWidth)
        // Bottom-left
        drawLine(bracketColor, Offset(left - pad, top + ovalHeight + pad), Offset(left - pad + bracketLen, top + ovalHeight + pad), bracketWidth)
        drawLine(bracketColor, Offset(left - pad, top + ovalHeight + pad), Offset(left - pad, top + ovalHeight + pad - bracketLen), bracketWidth)
        // Bottom-right
        drawLine(bracketColor, Offset(left + ovalWidth + pad, top + ovalHeight + pad), Offset(left + ovalWidth + pad - bracketLen, top + ovalHeight + pad), bracketWidth)
        drawLine(bracketColor, Offset(left + ovalWidth + pad, top + ovalHeight + pad), Offset(left + ovalWidth + pad, top + ovalHeight + pad - bracketLen), bracketWidth)
    }

    // Text below oval
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Spacer to push text below the oval area
            Spacer(modifier = Modifier.height(200.dp))
            Text(
                text = "Position your face in the frame",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
