package android.text

import java.awt.ComponentOrientation
import java.util.Locale

class BidiFormatter private constructor(private val isRtl: Boolean) {
    fun unicodeWrap(str: String?): String = str.orEmpty()

    companion object {
        @JvmStatic
        fun getInstance(rtlContext: Boolean): BidiFormatter = BidiFormatter(rtlContext)
    }
}

object TextUtils {
    @JvmStatic
    fun getLayoutDirectionFromLocale(locale: Locale?): Int {
        if (locale == null) return 0
        return if (ComponentOrientation.getOrientation(locale).isLeftToRight) 0 else 1
    }

    @JvmStatic
    fun isEmpty(str: CharSequence?): Boolean = str.isNullOrEmpty()
}

