plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    implementation("com.android.tools.build:gradle:8.5.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    implementation("cn.hutool:hutool-http:5.8.31")
    implementation("cn.hutool:hutool-crypto:5.8.31")
    implementation("org.tukaani:xz:1.10")
    implementation("org.kohsuke:github-api:1.324")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.51.0")
}