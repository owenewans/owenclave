plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.github.dyhkwong.sagernet.plugin.tuic"
    }
    namespace = "io.nekohasekai.sagernet.plugin.tuic"
}

setupPlugin("tuic")