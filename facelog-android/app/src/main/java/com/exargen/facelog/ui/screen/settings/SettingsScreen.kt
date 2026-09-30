package com.exargen.facelog.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.exargen.facelog.ui.component.FaceLogTopBar
import com.exargen.facelog.ui.theme.Error
import com.exargen.facelog.ui.theme.OnBackground
import com.exargen.facelog.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        FaceLogTopBar(title = "Settings", onNavigateBack = onNavigateBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Enrollment info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enrollment",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow("Enrolled Persons", "${uiState.enrolledCount}/${uiState.maxPersons}")
                    SettingsRow("Embeddings per Person", "5 (multi-angle)")
                }
            }

            // Recognition settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recognition",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Threshold: ${"%.2f".format(uiState.recognitionThreshold)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnBackground,
                    )
                    Slider(
                        value = uiState.recognitionThreshold,
                        onValueChange = viewModel::onThresholdChanged,
                        valueRange = 0.40f..0.80f,
                        steps = 7,
                    )
                    Text(
                        text = "Lower = more lenient, Higher = more strict",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            // App info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Info",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow("Version", uiState.appVersion)
                    SettingsRow("Mode", "On-Device Prototype")
                }
            }

            // Danger zone
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Management",
                        style = MaterialTheme.typography.titleLarge,
                        color = Error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::showClearConfirmation,
                        colors = ButtonDefaults.buttonColors(containerColor = Error),
                    ) {
                        Text("Clear All Data")
                    }
                }
            }
        }
    }

    // Confirmation dialog
    if (uiState.clearConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearConfirmation,
            title = { Text("Clear All Data?") },
            text = { Text("This will delete all enrolled persons and attendance records. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::clearAllData) {
                    Text("Delete All", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearConfirmation) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = OnBackground,
        )
    }
}
