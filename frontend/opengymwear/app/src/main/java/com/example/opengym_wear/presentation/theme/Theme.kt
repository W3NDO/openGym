package com.example.opengym_wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

private val accents = mapOf(
    "lime" to Color(0xFF30D158),
    "sky" to Color(0xFF0A84FF),
    "orange" to Color(0xFFFF9F0A),
    "violet" to Color(0xFFBF5AF2),
    "pink" to Color(0xFFFF375F),
    "red" to Color(0xFFFF453A),
    "teal" to Color(0xFF40C8E0),
    "gold" to Color(0xFFFFD60A),
)

@Composable
fun OpengymwearTheme(
    theme: String = "dark",
    accent: String = "lime",
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val primary = accents[accent] ?: accents.getValue("lime")
    MaterialTheme {
        val base = MaterialTheme.colorScheme
        val colors = if (useDarkTheme) {
            base.copy(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = primary.copy(alpha = 0.24f),
            onPrimaryContainer = Color.White,
                background = Color.Black,
                onBackground = Color.White,
                surfaceContainerLow = Color(0xFF101010),
                surfaceContainer = Color(0xFF1C1C1E),
                surfaceContainerHigh = Color(0xFF2C2C2E),
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFB0B0B5),
            )
        } else {
            base.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.2f),
            onPrimaryContainer = Color(0xFF171717),
                background = Color(0xFFF2F2F7),
                onBackground = Color(0xFF171717),
                surfaceContainerLow = Color.White,
                surfaceContainer = Color(0xFFE5E5EA),
                surfaceContainerHigh = Color(0xFFD1D1D6),
                onSurface = Color(0xFF171717),
                onSurfaceVariant = Color(0xFF636366),
            )
        }
        MaterialTheme(colorScheme = colors, content = content)
    }
}