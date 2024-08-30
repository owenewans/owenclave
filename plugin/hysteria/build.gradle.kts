plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.github.dyhkwong.sagernet.plugin.hysteria"
    }
    namespace = "io.nekohasekai.sagernet.plugin.hysteria"
}

setupPlugin("hysteria")