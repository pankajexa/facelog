package com.exargen.facelog.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val FaceLogColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryLight,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryLight,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    error = Error,
    surfaceVariant = SurfaceVariant
)

@Composable
fun FaceLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FaceLogColorScheme,
        typography = FaceLogTypography,
        content = content
    )
}
