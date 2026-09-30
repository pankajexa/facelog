package com.exargen.facelog.ui.screen.groupscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.camera.core.CameraSelector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.exargen.facelog.domain.model.AttendanceType
import com.exargen.facelog.ui.component.FaceLogTopBar
import com.exargen.facelog.ui.screen.attendance.CameraPreview
import com.exargen.facelog.ui.theme.SpoofAlert
import com.exargen.facelog.ui.theme.Success
import com.exargen.facelog.ui.theme.TextSecondary
import com.exargen.facelog.ui.theme.Warning

@Composable
fun GroupScanScreen(
    @Suppress("UNUSED_PARAMETER") attendanceType: String,
    onNavigateBack: () -> Unit,
    viewModel: GroupScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    val isCheckIn = uiState.attendanceType == AttendanceType.CHECK_IN
    val headerColor = if (isCheckIn) Color(0xFF4CAF50) else Color(0xFFFF9800)
    val headerText = if (isCheckIn) "Group Check In" else "Group Check Out"

    // Haptic feedback
    LaunchedEffect(Unit) {
        viewModel.hapticEvent.collect { event ->
            when (event) {
                GroupHapticEvent.FACE_CONFIRMED -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    kotlinx.coroutines.delay(100)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                GroupHapticEvent.ALREADY_RECORDED -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                GroupHapticEvent.SPOOF_DETECTED -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                GroupHapticEvent.ERROR -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    // Error snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FaceLogTopBar(
            title = headerText,
            onNavigateBack = onNavigateBack,
            containerColor = headerColor,
            contentColor = Color.White,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CameraPreview(
                onFrameAvailable = viewModel::onPreviewFrame,
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
            )

            // Live face tracking overlay
            GroupTrackingOverlay(trackedFaces = uiState.trackedFaces)

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

            // Scanning indicator badge (top-center)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = headerColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scanning",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            // Recorded count badge (top-right)
            if (uiState.totalRecorded > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
                    color = Success.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${uiState.totalRecorded} recorded",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Guide text when no faces detected
            if (uiState.trackedFaces.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Point camera at the group",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Faces are identified automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Bottom confirmed results panel
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.confirmedResults.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            ) {
                ConfirmedResultsCard(
                    results = uiState.confirmedResults,
                    accentColor = headerColor,
                    attendanceType = uiState.attendanceType,
                )
            }

            // Snackbar
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ConfirmedResultsCard(
    results: List<ConfirmedResult>,
    accentColor: Color,
    attendanceType: AttendanceType,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(accentColor, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${results.count { !it.alreadyRecorded }} Recorded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    color = accentColor,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = if (attendanceType == AttendanceType.CHECK_IN) "CHECK IN" else "CHECK OUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Result list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (results.size > 3) {
                            it.height(130.dp).verticalScroll(rememberScrollState())
                        } else it
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (result in results) {
                    ConfirmedResultRow(result)
                }
            }
        }
    }
}

@Composable
private fun ConfirmedResultRow(result: ConfirmedResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (result.alreadyRecorded) Color(0xFFF5F5F5) else Success.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (result.alreadyRecorded) Icons.Default.Schedule else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (result.alreadyRecorded) TextSecondary else Success,
            modifier = Modifier.size(18.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.personName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (result.alreadyRecorded) {
                Text(
                    text = "Already recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        Text(
            text = result.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}
