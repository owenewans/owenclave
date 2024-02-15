rootProject.extra.apply {
    set("androidPluginVersion", "8.2.2")
    set("kotlinVersion", "1.9.22")
    set("hutoolVersion", "5.8.26")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}