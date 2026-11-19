package io.nekohasekai.sagernet.desktop

/**
 * How one Android global preference key is honoured on the desktop client.
 *
 * This table is the *only* place the Android settings screen and the desktop
 * core config meet. It is maintained by hand on purpose: adding a preference on
 * Android makes [SettingsCatalogParser] pick it up, and the desktop settings
 * test fails the build until the key gets a binding here. That is the drift
 * guard: nothing is silently dropped and nothing is silently ignored.
 */
sealed interface Binding {

    /** Optional human readable note shown by `--print-settings`. */
    val note: String?

    /**
     * Null when the preference is usable on [os] (`linux`, `windows`, `darwin`),
     * otherwise the reason the row is rendered disabled there.
     */
    fun disabledReason(os: String): String? = null

    /**
     * Desktop-only dropdown options replacing the ones declared in the Android
     * XML, or null to keep the Android list. Keyed by OS; `*` is the fallback.
     */
    fun desktopOptions(os: String): List<MenuOption>? = null

}

/**
 * Pushed into the shared `DataStore` shim, which is the object the Android
 * `ConfigBuilder.kt` reads. [member] is the shim property name; it is usually
 * the same as the preference key, the exceptions are documented in
 * [SettingsBindings].
 */
data class DataStoreMember(val member: String) : Binding {
    override val note: String? = null
}

/**
 * Handled by a desktop-only setting or a deliberate desktop override rather
 * than by a plain shim property (for example the LAN bypass rule, or the
 * route mode that desktop expresses in `postProcess`).
 */
data class DesktopOverride(
    override val note: String,
    private val optionsByOs: Map<String, List<MenuOption>>? = null,
) : Binding {
    override fun desktopOptions(os: String): List<MenuOption>? =
        optionsByOs?.let { it[os] ?: it["*"] }
}

/**
 * Parsed and stored, but not consumed by the desktop core config (for example
 * the Android rule asset URLs, which only the Android updater downloads).
 * Kept enabled so the screen stays one to one.
 */
data class StoredOnly(override val note: String) : Binding

/**
 * No desktop equivalent. The row stays in its original section (structural
 * parity) but is rendered disabled with [reason].
 */
data class AndroidOnly(val reason: String) : Binding {
    override val note: String? = null
    override fun disabledReason(os: String): String = reason
}

/**
 * A preference the desktop supports on some platforms only: [reasons] maps an OS
 * (`linux`, `windows`, `darwin`, `*`) to the disabled reason, and a null value
 * means the row is enabled there. Used by the per-app rows, which are real on
 * Linux and honestly out of scope on Windows and macOS.
 */
data class PlatformOnly(
    private val reasons: Map<String, String?>,
    override val note: String? = null,
) : Binding {
    override fun disabledReason(os: String): String? =
        if (reasons.containsKey(os)) reasons[os] else reasons["*"]
}

/**
 * An Android plain `Preference` action the desktop implements too: the row stays
 * enabled and activating it runs [action] (see the Settings renderer).
 */
data class DesktopAction(val action: String) : Binding {
    override val note: String? = null
}

/** A key the desktop does not know about; only possible before the table is updated. */
data class Unsupported(val key: String) : Binding {
    override val note: String? = null
}

/**
 * The Android key -> desktop binding table.
 *
 * Grouped by the Android section so it can be reviewed against
 * `app/src/main/res/xml/global_preferences.xml`. Keys whose shim member differs
 * from the key itself are marked `NOTE`.
 */
object SettingsBindings {

    /** Android-only reason strings, kept short and specific. */
    private const val NO_NOTIFICATION = "Android only: the desktop client has no notifications"
    private const val NO_PACKAGE_MANAGER = "Android only: Android package manager feature"

    /**
     * Per-app routing is real on Linux (see [PerAppRouting]) and explicitly not
     * faked on Windows and macOS: the honest answer is that it would need a
     * kernel driver / system extension this application does not ship.
     */
    private const val PER_APP_WINDOWS =
        "Windows: per-app routing needs a WFP callout driver, which is out of scope for this " +
            "application. Use the desktop rule list instead."
    private const val PER_APP_MACOS =
        "macOS: per-app routing needs a NetworkExtension, which is out of scope for this " +
            "application. Use the desktop rule list instead."
    private const val PER_APP_BYPASS_LINUX =
        "Android only: per-app bypass is not implemented; the desktop supports an include list " +
            "instead (Desktop only → Per-app routing)"

    private val TABLE: Map<String, Binding> = linkedMapOf(
        // ------------------------------------------------------- App settings
        // Adapted for desktop: connect the selected profile when the client starts.
        "isAutoConnect" to DesktopOverride("auto connect the selected profile on desktop startup"),
        "appTheme" to DesktopOverride("the desktop window accent colour"),
        // Adapted for desktop: light / dark / follow the system theme.
        "nightTheme" to DesktopOverride("the desktop light/dark theme"),
        "appLanguage" to AndroidOnly("Android only: the desktop UI ships in English"),
        // Adapted for desktop: Android "VPN" is the desktop TUN device, "Proxy
        // only" keeps the local SOCKS/HTTP inbounds, and the desktop adds a third
        // mode that points the operating system proxy at the local HTTP port.
        // (The Android VpnService itself has no desktop counterpart, the TUN
        // device is created with tun2socks.)
        "serviceMode" to DesktopOverride(
            note = "desktop service mode: VPN = TUN device, System proxy = the OS proxy, " +
                "Proxy only = local inbounds",
            optionsByOs = mapOf(
                "*" to listOf(
                    MenuOption("VPN (TUN device)", "vpn"),
                    MenuOption("System proxy", "system"),
                    MenuOption("Proxy only", "proxy"),
                ),
            ),
        ),
        "tunImplementation" to AndroidOnly("Android only: the desktop TUN engine (tun2socks) has a single userspace stack"),
        // NOTE: key `mtu` (VpnService MTU) drives the desktop TUN MTU field.
        "mtu" to DesktopOverride("the desktop TUN device MTU"),
        "meteredNetwork" to AndroidOnly("Android only: the desktop client has no metered-network handling"),
        "enablePcap" to AndroidOnly("Android only: packet capture is done inside the Android VpnService"),
        "discardICMP" to AndroidOnly("Android only: the desktop tun2socks TUN stack does not expose ICMP policy"),
        "appTrafficStatistics" to AndroidOnly("Android only: the desktop client has no per-app traffic statistics"),
        "profileTrafficStatistics" to DataStoreMember("profileTrafficStatistics"),
        "speedInterval" to AndroidOnly(NO_NOTIFICATION),
        "showDirectSpeed" to AndroidOnly(NO_NOTIFICATION),
        "logLevel" to DataStoreMember("logLevel"),
        "providerRootCA" to StoredOnly("Android core root CA provider; the desktop core keeps its system roots"),

        // ----------------------------------------------------- Route settings
        // Adapted for desktop: adds the IPv6 address and split default routes to
        // the desktop TUN device (see TunSession).
        "enableVPNInterfaceIPv6Address" to DesktopOverride("IPv6 address and routes in the desktop TUN device"),
        // Real on Linux through the cgroup v2 + nftables layer in PerAppRouting;
        // explicitly out of scope (not faked) on Windows and macOS.
        "proxyApps" to PlatformOnly(
            reasons = mapOf(
                "linux" to null,
                "windows" to PER_APP_WINDOWS,
                "darwin" to PER_APP_MACOS,
                "*" to PER_APP_WINDOWS,
            ),
            note = "desktop: route only the processes listed under Desktop only → " +
                "Per-app routing through the TUN device (Linux)",
        ),
        "allowAppsBypassVpn" to PlatformOnly(
            reasons = mapOf(
                "linux" to PER_APP_BYPASS_LINUX,
                "windows" to PER_APP_WINDOWS,
                "darwin" to PER_APP_MACOS,
                "*" to PER_APP_WINDOWS,
            ),
        ),
        // NOTE: ConfigBuilder does not read bypassLan; the desktop turns it into
        // the "Bypass private networks" routing rule (see DesktopConfigBuilder).
        "bypassLan" to DesktopOverride("the desktop \"Bypass private networks\" routing rule"),
        "domainStrategy" to DataStoreMember("domainStrategy"),
        "trafficSniffing" to DataStoreMember("trafficSniffing"),
        "destinationOverride" to DataStoreMember("destinationOverride"),
        // NOTE: key `hijackDns0` -> shim member `hijackDns`.
        "hijackDns0" to DataStoreMember("hijackDns"),
        "outboundDomainStrategy" to DataStoreMember("outboundDomainStrategy"),
        "outboundDomainStrategyForDirect" to DataStoreMember("outboundDomainStrategyForDirect"),
        "outboundDomainStrategyForServer" to DataStoreMember("outboundDomainStrategyForServer"),
        "rulesProvider" to StoredOnly("Android rule asset provider; the desktop client does not download rule assets"),
        "rulesGeositeUrl" to StoredOnly("Android geosite asset URL; stored for parity"),
        "rulesGeoipUrl" to StoredOnly("Android geoip asset URL; stored for parity"),
        // NOTE: the desktop expresses RouteMode.DIRECT by rewriting the upstream
        // outbound to freedom instead of emitting the Android bypass rule, and it
        // always evaluates the desktop rule list (see DesktopConfigBuilder).
        "routeMode" to DesktopOverride("the desktop route mode (desktop rules, proxy all or direct only)"),

        // -------------------------------------------------- Protocol settings
        "connectionTestURL" to DataStoreMember("connectionTestURL"),
        "socksProxyChainEnabled" to DataStoreMember("socksProxyChainEnabled"),
        "socksProxyChainHost" to DataStoreMember("socksProxyChainHost"),
        "socksProxyChainPort" to DataStoreMember("socksProxyChainPort"),
        "socksProxyChainUsername" to DataStoreMember("socksProxyChainUsername"),
        "socksProxyChainPassword" to DataStoreMember("socksProxyChainPassword"),
        "enableTwps2" to StoredOnly("Android plugin feature (zapret2); the desktop core ignores it"),
        "enableUnlockRu" to DataStoreMember("enableUnlockRu"),
        "directProxyMode" to DataStoreMember("directProxyMode"),
        "realityDisableX25519Mlkem768" to DataStoreMember("realityDisableX25519Mlkem768"),
        "hysteria2OmitMaxDatagramFrameSize" to DataStoreMember("hysteria2OmitMaxDatagramFrameSize"),
        "grpcServiceNameCompat" to DataStoreMember("grpcServiceNameCompat"),
        "enableFragment" to DataStoreMember("enableFragment"),
        "fragmentMethod" to DataStoreMember("fragmentMethod"),
        "enableFragmentForDirect" to DataStoreMember("enableFragmentForDirect"),
        // NOTE: key `interruptReusedConnections0` -> shim member `interruptReusedConnections`.
        "interruptReusedConnections0" to DataStoreMember("interruptReusedConnections"),
        // Adapted for desktop: shows the insecure-profile warning in the profile list.
        "profileSecurityAdvisory" to DesktopOverride("the insecure profile warning in the desktop profile list"),

        // ----------------------------------------------------------------- HWID
        // NOTE: sendHwid is used by the Android subscription updater only; the
        // desktop keeps it as its own typed setting (used for the same headers).
        "sendHwid" to DesktopOverride("the desktop HWID reporting default"),
        // Adapted for desktop: the row regenerates the desktop device identity.
        "resetHwid" to DesktopAction("resetHwid"),

        // ----------------------------------------------------------- DNS settings
        "remoteDns" to DataStoreMember("remoteDns"),
        "remoteDnsQueryStrategy" to DataStoreMember("remoteDnsQueryStrategy"),
        "ednsClientIp" to DataStoreMember("ednsClientIp"),
        "useLocalDnsAsDirectDns" to DataStoreMember("useLocalDnsAsDirectDns"),
        "directDns" to DataStoreMember("directDns"),
        "directDnsQueryStrategy" to DataStoreMember("directDnsQueryStrategy"),
        "useLocalDnsAsBootstrapDns" to DataStoreMember("useLocalDnsAsBootstrapDns"),
        "bootstrapDns" to DataStoreMember("bootstrapDns"),
        // NOTE: key `dnsHosts0` -> shim member `hosts`.
        "dnsHosts0" to DataStoreMember("hosts"),
        "enableDnsRouting" to DataStoreMember("enableDnsRouting"),
        "enableFakeDns" to DataStoreMember("enableFakeDns"),

        // ------------------------------------------------------- Inbound settings
        "requireSocks" to DataStoreMember("requireSocks"),
        "socksPort" to DataStoreMember("socksPort"),
        "socksUsername" to DataStoreMember("socksUsername"),
        "socksPassword" to DataStoreMember("socksPassword"),
        "socksUDP" to DataStoreMember("socksUDP"),
        "requireHttp" to DataStoreMember("requireHttp"),
        "httpPort" to DataStoreMember("httpPort"),
        "httpUsername" to DataStoreMember("httpUsername"),
        "httpPassword" to DataStoreMember("httpPassword"),
        "appendHttpProxy" to AndroidOnly("Android only: Android sets the VPN HTTP proxy; the desktop uses Service mode = System proxy"),
        "httpProxyException" to AndroidOnly("Android only: Android sets the VPN HTTP proxy; the desktop uses Service mode = System proxy"),
        "requireTransproxy" to AndroidOnly("Android only: use Service mode = VPN (the desktop TUN device) instead of iptables tproxy"),
        "transproxyPort" to AndroidOnly("Android only: use Service mode = VPN (the desktop TUN device) instead of iptables tproxy"),
        // Adapted for desktop: the core listens for DNS on 127.0.0.1:<portLocalDns>
        // (the Android UDS variant is dropped by DesktopConfigBuilder.postProcess).
        "requireDnsInbound" to DataStoreMember("requireDnsInbound"),
        // NOTE: key `portLocalDns` -> shim member `localDNSPort`.
        "portLocalDns" to DataStoreMember("localDNSPort"),
        "allowAccess" to DataStoreMember("allowAccess"),

        // ---------------------------------------------------------- Misc settings
        "showGroupName" to AndroidOnly(NO_NOTIFICATION),
        // Adapted for desktop: hide or show the address in the desktop profile list.
        "alwaysShowAddress" to DesktopOverride("show the server address in the desktop profile list"),
        "acquireWakeLock" to AndroidOnly("Android only: keeps the Android VPN service alive"),
        "stunServers" to StoredOnly("used by the Android STUN test; stored for parity"),
        "fabStyle" to AndroidOnly("Android only: the floating action button"),
        "useIECUnit" to AndroidOnly("Android only: statistics formatting in the Android UI"),
        "queryAllPackagesAlternativeMethod" to AndroidOnly(NO_PACKAGE_MANAGER),
        "pprofServer" to StoredOnly("Android debug instance pprof listener; stored for parity"),
        "goDebug" to StoredOnly("Go runtime GODEBUG setting; applied on startup"),
        "environmentVariables" to StoredOnly("Go runtime environment variables; applied on startup"),
        "experimentalFlags" to DataStoreMember("experimentalFlagsProperties"),
    )

    /** The binding for [key], or [Binding.Unsupported] when the table is stale. */
    fun bindingFor(key: String): Binding = TABLE[key] ?: Unsupported(key)

    /** Keys the table knows, for the drift test (a key missing from the XML is stale). */
    val keys: Set<String> get() = TABLE.keys

    /** The whole table, for documentation and the drift test. */
    val entries: Map<String, Binding> get() = TABLE

}
