package io.nekohasekai.sagernet.desktop

import com.google.gson.JsonObject
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http3.Http3Bean
import io.nekohasekai.sagernet.fmt.masque.MASQUEBean
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.tuic5.Tuic5Bean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import java.util.UUID

/**
 * A stored proxy profile.
 *
 * Everything that is protocol specific lives in the shared [AbstractBean] of the
 * Android app, so the desktop client speaks exactly the same data model and the
 * same import parsers. A profile can alternatively carry a raw V2Ray JSON config
 * (custom profile), which is passed to the core unchanged.
 */
class Profile(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var bean: AbstractBean? = null,
    var customConfig: String? = null,
    /** Set when the profile came from a subscription, see [Subscription]. */
    var subscriptionId: String? = null,
) {

    val displayName: String
        get() = name.ifBlank { bean?.displayName()?.ifBlank { null } ?: "Unnamed" }

    val protocolName: String
        get() = when (val b = bean) {
            is HttpBean -> b.protocolName()
            is SOCKSBean -> b.protocolName()
            is VLESSBean -> "VLESS"
            is VMessBean -> "VMess"
            is ShadowsocksRBean -> "ShadowsocksR"
            is Tuic5Bean -> "TUIC"
            is AnyTLSBean -> "AnyTLS"
            is SSHBean -> "SSH"
            is ShadowQUICBean -> "ShadowQUIC"
            is Http3Bean -> "HTTP3"
            is MASQUEBean -> "MASQUE"
            null -> if (customConfig != null) "Custom" else "Unknown"
            else -> b.javaClass.simpleName.removeSuffix("Bean")
        }

    val address: String
        get() = bean?.displayAddress() ?: "raw config"

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        subscriptionId?.let { addProperty("subscriptionId", it) }
        bean?.let {
            addProperty("beanClass", it.javaClass.name)
            add("bean", gson.toJsonTree(it))
        }
        customConfig?.let { addProperty("customConfig", it) }
    }

    /**
     * Canonical JSON representation of everything in this profile except [id],
     * used to detect whether a refreshed subscription actually changed a node.
     */
    fun contentSignature(): String = JsonObject().apply {
        addProperty("name", name)
        subscriptionId?.let { addProperty("subscriptionId", it) }
        bean?.let {
            val normalized = runCatching { it.clone().apply { initializeDefaultValues() } }.getOrDefault(it)
            addProperty("beanClass", normalized.javaClass.name)
            add("bean", gson.toJsonTree(normalized))
        }
        customConfig?.let { addProperty("customConfig", it) }
    }.toString()

    companion object {

        /**
         * Preserves existing [Profile.id] values across subscription/config refreshes
         * whenever an incoming node matches an existing one (exact content first, then
         * same display name + protocol), and reports whether anything changed.
         */
        fun reconcileProfiles(existing: List<Profile>, incoming: List<Profile>): Pair<List<Profile>, Boolean> {
            if (existing.isEmpty()) {
                return incoming to incoming.isNotEmpty()
            }
            val existingSigs = existing.map { it.contentSignature() }
            val incomingSigs = incoming.map { it.contentSignature() }
            val usedExisting = BooleanArray(existing.size)
            val matchedIds = arrayOfNulls<String>(incoming.size)

            // Pass 1: exact content match preserves the exact existing ID.
            for (i in incoming.indices) {
                val sig = incomingSigs[i]
                for (j in existing.indices) {
                    if (!usedExisting[j] && existingSigs[j] == sig) {
                        usedExisting[j] = true
                        matchedIds[i] = existing[j].id
                        break
                    }
                }
            }

            // Pass 2: same logical server name (+ protocol, then name alone) keeps ID stable
            // when e.g. port/credentials changed, while still flagging `changed = true`.
            for (i in incoming.indices) {
                if (matchedIds[i] != null) continue
                val inc = incoming[i]
                var matchIdx = -1
                for (j in existing.indices) {
                    if (!usedExisting[j] &&
                        existing[j].displayName == inc.displayName &&
                        existing[j].protocolName == inc.protocolName
                    ) {
                        matchIdx = j
                        break
                    }
                }
                if (matchIdx < 0) {
                    for (j in existing.indices) {
                        if (!usedExisting[j] && existing[j].displayName == inc.displayName) {
                            matchIdx = j
                            break
                        }
                    }
                }
                if (matchIdx >= 0) {
                    usedExisting[matchIdx] = true
                    matchedIds[i] = existing[matchIdx].id
                }
            }

            val reconciled = incoming.mapIndexed { idx, p ->
                val reusedId = matchedIds[idx]
                if (reusedId != null && reusedId != p.id) {
                    Profile(
                        id = reusedId,
                        name = p.name,
                        bean = p.bean,
                        customConfig = p.customConfig,
                        subscriptionId = p.subscriptionId,
                    )
                } else {
                    p
                }
            }

            val changed = existing.size != reconciled.size ||
                existing.indices.any { idx ->
                    existing[idx].id != reconciled[idx].id || existingSigs[idx] != incomingSigs[idx]
                }
            return reconciled to changed
        }

        fun fromJson(json: JsonObject): Profile {
            val profile = Profile(
                id = json.get("id")?.asString ?: UUID.randomUUID().toString(),
                name = json.get("name")?.asString.orEmpty(),
                customConfig = json.get("customConfig")?.asString,
                subscriptionId = json.get("subscriptionId")?.takeIf { !it.isJsonNull }?.asString,
            )
            val beanClass = json.get("beanClass")?.asString
            val beanJson = json.get("bean")
            if (!beanClass.isNullOrEmpty() && beanJson != null && !beanJson.isJsonNull) {
                runCatching {
                    val type = Class.forName(beanClass)
                    val bean = gson.fromJson(beanJson, type) as AbstractBean
                    bean.initializeDefaultValues()
                    profile.bean = bean
                }
            }
            return profile
        }

    }

}
