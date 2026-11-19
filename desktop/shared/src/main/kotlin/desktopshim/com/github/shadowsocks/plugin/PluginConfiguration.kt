package com.github.shadowsocks.plugin

import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.utils.Commandline
import java.util.LinkedList

/**
 * Desktop replacement for the Android `PluginConfiguration`.
 *
 * The Android version looks up the plugin's default configuration through
 * `PluginManager` (an Android plugin registry). On desktop there is no plugin
 * registry, so the default is empty; everything else is identical, which keeps
 * SIP003 plugin strings (`plugin=obfs-local;obfs=http;obfs-host=...`)
 * round-tripping exactly like on Android.
 */
class PluginConfiguration(val pluginsOptions: MutableMap<String, PluginOptions>, var selected: String) {

    private constructor(plugins: List<PluginOptions>) : this(
        plugins.filter { it.id.isNotEmpty() }.associateBy { it.id }.toMutableMap(),
        if (plugins.isEmpty()) "" else plugins[0].id
    )

    constructor() : this(listOf())

    constructor(plugin: String) : this(plugin.split('\n').map { line ->
        if (line.startsWith("kcptun ")) {
            val opt = PluginOptions()
            opt.id = "kcptun"
            try {
                val iterator = Commandline.translateCommandline(line).drop(1).iterator()
                while (iterator.hasNext()) {
                    val option = iterator.next()
                    when {
                        option == "--nocomp" -> opt["nocomp"] = null
                        option.startsWith("--") -> opt[option.substring(2)] = iterator.next()
                        else -> throw IllegalArgumentException("Unknown kcptun parameter: $option")
                    }
                }
            } catch (exc: Exception) {
                Logs.w(exc)
            }
            opt
        } else PluginOptions(line)
    })

    fun getOptions(
        id: String = selected,
        defaultConfig: () -> String? = { null },
    ) = if (id.isEmpty()) PluginOptions() else pluginsOptions[id] ?: PluginOptions(id, defaultConfig())

    override fun toString(): String {
        val result = LinkedList<PluginOptions>()
        for ((id, opt) in pluginsOptions) if (id == this.selected) result.addFirst(opt) else result.addLast(opt)
        if (!pluginsOptions.contains(selected)) result.addFirst(getOptions())
        return result.joinToString("\n") { it.toString(false) }
    }

}
