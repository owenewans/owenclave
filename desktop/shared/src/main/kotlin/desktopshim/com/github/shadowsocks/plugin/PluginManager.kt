package com.github.shadowsocks.plugin

import java.io.FileNotFoundException

/**
 * Desktop replacement for the Android plugin registry `PluginManager`.
 *
 * Android resolves a SIP003 plugin id (for example `v2ray-plugin`) to an
 * installed plugin APK and hands back the executable path plus its default
 * options. Desktop has no APK registry: plugins are ordinary binaries launched
 * as child processes, so the lookup is delegated to [resolver], a hook the
 * desktop client installs once at startup. That keeps `:desktop:shared`
 * independent of `:desktop:app` (the dependency runs the other way round).
 *
 * Wiring from the desktop app (exact signature):
 *
 * ```
 * PluginManager.resolver = { configuration ->
 *     when (configuration.selected) {
 *         "naive" -> {
 *             val binary = DesktopRuntime.naiveBinary() // or any located plugin binary
 *             PluginManager.InitResult(binary.absolutePath, configuration.getOptions(), isV2 = false)
 *         }
 *         // v2ray-plugin / obfs-local are compiled into the core on desktop; return
 *         // null (or do not set a resolver) and ConfigBuilder falls back to the
 *         // plugin id it already emitted.
 *         else -> null
 *     }
 * }
 * ```
 *
 * With no resolver installed (the default) every lookup reports the plugin as
 * missing, which is what [PluginNotFoundException] models. The shared
 * `ConfigBuilder.kt` catches that exception for the internally implemented
 * `v2ray-plugin` and `obfs-local` and keeps the plain plugin id, so the
 * non-plugin protocol paths are completely unaffected.
 */
object PluginManager {

    /** Mirrors `PluginManager.PluginNotFoundException`. */
    class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin)

    /** Mirrors `PluginManager.InitResult`. */
    data class InitResult(
        val path: String,
        val options: PluginOptions,
        val isV2: Boolean = false,
    )

    /**
     * Optional plugin resolver installed by the desktop client. Returns `null`
     * when it does not handle the configuration, in which case [init] throws
     * [PluginNotFoundException].
     */
    @Volatile
    var resolver: ((PluginConfiguration) -> InitResult?)? = null

    @Throws(Throwable::class)
    fun init(configuration: PluginConfiguration): InitResult? {
        if (configuration.selected.isEmpty()) return null
        resolver?.invoke(configuration)?.let { return it }
        throw PluginNotFoundException(configuration.selected)
    }

}
