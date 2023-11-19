rootProject.extra.apply {
    set("androidPluginVersion", "7.4.2")
    set("kotlinVersion", "1.9.20")
    set("hutoolVersion", "5.7.22")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}