plugins {
    id("com.android.application")
    id("kotlin-parcelize")
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

setupApp()

android {
    namespace = "io.nekohasekai.sagernet"
}

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
