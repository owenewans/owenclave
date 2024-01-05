rootProject.extra.apply {
    set("androidPluginVersion", "8.2.1")
    set("kotlinVersion", "1.9.22")
    set("hutoolVersion", "5.8.24")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}