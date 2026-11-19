package androidx.compose.ui.text.font

import io.nekohasekai.sagernet.DesktopResourceLoader
import io.nekohasekai.sagernet.R
import java.io.File

private val cachedGoogleSansBytes: ByteArray? by lazy {
    DesktopResourceLoader::class.java.classLoader
        ?.getResourceAsStream("android_res/font/google_sans_flex.ttf")
        ?.use { it.readBytes() }
        ?: run {
            val devRoot = System.getProperty("owenclave.devRoot")
            val candidates = listOfNotNull(
                devRoot?.let { File(it, "app/src/main/res/font/google_sans_flex.ttf") },
                File("app/src/main/res/font/google_sans_flex.ttf"),
            )
            candidates.firstOrNull { it.isFile }?.readBytes()
        }
}

fun Font(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
): Font {
    val bytes = if (resId == R.font.google_sans_flex) cachedGoogleSansBytes else null
    return androidx.compose.ui.text.platform.Font(
        identity = "font_${resId}_${weight.weight}",
        data = bytes ?: cachedGoogleSansBytes ?: ByteArray(0),
        weight = weight,
        style = FontStyle.Normal,
    )
}

fun Font(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
    variationSettings: FontVariation.Settings,
): Font {
    val bytes = if (resId == R.font.google_sans_flex) cachedGoogleSansBytes else null
    return androidx.compose.ui.text.platform.Font(
        identity = "font_var_${resId}_${weight.weight}_${variationSettings.settings.size}",
        data = bytes ?: cachedGoogleSansBytes ?: ByteArray(0),
        weight = weight,
        style = FontStyle.Normal,
    )
}
