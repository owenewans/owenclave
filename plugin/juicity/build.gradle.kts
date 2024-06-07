plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.github.dyhkwong.sagernet.plugin.juicity"
    }
    namespace = "io.nekohasekai.sagernet.plugin.juicity"
}

setupPlugin("juicity")