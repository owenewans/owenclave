rootProject.extra.apply {
    set("androidPluginVersion", "8.3.1")
    set("kotlinVersion", "1.9.23")
    set("hutoolVersion", "5.8.27")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}