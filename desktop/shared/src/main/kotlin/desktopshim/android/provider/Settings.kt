package android.provider

import android.content.ContentResolver

/**
 * Desktop compile-only stand-in for `android.provider.Settings`.
 *
 * Copy of the two members used by the shared `ConfigBuilder.kt`. On desktop the
 * location service is always reported as disabled (see
 * `desktopshim/.../SagerNet.kt`), so the caller never gets this far; if it ever
 * did, `getInt` reports "not found" exactly like Android does on a device that
 * does not expose the setting.
 */
object Settings {

    const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"

    class SettingNotFoundException(name: String) : Exception(name)

    object Secure {

        const val LOCATION_MODE = "location_mode"
        const val LOCATION_MODE_OFF = 0

        @Throws(SettingNotFoundException::class)
        fun getInt(contentResolver: ContentResolver, name: String): Int {
            throw SettingNotFoundException(name)
        }

    }

}
