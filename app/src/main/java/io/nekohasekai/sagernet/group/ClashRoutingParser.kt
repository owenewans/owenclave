/******************************************************************************
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <https://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.ktx.Logs

/**
 * Best-effort translator from mihomo/clash-meta `proxy-groups` + `rules` (as used by
 * Remnawave-style subscriptions, see owenclave#6) into owenclave's native routing
 * primitives (v2ray/xray RoutingObject rules, expressed as [RuleEntity] rows).
 *
 * This is intentionally NOT a clash rule engine reimplementation. owenclave's engine has
 * no concept of a user-selectable, multi-candidate proxy group (mihomo `select`/`fallback`/
 * `url-test` groups) — a rule's outbound is always exactly one of: the subscription's proxy,
 * DIRECT, REJECT, or one specific proxy entity. So every mihomo proxy-group is *collapsed*
 * to one of those three outcomes by [resolveOutbound]: if any reachable member of the group
 * (including nested groups, and Remnawave's `remnawave.include-proxies` extension) is a real
 * proxy, the whole group routes through the proxy — never silently DIRECT, to avoid leaking
 * traffic that the source config intended to tunnel. Only a group whose every branch resolves
 * to `DIRECT` becomes bypass, and only a group whose every branch is `REJECT`/`REJECT-DROP`
 * becomes block.
 *
 * What's translated:
 * - `rules:` entries of type DOMAIN, DOMAIN-SUFFIX, DOMAIN-KEYWORD, IP-CIDR/IP-CIDR6, DST-PORT,
 *   MATCH, and RULE-SET references to *inline* `rule-providers` (expanded in place).
 *
 * What's explicitly skipped (collected in [ParsedRouting.warnings], not silently dropped):
 * - `rule-providers` of `type: http` (remote geosite/geoip .mrs/.yaml/.lst downloads) — mapping
 *   these to owenclave's `geosite:`/`geoip:` categories would require guessing category names
 *   that may not exist in the user's geosite.dat/geoip.dat, which would silently misroute
 *   traffic. Left for the user to recreate manually with `geosite:`/`geoip:` rules if desired.
 * - PROCESS-NAME-REGEX, IP-ASN, AND/OR/NOT logical combinators, and any other rule type without
 *   a direct native equivalent.
 * - `dns:`, `tun:`, `sniffer:` and other network-level knobs — those are process-wide app
 *   settings in owenclave, not per-subscription, so auto-applying them here would have
 *   surprising side effects on unrelated profiles. Not attempted.
 */
object ClashRoutingParser {

    data class ParsedRouting(
        val rules: List<RuleEntity>,
        val warnings: List<String>,
    )

    private const val OUTBOUND_PROXY = 0L
    private const val OUTBOUND_BYPASS = -1L
    private const val OUTBOUND_BLOCK = -2L

    @Suppress("UNCHECKED_CAST")
    fun parse(yaml: Map<*, *>, groupId: Long, namePrefix: String): ParsedRouting {
        val warnings = ArrayList<String>()
        val out = ArrayList<RuleEntity>()

        val rules = yaml["rules"] as? List<*> ?: return ParsedRouting(emptyList(), emptyList())

        // name -> "direct" | "reject" | "proxy" (anything else defined under `proxies:`)
        val proxyKinds = HashMap<String, String>()
        (yaml["proxies"] as? List<*>)?.forEach { entry ->
            val proxy = entry as? Map<String, Any?> ?: return@forEach
            val name = proxy["name"] as? String ?: return@forEach
            proxyKinds[name] = when ((proxy["type"] as? String)?.lowercase()) {
                "direct" -> "direct"
                "reject", "reject-drop" -> "reject"
                else -> "proxy"
            }
        }

        // name -> group definition, for recursive resolution
        data class ClashGroup(val proxies: List<String>, val includeProxies: Boolean)
        val groups = HashMap<String, ClashGroup>()
        (yaml["proxy-groups"] as? List<*>)?.forEach { entry ->
            val group = entry as? Map<String, Any?> ?: return@forEach
            val name = group["name"] as? String ?: return@forEach
            val members = (group["proxies"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val remnawave = group["remnawave"] as? Map<*, *>
            val includeProxies = (remnawave?.get("include-proxies") as? Boolean) ?: false
            groups[name] = ClashGroup(members, includeProxies)
        }

        fun resolveOutbound(name: String, visited: MutableSet<String> = HashSet()): Long {
            when (name.uppercase()) {
                "DIRECT" -> return OUTBOUND_BYPASS
                "REJECT", "REJECT-DROP", "PASS" -> return OUTBOUND_BLOCK
            }
            proxyKinds[name]?.let {
                return when (it) {
                    "direct" -> OUTBOUND_BYPASS
                    "reject" -> OUTBOUND_BLOCK
                    else -> OUTBOUND_PROXY
                }
            }
            val group = groups[name] ?: return OUTBOUND_PROXY // unknown reference: never silently leak direct
            if (!visited.add(name)) return OUTBOUND_PROXY // cycle guard
            if (group.includeProxies) return OUTBOUND_PROXY
            var sawProxy = false
            var sawDirect = false
            var sawBlock = false
            for (member in group.proxies) {
                when (resolveOutbound(member, visited)) {
                    OUTBOUND_PROXY -> sawProxy = true
                    OUTBOUND_BYPASS -> sawDirect = true
                    OUTBOUND_BLOCK -> sawBlock = true
                }
            }
            return when {
                sawProxy -> OUTBOUND_PROXY
                sawDirect -> OUTBOUND_BYPASS
                sawBlock -> OUTBOUND_BLOCK
                else -> OUTBOUND_PROXY // empty group: never silently leak direct
            }
        }

        // inline rule-providers: name -> list of "TYPE,payload" tokens (same syntax as `rules:`, minus target)
        val inlineProviders = HashMap<String, List<String>>()
        val httpProviderNames = HashSet<String>()
        (yaml["rule-providers"] as? Map<String, Any?>)?.forEach { (providerName, definition) ->
            val provider = definition as? Map<String, Any?> ?: return@forEach
            when (provider["type"] as? String) {
                "inline" -> {
                    val payload = (provider["payload"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    inlineProviders[providerName] = payload
                }
                "http" -> httpProviderNames.add(providerName)
                else -> {}
            }
        }

        var userOrder = 0L
        var skipped = 0
        val skippedTypes = LinkedHashSet<String>()
        val skippedHttpProviders = LinkedHashSet<String>()

        fun addRule(type: String, payload: String, target: String) {
            val entity = RuleEntity(name = "$namePrefix ${type.lowercase()}:$payload".take(255), userOrder = userOrder++, enabled = true)
            when (type) {
                "DOMAIN" -> entity.domains = "full:$payload"
                "DOMAIN-SUFFIX" -> entity.domains = "domain:$payload"
                "DOMAIN-KEYWORD" -> entity.domains = "keyword:$payload"
                "IP-CIDR", "IP-CIDR6" -> entity.ip = payload
                "DST-PORT" -> entity.port = payload
                "MATCH" -> {} // no matcher fields: matches everything
                else -> {
                    skipped++; skippedTypes.add(type); return
                }
            }
            entity.outbound = resolveOutbound(target)
            out.add(entity)
        }

        fun handleLine(line: String) {
            val tokens = line.split(",").map { it.trim() }
            if (tokens.isEmpty()) return
            val type = tokens[0]
            when (type) {
                "MATCH" -> {
                    if (tokens.size >= 2) addRule("MATCH", "", tokens[1])
                }
                "RULE-SET" -> {
                    if (tokens.size < 3) return
                    val providerName = tokens[1]
                    val target = tokens[2]
                    when {
                        inlineProviders.containsKey(providerName) -> {
                            inlineProviders[providerName]!!.forEach { payloadLine ->
                                val payloadTokens = payloadLine.split(",").map { it.trim() }
                                if (payloadTokens.size >= 2) {
                                    addRule(payloadTokens[0], payloadTokens[1], target)
                                }
                            }
                        }
                        httpProviderNames.contains(providerName) -> skippedHttpProviders.add(providerName)
                        else -> {
                            skipped++; skippedTypes.add("RULE-SET:$providerName")
                        }
                    }
                }
                "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "IP-CIDR", "IP-CIDR6", "DST-PORT" -> {
                    if (tokens.size >= 3) addRule(type, tokens[1], tokens[2])
                }
                else -> {
                    skipped++
                    skippedTypes.add(type)
                }
            }
        }

        rules.forEach { entry ->
            val line = entry as? String ?: return@forEach
            handleLine(line)
        }

        if (skipped > 0) {
            warnings.add("Skipped $skipped unsupported rule(s): ${skippedTypes.joinToString(", ")}")
        }
        if (skippedHttpProviders.isNotEmpty()) {
            warnings.add(
                "Skipped remote rule-provider(s) (no reliable geosite/geoip category mapping): " +
                    skippedHttpProviders.joinToString(", ")
            )
        }
        if (warnings.isNotEmpty()) {
            Logs.i("ClashRoutingParser: ${warnings.joinToString(" | ")}")
            out.add(0, RuleEntity(
                name = "$namePrefix ${warnings.size} warning(s), see logcat".take(255),
                userOrder = -1,
                enabled = false,
            ))
        }

        return ParsedRouting(out, warnings)
    }
}
