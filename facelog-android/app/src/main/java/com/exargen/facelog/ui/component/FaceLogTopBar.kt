package com.exargen.facelog.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.exargen.facelog.ui.theme.OnPrimary
import com.exargen.facelog.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceLogTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    containerColor: Color = Primary,
    contentColor: Color = OnPrimary,
) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
        )
    )
}
