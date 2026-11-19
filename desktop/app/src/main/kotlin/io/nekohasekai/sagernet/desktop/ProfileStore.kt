package io.nekohasekai.sagernet.desktop

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.RouteMode
import java.io.File

/** Desktop client settings. */
class DesktopSettings(
    var socksPort: Int = 10808,
    var httpPort: Int = 10809,
    /** [ROUTE_GLOBAL] proxies everything, [ROUTE_DIRECT] connects without a proxy. */
    var routeMode: String = ROUTE_GLOBAL,
    var logLevel: Int = LogLevel.INFO,
    var selectedProfileId: String? = null,
    /** Keep local/LAN traffic outside of the tunnel, like the Android default. */
    var bypassPrivateNetworks: Boolean = true,
    /** Send the device HWID to subscription servers, the Android global switch. */
    var sendHwid: Boolean = false,
    /** Client owned device id, see [currentHwid]. */
    var hwidValue: String = "",
    /** Fetch subscriptions through the running core, which also bypasses local filtering. */
    var fetchSubscriptionsThroughProxy: Boolean = true,
    /**
     * The Android "Service mode" row: [SERVICE_VPN] creates the desktop TUN
     * device, [SERVICE_SYSTEM] points the operating system proxy at the local
     * HTTP port, [SERVICE_PROXY] only keeps the local SOCKS/HTTP inbounds.
     */
    var serviceMode: String = SERVICE_PROXY,
    /** TUN interface name on Linux, empty for the default. */
    var tunInterface: String = "",
    var tunMtu: Int = 1500,
    /**
     * Linux per-app routing: process names (or absolute paths) whose traffic goes
     * through the TUN device. Only read when Service mode = VPN and the Android
     * `proxyApps` row is on. See [PerAppRouting].
     */
    var perAppProcesses: String = "",
    /**
     * Values of the Android global preferences, keyed by the constants in
     * [io.nekohasekai.sagernet.Key]. The keys backed by a typed field above
     * ([Key.SOCKS_PORT], [Key.HTTP_PORT], [Key.LOG_LEVEL], [Key.ROUTE_MODE],
     * [Key.SEND_HWID], [Key.BYPASS_LAN], [Key.MTU]) are not duplicated here.
     *
     * Keys without a stored value fall back to the default declared in the
     * Android `global_preferences.xml`, so the whole Android screen reaches the
     * core config even when the user never opened it.
     */
    val preferences: MutableMap<String, String> = LinkedHashMap(),
) {

    /** Transparent mode: capture all system traffic through a TUN device. */
    val tunEnabled: Boolean get() = serviceMode == SERVICE_VPN

    /** System proxy mode: point the OS proxy at the local HTTP port on connect. */
    val systemProxyEnabled: Boolean get() = serviceMode == SERVICE_SYSTEM

    companion object {
        const val ROUTE_GLOBAL = "global"
        const val ROUTE_DIRECT = "direct"

        /** The Android `routeMode` "rule" value; the desktop rule list is always evaluated. */
        const val ROUTE_RULE = "rule"

        /** Android `serviceMode` values, plus the desktop-only "system" mode. */
        const val SERVICE_VPN = "vpn"
        const val SERVICE_PROXY = "proxy"
        const val SERVICE_SYSTEM = "system"

        /**
         * Desktop defaults that intentionally differ from the Android XML/defaults
         * and have to win over them:
         *  - `requireHttp`: the desktop client always offers the HTTP inbound
         *    (its Settings text and ports have always assumed that), while the
         *    Android default is SOCKS only.
         *  - `profileTrafficStatistics`: the desktop client has no statistics
         *    store, so the policy counters stay off unless the user turns them on.
         */
        private val DESKTOP_DEFAULTS = mapOf(
            Key.REQUIRE_HTTP to "true",
            Key.PROFILE_TRAFFIC_STATISTICS to "false",
            // The desktop profile list has always shown the server address.
            Key.ALWAYS_SHOW_ADDRESS to "true",
        )

        /**
         * Android `DataStore` defaults for the keys whose `global_preferences.xml`
         * entry declares no `defaultValue`. Applying them explicitly keeps every
         * build deterministic (the shim is a mutable singleton) instead of relying
         * on whatever a previous build left behind.
         */
        private val ANDROID_DATASTORE_DEFAULTS = mapOf(
            Key.PERSIST_ACROSS_REBOOT to "false",
            Key.SOCKS_PROXY_CHAIN_ENABLED to "false",
            Key.SOCKS_PROXY_CHAIN_HOST to "",
            Key.SOCKS_PROXY_CHAIN_PORT to "0",
            Key.SOCKS_PROXY_CHAIN_USERNAME to "",
            Key.SOCKS_PROXY_CHAIN_PASSWORD to "",
            Key.ENABLE_UNLOCK_RU to "false",
            Key.DIRECT_PROXY_MODE to "false",
            Key.REALITY_DISABLE_X25519MLKEM768 to "false",
            Key.HYSTERIA2_OMIT_MAX_DATAGRAM_FRAME_SIZE to "false",
            Key.GRPC_SERVICE_NAME_COMPAT to "false",
            Key.ENABLE_FRAGMENT to "false",
            Key.FRAGMENT_METHOD to "0",
            Key.ENABLE_FRAGMENT_FOR_DIRECT to "false",
            Key.EDNS_CLIENT_IP to "",
            Key.DIRECT_DNS to "tcp://1.1.1.1",
            Key.BOOTSTRAP_DNS to "",
            Key.DNS_HOSTS to "",
            Key.ENABLE_FAKEDNS to "false",
            Key.HTTP_USERNAME to "",
            Key.HTTP_PASSWORD to "",
            Key.ALLOW_ACCESS to "false",
            Key.LOCAL_DNS_PORT to "6450",
            Key.EXPERIMENTAL_FLAGS to "",
            // Android-only rows keep a readable CURRENT value in `--print-settings`.
            Key.METERED_NETWORK to "false",
            Key.ENABLE_PCAP to "false",
            Key.APP_TRAFFIC_STATISTICS to "false",
            Key.SHOW_DIRECT_SPEED to "false",
            Key.ACQUIRE_WAKE_LOCK to "false",
            Key.USE_IEC_UNIT to "false",
            Key.SHOW_GROUP_NAME to "false",
            Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS to "false",
            Key.PROXY_APPS to "false",
            Key.ALLOW_APPS_BYPASS_VPN to "false",
            Key.HTTP_PROXY_EXCEPTION to "",
            Key.REQUIRE_TRANSPROXY to "false",
            Key.TRANSPROXY_PORT to "9200",
            Key.QUERY_ALL_PACKAGES_ALTERNATIVE_METHOD to "false",
            Key.APP_LANGUAGE to "",
            Key.ENABLE_TWPS2 to "false",
            Key.STUN_SERVERS to "",
            Key.PPROF_SERVER to "",
            Key.GODEBUG to "",
            Key.ENVIRONMENT_VARIABLES to "",
        )

        /** The preference keys backed by a typed desktop field, or null. */
        private fun typedValue(settings: DesktopSettings, key: String): String? = when (key) {
            Key.SOCKS_PORT -> settings.socksPort.toString()
            Key.HTTP_PORT -> settings.httpPort.toString()
            Key.LOG_LEVEL -> settings.logLevel.toString()
            // Android "Service mode" maps to the desktop service mode: VPN = the
            // desktop TUN device, System proxy = the OS proxy, Proxy only = local
            // SOCKS/HTTP inbounds. The desktop default is "proxy" because both a
            // TUN device and the OS proxy touch the host; the Android default is
            // "vpn".
            Key.SERVICE_MODE -> settings.serviceMode
            Key.ROUTE_MODE -> when (settings.routeMode) {
                ROUTE_DIRECT -> RouteMode.DIRECT.toString()
                ROUTE_RULE -> RouteMode.RULE.toString()
                else -> RouteMode.GLOBAL.toString()
            }
            Key.SEND_HWID -> settings.sendHwid.toString()
            Key.BYPASS_LAN -> settings.bypassPrivateNetworks.toString()
            Key.MTU -> settings.tunMtu.toString()
            else -> null
        }
    }

    /**
     * The value stored for [key], independent of whether a storable value exists:
     * typed field, then explicit desktop default, then the Android XML default,
     * then the Android `DataStore` default. Null means "never set anywhere".
     */
    fun storedOrDefault(key: String): String? =
        typedValue(this, key)
            ?: preferences[key]?.takeIf { it.isNotEmpty() }
            ?: DESKTOP_DEFAULTS[key]
            ?: SettingsCatalogParser.load().defaultValueOf(key)
            ?: ANDROID_DATASTORE_DEFAULTS[key]

    /** Current value of an Android preference key, for display and persistence. */
    fun value(key: String): String = storedOrDefault(key).orEmpty()

    /** Stores an Android preference key, routing the typed keys to their field. */
    fun setValue(key: String, value: String) {
        when (key) {
            Key.SOCKS_PORT -> value.toIntOrNull()?.let { socksPort = it }
            Key.HTTP_PORT -> value.toIntOrNull()?.let { httpPort = it }
            Key.LOG_LEVEL -> value.toIntOrNull()?.let { logLevel = it }
            // VPN enables the desktop TUN device, System proxy points the OS proxy
            // at the local HTTP port, Proxy only keeps the local inbounds.
            Key.SERVICE_MODE -> if (value.isNotBlank()) serviceMode = value
            Key.ROUTE_MODE -> routeMode = when (value.toIntOrNull()) {
                RouteMode.DIRECT -> ROUTE_DIRECT
                RouteMode.RULE -> ROUTE_RULE
                else -> ROUTE_GLOBAL
            }
            Key.SEND_HWID -> sendHwid = value.toBooleanStrictOrNull() ?: false
            Key.BYPASS_LAN -> bypassPrivateNetworks = value.toBooleanStrictOrNull() ?: true
            Key.MTU -> value.toIntOrNull()?.let { tunMtu = it }
            else -> preferences[key] = value
        }
    }

    /** Settings are immutable for the UI: every change copies the whole object. */
    fun copy(
        socksPort: Int = this.socksPort,
        httpPort: Int = this.httpPort,
        routeMode: String = this.routeMode,
        logLevel: Int = this.logLevel,
        selectedProfileId: String? = this.selectedProfileId,
        bypassPrivateNetworks: Boolean = this.bypassPrivateNetworks,
        sendHwid: Boolean = this.sendHwid,
        hwidValue: String = this.hwidValue,
        fetchSubscriptionsThroughProxy: Boolean = this.fetchSubscriptionsThroughProxy,
        serviceMode: String = this.serviceMode,
        tunInterface: String = this.tunInterface,
        tunMtu: Int = this.tunMtu,
        perAppProcesses: String = this.perAppProcesses,
        preferences: Map<String, String> = LinkedHashMap(this.preferences),
    ) = DesktopSettings(
        socksPort, httpPort, routeMode, logLevel, selectedProfileId, bypassPrivateNetworks,
        sendHwid, hwidValue, fetchSubscriptionsThroughProxy, serviceMode, tunInterface, tunMtu,
        perAppProcesses, LinkedHashMap(preferences),
    )

    /** Returns a copy with one Android preference key changed. */
    fun withValue(key: String, value: String): DesktopSettings = copy().apply { setValue(key, value) }

}

/**
 * Profiles and settings, stored as a single JSON document in the per user data
 * directory. The Android app uses Room for this; on desktop a JSON file keeps the
 * data model inspectable and portable.
 */
class ProfileStore(private val file: File = File(DesktopRuntime.dataDir, "desktop.json")) {

    val subscriptions = ArrayList<Subscription>()

    val rules = ArrayList<RoutingRule>()

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val profiles = ArrayList<Profile>()

    var settings = DesktopSettings()

    fun load() {
        if (!file.isFile) return
        runCatching {
            val root = gson.fromJson(file.readText(), JsonObject::class.java) ?: return
            profiles.clear()
            root.getAsJsonArray("profiles")?.forEach { element ->
                if (element.isJsonObject) {
                    profiles.add(Profile.fromJson(element.asJsonObject))
                }
            }
            root.getAsJsonArray("subscriptions")?.forEach { element ->
                if (element.isJsonObject) {
                    subscriptions.add(Subscription.fromJson(element.asJsonObject))
                }
            }
            root.getAsJsonArray("rules")?.forEach { element ->
                if (element.isJsonObject) {
                    rules.add(RoutingRule.fromJson(element.asJsonObject))
                }
            }
            root.getAsJsonObject("settings")?.let { json ->
                val preferences = LinkedHashMap<String, String>()
                json.getAsJsonObject("preferences")?.entrySet()?.forEach { (key, value) ->
                    if (value.isJsonPrimitive) preferences[key] = value.asString
                }
                settings = DesktopSettings(
                    socksPort = json.get("socksPort")?.asInt ?: 10808,
                    httpPort = json.get("httpPort")?.asInt ?: 10809,
                    routeMode = json.get("routeMode")?.asString ?: DesktopSettings.ROUTE_GLOBAL,
                    logLevel = json.get("logLevel")?.asInt ?: LogLevel.INFO,
                    selectedProfileId = json.get("selectedProfileId")?.takeIf { !it.isJsonNull }?.asString,
                    bypassPrivateNetworks = json.get("bypassPrivateNetworks")?.asBoolean ?: true,
                    sendHwid = json.get("sendHwid")?.asBoolean ?: false,
                    hwidValue = json.get("hwidValue")?.asString.orEmpty(),
                    fetchSubscriptionsThroughProxy = json.get("fetchSubscriptionsThroughProxy")?.asBoolean ?: true,
                    // Older documents only stored the `tunEnabled` boolean.
                    serviceMode = json.get("serviceMode")?.asString
                        ?: if (json.get("tunEnabled")?.asBoolean == true) {
                            DesktopSettings.SERVICE_VPN
                        } else {
                            DesktopSettings.SERVICE_PROXY
                        },
                    tunInterface = json.get("tunInterface")?.asString.orEmpty(),
                    tunMtu = json.get("tunMtu")?.asInt ?: 1500,
                    perAppProcesses = json.get("perAppProcesses")?.asString.orEmpty(),
                    preferences = preferences,
                )
            }
        }.onFailure {
            io.nekohasekai.sagernet.ktx.Logs.w("failed to read ${file.path}", it)
        }
    }

    fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val root = JsonObject()
            root.add("settings", JsonObject().apply {
                addProperty("socksPort", settings.socksPort)
                addProperty("httpPort", settings.httpPort)
                addProperty("routeMode", settings.routeMode)
                addProperty("logLevel", settings.logLevel)
                addProperty("bypassPrivateNetworks", settings.bypassPrivateNetworks)
                addProperty("sendHwid", settings.sendHwid)
                addProperty("hwidValue", settings.hwidValue)
                addProperty("fetchSubscriptionsThroughProxy", settings.fetchSubscriptionsThroughProxy)
                addProperty("serviceMode", settings.serviceMode)
                addProperty("tunInterface", settings.tunInterface)
                addProperty("tunMtu", settings.tunMtu)
                addProperty("perAppProcesses", settings.perAppProcesses)
                settings.selectedProfileId?.let { addProperty("selectedProfileId", it) }
                add("preferences", JsonObject().apply {
                    settings.preferences.forEach { (key, value) -> addProperty(key, value) }
                })
            })
            root.add("subscriptions", JsonArray().apply {
                subscriptions.forEach { add(it.toJson()) }
            })
            root.add("rules", JsonArray().apply {
                rules.forEach { add(it.toJson()) }
            })
            root.add("profiles", JsonArray().apply {
                profiles.forEach { add(it.toJson()) }
            })
            file.writeText(gson.toJson(root))
        }.onFailure {
            io.nekohasekai.sagernet.ktx.Logs.w("failed to write ${file.path}", it)
        }
    }

}
