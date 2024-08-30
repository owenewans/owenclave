plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "com.github.dyhkwong.sagernet.plugin.trojan_go"
    }
    namespace = "io.nekohasekai.sagernet.plugin.trojan_go"
}

setupPlugin("trojan_go")