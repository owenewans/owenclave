import com.google.protobuf.gradle.*

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.protobuf")
}

setupKotlinCommon()

val protobufVersion = "4.26.1"

dependencies {
    protobuf(project(":library:proto"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
}
android {
    namespace = "com.v2ray.core"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("java")
            }
        }
    }
}
