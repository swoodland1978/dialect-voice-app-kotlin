package com.dialect.voice.ui.theme

import androidx.compose.foundation.isSystemInDarkMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF424242),
    onSecondary = Color.White,
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    error = Color(0xFFD32F2F),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF212121),
    tertiary = Color(0xFF4DB6AC),
    onTertiary = Color(0xFF00362E),
    error = Color(0xFFCF6679),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun DialectVoiceTheme(
    darkTheme: Boolean = isSystemInDarkMode(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
