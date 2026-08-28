import com.google.protobuf.gradle.*

plugins {
    id("com.android.library")
    alias(libs.plugins.protobuf)
}

setupCommon()

dependencies {
    protobuf(project(":library:proto"))

    api(libs.protobuf.java)
}
android {
    namespace = "com.github.owenewans.owenclave.core"
    // app's legacy flavor targets minSdk 21; this module has no API-level-gated
    // code, so it must not force the app's manifest merge to require 23.
    defaultConfig.minSdk = 21
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.36.0"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("java")
            }
        }
    }
}
