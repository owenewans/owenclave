package io.nekohasekai.sagernet.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import java.util.UUID

/**
 * A subscription (the "group" of the Android app).
 *
 * The URL is kept so it can be refreshed later; the profiles it produced are
 * stored in [ProfileStore.profiles] with [Profile.subscriptionId] pointing back
 * here, which makes a refresh a replace of exactly those profiles.
 */
class Subscription(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var url: String = "",
    /** Ask the server for a configuration tailored to this device, like on Android. */
    sendHwid: Boolean = false,
    lastUpdated: Long = 0L,
    lastError: String? = null,
    profileCount: Int = 0,
) {

    var sendHwid by mutableStateOf(sendHwid)
    var lastUpdated by mutableStateOf(lastUpdated)
    var lastError by mutableStateOf(lastError)
    var profileCount by mutableStateOf(profileCount)

    val displayName: String get() = name.ifBlank { url.substringAfterLast('/').ifBlank { url } }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("url", url)
        addProperty("sendHwid", sendHwid)
        addProperty("lastUpdated", lastUpdated)
        lastError?.let { addProperty("lastError", it) }
        addProperty("profileCount", profileCount)
    }

    companion object {

        fun fromJson(json: JsonObject) = Subscription(
            id = json.get("id")?.asString ?: UUID.randomUUID().toString(),
            name = json.get("name")?.asString.orEmpty(),
            url = json.get("url")?.asString.orEmpty(),
            sendHwid = json.get("sendHwid")?.asBoolean ?: false,
            lastUpdated = json.get("lastUpdated")?.asLong ?: 0L,
            lastError = json.get("lastError")?.takeIf { !it.isJsonNull }?.asString,
            profileCount = json.get("profileCount")?.asInt ?: 0,
        )

    }

}

/** Where matching traffic goes. Mirrors the outbound tags of the generated config. */
enum class RuleTarget(val tag: String, val label: String) {
    PROXY("proxy", "Proxy"),
    DIRECT("direct", "Direct"),
    BLOCK("block", "Block"),
}

/**
 * A routing rule.
 *
 * The fields map one to one onto the V2Ray routing rule object that the core
 * consumes, and to the Android `RuleEntity`, so the semantics are identical:
 * `domains` accepts the usual `domain:` / `full:` / `keyword:` / `regexp:`
 * prefixes, `port` accepts ranges and lists.
 */
class RoutingRule(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    enabled: Boolean = true,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var network: String = "",
    var protocol: String = "",
    var target: String = RuleTarget.PROXY.tag,
) {

    var enabled by mutableStateOf(enabled)

    /** True when the rule has at least one matcher, otherwise the core rejects it. */
    val hasMatchers: Boolean
        get() = listOf(domains, ip, port, network, protocol).any { it.isNotBlank() }

    val summary: String
        get() = buildList {
            if (domains.isNotBlank()) add("domain=${domains.trim()}")
            if (ip.isNotBlank()) add("ip=${ip.trim()}")
            if (port.isNotBlank()) add("port=${port.trim()}")
            if (network.isNotBlank()) add("net=${network.trim()}")
            if (protocol.isNotBlank()) add("proto=${protocol.trim()}")
        }.joinToString(", ").ifBlank { "empty" }

    val displayName: String
        get() = name.ifBlank { summary }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("enabled", enabled)
        addProperty("domains", domains)
        addProperty("ip", ip)
        addProperty("port", port)
        addProperty("network", network)
        addProperty("protocol", protocol)
        addProperty("target", target)
    }

    companion object {

        fun fromJson(json: JsonObject) = RoutingRule(
            id = json.get("id")?.asString ?: UUID.randomUUID().toString(),
            name = json.get("name")?.asString.orEmpty(),
            enabled = json.get("enabled")?.asBoolean ?: true,
            domains = json.get("domains")?.asString.orEmpty(),
            ip = json.get("ip")?.asString.orEmpty(),
            port = json.get("port")?.asString.orEmpty(),
            network = json.get("network")?.asString.orEmpty(),
            protocol = json.get("protocol")?.asString.orEmpty(),
            target = json.get("target")?.asString ?: RuleTarget.PROXY.tag,
        )

    }

}

/** Client owned device HWID, per the Remnawave device limit protocol. */
fun DesktopSettings.currentHwid(): String {
    if (hwidValue.isBlank()) {
        hwidValue = UUID.randomUUID().toString().replace("-", "")
    }
    return hwidValue
}

/** The headers the Android app attaches when HWID reporting is enabled. */
fun DesktopSettings.hwidHeaders(subscriptionOverride: Boolean): Map<String, String> {
    if (!sendHwid && !subscriptionOverride) return emptyMap()
    val systemName = System.getProperty("os.name", "").lowercase()
    val os = when {
        systemName.contains("win") -> "Windows"
        systemName.contains("mac") -> "macOS"
        else -> "Linux"
    }
    return mapOf(
        "x-hwid" to currentHwid(),
        "x-device-os" to os,
        "x-ver-os" to System.getProperty("os.version", ""),
        "x-device-model" to "Desktop ${System.getProperty("os.arch", "")}".trim(),
    )
}
