plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    implementation("com.android.tools.build:gradle:8.5.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    implementation("cn.hutool:hutool-http:5.8.28")
    implementation("cn.hutool:hutool-crypto:5.8.28")
    implementation("org.tukaani:xz:1.9")
    implementation("org.kohsuke:github-api:1.321")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.51.0")
}