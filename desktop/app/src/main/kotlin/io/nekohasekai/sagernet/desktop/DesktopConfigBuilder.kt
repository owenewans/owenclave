package io.nekohasekai.sagernet.desktop

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.RouteMode
import io.nekohasekai.sagernet.TLS_FRAGMENTATION_METHOD
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean

/** Raised when a profile cannot be expressed as a V2Ray config for the core. */
class UnsupportedProfileException(message: String) : Exception(message)

/**
 * An external engine that has to be started next to the core because the core
 * does not implement the protocol itself (NaiveProxy).
 *
 * [bean] is the very same object that `buildV2RayConfig` used, so the mapping
 * fields it mutates (`finalAddress`/`finalPort`) are already applied when the
 * plugin config is generated from it.
 */
data class ExternalPlugin(
    val bean: AbstractBean,
    val port: Int,
    val username: String,
    val password: String,
)

/**
 * Additional inbounds injected for headless / Mihomo-style daemon mode:
 * - [mixedPort]: native `mixed` (HTTP + CONNECT + SOCKS5 + UDP) listener on [bindAddress].
 * - [tproxyPort]: optional Linux transparent proxy (`dokodemo-door` with `tproxy: "tproxy"`).
 */
data class ServerInboundOptions(
    val mixedPort: Int? = null,
    val bindAddress: String = "127.0.0.1",
    val username: String = "",
    val password: String = "",
    val tproxyPort: Int? = null,
    val probePort: Int? = null,
    val probeSecret: String = "",
)

/**
 * Automatic multi-outbound health-checked group (`fallback`, `url-test`, `load-balance`)
 * backed by `owenclave-core`'s `multiObservatory` + `routing.balancers`.
 */
data class BalancerSpec(
    val id: String,
    val profileIds: List<String>,
    val strategy: String = "leastPing",
    val stickyChoice: Boolean = false,
    val probeUrl: String = "",
    val probeIntervalSec: Int = 15,
    val fallbackToDirect: Boolean = false,
)

/** Result of [DesktopConfigBuilder.build]: the core JSON plus the plugins to run. */
class CoreConfig(val json: String, val plugins: List<ExternalPlugin>)

/**
 * Turns a desktop [Profile] into the JSON the core consumes.
 *
 * Unlike the earlier desktop-only builder this is a thin adapter over the real
 * Android generator (`fmt/ConfigBuilder.kt`, compiled into `:desktop:shared`),
 * so the desktop client understands every protocol the Android app does. The
 * shared generator is bound to the VPN service, Room and Android preferences, so
 * this adapter pushes the desktop settings into the `DataStore` shim, installs
 * the desktop routing rules on `SagerDatabase.rulesDao` and then strips the
 * Android-only inbounds from the result.
 */
object DesktopConfigBuilder {

    private val json = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /**
     * The same private-network list the pre-generator desktop builder emitted.
     * Kept in one rule so DNS and local-network handling stay equivalent.
     */
    private val PRIVATE_NETWORKS = listOf(
        "127.0.0.0/8",
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
        "::1/128",
        "fc00::/7",
        "fe80::/10",
    )

    /**
     * Every non-null [AbstractBean] is buildable: the shared generator has an
     * outbound branch for each protocol the Android app supports. NaiveProxy is
     * supported through an external plugin process (see [CoreConfig.plugins]).
     */
    fun supports(bean: AbstractBean?): Boolean = bean != null

    fun unsupportedReason(bean: AbstractBean?): String = when (bean) {
        null -> "The profile is empty"
        else -> "${bean.javaClass.simpleName.removeSuffix("Bean")} is not supported by the desktop client yet"
    }

    /**
     * Builds the core configuration for [profile].
     *
     * @param bindInterface physical interface the outbounds have to be pinned to
     *   in transparent (TUN) or router mode, or `null` when the system proxy is used.
     * @param serverInbounds optional mixed/TProxy listeners for daemon mode.
     */
    fun build(
        profile: Profile,
        settings: DesktopSettings,
        rules: List<RoutingRule> = emptyList(),
        bindInterface: String? = null,
        serverInbounds: ServerInboundOptions? = null,
        extraProfiles: List<Profile> = emptyList(),
        balancers: List<BalancerSpec> = emptyList(),
        primaryBalancer: BalancerSpec? = null,
    ): CoreConfig {
        val bean = profile.bean
        if (bean == null) {
            val raw = profile.customConfig
            if (raw.isNullOrBlank()) throw UnsupportedProfileException("The profile is empty")
            // Custom configs are never routed through the generator: the user
            // wrote them for the core already. Only the TUN interface binding is
            // injected, and only when there is one.
            return CoreConfig(postProcessCustom(raw, bindInterface), emptyList())
        }
        if (!supports(bean)) throw UnsupportedProfileException(unsupportedReason(bean))

        applySettings(settings)
        val direct = settings.routeMode == DesktopSettings.ROUTE_DIRECT

        val allBeanProfiles = LinkedHashMap<String, Profile>()
        allBeanProfiles[profile.id] = profile
        for (extra in extraProfiles) {
            if (extra.bean != null && extra.id !in allBeanProfiles) {
                allBeanProfiles[extra.id] = extra
            }
        }

        val validPrimaryMembers = if (!direct && primaryBalancer != null) {
            primaryBalancer.profileIds.distinct().filter { it in allBeanProfiles }
        } else {
            emptyList()
        }
        val usePrimaryBalancer = validPrimaryMembers.isNotEmpty() && primaryBalancer != null

        val usedIds = HashSet<Long>()
        fun allocId(seed: String): Long {
            var candidate = entityIdOf(seed)
            while (!usedIds.add(candidate)) {
                candidate++
            }
            return candidate
        }

        val primaryId = if (usePrimaryBalancer) {
            allocId("balancer-primary:${primaryBalancer!!.id}")
        } else {
            allocId(profile.id)
        }

        val profileEntityIds = LinkedHashMap<String, Long>()
        val dbEntities = ArrayList<ProxyEntity>()
        if (!usePrimaryBalancer) {
            profileEntityIds[profile.id] = primaryId
            if (balancers.isNotEmpty()) {
                dbEntities += ProxyEntity(id = primaryId, groupId = 0L).putBean(bean)
            }
        }
        for ((pid, p) in allBeanProfiles) {
            if (!usePrimaryBalancer && pid == profile.id) continue
            val eid = allocId(pid)
            profileEntityIds[pid] = eid
            dbEntities += ProxyEntity(id = eid, groupId = 0L).putBean(p.bean!!)
        }

        val balancerEntityIds = LinkedHashMap<String, Long>()
        val balancerDirectFallbackIds = HashSet<String>()
        val balancerOverrides = LinkedHashMap<String, BalancerSpec>()

        fun makeBalancerBean(spec: BalancerSpec, memberIds: List<String>): BalancerBean =
            BalancerBean().apply {
                initializeDefaultValues()
                name = spec.id
                type = BalancerBean.TYPE_LIST
                strategy = spec.strategy.ifBlank { "leastPing" }
                proxies = ArrayList(memberIds.mapNotNull { profileEntityIds[it] })
                probeUrl = spec.probeUrl.ifBlank { DEFAULT_PROBE_URL }
                probeInterval = spec.probeIntervalSec.coerceIn(1, 3600)
            }

        val entity = if (usePrimaryBalancer) {
            val pSpec = primaryBalancer!!
            balancerEntityIds[pSpec.id] = 0L
            balancerOverrides["balancer-proxy"] = pSpec
            ProxyEntity(id = primaryId, groupId = 0L).putBean(makeBalancerBean(pSpec, validPrimaryMembers))
        } else {
            ProxyEntity(id = primaryId, groupId = 0L).putBean(bean)
        }

        if (!direct) {
            for (bSpec in balancers) {
                val members = bSpec.profileIds.distinct().filter { it in profileEntityIds }
                if (members.isEmpty()) {
                    if (bSpec.fallbackToDirect) balancerDirectFallbackIds += bSpec.id
                    continue
                }
                val sameAsPrimary = usePrimaryBalancer && primaryBalancer != null && (
                    bSpec.id == primaryBalancer.id || (
                        members == validPrimaryMembers &&
                            bSpec.strategy == primaryBalancer.strategy &&
                            bSpec.stickyChoice == primaryBalancer.stickyChoice &&
                            bSpec.probeUrl == primaryBalancer.probeUrl &&
                            bSpec.probeIntervalSec == primaryBalancer.probeIntervalSec &&
                            bSpec.fallbackToDirect == primaryBalancer.fallbackToDirect
                        )
                    )
                if (sameAsPrimary) {
                    balancerEntityIds[bSpec.id] = 0L
                    continue
                }
                val bEntityId = allocId("balancer:${bSpec.id}")
                balancerEntityIds[bSpec.id] = bEntityId
                balancerOverrides["balancer-proxy-$bEntityId"] = bSpec
                dbEntities += ProxyEntity(id = bEntityId, groupId = 0L).putBean(makeBalancerBean(bSpec, members))
            }
        }

        if (dbEntities.isNotEmpty()) {
            SagerDatabase.proxyDao.updateProxy(dbEntities)
        }

        val probeMarkerToUser = LinkedHashMap<String, String>()
        val probeMarkerRules = ArrayList<RoutingRule>()
        val probeEnabled = serverInbounds?.probePort != null &&
            serverInbounds.probePort > 0 &&
            serverInbounds.probeSecret.isNotEmpty()
        if (probeEnabled && !direct) {
            val activeNaiveIds = HashSet<String>().apply {
                add(profile.id)
                addAll(validPrimaryMembers)
                balancers.forEach { addAll(it.profileIds) }
                rules.forEach { r ->
                    if (r.target.startsWith("profile:")) add(r.target.removePrefix("profile:"))
                }
            }
            var extraNaiveBudget = 8
            var markerIdx = 0
            for ((pid, p) in allBeanProfiles) {
                val isNaive = p.bean is NaiveBean
                if (isNaive && pid !in activeNaiveIds) {
                    if (extraNaiveBudget <= 0) continue
                    extraNaiveBudget--
                }
                val markerDomain = "full:owc-probe-${markerIdx++}.invalid"
                probeMarkerToUser[markerDomain] = "p:$pid"
                probeMarkerRules += RoutingRule(
                    id = "probe-marker-$markerIdx",
                    name = "probe-$pid",
                    domains = markerDomain,
                    target = "profile:$pid",
                )
            }
            val distinctBalancers = LinkedHashMap<String, BalancerSpec>()
            primaryBalancer?.let { distinctBalancers[it.id] = it }
            balancers.forEach { if (it.id !in distinctBalancers) distinctBalancers[it.id] = it }
            for ((bId, _) in distinctBalancers) {
                if (bId !in balancerEntityIds && bId !in balancerDirectFallbackIds) continue
                val markerDomain = "full:owc-probe-${markerIdx++}.invalid"
                probeMarkerToUser[markerDomain] = "b:$bId"
                probeMarkerRules += RoutingRule(
                    id = "probe-balancer-$markerIdx",
                    name = "probe-balancer-$bId",
                    domains = markerDomain,
                    target = "balancer:$bId",
                )
            }
        }

        installRules(
            rules = if (probeMarkerRules.isEmpty()) rules else probeMarkerRules + rules,
            bypassPrivateNetworks = settings.bypassPrivateNetworks,
            primaryProfileId = if (usePrimaryBalancer) "" else profile.id,
            extraEntityIds = profileEntityIds,
            balancerEntityIds = balancerEntityIds,
            balancerDirectFallbackIds = balancerDirectFallbackIds,
        )

        val result = buildV2RayConfig(entity)

        val plugins = if (direct) {
            // The proxy outbound is replaced by a freedom outbound below, so no
            // external engine is needed at all.
            emptyList()
        } else {
            result.index.flatMap { index ->
                index.chain.map { (triple, proxy) ->
                    ExternalPlugin(proxy.requireBean(), triple.first, triple.second, triple.third)
                }
            }
        }

        return CoreConfig(
            postProcess(
                config = result.config,
                settings = settings,
                bindInterface = bindInterface,
                proxyTags = (result.outboundTagsCurrent + result.outboundTags).toSet(),
                serverInbounds = serverInbounds,
                balancerOverrides = balancerOverrides,
                localPluginPorts = plugins.map { it.port }.toSet(),
                probeMarkerToUser = probeMarkerToUser,
            ),
            plugins,
        )
    }

    internal fun entityIdOf(profileId: String): Long =
        (profileId.hashCode().toLong() and 0x7FFF_FFFFL) + 100L

    // ------------------------------------------------------------------ settings

    /**
     * Pushes the desktop settings into the shared `DataStore` shim, in two
     * passes so the order is explicit:
     *
     * 1. Every Android global preference whose [Binding] is a [DataStoreMember] is
     *    applied from its persisted value (or its Android default). The member
     *    names are the shim property names; [applyToDataStore] is the single
     *    mapping point and throws for an unknown member.
     * 2. The desktop-only overrides run afterwards. They deliberately replace
     *    Android behaviour: there is no VpnService on desktop and the desktop
     *    expresses "direct only" in [postProcess] while always evaluating its own
     *    rule list.
     */
    private fun applySettings(settings: DesktopSettings) {
        // 1. the Android global preferences
        SettingsCatalogParser.load().entries.forEach { entry ->
            val binding = entry.binding
            if (binding is DataStoreMember) {
                applyToDataStore(binding.member, settings.storedOrDefault(entry.key))
            }
        }

        // 2. desktop-only overrides
        // MODE_PROXY (plus a non-SYSTEM TUN implementation) keeps ConfigBuilder
        // from emitting the Android VpnService plugin-protect arguments
        // (`--android_vpn` / `-V`) for SIP003 plugins.
        DataStore.serviceMode = Key.MODE_PROXY
        DataStore.tunImplementation = TunImplementation.GVISOR
        // Rules are always evaluated; ROUTE_DIRECT is expressed by turning the
        // proxy outbound into a freedom outbound in postProcess(). This keeps the
        // desktop rule list working for every route mode.
        DataStore.routeMode = RouteMode.RULE
        // The desktop client has no Android transparent proxy inbound; the TUN
        // device is served by the SOCKS inbound.
        DataStore.requireTransproxy = false
    }

    /**
     * Sets one member of the shared `DataStore` shim from its persisted string
     * form. [member] is the Android property name, which for most keys equals the
     * preference key; [SettingsBindings] documents the exceptions. Every
     * [DataStoreMember] in the binding table needs a branch here, which the
     * desktop settings test enforces.
     *
     * A null [value] resets the member to the Android `DataStore` default, so a
     * build never inherits a value from a previous build.
     */
    internal fun applyToDataStore(member: String, value: String?) {
        when (member) {
            // routing
            "domainStrategy" -> DataStore.domainStrategy = value ?: "AsIs"
            "trafficSniffing" -> DataStore.trafficSniffing = value.toBoolean()
            "destinationOverride" -> DataStore.destinationOverride = value.toBoolean()
            "hijackDns" -> DataStore.hijackDns = value.toBoolean()
            "outboundDomainStrategy" -> DataStore.outboundDomainStrategy = value ?: "AsIs"
            "outboundDomainStrategyForDirect" -> DataStore.outboundDomainStrategyForDirect = value ?: "AsIs"
            "outboundDomainStrategyForServer" -> DataStore.outboundDomainStrategyForServer = value ?: "AsIs"
            "profileTrafficStatistics" -> DataStore.profileTrafficStatistics = value.toBoolean()
            "allowAccess" -> DataStore.allowAccess = value.toBoolean()

            // DNS
            "remoteDns" -> DataStore.remoteDns = value ?: "tcp://1.1.1.1"
            "remoteDnsQueryStrategy" -> DataStore.remoteDnsQueryStrategy = value ?: "UseIP"
            "ednsClientIp" -> DataStore.ednsClientIp = value.orEmpty()
            "useLocalDnsAsDirectDns" -> DataStore.useLocalDnsAsDirectDns = value.toBoolean()
            "directDns" -> DataStore.directDns = value ?: "tcp://1.1.1.1"
            "directDnsQueryStrategy" -> DataStore.directDnsQueryStrategy = value ?: "UseIP"
            "useLocalDnsAsBootstrapDns" -> DataStore.useLocalDnsAsBootstrapDns = value.toBoolean()
            "bootstrapDns" -> DataStore.bootstrapDns = value.orEmpty()
            "hosts" -> DataStore.hosts = value.orEmpty()
            "enableDnsRouting" -> DataStore.enableDnsRouting = value.toBoolean()
            "enableFakeDns" -> DataStore.enableFakeDns = value.toBoolean()

            // logging / test URL
            "logLevel" -> DataStore.logLevel = value?.toIntOrNull() ?: LogLevel.INFO
            "connectionTestURL" ->
                DataStore.connectionTestURL = value ?: io.nekohasekai.sagernet.CONNECTION_TEST_URL

            // fragmentation and protocol flags
            "enableFragment" -> DataStore.enableFragment = value.toBoolean()
            "enableFragmentForDirect" -> DataStore.enableFragmentForDirect = value.toBoolean()
            "fragmentMethod" -> DataStore.fragmentMethod =
                value?.toIntOrNull() ?: TLS_FRAGMENTATION_METHOD.TLS_RECORD_FRAGMENTATION
            "interruptReusedConnections" -> DataStore.interruptReusedConnections = value.toBoolean()
            "realityDisableX25519Mlkem768" -> DataStore.realityDisableX25519Mlkem768 = value.toBoolean()
            "hysteria2OmitMaxDatagramFrameSize" -> DataStore.hysteria2OmitMaxDatagramFrameSize = value.toBoolean()
            "grpcServiceNameCompat" -> DataStore.grpcServiceNameCompat = value.toBoolean()
            "enableUnlockRu" -> DataStore.enableUnlockRu = value.toBoolean()
            "directProxyMode" -> DataStore.directProxyMode = value.toBoolean()

            // outbound SOCKS proxy chain
            "socksProxyChainEnabled" -> DataStore.socksProxyChainEnabled = value.toBoolean()
            "socksProxyChainHost" -> DataStore.socksProxyChainHost = value.orEmpty()
            "socksProxyChainPort" -> DataStore.socksProxyChainPort = value?.toIntOrNull() ?: 0
            "socksProxyChainUsername" -> DataStore.socksProxyChainUsername = value.orEmpty()
            "socksProxyChainPassword" -> DataStore.socksProxyChainPassword = value.orEmpty()

            // inbounds
            "requireSocks" -> DataStore.requireSocks = value.toBoolean()
            "socksPort" -> DataStore.socksPort = value?.toIntOrNull() ?: 2080
            "socksUsername" -> DataStore.socksUsername = value.orEmpty()
            "socksPassword" -> DataStore.socksPassword = value.orEmpty()
            "socksUDP" -> DataStore.socksUDP = value.toBoolean()
            "requireHttp" -> DataStore.requireHttp = value.toBoolean()
            "httpPort" -> DataStore.httpPort = value?.toIntOrNull() ?: 9080
            "httpUsername" -> DataStore.httpUsername = value.orEmpty()
            "httpPassword" -> DataStore.httpPassword = value.orEmpty()
            // The desktop keeps the core's own DNS listener instead of the
            // Android UDS one (postProcess drops only the UDS inbound).
            "requireDnsInbound" -> DataStore.requireDnsInbound = value.toBoolean()
            "localDNSPort" -> DataStore.localDNSPort = value?.toIntOrNull() ?: 6450

            // experimental flags are a java.util.Properties document
            "experimentalFlagsProperties" -> {
                DataStore.experimentalFlags = value.orEmpty()
                DataStore.experimentalFlagsProperties = java.util.Properties().apply {
                    value?.takeIf { it.isNotBlank() }?.let { text ->
                        load(java.io.StringReader(text))
                    }
                }
            }

            else -> throw IllegalStateException(
                "no DataStore mapping for shim member \"$member\": update DesktopConfigBuilder.applyToDataStore"
            )
        }
    }

    /** Android booleans are stored as text; anything but "true" is false. */
    private fun String?.toBoolean(): Boolean = this.equals("true", ignoreCase = true)

    // --------------------------------------------------------------------- rules

    /**
     * Installs the desktop routing rules on the shared `SagerDatabase` shim.
     *
     * The desktop model has no proxy ids, so the Android outbound encoding is
     * used directly: `0` is the selected profile, `-1` is the bypass (direct)
     * outbound and `-2` is the block outbound. `userOrder` keeps the list order,
     * and the private-network bypass is appended last exactly like the old
     * desktop builder did.
     */
    private fun installRules(
        rules: List<RoutingRule>,
        bypassPrivateNetworks: Boolean,
        primaryProfileId: String = "",
        extraEntityIds: Map<String, Long> = emptyMap(),
        balancerEntityIds: Map<String, Long> = emptyMap(),
        balancerDirectFallbackIds: Set<String> = emptySet(),
    ) {
        val entities = ArrayList<RuleEntity>()
        var privateInserted = false

        fun appendPrivateBypass() {
            if (bypassPrivateNetworks && !privateInserted) {
                privateInserted = true
                entities += RuleEntity(
                    id = entities.size.toLong() + 1,
                    name = "Bypass private networks",
                    userOrder = entities.size.toLong(),
                    enabled = true,
                    ip = PRIVATE_NETWORKS.joinToString("\n"),
                    outbound = -1L,
                )
            }
        }

        for (rule in rules) {
            val isPureCatchAll = rule.domains.isEmpty() &&
                rule.ip.isEmpty() &&
                rule.port.isEmpty() &&
                rule.protocol.isEmpty()
            if (isPureCatchAll) {
                // Keep RFC1918 / loopback bypass ahead of Mihomo `MATCH` catch-alls.
                appendPrivateBypass()
            }
            val resolvedOutbound = when {
                rule.target == RuleTarget.DIRECT.tag -> -1L
                rule.target == RuleTarget.BLOCK.tag -> -2L
                rule.target.startsWith("balancer:") -> {
                    val bId = rule.target.removePrefix("balancer:")
                    when {
                        bId in balancerDirectFallbackIds -> -1L
                        bId in balancerEntityIds -> balancerEntityIds.getValue(bId)
                        else -> 0L
                    }
                }
                rule.target.startsWith("profile:") -> {
                    val targetProfileId = rule.target.removePrefix("profile:")
                    if (targetProfileId == primaryProfileId) 0L else (extraEntityIds[targetProfileId] ?: 0L)
                }
                else -> 0L
            }
            entities += RuleEntity(
                id = entities.size.toLong() + 1,
                name = rule.name,
                userOrder = entities.size.toLong(),
                enabled = rule.enabled,
                domains = rule.domains,
                ip = rule.ip,
                port = rule.port,
                network = rule.network,
                protocol = rule.protocol,
                outbound = resolvedOutbound,
            )
        }
        appendPrivateBypass()
        androidx.room.DesktopSagerDatabase.enabledRulesOverride = entities
    }

    // -------------------------------------------------------------- post process

    /**
     * Removes everything that only makes sense inside the Android VPN service
     * and applies the desktop specific outbound tweaks.
     *
     * @param proxyTags tags of the outbounds that carry the selected profile;
     *   they are replaced by a direct outbound in [DesktopSettings.ROUTE_DIRECT].
     */
    private fun postProcess(
        config: String,
        settings: DesktopSettings,
        bindInterface: String?,
        proxyTags: Set<String>,
        serverInbounds: ServerInboundOptions? = null,
        balancerOverrides: Map<String, BalancerSpec> = emptyMap(),
        localPluginPorts: Set<Int> = emptySet(),
        probeMarkerToUser: Map<String, String> = emptyMap(),
    ): String {
        val root = runCatching { JsonParser.parseString(config).asJsonObject }.getOrElse { return config }

        // 1. Drop the Android-only inbounds: the `ipc-in` UDS Android uses for
        //    per-app traffic stats, and the `ipc_dns.sock` UDS of the Android
        //    local DNS. The port based `dns-in` listener the Android XML exposes
        //    through `requireDnsInbound` is a real UDP/TCP socket and is kept, so
        //    the desktop can serve DNS on `127.0.0.1:<portLocalDns>` too.
        val inbounds = root.getAsJsonArray("inbounds") ?: JsonArray()
        val keptInbounds = JsonArray()
        var sniffingTemplate: JsonObject? = null
        inbounds.forEach { element ->
            val inbound = element.asJsonObject
            val tag = inbound.get("tag")?.asString
            if (tag == TAG_IPC_IN) return@forEach
            if (tag == TAG_DNS_IN && inbound.get("listen")?.asString.orEmpty().endsWith(".sock")) {
                return@forEach
            }
            if (sniffingTemplate == null && inbound.has("sniffing")) {
                sniffingTemplate = inbound.getAsJsonObject("sniffing").deepCopy()
            }
            keptInbounds.add(inbound)
        }
        val hasProbeInbound = serverInbounds?.probePort != null &&
            serverInbounds.probePort > 0 &&
            serverInbounds.probeSecret.isNotEmpty()
        serverInbounds?.let { extra ->
            val listen = extra.bindAddress.trim().let { if (it.isEmpty() || it == "*") "0.0.0.0" else it }
            if (extra.mixedPort != null && extra.mixedPort > 0) {
                val mixed = JsonObject().apply {
                    addProperty("tag", "mixed-in")
                    addProperty("listen", listen)
                    addProperty("port", extra.mixedPort)
                    addProperty("protocol", "mixed")
                    add("settings", JsonObject().apply {
                        addProperty("udp", true)
                        if (extra.username.isNotEmpty() || extra.password.isNotEmpty()) {
                            addProperty("auth", "password")
                            add("accounts", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("user", extra.username)
                                    addProperty("pass", extra.password)
                                })
                            })
                        }
                    })
                    sniffingTemplate?.let { add("sniffing", it.deepCopy()) }
                }
                keptInbounds.add(mixed)
            }
            if (extra.tproxyPort != null && extra.tproxyPort > 0) {
                val tproxy = JsonObject().apply {
                    addProperty("tag", "tproxy-in")
                    addProperty("listen", listen)
                    addProperty("port", extra.tproxyPort)
                    addProperty("protocol", "dokodemo-door")
                    add("settings", JsonObject().apply {
                        addProperty("network", "tcp,udp")
                        addProperty("followRedirect", true)
                    })
                    add("streamSettings", JsonObject().apply {
                        add("sockopt", JsonObject().apply {
                            addProperty("tproxy", "tproxy")
                        })
                    })
                    sniffingTemplate?.let { add("sniffing", it.deepCopy()) }
                }
                keptInbounds.add(tproxy)
            }
            if (hasProbeInbound) {
                val accounts = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("user", "direct")
                        addProperty("pass", extra.probeSecret)
                    })
                    probeMarkerToUser.values.distinct().forEach { userTag ->
                        add(JsonObject().apply {
                            addProperty("user", userTag)
                            addProperty("pass", extra.probeSecret)
                        })
                    }
                }
                val probeInbound = JsonObject().apply {
                    addProperty("tag", TAG_PROBE_IN)
                    addProperty("listen", "127.0.0.1")
                    addProperty("port", extra.probePort)
                    addProperty("protocol", "socks")
                    add("settings", JsonObject().apply {
                        addProperty("auth", "password")
                        addProperty("udp", false)
                        add("accounts", accounts)
                    })
                }
                keptInbounds.add(probeInbound)
            }
        }
        root.add("inbounds", keptInbounds)
        val inboundTags = keptInbounds.mapNotNull { it.asJsonObject.get("tag")?.asString }.toSet()

        var needsBypassFallback = false
        // 2. Routing rules that referenced a dropped inbound (`dns-in` fed the
        //    `dns-out` outbound) would make the core reject the config.
        val routingObj = root.getAsJsonObject("routing") ?: if (hasProbeInbound) {
            JsonObject().also { root.add("routing", it) }
        } else {
            null
        }
        routingObj?.let { routing ->
            routing.getAsJsonArray("balancers")?.forEach { element ->
                val balancerObj = element.asJsonObject
                val bTag = balancerObj.get("tag")?.asString ?: return@forEach
                val spec = balancerOverrides[bTag] ?: return@forEach
                if (spec.strategy == "fallback") {
                    balancerObj.getAsJsonObject("strategy")?.let { strat ->
                        strat.addProperty("type", "fallback")
                        strat.getAsJsonObject("settings")?.remove("aliveOnly")
                    }
                }
                if (spec.fallbackToDirect) {
                    balancerObj.addProperty("fallbackTag", TAG_BYPASS_FALLBACK)
                    needsBypassFallback = true
                } else if (spec.stickyChoice && spec.strategy == "leastPing") {
                    // Exclave-core's stickyChoice caches the last alive tag even after all nodes die,
                    // which suppresses `fallbackTag`; only enable stickyChoice when fallbackToDirect is off.
                    balancerObj.getAsJsonObject("strategy")
                        ?.getAsJsonObject("settings")
                        ?.addProperty("stickyChoice", true)
                }
            }
            val rules = routing.getAsJsonArray("rules") ?: JsonArray()
            val dnsTopRules = ArrayList<JsonObject>()
            val probeTopRules = ArrayList<JsonObject>()
            val regularRules = ArrayList<JsonObject>()

            if (hasProbeInbound) {
                probeTopRules += JsonObject().apply {
                    addProperty("type", "field")
                    add("inboundTag", JsonArray().apply { add(TAG_PROBE_IN) })
                    add("user", JsonArray().apply { add("direct") })
                    addProperty("outboundTag", "bypass")
                }
            }

            rules.forEach { element ->
                val rule = element.asJsonObject
                val inboundTag = rule.getAsJsonArray("inboundTag")
                if (inboundTag != null && inboundTag.any { it.asString !in inboundTags }) return@forEach

                val domainsArr = rule.getAsJsonArray("domains") ?: rule.getAsJsonArray("domain")
                val singleDomain = if (domainsArr != null && domainsArr.size() == 1) domainsArr[0].asString else null
                val mappedProbeUser = singleDomain?.let { probeMarkerToUser[it] }
                if (mappedProbeUser != null) {
                    if (hasProbeInbound) {
                        val rewritten = rule.deepCopy().apply {
                            remove("domains")
                            remove("domain")
                            add("inboundTag", JsonArray().apply { add(TAG_PROBE_IN) })
                            add("user", JsonArray().apply { add(mappedProbeUser) })
                        }
                        probeTopRules += rewritten
                    }
                    return@forEach
                }

                if (rule.get("outboundTag")?.asString == "dns-out") {
                    dnsTopRules += rule
                } else {
                    regularRules += rule
                }
            }

            val keptRules = JsonArray()
            dnsTopRules.forEach { keptRules.add(it) }
            probeTopRules.forEach { keptRules.add(it) }
            regularRules.forEach { keptRules.add(it) }
            routing.add("rules", keptRules)
            if (keptRules.size() == 0 && routing.getAsJsonArray("balancers") == null) {
                root.remove("routing")
            }
        }

        val outbounds = root.getAsJsonArray("outbounds") ?: JsonArray()
        if (needsBypassFallback && outbounds.none { it.asJsonObject.get("tag")?.asString == TAG_BYPASS_FALLBACK }) {
            val templateBypass = outbounds.firstOrNull { it.asJsonObject.get("tag")?.asString == "bypass" }
                ?.asJsonObject
                ?.deepCopy()
                ?: JsonObject().apply { addProperty("protocol", "freedom") }
            templateBypass.addProperty("tag", TAG_BYPASS_FALLBACK)
            outbounds.add(templateBypass)
        }

        // 3. "Direct only" mode: the selected profile is not dialled, the core
        //    sends everything out directly. The tag is kept so the routing rules
        //    that point at it still resolve.
        if (settings.routeMode == DesktopSettings.ROUTE_DIRECT) {
            outbounds.forEach { element ->
                val outbound = element.asJsonObject
                if (outbound.get("tag")?.asString in proxyTags) {
                    outbound.addProperty("protocol", "freedom")
                    outbound.remove("settings")
                    outbound.remove("mux")
                    outbound.remove("smux")
                    outbound.remove("streamSettings")
                }
            }
        }

        // 4. Transparent mode: every outbound that dials a real socket (so
        //    everything except blackhole, internal DNS, and local loopback
        //    SOCKS bridges to external plugins like NaiveProxy) has to be pinned
        //    to the physical interface, otherwise it would loop back into TUN.
        if (!bindInterface.isNullOrBlank()) {
            outbounds.forEach { element ->
                element.asJsonObject.bindToInterface(bindInterface, localPluginPorts)
            }
        }

        return json.toJson(root)
    }

    /** Custom configs only get the TUN interface binding, nothing else. */
    private fun postProcessCustom(config: String, bindInterface: String?): String {
        if (bindInterface.isNullOrBlank()) return config
        val root = runCatching { JsonParser.parseString(config).asJsonObject }.getOrElse { return config }
        root.getAsJsonArray("outbounds")?.forEach { element ->
            element.asJsonObject.bindToInterface(bindInterface)
        }
        return json.toJson(root)
    }

    /** Pins every socket of this outbound to a network interface. */
    private fun JsonObject.bindToInterface(interfaceName: String, localPluginPorts: Set<Int> = emptySet()) {
        val protocol = get("protocol")?.asString
        if (protocol == "blackhole" || protocol == "dns") return
        if (protocol == "socks" && localPluginPorts.isNotEmpty()) {
            val servers = getAsJsonObject("settings")?.getAsJsonArray("servers")
            val targetsLocalPlugin = servers != null && servers.size() > 0 && servers.all { srv ->
                val obj = srv.asJsonObject
                val addr = obj.get("address")?.asString
                val port = obj.get("port")?.asInt
                (addr == "127.0.0.1" || addr == "::1") && port != null && port in localPluginPorts
            }
            if (targetsLocalPlugin) return
        }
        val streamSettings = getAsJsonObject("streamSettings") ?: JsonObject().also { add("streamSettings", it) }
        val sockopt = streamSettings.getAsJsonObject("sockopt") ?: JsonObject().also { streamSettings.add("sockopt", it) }
        sockopt.addProperty("bindToDevice", interfaceName)
    }

    private const val TAG_IPC_IN = "ipc-in"
    private const val TAG_DNS_IN = "dns-in"
    const val TAG_PROBE_IN = "probe-socks-in"
    const val TAG_BYPASS_FALLBACK = "bypass-fallback"
    const val DEFAULT_PROBE_URL = "http://cp.cloudflare.com/generate_204"

}
