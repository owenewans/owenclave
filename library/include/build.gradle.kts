plugins {
    id("com.android.library")
}

setupCommon()

dependencies {
    implementation("androidx.annotation:annotation:1.8.0")
}
android {
    namespace = "com.android.stub"
}
