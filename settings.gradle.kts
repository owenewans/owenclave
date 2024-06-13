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
        include(":plugin:mieru")
        include(":plugin:juicity")
    }
    buildPlugin == "none" -> {
    }
    else -> {
        include(":plugin:$buildPlugin")
    }
}

include(":app")

include(":library:termux:terminal-emulator")
include(":library:termux:terminal-view")

rootProject.name = "SagerNet"