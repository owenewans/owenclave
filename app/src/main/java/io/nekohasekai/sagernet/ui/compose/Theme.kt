package io.nekohasekai.sagernet.ui.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.Theme

val LocalAppThemeId = staticCompositionLocalOf { Theme.PINK }

val OwenclaveShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private fun ColorScheme.applyPitchBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    surfaceVariant = Color.Black,
)

@Composable
fun OwenclaveTheme(
    themeId: Int = DataStore.appTheme,
    nightTheme: Int = DataStore.nightTheme,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (nightTheme) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val isDynamic = themeId == Theme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isPitchBlack = themeId == Theme.BLACK && darkTheme

    val baseScheme = when {
        isDynamic && darkTheme -> dynamicDarkColorScheme(context)
        isDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> colorSchemeFor(themeId).dark
        else -> colorSchemeFor(themeId).light
    }

    val colorScheme = if (isPitchBlack) baseScheme.applyPitchBlack() else baseScheme

    CompositionLocalProvider(LocalAppThemeId provides themeId) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = OwenclaveTypography,
            shapes = OwenclaveShapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
