package com.lethe.mediaplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF7C4DFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF14141A),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFB8B8C0)
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White
)

@Composable
fun LetheMediaPlayerTheme(
    darkTheme: Boolean = true, // Player-Design ist standardmäßig dunkel (wie im Screenshot)
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme || isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
