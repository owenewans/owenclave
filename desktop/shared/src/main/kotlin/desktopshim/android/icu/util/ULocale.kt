package android.icu.util

import java.util.Locale

object ULocale {
    @JvmStatic
    fun getDisplayCountry(localeID: String, displayLocaleID: String): String {
        val loc = Locale.forLanguageTag(localeID)
        val disp = Locale.forLanguageTag(displayLocaleID)
        return loc.getDisplayCountry(disp)
    }
}

