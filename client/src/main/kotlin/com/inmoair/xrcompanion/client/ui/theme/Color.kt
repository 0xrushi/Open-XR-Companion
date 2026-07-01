package com.inmoair.xrcompanion.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ClientThemeChoice(
    val key: String,
    val displayName: String,
) {
    DARK("dark", "Dark"),
    DRACULA("dracula", "Dracula"),
    SOLARIS("solaris", "Solaris"),
    DARK_BROWN_GOLDEN("dark_brown_golden", "Dark Brown Golden");

    companion object {
        fun fromKey(key: String): ClientThemeChoice =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

data class XRThemeColors(
    val darkBackground: Color,
    val surfaceDark: Color,
    val cardDark: Color,
    val controlSurface: Color,
    val accentBlue: Color,
    val accentBlueDim: Color,
    val statusGreen: Color,
    val statusRed: Color,
    val statusYellow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val dividerColor: Color,
    val touchpadSurface: Color,
    val touchpadBorder: Color,
)

val LocalXRThemeColors = staticCompositionLocalOf { darkThemeColors }

val DarkBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.darkBackground

val SurfaceDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.surfaceDark

val CardDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.cardDark

val ControlSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.controlSurface

val AccentBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.accentBlue

val AccentBlueDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.accentBlueDim

val StatusGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.statusGreen

val StatusRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.statusRed

val StatusYellow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.statusYellow

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.textSecondary

val DividerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.dividerColor

val TouchpadSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.touchpadSurface

val TouchpadBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalXRThemeColors.current.touchpadBorder

fun colorsFor(choice: ClientThemeChoice): XRThemeColors = when (choice) {
    ClientThemeChoice.DARK -> darkThemeColors
    ClientThemeChoice.DRACULA -> draculaThemeColors
    ClientThemeChoice.SOLARIS -> solarisThemeColors
    ClientThemeChoice.DARK_BROWN_GOLDEN -> darkBrownGoldenThemeColors
}

private val darkThemeColors = XRThemeColors(
    darkBackground = Color(0xFF08111F),
    surfaceDark = Color(0xFF0D1628),
    cardDark = Color(0xFF111B2D),
    controlSurface = Color(0xFF405066),
    accentBlue = Color(0xFF9CCAFF),
    accentBlueDim = Color(0xFF5A7898),
    statusGreen = Color(0xFF56BF5E),
    statusRed = Color(0xFFFF5252),
    statusYellow = Color(0xFFFFD740),
    textPrimary = Color(0xFFF3F6FA),
    textSecondary = Color(0xFFA7ADB8),
    dividerColor = Color(0xFF263248),
    touchpadSurface = Color(0xFF162237),
    touchpadBorder = Color(0xFF46536B),
)

private val draculaThemeColors = XRThemeColors(
    darkBackground = Color(0xFF191A21),
    surfaceDark = Color(0xFF232532),
    cardDark = Color(0xFF2B2E3F),
    controlSurface = Color(0xFF44475A),
    accentBlue = Color(0xFFFF79C6),
    accentBlueDim = Color(0xFFBD93F9),
    statusGreen = Color(0xFF50FA7B),
    statusRed = Color(0xFFFF5555),
    statusYellow = Color(0xFFF1FA8C),
    textPrimary = Color(0xFFF8F8F2),
    textSecondary = Color(0xFFBFC1D2),
    dividerColor = Color(0xFF44475A),
    touchpadSurface = Color(0xFF24283A),
    touchpadBorder = Color(0xFF6272A4),
)

private val solarisThemeColors = XRThemeColors(
    darkBackground = Color(0xFF051C24),
    surfaceDark = Color(0xFF092B35),
    cardDark = Color(0xFF103844),
    controlSurface = Color(0xFF265867),
    accentBlue = Color(0xFFFFC857),
    accentBlueDim = Color(0xFF2EC4B6),
    statusGreen = Color(0xFF7BD88F),
    statusRed = Color(0xFFFF6B6B),
    statusYellow = Color(0xFFFFD166),
    textPrimary = Color(0xFFF4FBF8),
    textSecondary = Color(0xFF9BC3C5),
    dividerColor = Color(0xFF1E4E5A),
    touchpadSurface = Color(0xFF0C303D),
    touchpadBorder = Color(0xFF3E7582),
)

private val darkBrownGoldenThemeColors = XRThemeColors(
    darkBackground = Color(0xFF150F0A),
    surfaceDark = Color(0xFF21170F),
    cardDark = Color(0xFF2B1E12),
    controlSurface = Color(0xFF5C472A),
    accentBlue = Color(0xFFE3B65A),
    accentBlueDim = Color(0xFF9B7534),
    statusGreen = Color(0xFF8BCB88),
    statusRed = Color(0xFFE05D44),
    statusYellow = Color(0xFFF6C85F),
    textPrimary = Color(0xFFFFF7E8),
    textSecondary = Color(0xFFC5AE89),
    dividerColor = Color(0xFF4B3822),
    touchpadSurface = Color(0xFF24180E),
    touchpadBorder = Color(0xFF6B5432),
)
