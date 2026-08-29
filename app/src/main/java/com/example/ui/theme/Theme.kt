package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaOnContainer,
    secondary = CharcoalTextMuted,
    onSecondary = Color.White,
    secondaryContainer = CreamSurfaceVariant,
    onSecondaryContainer = CharcoalText,
    tertiary = TerracottaDark,
    onTertiary = Color.White,
    background = CreamBackground,
    onBackground = CharcoalText,
    surface = CreamSurface,
    onSurface = CharcoalText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = CharcoalTextMuted,
    outline = WarmBorder,
    outlineVariant = WarmBorderSubtle
)

@Composable
fun SonetAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

