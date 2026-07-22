package io.nekohasekai.sagernet.ui.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.Theme

val LocalAppThemeId = staticCompositionLocalOf { Theme.PINK }

val OwenclaveShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(48.dp),
)

@Composable
fun OwenclaveTheme(
    themeId: Int = DataStore.appTheme,
    darkTheme: Boolean = when (DataStore.nightTheme) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDynamic = themeId == Theme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        isDynamic && darkTheme -> dynamicDarkColorScheme(context)
        isDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> colorSchemeFor(themeId).dark
        else -> colorSchemeFor(themeId).light
    }

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
