package io.nekohasekai.sagernet.ui.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.nekohasekai.sagernet.utils.Theme

data class ThemeColors(
    val light: ColorScheme,
    val dark: ColorScheme,
)

fun colorSchemeFor(themeId: Int): ThemeColors = when (themeId) {
    Theme.RED -> ThemeColors(LightRed, DarkRed)
    Theme.PINK -> ThemeColors(LightPink, DarkPink)
    Theme.PURPLE -> ThemeColors(LightPurple, DarkPurple)
    Theme.DEEP_PURPLE -> ThemeColors(LightDeepPurple, DarkDeepPurple)
    Theme.INDIGO -> ThemeColors(LightIndigo, DarkIndigo)
    Theme.BLUE -> ThemeColors(LightBlue, DarkBlue)
    Theme.LIGHT_BLUE -> ThemeColors(LightLightBlue, DarkLightBlue)
    Theme.CYAN -> ThemeColors(LightCyan, DarkCyan)
    Theme.TEAL -> ThemeColors(LightTeal, DarkTeal)
    Theme.GREEN -> ThemeColors(LightGreen, DarkGreen)
    Theme.LIGHT_GREEN -> ThemeColors(LightLightGreen, DarkLightGreen)
    Theme.LIME -> ThemeColors(LightLime, DarkLime)
    Theme.YELLOW -> ThemeColors(LightYellow, DarkYellow)
    Theme.AMBER -> ThemeColors(LightAmber, DarkAmber)
    Theme.ORANGE -> ThemeColors(LightOrange, DarkOrange)
    Theme.DEEP_ORANGE -> ThemeColors(LightDeepOrange, DarkDeepOrange)
    Theme.BROWN -> ThemeColors(LightBrown, DarkBrown)
    Theme.GREY -> ThemeColors(LightGrey, DarkGrey)
    Theme.BLUE_GREY -> ThemeColors(LightBlueGrey, DarkBlueGrey)
    Theme.BLACK -> ThemeColors(LightBlack, DarkBlack)
    Theme.DYNAMIC -> ThemeColors(LightPink, DarkPink)
    Theme.UNRECOVERY -> ThemeColors(LightUnrecovery, DarkUnrecovery)
    else -> ThemeColors(LightPink, DarkPink)
}

// ── Pink (default) ──
val LightPink = lightColorScheme(
    primary = Color(0xFFE91E63), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFFE91E63), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2), onSecondaryContainer = Color(0xFF3E001D),
    tertiary = Color(0xFFBA68C8), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F0), onTertiaryContainer = Color(0xFF370D32),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF0F4), onBackground = Color(0xFF201018),
    surface = Color(0xFFFFF0F4), onSurface = Color(0xFF201018),
    surfaceVariant = Color(0xFFF2DCE2), onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377), outlineVariant = Color(0xFFD5C2C7),
    surfaceTint = Color(0xFFE91E63), inverseSurface = Color(0xFF362528),
    inverseOnSurface = Color(0xFFFBEDEF), inversePrimary = Color(0xFFFFB1C8),
)
val DarkPink = darkColorScheme(
    primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7E2949), onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFFFB1C8), onSecondary = Color(0xFF5E1133),
    secondaryContainer = Color(0xFF7E2949), onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFE1B8E8), onTertiary = Color(0xFF4A2745),
    tertiaryContainer = Color(0xFF633C5C), onTertiaryContainer = Color(0xFFFFD7F0),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF170F13), onBackground = Color(0xFFF0E0E5),
    surface = Color(0xFF170F13), onSurface = Color(0xFFF0E0E5),
    surfaceVariant = Color(0xFF514347), onSurfaceVariant = Color(0xFFD5C2C7),
    outline = Color(0xFF9E8D91), outlineVariant = Color(0xFF514347),
    surfaceTint = Color(0xFFFFB1C8), inverseSurface = Color(0xFFF0E0E5),
    inverseOnSurface = Color(0xFF362528), inversePrimary = Color(0xFFE91E63),
)

// ── Red ──
val LightRed = lightColorScheme(
    primary = Color(0xFFF44336), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFFF44336), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6), onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFFB33120), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4), onTertiaryContainer = Color(0xFF410001),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF221918),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF221918),
    surfaceVariant = Color(0xFFE3E2E2), onSurfaceVariant = Color(0xFF4B4747),
    outline = Color(0xFF7D7777), surfaceTint = Color(0xFFF44336),
)
val DarkRed = darkColorScheme(
    primary = Color(0xFFFFB4AB), onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A), onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFB4AB), onSecondary = Color(0xFF690005),
    secondaryContainer = Color(0xFF93000A), onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFFFB59E), onTertiary = Color(0xFF5D1500),
    tertiaryContainer = Color(0xFF842C17), onTertiaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF1A1413), onBackground = Color(0xFFF2E7E5),
    surface = Color(0xFF1A1413), onSurface = Color(0xFFF2E7E5),
    surfaceVariant = Color(0xFF4B4747), onSurfaceVariant = Color(0xFFCDC7C7),
    outline = Color(0xFF968C8C), surfaceTint = Color(0xFFFFB4AB),
)

// ── Purple ──
val LightPurple = lightColorScheme(
    primary = Color(0xFF9C27B0), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6FF), onPrimaryContainer = Color(0xFF3B0029),
    secondary = Color(0xFF9C27B0), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD6FF), onSecondaryContainer = Color(0xFF3B0029),
    tertiary = Color(0xFF7C4DFF), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DEFF), onTertiaryContainer = Color(0xFF26005A),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF6FB), onBackground = Color(0xFF201018),
    surface = Color(0xFFFFF6FB), onSurface = Color(0xFF201018),
    surfaceVariant = Color(0xFFEADFE0), onSurfaceVariant = Color(0xFF4E4446),
    outline = Color(0xFF807476), surfaceTint = Color(0xFF9C27B0),
)
val DarkPurple = darkColorScheme(
    primary = Color(0xFFE1B8E8), onPrimary = Color(0xFF560050),
    primaryContainer = Color(0xFF730072), onPrimaryContainer = Color(0xFFFFD6FF),
    secondary = Color(0xFFE1B8E8), onSecondary = Color(0xFF560050),
    secondaryContainer = Color(0xFF730072), onSecondaryContainer = Color(0xFFFFD6FF),
    tertiary = Color(0xFFC6BFFF), onTertiary = Color(0xFF3D2080),
    tertiaryContainer = Color(0xFF5838A0), onTertiaryContainer = Color(0xFFE8DEFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF180F13), onBackground = Color(0xFFF1E7E8),
    surface = Color(0xFF180F13), onSurface = Color(0xFFF1E7E8),
    surfaceVariant = Color(0xFF4E4446), onSurfaceVariant = Color(0xFFD2C2C4),
    outline = Color(0xFF9B8D8F), surfaceTint = Color(0xFFE1B8E8),
)

// ── Deep Purple ──
val LightDeepPurple = lightColorScheme(
    primary = Color(0xFF673AB7), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF673AB7), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF), onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFF8C4F00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF2C1500),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F), surfaceTint = Color(0xFF673AB7),
)
val DarkDeepPurple = darkColorScheme(
    primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF381E73),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCFBCFF), onSecondary = Color(0xFF381E73),
    secondaryContainer = Color(0xFF4F378B), onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFFFFB870), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693A00), onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF141218), onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141218), onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF49454E), onSurfaceVariant = Color(0xFFCCC4CF),
    outline = Color(0xFF948F9A), surfaceTint = Color(0xFFCFBCFF),
)

// ── Indigo ──
val LightIndigo = lightColorScheme(
    primary = Color(0xFF3F51B5), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF3F51B5), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1FF), onSecondaryContainer = Color(0xFF001257),
    tertiary = Color(0xFF0061A4), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2E4FF), onTertiaryContainer = Color(0xFF001D34),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFEFBFF), onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFFEFBFF), onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680), surfaceTint = Color(0xFF3F51B5),
)
val DarkIndigo = darkColorScheme(
    primary = Color(0xFFB9C3FF), onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF1F3994), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB9C3FF), onSecondary = Color(0xFF002A78),
    secondaryContainer = Color(0xFF1F3994), onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFF9ECBFF), onTertiary = Color(0xFF003256),
    tertiaryContainer = Color(0xFF004A77), onTertiaryContainer = Color(0xFFD2E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF121316), onBackground = Color(0xFFE4E2E9),
    surface = Color(0xFF121316), onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A), surfaceTint = Color(0xFFB9C3FF),
)

// ── Blue ──
val LightBlue = lightColorScheme(
    primary = Color(0xFF2196F3), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF2196F3), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E2FF), onSecondaryContainer = Color(0xFF001A41),
    tertiary = Color(0xFF006A6A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6FF7F7), onTertiaryContainer = Color(0xFF002020),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFEFBFF), onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFFEFBFF), onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F), surfaceTint = Color(0xFF2196F3),
)
val DarkBlue = darkColorScheme(
    primary = Color(0xFFAFC6FF), onPrimary = Color(0xFF002F69),
    primaryContainer = Color(0xFF004693), onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFAFC6FF), onSecondary = Color(0xFF002F69),
    secondaryContainer = Color(0xFF004693), onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFF4CDADA), onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF005050), onTertiaryContainer = Color(0xFF6FF7F7),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF121316), onBackground = Color(0xFFE4E2E9),
    surface = Color(0xFF121316), onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), surfaceTint = Color(0xFFAFC6FF),
)

// ── Light Blue ──
val LightLightBlue = lightColorScheme(
    primary = Color(0xFF03A9F4), onPrimary = Color.White,
    primaryContainer = Color(0xFFC4EFFF), onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF03A9F4), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4EFFF), onSecondaryContainer = Color(0xFF001E2C),
    tertiary = Color(0xFF006874), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9EECFD), onTertiaryContainer = Color(0xFF001F24),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDDE3EA), onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E), surfaceTint = Color(0xFF03A9F4),
)
val DarkLightBlue = darkColorScheme(
    primary = Color(0xFF7DD0FF), onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6D), onPrimaryContainer = Color(0xFFC4EFFF),
    secondary = Color(0xFF7DD0FF), onSecondary = Color(0xFF00344C),
    secondaryContainer = Color(0xFF004C6D), onSecondaryContainer = Color(0xFFC4EFFF),
    tertiary = Color(0xFF82D0E2), onTertiary = Color(0xFF003744),
    tertiaryContainer = Color(0xFF004F5D), onTertiaryContainer = Color(0xFF9EECFD),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF111417), onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111417), onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF41484D), onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9196), surfaceTint = Color(0xFF7DD0FF),
)

// ── Cyan ──
val LightCyan = lightColorScheme(
    primary = Color(0xFF00BCD4), onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF9CF0FF), onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF00BCD4), onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF9CF0FF), onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF006784), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDE9FF), onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFAFDFD), onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFAFDFD), onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDAE4E4), onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A), surfaceTint = Color(0xFF00BCD4),
)
val DarkCyan = darkColorScheme(
    primary = Color(0xFF40E0EB), onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53), onPrimaryContainer = Color(0xFF9CF0FF),
    secondary = Color(0xFF40E0EB), onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53), onSecondaryContainer = Color(0xFF9CF0FF),
    tertiary = Color(0xFF6FD0FF), onTertiary = Color(0xFF003548),
    tertiaryContainer = Color(0xFF004D67), onTertiaryContainer = Color(0xFFBDE9FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF0E1414), onBackground = Color(0xFFDFE3E3),
    surface = Color(0xFF0E1414), onSurface = Color(0xFFDFE3E3),
    surfaceVariant = Color(0xFF3F4849), onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF889392), surfaceTint = Color(0xFF40E0EB),
)

// ── Teal ──
val LightTeal = lightColorScheme(
    primary = Color(0xFF009688), onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0E4), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF009688), onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF0E4), onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF006A60), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF74F7E0), onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFAFDF9), onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFAFDF9), onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7975), surfaceTint = Color(0xFF009688),
)
val DarkTeal = darkColorScheme(
    primary = Color(0xFF53DBC9), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF9CF0E4),
    secondary = Color(0xFF53DBC9), onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048), onSecondaryContainer = Color(0xFF9CF0E4),
    tertiary = Color(0xFF54DBC4), onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005049), onTertiaryContainer = Color(0xFF74F7E0),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF0E1412), onBackground = Color(0xFFDFE3DE),
    surface = Color(0xFF0E1412), onSurface = Color(0xFFDFE3DE),
    surfaceVariant = Color(0xFF3F4946), onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF88938F), surfaceTint = Color(0xFF53DBC9),
)

// ── Green ──
val LightGreen = lightColorScheme(
    primary = Color(0xFF4CAF50), onPrimary = Color(0xFF00380A),
    primaryContainer = Color(0xFFA4F8A0), onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF4CAF50), onSecondary = Color(0xFF00380A),
    secondaryContainer = Color(0xFFA4F8A0), onSecondaryContainer = Color(0xFF002204),
    tertiary = Color(0xFF006C4C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF8AF8C4), onTertiaryContainer = Color(0xFF002114),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFF7FFF3), onBackground = Color(0xFF181C17),
    surface = Color(0xFFF7FFF3), onSurface = Color(0xFF181C17),
    surfaceVariant = Color(0xFFDEE6D8), onSurfaceVariant = Color(0xFF424840),
    outline = Color(0xFF72796F), surfaceTint = Color(0xFF4CAF50),
)
val DarkGreen = darkColorScheme(
    primary = Color(0xFF6EEC86), onPrimary = Color(0xFF00390E),
    primaryContainer = Color(0xFF005317), onPrimaryContainer = Color(0xFFA4F8A0),
    secondary = Color(0xFF6EEC86), onSecondary = Color(0xFF00390E),
    secondaryContainer = Color(0xFF005317), onSecondaryContainer = Color(0xFFA4F8A0),
    tertiary = Color(0xFF6DDBA9), onTertiary = Color(0xFF003825),
    tertiaryContainer = Color(0xFF005138), onTertiaryContainer = Color(0xFF8AF8C4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF0F140E), onBackground = Color(0xFFE0E4DA),
    surface = Color(0xFF0F140E), onSurface = Color(0xFFE0E4DA),
    surfaceVariant = Color(0xFF424840), onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8B9387), surfaceTint = Color(0xFF6EEC86),
)

// ── Light Green ──
val LightLightGreen = lightColorScheme(
    primary = Color(0xFF8BC34A), onPrimary = Color(0xFF063900),
    primaryContainer = Color(0xFFC6F480), onPrimaryContainer = Color(0xFF0C2000),
    secondary = Color(0xFF8BC34A), onSecondary = Color(0xFF063900),
    secondaryContainer = Color(0xFFC6F480), onSecondaryContainer = Color(0xFF0C2000),
    tertiary = Color(0xFF386A00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB5F367), onTertiaryContainer = Color(0xFF0D2000),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFF8FFF0), onBackground = Color(0xFF181E13),
    surface = Color(0xFFF8FFF0), onSurface = Color(0xFF181E13),
    surfaceVariant = Color(0xFFE2E8D4), onSurfaceVariant = Color(0xFF44482F),
    outline = Color(0xFF72785E), surfaceTint = Color(0xFF8BC34A),
)
val DarkLightGreen = darkColorScheme(
    primary = Color(0xFFABD700), onPrimary = Color(0xFF1B3700),
    primaryContainer = Color(0xFF285100), onPrimaryContainer = Color(0xFFC6F480),
    secondary = Color(0xFFABD700), onSecondary = Color(0xFF1B3700),
    secondaryContainer = Color(0xFF285100), onSecondaryContainer = Color(0xFFC6F480),
    tertiary = Color(0xFF9BD650), onTertiary = Color(0xFF1A3700),
    tertiaryContainer = Color(0xFF275100), onTertiaryContainer = Color(0xFFB5F367),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF12150A), onBackground = Color(0xFFE2E9D6),
    surface = Color(0xFF12150A), onSurface = Color(0xFFE2E9D6),
    surfaceVariant = Color(0xFF44482F), onSurfaceVariant = Color(0xFFC6CCAF),
    outline = Color(0xFF90967E), surfaceTint = Color(0xFFABD700),
)

// ── Lime ──
val LightLime = lightColorScheme(
    primary = Color(0xFFCDDC39), onPrimary = Color(0xFF1F2600),
    primaryContainer = Color(0xFFE6F480), onPrimaryContainer = Color(0xFF2D3500),
    secondary = Color(0xFFCDDC39), onSecondary = Color(0xFF1F2600),
    secondaryContainer = Color(0xFFE6F480), onSecondaryContainer = Color(0xFF2D3500),
    tertiary = Color(0xFF5C6D00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7F670), onTertiaryContainer = Color(0xFF161F00),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFF9FFE0), onBackground = Color(0xFF1A1C05),
    surface = Color(0xFFF9FFE0), onSurface = Color(0xFF1A1C05),
    surfaceVariant = Color(0xFFE5E7D0), onSurfaceVariant = Color(0xFF44472B),
    outline = Color(0xFF75775B), surfaceTint = Color(0xFFCDDC39),
)
val DarkLime = darkColorScheme(
    primary = Color(0xFFC9D600), onPrimary = Color(0xFF333A00),
    primaryContainer = Color(0xFF4D5600), onPrimaryContainer = Color(0xFFE6F480),
    secondary = Color(0xFFC9D600), onSecondary = Color(0xFF333A00),
    secondaryContainer = Color(0xFF4D5600), onSecondaryContainer = Color(0xFFE6F480),
    tertiary = Color(0xFFB6D536), onTertiary = Color(0xFF2A3400),
    tertiaryContainer = Color(0xFF3F4D00), onTertiaryContainer = Color(0xFFD7F670),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF12140A), onBackground = Color(0xFFE3E5CB),
    surface = Color(0xFF12140A), onSurface = Color(0xFFE3E5CB),
    surfaceVariant = Color(0xFF44472B), onSurfaceVariant = Color(0xFFC9CBAE),
    outline = Color(0xFF929574), surfaceTint = Color(0xFFC9D600),
)

// ── Yellow ──
val LightYellow = lightColorScheme(
    primary = Color(0xFFFFEB3B), onPrimary = Color(0xFF2B2B00),
    primaryContainer = Color(0xFFFFF083), onPrimaryContainer = Color(0xFF424200),
    secondary = Color(0xFFFFEB3B), onSecondary = Color(0xFF2B2B00),
    secondaryContainer = Color(0xFFFFF083), onSecondaryContainer = Color(0xFF424200),
    tertiary = Color(0xFF6B5D10), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E787), onTertiaryContainer = Color(0xFF201B00),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF9EE), onBackground = Color(0xFF1D1B00),
    surface = Color(0xFFFFF9EE), onSurface = Color(0xFF1D1B00),
    surfaceVariant = Color(0xFFE9E2CC), onSurfaceVariant = Color(0xFF4A482F),
    outline = Color(0xFF7B7959), surfaceTint = Color(0xFFFFEB3B),
)
val DarkYellow = darkColorScheme(
    primary = Color(0xFFE6CC00), onPrimary = Color(0xFF383000),
    primaryContainer = Color(0xFF524700), onPrimaryContainer = Color(0xFFFFF083),
    secondary = Color(0xFFE6CC00), onSecondary = Color(0xFF383000),
    secondaryContainer = Color(0xFF524700), onSecondaryContainer = Color(0xFFFFF083),
    tertiary = Color(0xFFD9CA6E), onTertiary = Color(0xFF3A2F00),
    tertiaryContainer = Color(0xFF524700), onTertiaryContainer = Color(0xFFF6E787),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF131400), onBackground = Color(0xFFE9E2CC),
    surface = Color(0xFF131400), onSurface = Color(0xFFE9E2CC),
    surfaceVariant = Color(0xFF4A482F), onSurfaceVariant = Color(0xFFCDCFB2),
    outline = Color(0xFF969782), surfaceTint = Color(0xFFE6CC00),
)

// ── Amber ──
val LightAmber = lightColorScheme(
    primary = Color(0xFFFFC107), onPrimary = Color(0xFF3D2C00),
    primaryContainer = Color(0xFFFFF0C8), onPrimaryContainer = Color(0xFF3D2C00),
    secondary = Color(0xFFFFC107), onSecondary = Color(0xFF3D2C00),
    secondaryContainer = Color(0xFFFFF0C8), onSecondaryContainer = Color(0xFF3D2C00),
    tertiary = Color(0xFF735C00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08D), onTertiaryContainer = Color(0xFF231B00),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF8EE), onBackground = Color(0xFF231B00),
    surface = Color(0xFFFFF8EE), onSurface = Color(0xFF231B00),
    surfaceVariant = Color(0xFFEBE3CB), onSurfaceVariant = Color(0xFF4B442F),
    outline = Color(0xFF7C7563), surfaceTint = Color(0xFFFFC107),
)
val DarkAmber = darkColorScheme(
    primary = Color(0xFFFFB300), onPrimary = Color(0xFF3F2D00),
    primaryContainer = Color(0xFF5C4400), onPrimaryContainer = Color(0xFFFFE08D),
    secondary = Color(0xFFFFB300), onSecondary = Color(0xFF3F2D00),
    secondaryContainer = Color(0xFF5C4400), onSecondaryContainer = Color(0xFFFFE08D),
    tertiary = Color(0xFFE9C36B), onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF584400), onTertiaryContainer = Color(0xFFFFE08D),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF15120A), onBackground = Color(0xFFEDE0CB),
    surface = Color(0xFF15120A), onSurface = Color(0xFFEDE0CB),
    surfaceVariant = Color(0xFF4B442F), onSurfaceVariant = Color(0xFFCEC8B2),
    outline = Color(0xFF97917D), surfaceTint = Color(0xFFFFB300),
)

// ── Orange ──
val LightOrange = lightColorScheme(
    primary = Color(0xFFFF9800), onPrimary = Color(0xFF3D2700),
    primaryContainer = Color(0xFFFFDCC2), onPrimaryContainer = Color(0xFF3D2700),
    secondary = Color(0xFFFF9800), onSecondary = Color(0xFF3D2700),
    secondaryContainer = Color(0xFFFFDCC2), onSecondaryContainer = Color(0xFF3D2700),
    tertiary = Color(0xFF7C5800), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB6), onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF6F2), onBackground = Color(0xFF231A12),
    surface = Color(0xFFFFF6F2), onSurface = Color(0xFF231A12),
    surfaceVariant = Color(0xFFF4DED2), onSurfaceVariant = Color(0xFF52443B),
    outline = Color(0xFF84746A), surfaceTint = Color(0xFFFF9800),
)
val DarkOrange = darkColorScheme(
    primary = Color(0xFFFFB300), onPrimary = Color(0xFF3D2700),
    primaryContainer = Color(0xFF5C4200), onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFFFB300), onSecondary = Color(0xFF3D2700),
    secondaryContainer = Color(0xFF5C4200), onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFFFB870), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693A00), onTertiaryContainer = Color(0xFFFFDDB6),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF18120C), onBackground = Color(0xFFEFDDD3),
    surface = Color(0xFF18120C), onSurface = Color(0xFFEFDDD3),
    surfaceVariant = Color(0xFF52443B), onSurfaceVariant = Color(0xFFD7C3B7),
    outline = Color(0xFF9F8D83), surfaceTint = Color(0xFFFFB300),
)

// ── Deep Orange ──
val LightDeepOrange = lightColorScheme(
    primary = Color(0xFFFF5722), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFFFF5722), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF), onSecondaryContainer = Color(0xFF380D00),
    tertiary = Color(0xFFB33120), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4), onTertiaryContainer = Color(0xFF410001),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF6F5), onBackground = Color(0xFF221917),
    surface = Color(0xFFFFF6F5), onSurface = Color(0xFF221917),
    surfaceVariant = Color(0xFFF4DED9), onSurfaceVariant = Color(0xFF52443F),
    outline = Color(0xFF847571), surfaceTint = Color(0xFFFF5722),
)
val DarkDeepOrange = darkColorScheme(
    primary = Color(0xFFFFB596), onPrimary = Color(0xFF5B1500),
    primaryContainer = Color(0xFF822E00), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFFFB596), onSecondary = Color(0xFF5B1500),
    secondaryContainer = Color(0xFF822E00), onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFFFB59E), onTertiary = Color(0xFF5D1500),
    tertiaryContainer = Color(0xFF842C17), onTertiaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF1A1412), onBackground = Color(0xFFF2E5E2),
    surface = Color(0xFF1A1412), onSurface = Color(0xFFF2E5E2),
    surfaceVariant = Color(0xFF52443F), onSurfaceVariant = Color(0xFFD7C2BC),
    outline = Color(0xFF9F8C88), surfaceTint = Color(0xFFFFB596),
)

// ── Brown ──
val LightBrown = lightColorScheme(
    primary = Color(0xFF795548), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBC6BF), onPrimaryContainer = Color(0xFF321100),
    secondary = Color(0xFF795548), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBC6BF), onSecondaryContainer = Color(0xFF321100),
    tertiary = Color(0xFF735B3E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF291806),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFF8F5), onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5), onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED5), onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF84746A), surfaceTint = Color(0xFF795548),
)
val DarkBrown = darkColorScheme(
    primary = Color(0xFFC9AAA0), onPrimary = Color(0xFF4D2814),
    primaryContainer = Color(0xFF683E27), onPrimaryContainer = Color(0xFFDBC6BF),
    secondary = Color(0xFFC9AAA0), onSecondary = Color(0xFF4D2814),
    secondaryContainer = Color(0xFF683E27), onSecondaryContainer = Color(0xFFDBC6BF),
    tertiary = Color(0xFFE1C29A), onTertiary = Color(0xFF402D12),
    tertiaryContainer = Color(0xFF584327), onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF1A1411), onBackground = Color(0xFFEFDDD7),
    surface = Color(0xFF1A1411), onSurface = Color(0xFFEFDDD7),
    surfaceVariant = Color(0xFF52443C), onSurfaceVariant = Color(0xFFD7C2BA),
    outline = Color(0xFF9F8C84), surfaceTint = Color(0xFFC9AAA0),
)

// ── Grey ──
val LightGrey = lightColorScheme(
    primary = Color(0xFF9E9E9E), onPrimary = Color(0xFF1B1B1B),
    primaryContainer = Color(0xFFE4E2E4), onPrimaryContainer = Color(0xFF2C2C2C),
    secondary = Color(0xFF9E9E9E), onSecondary = Color(0xFF1B1B1B),
    secondaryContainer = Color(0xFFE4E2E4), onSecondaryContainer = Color(0xFF2C2C2C),
    tertiary = Color(0xFF6B5D70), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF), onTertiaryContainer = Color(0xFF251A2D),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFEFBFF), onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF), onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1E5), onSurfaceVariant = Color(0xFF46464A),
    outline = Color(0xFF77767C), surfaceTint = Color(0xFF9E9E9E),
)
val DarkGrey = darkColorScheme(
    primary = Color(0xFFC6C4C7), onPrimary = Color(0xFF2E2E2E),
    primaryContainer = Color(0xFF454547), onPrimaryContainer = Color(0xFFE4E2E4),
    secondary = Color(0xFFC6C4C7), onSecondary = Color(0xFF2E2E2E),
    secondaryContainer = Color(0xFF454547), onSecondaryContainer = Color(0xFFE4E2E4),
    tertiary = Color(0xFFD7BEE3), onTertiary = Color(0xFF3B2E44),
    tertiaryContainer = Color(0xFF53455C), onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF131316), onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF131316), onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF46464A), onSurfaceVariant = Color(0xFFC7C5CA),
    outline = Color(0xFF909094), surfaceTint = Color(0xFFC6C4C7),
)

// ── Blue Grey ──
val LightBlueGrey = lightColorScheme(
    primary = Color(0xFF607D8B), onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE4F0), onPrimaryContainer = Color(0xFF072130),
    secondary = Color(0xFF607D8B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBE4F0), onSecondaryContainer = Color(0xFF072130),
    tertiary = Color(0xFF4B657A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFE6FF), onTertiaryContainer = Color(0xFF042032),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFAFCFE), onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFAFCFE), onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3E6), onSurfaceVariant = Color(0xFF414849),
    outline = Color(0xFF71787A), surfaceTint = Color(0xFF607D8B),
)
val DarkBlueGrey = darkColorScheme(
    primary = Color(0xFFAFCCE0), onPrimary = Color(0xFF0F3445),
    primaryContainer = Color(0xFF2F4A5B), onPrimaryContainer = Color(0xFFCBE4F0),
    secondary = Color(0xFFAFCCE0), onSecondary = Color(0xFF0F3445),
    secondaryContainer = Color(0xFF2F4A5B), onSecondaryContainer = Color(0xFFCBE4F0),
    tertiary = Color(0xFFB2CAE3), onTertiary = Color(0xFF1B344A),
    tertiaryContainer = Color(0xFF324B62), onTertiaryContainer = Color(0xFFCFE6FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF0F1416), onBackground = Color(0xFFDFE3E6),
    surface = Color(0xFF0F1416), onSurface = Color(0xFFDFE3E6),
    surfaceVariant = Color(0xFF414849), onSurfaceVariant = Color(0xFFC1C7CA),
    outline = Color(0xFF8B9294), surfaceTint = Color(0xFFAFCCE0),
)

// ── Black ──
val LightBlack = lightColorScheme(
    primary = Color(0xFF212121), onPrimary = Color.White,
    primaryContainer = Color(0xFF424242), onPrimaryContainer = Color.White,
    secondary = Color(0xFF212121), onSecondary = Color.White,
    secondaryContainer = Color(0xFF424242), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF636363), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF888888), onTertiaryContainer = Color.Black,
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFF121212), onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF121212), onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF2E2E2E), onSurfaceVariant = Color(0xFFCACACA),
    outline = Color(0xFF929292), surfaceTint = Color(0xFFE3E3E3),
)
val DarkBlack = darkColorScheme(
    primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF212121),
    primaryContainer = Color(0xFF424242), onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFFFFF), onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFF424242), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFC0C0C0), onTertiary = Color(0xFF1B1B1B),
    tertiaryContainer = Color(0xFF3D3D3D), onTertiaryContainer = Color.White,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF000000), onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF000000), onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF2E2E2E), onSurfaceVariant = Color(0xFFCACACA),
    outline = Color(0xFF929292), surfaceTint = Color(0xFFFFFFFF),
)

// ── Unrecovery ──
// Dark: deep reddish-black bg, bright blue primary, proper tonal layers
val LightUnrecovery = lightColorScheme(
    primary = Color(0xFF0F1F40), onPrimary = Color(0xFFF2F2F2),
    primaryContainer = Color(0xFFDDE4FF), onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF0E2440), onSecondary = Color(0xFFF2F2F2),
    secondaryContainer = Color(0xFFDBC8CC), onSecondaryContainer = Color(0xFF26191C),
    tertiary = Color(0xFF6B6870), onTertiary = Color(0xFFF2F2F2),
    tertiaryContainer = Color(0xFFEDE0E4), onTertiaryContainer = Color(0xFF26191C),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F2F2), onBackground = Color(0xFF26191C),
    surface = Color(0xFFF5F2F2), onSurface = Color(0xFF26191C),
    surfaceVariant = Color(0xFFE0DCDC), onSurfaceVariant = Color(0xFF4A3F43),
    outline = Color(0xFF7A6F73), outlineVariant = Color(0xFFCCC8C8),
    surfaceTint = Color(0xFF0F1F40), inverseSurface = Color(0xFF26191C),
    inverseOnSurface = Color(0xFFF2F2F2), inversePrimary = Color(0xFF6B8FD4),
)
val DarkUnrecovery = darkColorScheme(
    primary = Color(0xFF6B8FD4), onPrimary = Color(0xFF0F1F40),
    primaryContainer = Color(0xFF2D3D5C), onPrimaryContainer = Color(0xFFD0D8F0),
    secondary = Color(0xFF8B9BB0), onSecondary = Color(0xFF0E2440),
    secondaryContainer = Color(0xFF2E2428), onSecondaryContainer = Color(0xFFD0C8CC),
    tertiary = Color(0xFFB0A0A4), onTertiary = Color(0xFF26191C),
    tertiaryContainer = Color(0xFF3A2A2E), onTertiaryContainer = Color(0xFFD0C8CC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1E1518), onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1E1518), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF4A3A3E), onSurfaceVariant = Color(0xFFC0B0B4),
    outline = Color(0xFF8B7B7F), outlineVariant = Color(0xFF4A3A3E),
    surfaceTint = Color(0xFF6B8FD4), inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color(0xFF26191C), inversePrimary = Color(0xFF0F1F40),
)
