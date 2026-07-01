package com.inmoair.xrcompanion.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun XRClientTheme(
    themeChoice: ClientThemeChoice = ClientThemeChoice.DARK,
    content: @Composable () -> Unit,
) {
    val colors = colorsFor(themeChoice)
    val colorScheme = darkColorScheme(
        primary          = colors.accentBlue,
        onPrimary        = colors.textPrimary,
        secondary        = colors.accentBlueDim,
        onSecondary      = colors.textPrimary,
        background       = colors.darkBackground,
        onBackground     = colors.textPrimary,
        surface          = colors.surfaceDark,
        onSurface        = colors.textPrimary,
        surfaceVariant   = colors.cardDark,
        onSurfaceVariant = colors.textSecondary,
        error            = colors.statusRed,
        onError          = colors.textPrimary,
    )

    CompositionLocalProvider(LocalXRThemeColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = XRTypography,
            content     = content
        )
    }
}
