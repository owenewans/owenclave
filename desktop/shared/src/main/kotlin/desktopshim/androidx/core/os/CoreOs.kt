package androidx.core.os

import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable
import java.util.Locale

fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle {
    val bundle = Bundle()
    for ((k, v) in pairs) {
        when (v) {
            null -> bundle.map[k] = null
            is String -> bundle.putString(k, v)
            is Int -> bundle.putInt(k, v)
            is Long -> bundle.putLong(k, v)
            is Boolean -> bundle.putBoolean(k, v)
            is Parcelable -> bundle.putParcelable(k, v)
            is Serializable -> bundle.map[k] = v
            else -> bundle.map[k] = v
        }
    }
    return bundle
}

class LocaleListCompat private constructor(private val locales: List<Locale>) {
    fun isEmpty(): Boolean = locales.isEmpty()
    fun size(): Int = locales.size
    operator fun get(index: Int): Locale? = locales.getOrNull(index)
    fun toLanguageTags(): String = locales.joinToString(",") { it.toLanguageTag() }

    companion object {
        private val EMPTY = LocaleListCompat(emptyList())

        @JvmStatic
        fun getEmptyLocaleList(): LocaleListCompat = EMPTY

        @JvmStatic
        fun forLanguageTags(tags: String?): LocaleListCompat {
            if (tags.isNullOrBlank()) return EMPTY
            val list = tags.split(',').mapNotNull { tag ->
                val trimmed = tag.trim()
                if (trimmed.isEmpty()) null else Locale.forLanguageTag(trimmed)
            }
            return LocaleListCompat(list)
        }
    }
}

