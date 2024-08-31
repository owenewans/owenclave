plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlin-parcelize")
}

setupCommon()
android {
    namespace = "io.nekohasekai.sagernet.plugin"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
