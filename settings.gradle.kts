include(":library:proto")
include(":library:proto-stub")

include(":plugin:api")
include(":plugin:naive")

include(":app")

include(":desktop:shared")
include(":desktop:app")

rootProject.name = "Owenclave"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
    }
}
