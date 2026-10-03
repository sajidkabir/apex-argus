package com.apexpredator.argus.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

private val ApexColorScheme = darkColorScheme(
    primary = ApexAccent,
    onPrimary = ApexBackground,
    secondary = ApexAccentDim,
    background = ApexBackground,
    onBackground = ApexTextPrimary,
    surface = ApexSurface,
    onSurface = ApexTextPrimary,
    surfaceVariant = ApexSurfaceVariant,
    onSurfaceVariant = ApexTextSecondary,
    outline = ApexDivider
)

@Composable
fun ApexArgusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ApexColorScheme,
        typography = Typography(),
        content = content
    )
}
