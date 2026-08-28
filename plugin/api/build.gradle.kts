plugins {
    id("com.android.library")
}

setupCommon()

android {
    namespace = "io.nekohasekai.sagernet.plugin"
    // app's legacy flavor targets minSdk 21; this module has no API-level-gated
    // code, so it must not force the app's manifest merge to require 23.
    defaultConfig.minSdk = 21
}
