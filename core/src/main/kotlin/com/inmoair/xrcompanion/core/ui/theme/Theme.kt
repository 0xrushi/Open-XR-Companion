package com.inmoair.xrcompanion.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = AccentBlue,
    onPrimary        = TextPrimary,
    secondary        = AccentBlueDim,
    onSecondary      = TextPrimary,
    background       = DarkBackground,
    onBackground     = TextPrimary,
    surface          = SurfaceDark,
    onSurface        = TextPrimary,
    surfaceVariant   = CardDark,
    onSurfaceVariant = TextSecondary,
    error            = StatusRed,
    onError          = TextPrimary,
)

@Composable
fun XRCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = XRTypography,
        content     = content
    )
}
