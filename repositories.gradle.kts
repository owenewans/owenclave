rootProject.extra.apply {
    set("androidPluginVersion", "7.4.2")
    set("kotlinVersion", "1.9.22")
    set("hutoolVersion", "5.8.24")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}