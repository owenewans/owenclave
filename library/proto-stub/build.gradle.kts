import com.google.protobuf.gradle.*

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.protobuf")
}

setupKotlinCommon()

val protobufVersion = "3.19.1"

dependencies {
    protobuf(project(":library:proto"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
}
android {
    namespace = "com.v2ray.core"
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
