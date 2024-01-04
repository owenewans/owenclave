include(":library:stub")
include(":library:include")
include(":library:proto")
include(":library:proto-stub")

include(":plugin:api")

val buildPlugin = System.getenv("BUILD_PLUGIN")
when {
    buildPlugin.isNullOrBlank() -> {
        include(":plugin:naive")
        include(":plugin:brook")
        include(":plugin:hysteria2")
        include(":plugin:mieru2")
        include(":plugin:shadowtls")
    }
    buildPlugin == "none" -> {
    }
    else -> {
        include(":plugin:$buildPlugin")
    }
}

includeBuild("external/termux-view") {
    name = "termux-view"
    dependencySubstitution {
        substitute(module("termux:terminal-view:1.0")).using(project(":terminal-view"))
    }
}

include(":app")
rootProject.name = "SagerNet"