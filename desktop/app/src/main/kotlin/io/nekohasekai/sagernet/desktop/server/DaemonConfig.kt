package io.nekohasekai.sagernet.desktop.server

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.desktop.DesktopRuntime
import java.io.File

/**
 * Configuration for the headless Mihomo-compatible Owenclave daemon.
 *
 * Precedence: CLI arguments > environment variables > `config.yaml` > defaults.
 */
data class DaemonSubscriptionSeed(
    val name: String,
    val url: String,
    val userAgent: String = "",
    val sendHwid: Boolean = false,
)

data class DaemonConfig(
    val configFile: File?,
    val dataDir: File,
    var mixedPort: Int = 7890,
    var socksPort: Int = 0,
    var httpPort: Int = 0,
    var tproxyPort: Int = 0,
    var allowLan: Boolean = true,
    var bindAddress: String = "0.0.0.0",
    var interfaceName: String = "",
    var mode: String = "rule",
    var logLevel: String = "info",
    var ipv6: Boolean = true,
    var fallbackToDirect: Boolean = false,
    var healthCheckInterval: Int = 0,
    val hasExplicitFallbackToDirect: Boolean = false,
    val hasExplicitHealthCheckInterval: Boolean = false,
    val externalController: String = "0.0.0.0:9090",
    val secret: String = "",
    val subscriptionUserAgent: String = "Owenclave/${BuildConfig.VERSION_NAME}",
    val autoUpdateMinutes: Int = 60,
    val seedSubscriptions: List<DaemonSubscriptionSeed> = emptyList(),
    val rawConfigText: String? = null,
) {

    fun toCoreLogLevel(): Int = when (logLevel.lowercase()) {
        "silent", "none" -> LogLevel.NONE
        "error" -> LogLevel.ERROR
        "warning", "warn" -> LogLevel.WARNING
        "debug" -> LogLevel.DEBUG
        else -> LogLevel.INFO
    }

    fun effectiveListenAddress(): String {
        if (!allowLan) return "127.0.0.1"
        val trimmed = bindAddress.trim()
        return if (trimmed.isEmpty() || trimmed == "*") "0.0.0.0" else trimmed
    }

    companion object {

        fun load(
            args: Array<String> = emptyArray(),
            env: Map<String, String> = System.getenv(),
        ): DaemonConfig {
            val cliConfig = argValue(args, "--config") ?: argValue(args, "-c") ?: argValue(args, "-f")
            val cliDataDir = argValue(args, "--data-dir") ?: argValue(args, "-d")

            val dataDir = when {
                !cliDataDir.isNullOrBlank() -> File(cliDataDir)
                !env["DATA_DIR"].isNullOrBlank() -> File(env.getValue("DATA_DIR"))
                File("/data").isDirectory && File("/data").canWrite() -> File("/data")
                else -> File(DesktopRuntime.dataDir, "daemon")
            }.apply { mkdirs() }

            val configCandidates = listOfNotNull(
                cliConfig?.let(::File),
                env["CONFIG_FILE"]?.takeIf { it.isNotBlank() }?.let(::File),
                File("/config/config.yaml"),
                File("/root/.config/mihomo/config.yaml"),
                File(dataDir, "config.yaml"),
            )
            val configFile = configCandidates.firstOrNull { it.isFile }
            val rawYamlText = configFile?.let { runCatching { it.readText() }.getOrNull() }
            val yaml = rawYamlText?.let { MihomoYamlSupport.loadYamlMap(it) }.orEmpty()

            fun yamlInt(vararg keys: String): Int? =
                keys.firstNotNullOfOrNull { k -> yaml[k]?.toString()?.trim()?.toIntOrNull() }

            fun yamlBool(vararg keys: String): Boolean? =
                keys.firstNotNullOfOrNull { k ->
                    yaml[k]?.toString()?.trim()?.lowercase()?.let {
                        when (it) {
                            "true", "1", "yes" -> true
                            "false", "0", "no" -> false
                            else -> null
                        }
                    }
                }

            fun yamlStr(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { k -> yaml[k]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }

            val mixedPort = argValue(args, "--mixed-port")?.toIntOrNull()
                ?: env["MIXED_PORT"]?.toIntOrNull()
                ?: yamlInt("mixed-port")
                ?: 7890

            val socksPort = argValue(args, "--socks-port")?.toIntOrNull()
                ?: env["SOCKS_PORT"]?.toIntOrNull()
                ?: yamlInt("socks-port")
                ?: 0

            val httpPort = argValue(args, "--http-port")?.toIntOrNull()
                ?: env["HTTP_PORT"]?.toIntOrNull()
                ?: yamlInt("port", "http-port")
                ?: 0

            val tproxyPort = argValue(args, "--tproxy-port")?.toIntOrNull()
                ?: env["TPROXY_PORT"]?.toIntOrNull()
                ?: yamlInt("tproxy-port")
                ?: 0

            val allowLan = env["ALLOW_LAN"]?.lowercase()?.let { it == "true" || it == "1" }
                ?: yamlBool("allow-lan")
                ?: true

            val bindAddress = argValue(args, "--bind-address")
                ?: env["BIND_ADDRESS"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("bind-address")
                ?: "0.0.0.0"

            val interfaceName = argValue(args, "--interface-name")
                ?: env["INTERFACE_NAME"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("interface-name")
                ?: ""

            val mode = (argValue(args, "--mode")
                ?: env["MODE"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("mode")
                ?: "rule").lowercase().let {
                if (it in setOf("rule", "global", "direct")) it else "rule"
            }

            val logLevel = (argValue(args, "--log-level")
                ?: env["LOG_LEVEL"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("log-level")
                ?: "info").lowercase()

            val ipv6 = env["IPV6"]?.lowercase()?.let { it == "true" || it == "1" }
                ?: yamlBool("ipv6")
                ?: true

            val explicitFallbackToDirect = argValue(args, "--fallback-to-direct")?.lowercase()?.let { it == "true" || it == "1" }
                ?: env["FALLBACK_TO_DIRECT"]?.lowercase()?.let { it == "true" || it == "1" || it == "yes" }
                ?: yamlBool("fallback-to-direct", "bypass-on-unavailable")
            val fallbackToDirect = explicitFallbackToDirect ?: false

            val explicitHealthCheckInterval = argValue(args, "--health-check-interval")?.toIntOrNull()
                ?: env["HEALTH_CHECK_INTERVAL"]?.toIntOrNull()
                ?: yamlInt("health-check-interval")
            val healthCheckInterval = explicitHealthCheckInterval ?: 0

            val controller = argValue(args, "--controller")
                ?: env["EXTERNAL_CONTROLLER"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("external-controller")
                ?: "0.0.0.0:9090"

            val secret = argValue(args, "--secret")
                ?: env["SECRET"]?.takeIf { it.isNotBlank() }
                ?: env["API_SECRET"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("secret")
                ?: ""

            val defaultUa = env["SUBSCRIPTION_USER_AGENT"]?.takeIf { it.isNotBlank() }
                ?: yamlStr("global-ua", "subscription-user-agent")
                ?: "Owenclave/${BuildConfig.VERSION_NAME}"

            val autoUpdateMinutes = env["AUTO_UPDATE_MINUTES"]?.toIntOrNull()
                ?: yamlInt("auto-update-minutes")
                ?: 60

            val seeds = ArrayList<DaemonSubscriptionSeed>()

            // 1. Mihomo `proxy-providers` (type: http, url: ...)
            val proxyProviders = yaml["proxy-providers"] as? Map<*, *>
            proxyProviders?.forEach { (k, v) ->
                val spec = v as? Map<*, *> ?: return@forEach
                val url = spec["url"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                val name = k?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Provider"
                seeds += DaemonSubscriptionSeed(name = name, url = url, userAgent = defaultUa)
            }

            // 2. Owenclave `subscriptions` list in config.yaml
            val yamlSubs = yaml["subscriptions"] as? List<*>
            yamlSubs?.forEachIndexed { idx, entry ->
                when (entry) {
                    is String -> entry.trim().takeIf { it.isNotEmpty() }?.let { url ->
                        seeds += DaemonSubscriptionSeed(name = "Subscription ${idx + 1}", url = url, userAgent = defaultUa)
                    }
                    is Map<*, *> -> {
                        val url = entry["url"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
                        val name = entry["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                            ?: "Subscription ${idx + 1}"
                        val ua = entry["user-agent"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: defaultUa
                        val hwid = entry["send-hwid"]?.toString()?.equals("true", ignoreCase = true) == true
                        seeds += DaemonSubscriptionSeed(name = name, url = url, userAgent = ua, sendHwid = hwid)
                    }
                }
            }

            // 3. SUBSCRIPTION_URL environment variable (newline or comma separated)
            env["SUBSCRIPTION_URL"]?.split('\n', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.forEachIndexed { idx, url ->
                    if (seeds.none { it.url == url }) {
                        val name = env["SUBSCRIPTION_NAME"]?.takeIf { it.isNotBlank() && idx == 0 }
                            ?: "Subscription ${seeds.size + 1}"
                        seeds += DaemonSubscriptionSeed(name = name, url = url, userAgent = defaultUa)
                    }
                }

            return DaemonConfig(
                configFile = configFile,
                dataDir = dataDir,
                mixedPort = mixedPort,
                socksPort = socksPort,
                httpPort = httpPort,
                tproxyPort = tproxyPort,
                allowLan = allowLan,
                bindAddress = bindAddress,
                interfaceName = interfaceName,
                mode = mode,
                logLevel = logLevel,
                ipv6 = ipv6,
                fallbackToDirect = fallbackToDirect,
                healthCheckInterval = healthCheckInterval,
                hasExplicitFallbackToDirect = explicitFallbackToDirect != null,
                hasExplicitHealthCheckInterval = explicitHealthCheckInterval != null,
                externalController = controller,
                secret = secret,
                subscriptionUserAgent = defaultUa,
                autoUpdateMinutes = autoUpdateMinutes,
                seedSubscriptions = seeds,
                rawConfigText = rawYamlText,
            )
        }

        private fun argValue(args: Array<String>, flag: String): String? {
            val idx = args.indexOf(flag)
            if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]
            val prefix = "$flag="
            return args.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        }

    }

}

