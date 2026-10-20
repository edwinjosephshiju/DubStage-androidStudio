package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DubStageDarkColorScheme = darkColorScheme(
    primary = StudioAccent,
    onPrimary = Color.White,
    primaryContainer = StudioElevated,
    onPrimaryContainer = TextHighEmphasis,
    secondary = StemUserTake,
    onSecondary = Color.Black,
    secondaryContainer = StudioContainer,
    onSecondaryContainer = TextHighEmphasis,
    tertiary = StemVocal,
    onTertiary = Color.White,
    background = StudioCanvas,
    onBackground = TextHighEmphasis,
    surface = StudioContainer,
    onSurface = TextHighEmphasis,
    surfaceVariant = StudioElevated,
    onSurfaceVariant = TextMediumEmphasis,
    outline = StudioBorder,
    outlineVariant = StudioBorderBright,
    error = StatusRecording,
    onError = Color.White
)

@Composable
fun DubStageTheme(
    content: @Composable () -> Unit
) {
    // Studio DAW runs in high-contrast professional dark studio theme
    MaterialTheme(
        colorScheme = DubStageDarkColorScheme,
        typography = Typography,
        content = content
    )
}
