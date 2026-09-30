package com.exargen.facelog.ui.screen.enrollment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.exargen.facelog.ml.FaceGuideState
import com.exargen.facelog.ui.component.FaceLogTopBar
import com.exargen.facelog.ui.screen.attendance.CameraPreview
import com.exargen.facelog.ui.theme.Background
import com.exargen.facelog.ui.theme.Error
import com.exargen.facelog.ui.theme.ErrorLight
import com.exargen.facelog.ui.theme.GuideCaptured
import com.exargen.facelog.ui.theme.GuideReady
import com.exargen.facelog.ui.theme.OnBackground
import com.exargen.facelog.ui.theme.OnPrimary
import com.exargen.facelog.ui.theme.Primary
import com.exargen.facelog.ui.theme.Success
import com.exargen.facelog.ui.theme.SuccessLight
import com.exargen.facelog.ui.theme.Surface
import com.exargen.facelog.ui.theme.TextSecondary

@Composable
fun EnrollmentScreen(
    onNavigateBack: () -> Unit,
    viewModel: EnrollmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        FaceLogTopBar(
            title = "Enroll Person",
            onNavigateBack = {
                if (uiState.phase == EnrollmentPhase.NAME_ENTRY || uiState.phase == EnrollmentPhase.SUCCESS) {
                    onNavigateBack()
                } else {
                    viewModel.backToNameEntry()
                }
            },
        )

        AnimatedContent(
            targetState = uiState.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "enrollment_phase",
        ) { phase ->
            when (phase) {
                EnrollmentPhase.NAME_ENTRY -> NameEntryPhase(
                    uiState = uiState,
                    onNameChanged = viewModel::onNameChanged,
                    onProceed = viewModel::proceedToFaceScan,
                )
                EnrollmentPhase.FACE_SCAN -> FaceScanPhase(
                    uiState = uiState,
                    onFrameAvailable = viewModel::onFrameAvailable,
                    onBack = viewModel::backToNameEntry,
                    viewModel = viewModel,
                )
                EnrollmentPhase.PROCESSING -> ProcessingPhase()
                EnrollmentPhase.SUCCESS -> SuccessPhase(
                    name = uiState.name,
                    onEnrollAnother = viewModel::enrollAnother,
                    onDone = onNavigateBack,
                )
            }
        }
    }
}

@Composable
private fun NameEntryPhase(
    uiState: EnrollmentUiState,
    onNameChanged: (String) -> Unit,
    onProceed: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "New Person",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground,
                    )

                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = onNameChanged,
                        label = { Text("Person Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            focusedLabelColor = Primary,
                            cursorColor = Primary,
                        ),
                    )

                    Button(
                        onClick = onProceed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = uiState.canEnroll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Next: Scan Face (5 angles)")
                    }
                }
            }
        }

        // Status message
        if (uiState.statusMessage != null) {
            item {
                StatusMessage(
                    message = uiState.statusMessage,
                    isError = uiState.isError,
                )
            }
        }

        // Enrolled persons
        if (uiState.enrolledPersonNames.isNotEmpty()) {
            item {
                Text(
                    text = "Enrolled Persons (${uiState.enrolledCount})",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBackground,
                )
            }

            items(uiState.enrolledPersonNames) { name ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Enrolled",
                            tint = Success,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceScanPhase(
    uiState: EnrollmentUiState,
    onFrameAvailable: (android.graphics.Bitmap) -> Unit,
    onBack: () -> Unit,
    viewModel: EnrollmentViewModel,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Haptic on capture
    LaunchedEffect(Unit) {
        viewModel.hapticEvent.collect {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CameraPreview(
                onFrameAvailable = onFrameAvailable,
                modifier = Modifier.fillMaxSize(),
            )

            // Rich scan overlay with guide state
            FaceScanOverlay(
                guideState = uiState.guideState,
                currentAngle = uiState.currentAngle,
                currentAngleIndex = uiState.currentAngleIndex,
                capturedCount = uiState.capturedCount,
                totalAngles = uiState.totalAngles,
                alignProgress = uiState.alignProgress,
                qualityScore = uiState.qualityScore,
                modifier = Modifier.fillMaxSize(),
            )

            // White flash overlay on capture
            if (uiState.showCaptureFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f)),
                )
            }

            // Top info bar with angle chips
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Enrolling: ${uiState.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )

                    // Angle label chips
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FaceScanAngle.entries.forEachIndexed { index, angle ->
                            val isCaptured = index < uiState.capturedCount
                            val isCurrent = index == uiState.currentAngleIndex

                            val chipBg = when {
                                isCaptured -> GuideCaptured.copy(alpha = 0.8f)
                                isCurrent -> Color.White.copy(alpha = 0.2f)
                                else -> Color.Transparent
                            }
                            val chipTextColor = when {
                                isCaptured -> Color.White
                                isCurrent -> Color.White
                                else -> Color.White.copy(alpha = 0.4f)
                            }

                            Surface(
                                color = chipBg,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = if (isCaptured) "${angle.label.uppercase()} \u2713"
                                    else angle.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chipTextColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            // Instruction text inside camera area, below oval, with drop shadow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val textColor by animateColorAsState(
                    targetValue = when (uiState.guideState) {
                        FaceGuideState.ALIGNING -> GuideReady
                        FaceGuideState.CAPTURED -> GuideCaptured
                        FaceGuideState.WRONG_ANGLE -> Color(0xFFEF5350)
                        else -> Color.White
                    },
                    animationSpec = tween(200),
                    label = "instructionColor",
                )
                Text(
                    text = uiState.feedbackMessage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }

            // Status message overlay for errors
            if (uiState.statusMessage != null && uiState.isError) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    color = ErrorLight,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Error,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Bottom bar - simplified to cancel only
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ProcessingPhase() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = Primary,
                strokeWidth = 4.dp,
            )
            Text(
                text = "Processing...",
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground,
            )
            Text(
                text = "Saving face embeddings",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SuccessPhase(
    name: String,
    onEnrollAnother: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(80.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Enrollment Complete!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$name has been enrolled with 5 face angles.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onEnrollAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enroll Another Person")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean,
) {
    val containerColor = if (isError) ErrorLight else SuccessLight
    val contentColor = if (isError) Error else Success
    val icon = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        }
    }
}
