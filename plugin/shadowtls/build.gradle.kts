plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.github.dyhkwong.sagernet.plugin.shadowtls"
    }
    namespace = "io.nekohasekai.sagernet.plugin.shadowtls"
}

setupPlugin("shadowtls")