package androidx.appcompat.app

import androidx.core.os.LocaleListCompat

object AppCompatDelegate {
    const val MODE_NIGHT_FOLLOW_SYSTEM = -1
    const val MODE_NIGHT_NO = 1
    const val MODE_NIGHT_YES = 2
    const val MODE_NIGHT_AUTO_BATTERY = 3

    private var nightMode: Int = MODE_NIGHT_FOLLOW_SYSTEM
    private var appLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()

    @JvmStatic
    fun setDefaultNightMode(mode: Int) {
        nightMode = mode
    }

    @JvmStatic
    fun getDefaultNightMode(): Int = nightMode

    @JvmStatic
    fun setApplicationLocales(locales: LocaleListCompat) {
        appLocales = locales
    }

    @JvmStatic
    fun getApplicationLocales(): LocaleListCompat = appLocales
}

