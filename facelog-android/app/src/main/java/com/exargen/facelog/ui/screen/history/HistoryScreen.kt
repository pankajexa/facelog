package com.exargen.facelog.ui.screen.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.exargen.facelog.ui.component.FaceLogTopBar
import com.exargen.facelog.ui.theme.OnBackground
import com.exargen.facelog.ui.theme.Success
import com.exargen.facelog.ui.theme.SuccessLight
import com.exargen.facelog.ui.theme.TextSecondary
import com.exargen.facelog.ui.theme.Warning
import com.exargen.facelog.ui.theme.WarningLight
import java.io.File

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val records by viewModel.records.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        FaceLogTopBar(title = "Attendance History", onNavigateBack = onNavigateBack)

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No attendance records yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records) { record ->
                    HistoryRecordCard(record)
                }
            }
        }
    }
}

@Composable
fun HistoryRecordCard(record: HistoryRecordDisplay) {
    val isCheckIn = record.type == "CHECK_IN"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCheckIn) SuccessLight else WarningLight,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Photo thumbnail or icon
                val photoBitmap = record.photoPath?.let { path ->
                    remember(path) {
                        if (File(path).exists()) {
                            BitmapFactory.decodeFile(path)?.asImageBitmap()
                        } else null
                    }
                }

                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = "Attendance photo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = if (isCheckIn) Icons.Default.Login else Icons.Default.Logout,
                        contentDescription = record.type,
                        tint = if (isCheckIn) Success else Warning,
                    )
                }

                Column {
                    Text(
                        text = record.personName,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnBackground,
                    )
                    Text(
                        text = if (isCheckIn) "Check In" else "Check Out",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = record.time,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                )
                Text(
                    text = record.date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (record.locationName != null || (record.latitude != null && record.longitude != null)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = record.locationName
                                ?: "%.4f, %.4f".format(record.latitude, record.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

data class HistoryRecordDisplay(
    val personName: String,
    val type: String,
    val time: String,
    val date: String,
    val confidence: Float,
    val photoPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
)
