package io.nekohasekai.sagernet.desktop.server

import io.nekohasekai.sagernet.desktop.BalancerSpec
import io.nekohasekai.sagernet.desktop.Profile
import io.nekohasekai.sagernet.desktop.RoutingRule
import io.nekohasekai.sagernet.desktop.RuleTarget
import io.nekohasekai.sagernet.desktop.SubscriptionImporter
import io.nekohasekai.sagernet.group.YAMLConstructor
import io.nekohasekai.sagernet.group.parseClashProxies
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.io.File
import java.util.regex.Pattern

/**
 * Mihomo / Clash.Meta YAML configuration, proxy-group and rule-provider support
 * for headless/Docker server mode.
 *
 * Unlike Android subscription import (where groups are static at import time),
 * the server keeps the full [ProxyGroupSpec] graph and raw rule descriptors so
 * switching a group in the Web UI or via `PUT /proxies/{group}` dynamically
 * re-evaluates rule targets (`PROXY` vs `DIRECT` vs `REJECT`) and reloads the core.
 */
object MihomoYamlSupport {

    const val DEFAULT_PROBE_URL = "http://cp.cloudflare.com/generate_204"
    val AUTO_BALANCER_TYPES = setOf("fallback", "url-test", "load-balance")

    data class ProxyGroupSpec(
        val name: String,
        val type: String,
        val proxies: List<String>,
        val url: String? = null,
        val interval: Int? = null,
        val tolerance: Int? = null,
        val includeAll: Boolean = false,
        val filter: String? = null,
        val excludeFilter: String? = null,
        val fallbackToDirect: Boolean = false,
    ) {
        val isAutoBalancer: Boolean
            get() = type in AUTO_BALANCER_TYPES
    }

    data class RuleProviderSpec(
        val name: String,
        val type: String,
        val behavior: String,
        val format: String,
        val url: String? = null,
        val path: String? = null,
        val inlinePayload: List<String> = emptyList(),
    )

    data class ParsedMihomoDocument(
        val rawMap: Map<String, Any?>,
        val profiles: List<Profile>,
        val virtualDirectNames: Set<String>,
        val virtualRejectNames: Set<String>,
        val virtualDnsNames: Set<String> = emptySet(),
        val groups: List<ProxyGroupSpec>,
        val ruleProviders: Map<String, RuleProviderSpec>,
        val rawRules: List<String>,
    ) {
        fun hasSameRoutingSemantics(other: ParsedMihomoDocument?): Boolean {
            if (other == null) return false
            return virtualDirectNames == other.virtualDirectNames &&
                virtualRejectNames == other.virtualRejectNames &&
                virtualDnsNames == other.virtualDnsNames &&
                groups == other.groups &&
                ruleProviders == other.ruleProviders &&
                rawRules == other.rawRules
        }
    }

    /** Official Telegram AS149154 / Messenger IPv4 & IPv6 prefixes for `GEOIP,telegram` / `telegram.mrs`. */
    private val TELEGRAM_CIDRS = listOf(
        "91.108.4.0/22",
        "91.108.8.0/22",
        "91.108.12.0/22",
        "91.108.16.0/22",
        "91.108.20.0/22",
        "91.108.56.0/22",
        "95.161.64.0/20",
        "149.154.160.0/20",
        "185.76.151.0/24",
        "2001:b28:f23d::/48",
        "2001:b28:f23f::/48",
        "2001:b28:f23c::/48",
        "2001:67c:4e8::/48",
    )

    /** Official Cloudflare IPv4 & IPv6 edge prefixes for `cloudflare.mrs` / `GEOIP,cloudflare`. */
    private val CLOUDFLARE_CIDRS = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22",
        "2400:cb00::/32",
        "2606:4700::/32",
        "2803:f800::/32",
        "2405:b500::/32",
        "2405:8100::/32",
        "2a06:98c0::/29",
        "2c0f:f248::/32",
    )

    @Suppress("UNCHECKED_CAST")
    fun loadYamlMap(text: String): Map<String, Any?>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val loaderOptions = LoaderOptions().apply {
                codePointLimit = 16 * 1024 * 1024
            }
            val dumperOptions = DumperOptions()
            val yaml = Yaml(
                YAMLConstructor(loaderOptions),
                Representer(dumperOptions),
                dumperOptions,
                object : Resolver() {
                    override fun addImplicitResolver(tag: Tag, regexp: Pattern, first: String?, limit: Int) {
                        when (tag) {
                            Tag.FLOAT -> {}
                            Tag.BOOL -> super.addImplicitResolver(
                                tag,
                                Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$"),
                                "tTfF",
                                limit,
                            )
                            else -> super.addImplicitResolver(tag, regexp, first, limit)
                        }
                    }
                },
            )
            yaml.loadAs(trimmed, Map::class.java) as? Map<String, Any?>
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    fun parseDocument(text: String, subscriptionId: String? = null): ParsedMihomoDocument? {
        val map = loadYamlMap(text) ?: return null
        val rawProxies = (map["proxies"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

        val virtualDirect = LinkedHashSet<String>()
        val virtualReject = LinkedHashSet<String>()
        val virtualDns = LinkedHashSet<String>()
        val realProxyMaps = ArrayList<Map<String, Any?>>()

        for (entry in rawProxies) {
            val name = entry["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            when (entry["type"]?.toString()?.trim()?.lowercase()) {
                "direct" -> virtualDirect += name
                "reject", "reject-drop" -> virtualReject += name
                "dns" -> virtualDns += name
                "pass" -> {}
                else -> realProxyMaps += entry
            }
        }

        val beans = if (realProxyMaps.isNotEmpty()) {
            runCatching { parseClashProxies(realProxyMaps).map { it.applyDefaultValues() } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val profiles = beans.map { bean ->
            Profile(
                name = bean.name,
                bean = bean,
                subscriptionId = subscriptionId,
            )
        }

        val groups = ArrayList<ProxyGroupSpec>()
        val rawGroups = (map["proxy-groups"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()
        for (group in rawGroups) {
            val name = group["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val type = group["type"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "select" }
            val members = (group["proxies"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            val url = group["url"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val interval = group["interval"]?.toString()?.toIntOrNull()
            val tolerance = group["tolerance"]?.toString()?.toIntOrNull()
            val includeAll = group["include-all"]?.toString()?.equals("true", ignoreCase = true) == true ||
                group["include-all-proxies"]?.toString()?.equals("true", ignoreCase = true) == true
            val filter = group["filter"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val excludeFilter = group["exclude-filter"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val fallbackToDirect = group["fallback-to-direct"]?.toString()?.equals("true", ignoreCase = true) == true
            groups += ProxyGroupSpec(
                name = name,
                type = type,
                proxies = members,
                url = url,
                interval = interval,
                tolerance = tolerance,
                includeAll = includeAll,
                filter = filter,
                excludeFilter = excludeFilter,
                fallbackToDirect = fallbackToDirect,
            )
        }

        val ruleProviders = LinkedHashMap<String, RuleProviderSpec>()
        val rawProviders = map["rule-providers"] as? Map<*, *>
        rawProviders?.forEach { (k, v) ->
            val name = k?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val spec = v as? Map<*, *> ?: return@forEach
            val type = spec["type"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "http" }
            val behavior = spec["behavior"]?.toString()?.trim()?.lowercase().orEmpty().ifEmpty { "classical" }
            val url = spec["url"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val path = spec["path"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val format = spec["format"]?.toString()?.trim()?.lowercase()
                ?: if (url?.substringBefore('?')?.endsWith(".mrs", ignoreCase = true) == true) "mrs" else "yaml"
            val payload = (spec["payload"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            ruleProviders[name] = RuleProviderSpec(
                name = name,
                type = type,
                behavior = behavior,
                format = format,
                url = url,
                path = path,
                inlinePayload = payload,
            )
        }

        val rawRules = (map["rules"] as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() && !s.startsWith("#") } }
            .orEmpty()

        return ParsedMihomoDocument(
            rawMap = map,
            profiles = profiles,
            virtualDirectNames = virtualDirect,
            virtualRejectNames = virtualReject,
            virtualDnsNames = virtualDns,
            groups = groups,
            ruleProviders = ruleProviders,
            rawRules = rawRules,
        )
    }

    /**
     * Expands a group's member list, honoring `include-all` / `include-all-proxies`,
     * `filter`, and `exclude-filter`, while keeping trailing `DIRECT` / virtual-direct
     * entries after included real proxies so automatic failover prefers alive proxies first.
     */
    fun expandGroupChoices(
        spec: ProxyGroupSpec,
        allProfileNames: List<String>,
        virtualDirect: Set<String> = emptySet(),
    ): List<String> {
        val filterRegex = spec.filter?.let { runCatching { Regex(it) }.getOrNull() }
        val excludeRegex = spec.excludeFilter?.let { runCatching { Regex(it) }.getOrNull() }
        fun matchesFilters(name: String): Boolean {
            if (filterRegex != null && !filterRegex.containsMatchIn(name)) return false
            if (excludeRegex != null && excludeRegex.containsMatchIn(name)) return false
            return true
        }
        fun isDirectEntry(name: String): Boolean =
            name.equals("DIRECT", ignoreCase = true) || name in virtualDirect

        val ordered = LinkedHashSet<String>()
        val trailingDirect = LinkedHashSet<String>()

        for (p in spec.proxies) {
            if (spec.includeAll && isDirectEntry(p)) {
                trailingDirect += p
            } else {
                ordered += p
            }
        }
        if (spec.includeAll || (spec.proxies.isEmpty() && allProfileNames.isNotEmpty())) {
            allProfileNames.filter(::matchesFilters).forEach { ordered += it }
        }
        trailingDirect.forEach { ordered += it }
        return ordered.toList()
    }

    /**
     * Resolves a proxy or group name to a [RuleTarget] (`PROXY`, `DIRECT`, or `BLOCK`)
     * given the current [groupSelections].
     */
    fun resolveRuleTarget(
        targetName: String,
        groupsByName: Map<String, ProxyGroupSpec>,
        groupSelections: Map<String, String>,
        virtualDirect: Set<String>,
        virtualReject: Set<String>,
        virtualDns: Set<String> = emptySet(),
        allProfileNames: List<String> = emptyList(),
        visited: MutableSet<String> = HashSet(),
    ): RuleTarget? {
        val trimmed = targetName.trim()
        when (trimmed.uppercase()) {
            "DIRECT" -> return RuleTarget.DIRECT
            "REJECT", "REJECT-DROP" -> return RuleTarget.BLOCK
            "PASS", "COMPATIBLE", "DNS-OUT" -> return null
        }
        if (trimmed in virtualDns) return null
        if (trimmed in virtualDirect) return RuleTarget.DIRECT
        if (trimmed in virtualReject) return RuleTarget.BLOCK

        val group = groupsByName[trimmed] ?: return RuleTarget.PROXY
        if (!visited.add(trimmed)) return RuleTarget.PROXY

        val choices = expandGroupChoices(group, allProfileNames, virtualDirect)
        if (group.isAutoBalancer) {
            val hasNonDirect = choices.any { entry ->
                !entry.equals("DIRECT", ignoreCase = true) &&
                    entry !in virtualDirect &&
                    !entry.equals("REJECT", ignoreCase = true) &&
                    !entry.equals("REJECT-DROP", ignoreCase = true) &&
                    entry !in virtualReject
            }
            if (hasNonDirect) return RuleTarget.PROXY
            val first = choices.firstOrNull() ?: return if (group.fallbackToDirect) RuleTarget.DIRECT else RuleTarget.PROXY
            return resolveRuleTarget(first, groupsByName, groupSelections, virtualDirect, virtualReject, virtualDns, allProfileNames, visited)
        }

        val selected = groupSelections[trimmed]?.takeIf { it in choices }
            ?: choices.firstOrNull()
            ?: return RuleTarget.PROXY
        return resolveRuleTarget(selected, groupsByName, groupSelections, virtualDirect, virtualReject, virtualDns, allProfileNames, visited)
    }

    /**
     * Resolves the concrete [Profile] selected by [targetName] (walking nested groups).
     */
    fun resolveSelectedProfile(
        targetName: String,
        profilesByName: Map<String, Profile>,
        groupsByName: Map<String, ProxyGroupSpec>,
        groupSelections: Map<String, String>,
        visited: MutableSet<String> = HashSet(),
    ): Profile? {
        profilesByName[targetName]?.let { return it }
        val group = groupsByName[targetName] ?: return null
        if (!visited.add(targetName)) return null
        val choices = expandGroupChoices(group, profilesByName.keys.toList())
        val chosen = groupSelections[targetName]?.takeIf { it in choices }
            ?: choices.firstOrNull { !it.equals("DIRECT", ignoreCase = true) && !it.equals("REJECT", ignoreCase = true) }
            ?: choices.firstOrNull()
            ?: return null
        return resolveSelectedProfile(chosen, profilesByName, groupsByName, groupSelections, visited)
    }

    /**
     * Resolves [targetName] to a [BalancerSpec] when it points (directly or through
     * `select` chains) to an automatic group (`fallback`, `url-test`, `load-balance`),
     * or when [defaultFallbackToDirect] wraps a single proxy profile for bypass failover.
     */
    fun resolveBalancerSpec(
        targetName: String,
        profilesByName: Map<String, Profile>,
        groupsByName: Map<String, ProxyGroupSpec>,
        groupSelections: Map<String, String>,
        virtualDirect: Set<String> = emptySet(),
        virtualReject: Set<String> = emptySet(),
        defaultFallbackToDirect: Boolean = false,
        healthCheckIntervalCap: Int = 0,
        visited: MutableSet<String> = HashSet(),
    ): BalancerSpec? {
        val trimmed = targetName.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("DIRECT", ignoreCase = true) || trimmed in virtualDirect) return null
        if (trimmed.equals("REJECT", ignoreCase = true) || trimmed.equals("REJECT-DROP", ignoreCase = true) || trimmed in virtualReject) return null

        val allProfileNames = profilesByName.keys.toList()
        fun computeInterval(rawInterval: Int?): Int {
            val base = (rawInterval ?: 15).coerceIn(1, 3600)
            return if (healthCheckIntervalCap > 0) minOf(base, healthCheckIntervalCap.coerceAtLeast(1)) else base
        }

        profilesByName[trimmed]?.let { single ->
            if (!defaultFallbackToDirect || single.bean == null) return null
            return BalancerSpec(
                id = "profile-fb:${single.id}",
                profileIds = listOf(single.id),
                strategy = "fallback",
                stickyChoice = false,
                probeUrl = DEFAULT_PROBE_URL,
                probeIntervalSec = computeInterval(null),
                fallbackToDirect = true,
            )
        }

        val group = groupsByName[trimmed] ?: return null
        if (!visited.add(trimmed)) return null
        val choices = expandGroupChoices(group, allProfileNames, virtualDirect)

        if (!group.isAutoBalancer) {
            val chosen = groupSelections[trimmed]?.takeIf { it in choices }
                ?: choices.firstOrNull()
                ?: return null
            return resolveBalancerSpec(
                targetName = chosen,
                profilesByName = profilesByName,
                groupsByName = groupsByName,
                groupSelections = groupSelections,
                virtualDirect = virtualDirect,
                virtualReject = virtualReject,
                defaultFallbackToDirect = defaultFallbackToDirect,
                healthCheckIntervalCap = healthCheckIntervalCap,
                visited = visited,
            )
        }

        val members = LinkedHashMap<String, Profile>()
        var hasDirectFallback = group.fallbackToDirect || defaultFallbackToDirect

        fun collectFromGroup(g: ProxyGroupSpec, seenGroups: MutableSet<String>) {
            if (!seenGroups.add(g.name)) return
            if (g.fallbackToDirect) hasDirectFallback = true
            val expanded = expandGroupChoices(g, allProfileNames, virtualDirect)
            for (item in expanded) {
                when {
                    item.equals("DIRECT", ignoreCase = true) || item in virtualDirect -> {
                        hasDirectFallback = true
                    }
                    item.equals("REJECT", ignoreCase = true) || item.equals("REJECT-DROP", ignoreCase = true) || item in virtualReject -> {}
                    item in profilesByName -> {
                        val p = profilesByName.getValue(item)
                        if (p.bean != null) members[p.id] = p
                    }
                    item in groupsByName -> {
                        val sub = groupsByName.getValue(item)
                        if (sub.isAutoBalancer) {
                            collectFromGroup(sub, seenGroups)
                        } else {
                            resolveSelectedProfile(item, profilesByName, groupsByName, groupSelections)?.let { p ->
                                if (p.bean != null) members[p.id] = p
                            }
                        }
                    }
                }
            }
        }

        collectFromGroup(group, HashSet())
        if (members.isEmpty()) return null

        val strategy = when (group.type) {
            "fallback" -> "fallback"
            "load-balance" -> "random"
            else -> "leastPing"
        }
        val sticky = !hasDirectFallback && group.type == "url-test" && (group.tolerance ?: 0) > 0
        return BalancerSpec(
            id = group.name,
            profileIds = members.keys.toList(),
            strategy = strategy,
            stickyChoice = sticky,
            probeUrl = group.url?.takeIf { it.isNotBlank() } ?: DEFAULT_PROBE_URL,
            probeIntervalSec = computeInterval(group.interval),
            fallbackToDirect = hasDirectFallback,
        )
    }

    /**
     * Compiles Mihomo `rules` + `rule-providers` into Owenclave [RoutingRule]s,
     * caching downloaded text/yaml HTTP rule-providers in [cacheDir] and mapping
     * known binary `.mrs` rule-providers to verified `geosite:` / `geoip:` categories.
     */
    fun compileRules(
        rawRules: List<String>,
        ruleProviders: Map<String, RuleProviderSpec>,
        groupsByName: Map<String, ProxyGroupSpec>,
        groupSelections: Map<String, String>,
        virtualDirect: Set<String>,
        virtualReject: Set<String>,
        virtualDns: Set<String> = emptySet(),
        cacheDir: File,
        configBaseDir: File? = null,
        socksPort: Int? = null,
        profilesByName: Map<String, Profile> = emptyMap(),
        balancersOut: MutableMap<String, BalancerSpec>? = null,
        defaultFallbackToDirect: Boolean = false,
        healthCheckIntervalCap: Int = 0,
        log: (String) -> Unit = {},
    ): List<RoutingRule> {
        if (rawRules.isEmpty()) return emptyList()
        cacheDir.mkdirs()

        val allProfileNames = profilesByName.keys.toList()
        val providerMatchersCache = HashMap<String, ProviderMatchers>()
        fun providerMatchersFor(name: String): ProviderMatchers? {
            val spec = ruleProviders[name] ?: return null
            return providerMatchersCache.getOrPut(name) {
                resolveProviderMatchers(spec, cacheDir, configBaseDir, socksPort, log)
            }
        }
        fun effectiveTargetTag(targetName: String): String? {
            val target = resolveRuleTarget(
                targetName = targetName,
                groupsByName = groupsByName,
                groupSelections = groupSelections,
                virtualDirect = virtualDirect,
                virtualReject = virtualReject,
                virtualDns = virtualDns,
                allProfileNames = allProfileNames,
            ) ?: return null
            if (target == RuleTarget.PROXY && profilesByName.isNotEmpty()) {
                if (balancersOut != null) {
                    val balancerSpec = resolveBalancerSpec(
                        targetName = targetName,
                        profilesByName = profilesByName,
                        groupsByName = groupsByName,
                        groupSelections = groupSelections,
                        virtualDirect = virtualDirect,
                        virtualReject = virtualReject,
                        defaultFallbackToDirect = defaultFallbackToDirect,
                        healthCheckIntervalCap = healthCheckIntervalCap,
                    )
                    if (balancerSpec != null) {
                        balancersOut[balancerSpec.id] = balancerSpec
                        return "balancer:${balancerSpec.id}"
                    }
                    // If an auto-balancer had 0 real profiles and fallbackToDirect is enabled, go direct
                    val group = groupsByName[targetName.trim()]
                    if ((group?.fallbackToDirect == true || defaultFallbackToDirect) &&
                        resolveSelectedProfile(targetName, profilesByName, groupsByName, groupSelections) == null
                    ) {
                        return RuleTarget.DIRECT.tag
                    }
                }
                val resolvedProfile = resolveSelectedProfile(targetName, profilesByName, groupsByName, groupSelections)
                if (resolvedProfile != null) {
                    return "profile:${resolvedProfile.id}"
                }
            }
            return target.tag
        }
        val compiled = ArrayList<RoutingRule>()

        for ((index, line) in rawRules.withIndex()) {
            if (line.startsWith("AND,", ignoreCase = true)) {
                parseAndRule(
                    line,
                    index,
                    ::effectiveTargetTag,
                    ::providerMatchersFor,
                )?.let { compiled += it }
                continue
            }

            val parts = line.split(',').map { it.trim() }
            if (parts.size < 2) continue
            val type = parts[0].uppercase()

            if (type == "MATCH" || type == "FINAL") {
                val targetTag = effectiveTargetTag(parts[1]) ?: continue
                // Default outbound is already active profile in Rule mode; emit explicit catch-all
                // when DIRECT, BLOCK, or a specific non-default profile is targeted.
                if (targetTag != RuleTarget.PROXY.tag) {
                    compiled += RoutingRule(
                        id = "mihomo-match-$index",
                        name = "[Mihomo] MATCH -> ${parts[1]}",
                        network = "tcp,udp",
                        target = targetTag,
                    )
                }
                continue
            }

            if (parts.size < 3) continue
            val value = parts[1]
            val targetName = parts[2]
            val targetTag = effectiveTargetTag(targetName) ?: continue

            when (type) {
                "DOMAIN" -> translateDomainEntry(value, "domain")?.let { d ->
                    compiled += RoutingRule(id = "mihomo-$index", name = "[Mihomo] $type,$value", domains = d, target = targetTag)
                }
                "DOMAIN-SUFFIX" -> {
                    val clean = value.trimStart('.')
                    if (clean.isNotEmpty()) {
                        compiled += RoutingRule(
                            id = "mihomo-$index",
                            name = "[Mihomo] $type,$value",
                            domains = "domain:$clean",
                            target = targetTag,
                        )
                    }
                }
                "DOMAIN-KEYWORD" -> if (value.isNotEmpty()) {
                    compiled += RoutingRule(
                        id = "mihomo-$index",
                        name = "[Mihomo] $type,$value",
                        domains = "keyword:$value",
                        target = targetTag,
                    )
                }
                "DOMAIN-REGEX" -> if (value.isNotEmpty()) {
                    compiled += RoutingRule(
                        id = "mihomo-$index",
                        name = "[Mihomo] $type,$value",
                        domains = "regexp:$value",
                        target = targetTag,
                    )
                }
                "GEOSITE" -> normalizeGeosite(value)?.let { gs ->
                    compiled += RoutingRule(
                        id = "mihomo-$index",
                        name = "[Mihomo] GEOSITE,$value",
                        domains = gs,
                        target = targetTag,
                    )
                }
                "IP-CIDR", "IP-CIDR6" -> if (value.isNotEmpty()) {
                    compiled += RoutingRule(
                        id = "mihomo-$index",
                        name = "[Mihomo] $type,$value",
                        ip = value,
                        target = targetTag,
                    )
                }
                "GEOIP" -> {
                    val ips = normalizeGeoip(value)
                    if (ips.isNotEmpty()) {
                        compiled += RoutingRule(
                            id = "mihomo-$index",
                            name = "[Mihomo] GEOIP,$value",
                            ip = ips.joinToString("\n"),
                            target = targetTag,
                        )
                    }
                }
                "DST-PORT" -> normalizePort(value)?.let { p ->
                    compiled += RoutingRule(
                        id = "mihomo-$index",
                        name = "[Mihomo] DST-PORT,$value",
                        port = p,
                        target = targetTag,
                    )
                }
                "NETWORK" -> {
                    val net = value.lowercase()
                    if (net == "tcp" || net == "udp") {
                        compiled += RoutingRule(
                            id = "mihomo-$index",
                            name = "[Mihomo] NETWORK,$value",
                            network = net,
                            target = targetTag,
                        )
                    }
                }
                "RULE-SET" -> {
                    val matchers = providerMatchersFor(value) ?: continue
                    if (matchers.domains.isNotEmpty()) {
                        compiled += RoutingRule(
                            id = "mihomo-$index-d",
                            name = "[Mihomo] RULE-SET,$value (domains)",
                            domains = matchers.domains.joinToString("\n"),
                            target = targetTag,
                        )
                    }
                    if (matchers.ips.isNotEmpty()) {
                        compiled += RoutingRule(
                            id = "mihomo-$index-ip",
                            name = "[Mihomo] RULE-SET,$value (ip)",
                            ip = matchers.ips.joinToString("\n"),
                            target = targetTag,
                        )
                    }
                    matchers.classicalRules.forEachIndexed { subIdx, subRule ->
                        compiled += RoutingRule(
                            id = "mihomo-$index-c-$subIdx",
                            name = "[Mihomo] RULE-SET,$value #$subIdx",
                            domains = subRule.domains,
                            ip = subRule.ip,
                            port = subRule.port,
                            network = subRule.network,
                            protocol = subRule.protocol,
                            target = targetTag,
                        )
                    }
                }
            }
        }
        return compiled
    }

    private data class ProviderMatchers(
        val domains: List<String> = emptyList(),
        val ips: List<String> = emptyList(),
        val classicalRules: List<RoutingRule> = emptyList(),
    )

    private fun resolveProviderMatchers(
        spec: RuleProviderSpec,
        cacheDir: File,
        configBaseDir: File?,
        socksPort: Int?,
        log: (String) -> Unit,
    ): ProviderMatchers {
        if (spec.type == "inline") {
            return parsePayloadEntries(spec.behavior, spec.inlinePayload)
        }
        if (spec.format == "mrs") {
            return mapMrsProvider(spec, log)
        }
        val localPathFile = spec.path
            ?.takeIf { !it.contains("..") }
            ?.let { rel -> configBaseDir?.let { base -> File(base, rel.removePrefix("./")) } }
        if (spec.type == "file") {
            val body = localPathFile?.takeIf { it.isFile }?.readText() ?: return ProviderMatchers()
            return parseProviderBody(spec, body)
        }
        val url = spec.url ?: return ProviderMatchers()
        val safeName = spec.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cacheFile = File(cacheDir, "$safeName.${spec.format}")
        val body = runCatching {
            val downloaded = SubscriptionImporter.download(url, socksPort)
            cacheFile.writeText(downloaded)
            downloaded
        }.getOrElse { err ->
            when {
                cacheFile.isFile -> {
                    log("[rules] using cached rule-provider '${spec.name}' (${err.message})")
                    cacheFile.readText()
                }
                localPathFile?.isFile == true -> {
                    log("[rules] using existing rule-set file '${localPathFile.path}' for '${spec.name}'")
                    localPathFile.readText()
                }
                else -> {
                    log("[rules] could not fetch rule-provider '${spec.name}': ${err.message}")
                    return ProviderMatchers()
                }
            }
        }
        return parseProviderBody(spec, body)
    }

    private fun parseProviderBody(spec: RuleProviderSpec, body: String): ProviderMatchers {
        val entries = if (spec.format == "yaml") {
            val map = loadYamlMap(body)
            (map?.get("payload") as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() && !s.startsWith("#") } }
                .orEmpty()
        } else {
            body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
        return parsePayloadEntries(spec.behavior, entries)
    }

    private fun parsePayloadEntries(behavior: String, entries: List<String>): ProviderMatchers {
        return when (behavior) {
            "domain" -> ProviderMatchers(domains = entries.mapNotNull { translateDomainEntry(it, "domain") })
            "ipcidr" -> ProviderMatchers(ips = entries.filter { it.isNotBlank() })
            "classical" -> {
                val domains = ArrayList<String>()
                val ips = ArrayList<String>()
                val classical = ArrayList<RoutingRule>()
                for (entry in entries) {
                    if (entry.startsWith("AND,", ignoreCase = true)) {
                        parseAndRule(
                            line = "$entry,PROXY",
                            index = classical.size,
                            effectiveTargetTag = { RuleTarget.PROXY.tag },
                        )?.let { classical += it }
                        continue
                    }
                    val parts = entry.split(',').map { it.trim() }
                    if (parts.size < 2) continue
                    val kind = parts[0].uppercase()
                    val v = parts[1]
                    when (kind) {
                        "DOMAIN" -> if (v.isNotEmpty()) domains += "full:$v"
                        "DOMAIN-SUFFIX" -> v.trimStart('.').takeIf { it.isNotEmpty() }?.let { domains += "domain:$it" }
                        "DOMAIN-KEYWORD" -> if (v.isNotEmpty()) domains += "keyword:$v"
                        "DOMAIN-REGEX" -> if (v.isNotEmpty()) domains += "regexp:$v"
                        "GEOSITE" -> normalizeGeosite(v)?.let { domains += it }
                        "IP-CIDR", "IP-CIDR6" -> if (v.isNotEmpty()) ips += v
                        "GEOIP" -> ips += normalizeGeoip(v)
                        "DST-PORT" -> normalizePort(v)?.let { classical += RoutingRule(port = it) }
                        "PROCESS-NAME", "PROCESS-NAME-REGEX" -> {
                            // Desktop/server core does not filter remote LAN clients by process-name;
                            // ignore silently like ClashRoutingParser.
                        }
                    }
                }
                ProviderMatchers(domains = domains, ips = ips, classicalRules = classical)
            }
            else -> ProviderMatchers()
        }
    }

    /**
     * Maps binary MetaCubeX `.mrs` rule-set names/URLs to verified `geosite:` and
     * `geoip:` categories shipped in `geosite.dat` / `geoip.dat`.
     */
    private fun mapMrsProvider(spec: RuleProviderSpec, log: (String) -> Unit): ProviderMatchers {
        val urlSlug = spec.url
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.removeSuffix(".mrs")
            ?.lowercase()
            .orEmpty()
        val key = spec.name.lowercase()

        if (spec.behavior == "ipcidr" || spec.url?.contains("/geoip/", ignoreCase = true) == true) {
            val token = when {
                urlSlug == "telegram" || key.contains("telegram") -> return ProviderMatchers(ips = TELEGRAM_CIDRS)
                urlSlug == "cloudflare" || key.contains("cloudflare") -> return ProviderMatchers(ips = CLOUDFLARE_CIDRS)
                urlSlug == "private" || key.contains("private") -> "geoip:private"
                urlSlug == "ru" || key.contains("ru") -> "geoip:ru"
                urlSlug == "cn" || key.contains("cn") -> "geoip:cn"
                urlSlug.length == 2 && urlSlug.all { it in 'a'..'z' } -> "geoip:$urlSlug"
                else -> {
                    log("[rules] skipping unmapped ipcidr .mrs rule-provider '${spec.name}' ($urlSlug)")
                    return ProviderMatchers()
                }
            }
            return ProviderMatchers(ips = listOf(token))
        }

        val geosite = normalizeGeosite(urlSlug.ifEmpty { key })
            ?: normalizeGeosite(key)
        if (geosite != null) {
            return ProviderMatchers(domains = listOf(geosite))
        }
        log("[rules] skipping unmapped domain .mrs rule-provider '${spec.name}' ($urlSlug)")
        return ProviderMatchers()
    }

    /**
     * Normalizes a Mihomo geosite name to a category confirmed to exist in `geosite.dat`.
     */
    fun normalizeGeosite(raw: String): String? {
        val clean = raw.trim().lowercase().substringBefore('@')
        if (clean.isEmpty()) return null
        val mapped = when (clean) {
            "ru", "geosite-ru", "category-ru" -> "category-ru"
            "private", "geosite-private" -> "private"
            "youtube" -> "youtube"
            "discord", "discord_domains", "discord-domains" -> "discord"
            "telegram", "telegram-domains" -> "telegram"
            "ai", "category-ai-!cn", "openai-ai" -> "category-ai-!cn"
            "category-porn", "porn" -> "category-porn"
            "google-deepmind", "deepmind" -> "google-deepmind"
            "speedtest", "speedtest-net", "ookla-speedtest" -> "speedtest"
            "category-remote-control", "remote-control" -> "category-remote-control"
            "google" -> "google"
            "github" -> "github"
            "openai" -> "openai"
            "anthropic" -> "anthropic"
            "netflix" -> "netflix"
            "twitter", "x" -> "twitter"
            "facebook", "meta" -> "facebook"
            "instagram" -> "instagram"
            "ads", "category-ads-all", "oisd", "oisd_big", "oisd-big" -> "category-ads-all"
            "cn", "geolocation-cn" -> "cn"
            "geolocation-!cn", "gfw" -> "geolocation-!cn"
            else -> return null
        }
        return "geosite:$mapped"
    }

    private fun normalizeGeoip(raw: String): List<String> {
        val clean = raw.trim().lowercase()
        return when {
            clean == "telegram" -> TELEGRAM_CIDRS
            clean == "cloudflare" -> CLOUDFLARE_CIDRS
            clean == "private" -> listOf("geoip:private")
            clean.length == 2 && clean.all { it in 'a'..'z' } -> listOf("geoip:$clean")
            else -> emptyList()
        }
    }

    private fun translateDomainEntry(raw: String, mode: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("+.")) {
            val rest = s.substring(2)
            return if (rest.isEmpty() || '*' in rest || '+' in rest) null else "domain:$rest"
        }
        if (s.startsWith(".")) {
            val rest = s.substring(1)
            return if (rest.isEmpty() || '*' in rest || '+' in rest) null else "domain:$rest"
        }
        if ('*' in s || '+' in s) {
            val regex = buildString {
                append('^')
                for (ch in s) {
                    when (ch) {
                        '*' -> append("[^.]*")
                        '.' -> append("\\.")
                        '+', '?', '^', '$', '(', ')', '[', ']', '{', '}', '|', '\\' -> append('\\').append(ch)
                        else -> append(ch)
                    }
                }
                append('$')
            }
            return "regexp:$regex"
        }
        return if (mode == "domain") "full:$s" else s
    }

    private fun normalizePort(raw: String): String? {
        val normalized = raw.trim().replace('/', ',')
        return normalized.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() || c == ',' || c == '-' } }
    }

    /**
     * Translates simple Mihomo `AND,((COND1),(COND2)),TARGET` rules where conditions
     * constrain orthogonal fields (`NETWORK`, `DST-PORT`, `DOMAIN*`, `IP-CIDR*`).
     */
    private fun parseAndRule(
        line: String,
        index: Int,
        effectiveTargetTag: (String) -> String?,
        providerLookup: (String) -> ProviderMatchers? = { null },
    ): RoutingRule? {
        val lastComma = line.lastIndexOf(',')
        if (lastComma <= 4) return null
        val targetName = line.substring(lastComma + 1).trim()
        val targetTag = effectiveTargetTag(targetName) ?: return null
        val body = line.substring(4, lastComma).trim()
        if (!body.startsWith("(") || !body.endsWith(")")) return null

        val inner = body.substring(1, body.length - 1)
        val condRegex = Regex("\\(([^()]+)\\)")
        val matches = condRegex.findAll(inner).map { it.groupValues[1].trim() }.toList()
        if (matches.isEmpty()) return null

        val domains = ArrayList<String>()
        val ips = ArrayList<String>()
        var port = ""
        var network = ""

        for (cond in matches) {
            val parts = cond.split(',').map { it.trim() }
            if (parts.size < 2) return null
            val kind = parts[0].uppercase()
            val v = parts[1]
            when (kind) {
                "NETWORK" -> {
                    val n = v.lowercase()
                    if (n != "tcp" && n != "udp") return null
                    if (network.isNotEmpty() && network != n) return null
                    network = n
                }
                "DST-PORT" -> {
                    val p = normalizePort(v) ?: return null
                    port = if (port.isEmpty()) p else "$port,$p"
                }
                "DOMAIN" -> domains += "full:$v"
                "DOMAIN-SUFFIX" -> domains += "domain:${v.trimStart('.')}"
                "DOMAIN-KEYWORD" -> domains += "keyword:$v"
                "GEOSITE" -> domains += (normalizeGeosite(v) ?: return null)
                "IP-CIDR", "IP-CIDR6" -> ips += v
                "GEOIP" -> ips += normalizeGeoip(v).ifEmpty { return null }
                "RULE-SET" -> {
                    val pm = providerLookup(v) ?: return null
                    if (pm.classicalRules.isNotEmpty() || (pm.domains.isEmpty() && pm.ips.isEmpty())) return null
                    domains += pm.domains
                    ips += pm.ips
                }
                else -> return null
            }
        }

        val rule = RoutingRule(
            id = "mihomo-and-$index",
            name = "[Mihomo] $line",
            domains = domains.joinToString("\n"),
            ip = ips.joinToString("\n"),
            port = port,
            network = network,
            target = targetTag,
        )
        return rule.takeIf { it.hasMatchers }
    }

}
