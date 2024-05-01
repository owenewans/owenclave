plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.protobuf")
}

setupApp()

android {
    androidResources {
        generateLocaleConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    kapt.arguments {
        arg("room.incremental", true)
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        viewBinding = true
    }
    namespace = "io.nekohasekai.sagernet"
}

dependencies {

    val hutoolVersion = rootProject.extra["hutoolVersion"].toString()

    implementation(fileTree("libs"))
    compileOnly(project(":library:stub"))
    implementation(project(":library:include"))
    implementation(project(":library:termux:terminal-view"))
    implementation(project(":library:termux:terminal-emulator"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.camera:camera-view:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.work:work-multiprocess:2.9.0")

    implementation("com.takisoft.preferencex:preferencex:1.1.0")
    implementation("com.takisoft.preferencex:preferencex-simplemenu:1.1.0")
    implementation("com.takisoft.preferencex:preferencex-colorpicker:1.1.0")

    implementation("com.google.android.material:material:1.11.0")
    implementation("cn.hutool:hutool-core:$hutoolVersion")
    implementation("cn.hutool:hutool-json:$hutoolVersion")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.zxing:core:3.5.3")

    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.github.daniel-stoneuk:material-about-library:3.2.0-rc01")
    implementation("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.esotericsoftware:kryo:5.6.0")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.4")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    implementation("com.blacksquircle.ui:editorkit:2.0.0")
    implementation("com.blacksquircle.ui:language-json:2.0.0")


    implementation(project(":library:proto-stub"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

