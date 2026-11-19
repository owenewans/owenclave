package android

/**
 * Desktop compile-only stand-in for `android.Manifest`.
 *
 * The shared `ConfigBuilder.kt` only reads `Manifest.permission.*` in its
 * Android-only SSID/location routing branch. The string values are the exact
 * platform constants, so the code reads like the Android original even though
 * no real permission check happens on desktop (see `desktopshim/.../ktx/App.kt`).
 */
object Manifest {

    object permission {

        const val INTERNET = "android.permission.INTERNET"
        const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        const val ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"

    }

}
