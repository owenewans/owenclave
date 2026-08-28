plugins {
    id("com.android.application")
    id("kotlin-parcelize")
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.compose.compiler)
}

setupApp()

android {
    namespace = "io.nekohasekai.sagernet"

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../owenclave.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "owenclave"
            keyAlias = System.getenv("KEY_ALIAS") ?: "owenclave"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "owenclave"
        }
    }
}

android.buildTypes["release"].signingConfig = android.signingConfigs["release"]

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("src/main/aboutlibraries/config")
        includePlatform = true
    }
    export {
        excludeFields.addAll("name", "description", "developers", "funding", "licenses", "organization", "scm", "website", "License")
        prettyPrint = true
    }
    exports {
        create("ossRelease") {
            outputFile = file("src/main/aboutlibraries/aboutlibraries.json")
        }
        create("legacyRelease") {
            outputFile = file("src/main/aboutlibraries/aboutlibraries_legacy.json")
        }
    }
}

dependencies {
    implementation(fileTree("libs"))
    implementation(project(":plugin:api"))
    implementation(project(":library:proto-stub"))
    implementation(libs.kotlinx.coroutines.android)
    "ossImplementation"(libs.core.ktx)
    "ossImplementation"(libs.activity.ktx)
    "ossImplementation"(libs.fragment.ktx)
    "ossImplementation"(libs.camera.view)
    "ossImplementation"(libs.camera.lifecycle)
    "ossImplementation"(libs.camera.camera2)
    implementation(libs.swiperefreshlayout)
    "ossImplementation"(libs.appcompat)
    implementation(libs.preference)
    implementation(libs.flexbox)
    "ossImplementation"(libs.work.runtime.ktx)
    "ossImplementation"(libs.work.multiprocess)
    "ossImplementation"(libs.room.runtime)
    "kspOss"(libs.room.compiler)
    "ossImplementation"(libs.room.ktx)
    "ossImplementation"(libs.material)
    implementation(libs.gson)
    implementation(libs.zxing.core)
    implementation(libs.snakeyaml)
    implementation(libs.material.about.library)
    implementation(libs.process.phoenix)
    implementation(libs.kryo)
    implementation(libs.jini.lib)
    implementation(libs.markwon.core)
    implementation(libs.recyclerview.fastscroll) {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }
    implementation(libs.editorkit)
    implementation(libs.editorkit.language.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.graphics.shapes)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    "legacyImplementation"(libs.core.ktx.minSdk21)
    "legacyImplementation"(libs.activity.ktx.minSdk21)
    "legacyImplementation"(libs.fragment.ktx.minSdk21)
    "legacyImplementation"(libs.camera.view.minSdk21)
    "legacyImplementation"(libs.camera.lifecycle.minSdk21)
    "legacyImplementation"(libs.camera.camera2.minSdk21)
    "legacyImplementation"(libs.appcompat.minSdk21)
    "legacyImplementation"(libs.work.runtime.ktx.minSdk21)
    "legacyImplementation"(libs.work.multiprocess.minSdk21)
    "legacyImplementation"(libs.room.runtime.minSdk21)
    "kspLegacy"(libs.room.compiler.minSdk21)
    "legacyImplementation"(libs.room.ktx.minSdk21)
    "legacyImplementation"(libs.material.minSdk21)
}
