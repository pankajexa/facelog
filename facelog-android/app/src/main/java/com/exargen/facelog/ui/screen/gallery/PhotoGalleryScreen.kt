package com.exargen.facelog.ui.screen.gallery

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.exargen.facelog.ui.component.FaceLogTopBar
import com.exargen.facelog.ui.theme.Primary
import com.exargen.facelog.ui.theme.Success
import com.exargen.facelog.ui.theme.SuccessLight
import com.exargen.facelog.ui.theme.TextSecondary
import com.exargen.facelog.ui.theme.Warning
import com.exargen.facelog.ui.theme.WarningLight

@Composable
fun PhotoGalleryScreen(
    viewModel: PhotoGalleryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsState()

    BackHandler {
        if (!viewModel.onBackPressed()) {
            onNavigateBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val title = when (val state = viewState) {
            is GalleryViewState.DateList -> "Photo Gallery"
            is GalleryViewState.PhotoGrid -> state.dateLabel
        }
        FaceLogTopBar(
            title = title,
            onNavigateBack = {
                if (!viewModel.onBackPressed()) {
                    onNavigateBack()
                }
            },
        )

        when (val state = viewState) {
            is GalleryViewState.DateList -> DateListView(
                dates = state.dates,
                onDateSelected = viewModel::onDateSelected,
            )
            is GalleryViewState.PhotoGrid -> PhotoGridView(photos = state.photos)
        }
    }
}

@Composable
private fun DateListView(
    dates: List<DateGroup>,
    onDateSelected: (DateGroup) -> Unit,
) {
    if (dates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No photos yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Text(
                    text = "Photos will appear here after attendance",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(dates) { dateGroup ->
                DateCard(dateGroup = dateGroup, onClick = { onDateSelected(dateGroup) })
            }
        }
    }
}

@Composable
private fun DateCard(
    dateGroup: DateGroup,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateGroup.dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${dateGroup.photoCount} photo${if (dateGroup.photoCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PhotoGridView(photos: List<PhotoItem>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos) { photo ->
            PhotoCell(photo = photo)
        }
    }
}

@Composable
private fun PhotoCell(photo: PhotoItem) {
    val isCheckIn = photo.attendanceType == "CHECK_IN"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Photo thumbnail
            val bitmap = remember(photo.photoPath) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(photo.photoPath, opts)?.asImageBitmap()
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Photo of ${photo.personName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Info below photo
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = photo.personName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = photo.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCheckIn) SuccessLight else WarningLight,
                    ) {
                        Text(
                            text = if (isCheckIn) "IN" else "OUT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCheckIn) Success else Warning,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (photo.locationName != null || (photo.latitude != null && photo.longitude != null)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = TextSecondary,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = photo.locationName
                                ?: "%.4f, %.4f".format(photo.latitude, photo.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
