package android.content.res

import android.os.LocaleList
import android.util.TypedValue
import io.nekohasekai.sagernet.DesktopResourceLoader
import java.io.InputStream
import java.util.Locale

class Configuration {
    companion object {
        const val UI_MODE_NIGHT_MASK = 0x30
        const val UI_MODE_NIGHT_NO = 0x10
        const val UI_MODE_NIGHT_YES = 0x20
        const val UI_MODE_TYPE_NORMAL = 1
        const val UI_MODE_TYPE_TELEVISION = 4
    }

    @JvmField
    var locale: Locale = Locale.getDefault()

    @JvmField
    var uiMode: Int = UI_MODE_NIGHT_YES

    var locales: LocaleList
        get() = LocaleList(locale)
        set(value) {
            if (!value.isEmpty()) locale = value[0]
        }
}

class AssetManager {
    fun open(fileName: String): InputStream =
        DesktopResourceLoader.openAssetStream(fileName)
            ?: throw java.io.FileNotFoundException("Asset not found: $fileName")

    fun list(path: String): Array<String>? = emptyArray()
}

class Resources {
    val configuration: Configuration = Configuration()
    val assets: AssetManager = AssetManager()

    class Theme {
        fun resolveAttribute(resid: Int, outValue: TypedValue, resolveRefs: Boolean): Boolean = false
    }

    fun getString(id: Int): String = DesktopResourceLoader.getString(id)
    fun getString(id: Int, vararg formatArgs: Any?): String = DesktopResourceLoader.getString(id, *formatArgs)
    fun getStringArray(id: Int): Array<String> = DesktopResourceLoader.getStringArray(id)
    fun getIntArray(id: Int): IntArray =
        DesktopResourceLoader.getStringArray(id).mapNotNull { it.trim().removePrefix("#").toIntOrNull(16) ?: it.toIntOrNull() }.toIntArray()
    fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        DesktopResourceLoader.getQuantityString(id, quantity, *formatArgs)
    fun getQuantityString(id: Int, quantity: Int): String =
        DesktopResourceLoader.getQuantityString(id, quantity, quantity)
    fun getInteger(id: Int): Int = 200
    fun openRawResource(id: Int): InputStream = DesktopResourceLoader.openRawResource(id)

    companion object {
        private val systemResources = Resources()
        @JvmStatic
        fun getSystem(): Resources = systemResources
    }
}

