plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    val androidPluginVersion = rootProject.extra["androidPluginVersion"].toString()
    val kotlinVersion = rootProject.extra["kotlinVersion"].toString()
    val hutoolVersion = rootProject.extra["hutoolVersion"].toString()
    implementation("com.android.tools.build:gradle:$androidPluginVersion")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("cn.hutool:hutool-http:$hutoolVersion")
    implementation("cn.hutool:hutool-crypto:$hutoolVersion")
    implementation("org.tukaani:xz:1.9")
    implementation("org.kohsuke:github-api:1.321")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.51.0")
}