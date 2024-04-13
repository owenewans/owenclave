plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.github.dyhkwong.sagernet.plugin.mieru"
    }
    namespace = "io.nekohasekai.sagernet.plugin.mieru"
}

setupPlugin("mieru")