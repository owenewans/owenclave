/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.group

import android.net.Uri
import cn.hutool.core.codec.Base64
import cn.hutool.json.*
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria2.Hysteria2Bean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.fixInvalidParams
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic5.Tuic5Bean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import org.ini4j.Ini
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.StringReader

@Suppress("EXPERIMENTAL_API_USAGE")
object RawUpdater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean
    ) {

        val link = subscription.link
        var proxies: List<AbstractBean>
        if (link.startsWith("content://")) {
            val contentText = app.contentResolver.openInputStream(Uri.parse(link))
                ?.bufferedReader()
                ?.readText()

            proxies = contentText?.let { parseRaw(contentText) }
                ?: error(app.getString(R.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                if (SagerNet.started && DataStore.startedProfile > 0) {
                    useSocks5(DataStore.socksPort)
                }
            }.newRequest().apply {
                setURL(subscription.link)
                if (subscription.customUserAgent.isNotBlank()) {
                    setUserAgent(subscription.customUserAgent)
                } else {
                    setUserAgent(USER_AGENT)
                }
            }.execute()

            proxies = parseRaw(response.contentString)
                ?: error(app.getString(R.string.no_proxies_found))

            val subscriptionUserinfo = response.getHeader("Subscription-Userinfo")
            if (subscriptionUserinfo.isNotEmpty()) {
                fun get(regex: String): String? {
                    return regex.toRegex().findAll(subscriptionUserinfo).mapNotNull {
                        if (it.groupValues.size > 1) it.groupValues[1] else null
                    }.firstOrNull()
                }
                var used = 0L
                try {
                    val upload = get("upload=([0-9]+)")?.toLong() ?: -1L
                    if (upload > 0L) {
                        used += upload
                    }
                    val download = get("download=([0-9]+)")?.toLong() ?: -1L
                    if (download > 0L) {
                        used += download
                    }
                    val total = get("total=([0-9]+)")?.toLong() ?: -1L
                    subscription.apply {
                        if (upload > 0L || download > 0L) {
                            bytesUsed = used
                            bytesRemaining = if (total > 0L) total - used else -1L
                        } else {
                            bytesUsed = -1L
                            bytesRemaining = -1L
                        }
                        expiryDate = get("expire=([0-9]+)")?.toLong() ?: -1L
                    }
                } catch (_: Exception) {
                }
            } else {
                subscription.apply {
                    bytesUsed = -1L
                    bytesRemaining = -1L
                    expiryDate = -1L
                }

            }

        }

        val proxiesMap = LinkedHashMap<String, AbstractBean>()
        for (proxy in proxies) {
            var index = 0
            var name = proxy.displayName()
            while (proxiesMap.containsKey(name)) {
                println("Exists name: $name")
                index++
                name = name.replace(" (${index - 1})", "")
                name = "$name ($index)"
                proxy.name = name
            }
            proxiesMap[proxy.displayName()] = proxy
        }
        proxies = proxiesMap.values.toList()

        if (subscription.forceResolve) forceResolve(proxies, proxyGroup.id)

        val exists = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
        val duplicate = ArrayList<String>()
        if (subscription.deduplication) {
            Logs.d("Before deduplication: ${proxies.size}")
            val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
            val uniqueNames = HashMap<Protocols.Deduplication, String>()
            for (_proxy in proxies) {
                val proxy = Protocols.Deduplication(_proxy, _proxy.javaClass.toString())
                if (!uniqueProxies.add(proxy)) {
                    val index = uniqueProxies.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotBlank()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(_proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = _proxy.displayName()
                }
            }
            uniqueProxies.retainAll(uniqueNames.keys)
            proxies = uniqueProxies.toList().map { it.bean }
        }

        Logs.d("New profiles: ${proxies.size}")

        val nameMap = proxies.associateBy { bean ->
            bean.displayName()
        }

        Logs.d("Unique profiles: ${nameMap.size}")

        val toDelete = ArrayList<ProxyEntity>()
        val toReplace = exists.mapNotNull { entity ->
            val name = entity.displayName()
            if (nameMap.contains(name)) name to entity else let {
                toDelete.add(entity)
                null
            }
        }.toMap()

        Logs.d("toDelete profiles: ${toDelete.size}")
        Logs.d("toReplace profiles: ${toReplace.size}")

        val toUpdate = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size
        for ((name, bean) in nameMap.entries) {
            if (toReplace.contains(name)) {
                val entity = toReplace[name]!!
                val existsBean = entity.requireBean()
                existsBean.applyFeatureSettings(bean)
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name

                        Logs.d("Updated profile: $name")
                    }
                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder

                        Logs.d("Reordered profile: $name")
                    }
                    else -> {
                        Logs.d("Ignored profile: $name")
                    }
                }
            } else {
                changed++
                SagerDatabase.proxyDao.addProxy(ProxyEntity(
                    groupId = proxyGroup.id, userOrder = userOrder
                ).apply {
                    putBean(bean)
                })
                added.add(name)
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }

        SagerDatabase.proxyDao.updateProxy(toUpdate).also {
            Logs.d("Updated profiles: $it")
        }

        SagerDatabase.proxyDao.deleteProxy(toDelete).also {
            Logs.d("Deleted profiles: $it")
        }

        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).toInt()

        if (existCount != proxies.size) {
            Logs.e("Exist profiles: $existCount, new profiles: ${proxies.size}")
        }

        subscription.lastUpdated = System.currentTimeMillis() / 1000
        SagerDatabase.groupDao.updateGroup(proxyGroup)
        finishUpdate(proxyGroup)

        userInterface?.onUpdateSuccess(
            proxyGroup, changed, added, updated, deleted, duplicate, byUser
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseRaw(text: String): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        if (text.contains("proxies")) {
            try {
                // Mihomo (f.k.a. Clash.Meta), Clash
                val yaml = Yaml().apply {
                    addTypeDescription(TypeDescription(String::class.java, "str"))
                }.loadAs(text, Map::class.java)["proxies"] as? List<Map<String, Any?>>
                if (yaml != null) {
                    proxies.addAll(parseClashProxies(yaml))
                    proxies.forEach { it.initializeDefaultValues() }
                    return proxies
                }
            } catch (e: YAMLException) {
                Logs.w(e)
            }
        }

        if (text.contains("[Interface]")) {
            // wireguard
            try {
                val p = parseWireGuard(text)
                if (p.isNotEmpty()) {
                    proxies.addAll(parseWireGuard(text))
                    return proxies
                }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            val json = JSONUtil.parse(Libcore.stripJSON(text))
            return parseJSON(json)
        } catch (e: JSONException) {
            Logs.w(e)
        }

        try {
            return parseProxies(text.decodeBase64UrlSafe()).takeIf { it.isNotEmpty() }
                ?: error("Not found")
        } catch (e: Exception) {
            Logs.w(e)
        }

        try {
            return parseProxies(text).takeIf { it.isNotEmpty() } ?: error("Not found")
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (e: Exception) {
            Logs.w(e)
        }

        return null
    }

    fun parseWireGuard(conf: String): List<WireGuardBean> {
        val ini = Ini(StringReader(conf))
        val iface = ini["Interface"] ?: error("Missing 'Interface' selection")
        val bean = WireGuardBean().applyDefaultValues()
        val localAddresses = iface.getAll("Address")
        if (localAddresses.isNullOrEmpty()) error("Empty address in 'Interface' selection")
        bean.localAddress = localAddresses.flatMap { it.filterNot { it.isWhitespace() }.split(",") }.joinToString("\n")
        bean.privateKey = iface["PrivateKey"]
        bean.mtu = iface["MTU"]?.toInt()?.takeIf { it > 0 } ?: 1420
        val peers = ini.getAll("Peer")
        if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")
        val beans = mutableListOf<WireGuardBean>()
        for (peer in peers) {
            val endpoint = peer["Endpoint"]
            if (endpoint.isNullOrBlank() || !endpoint.contains(":")) {
                continue
            }

            val peerBean = bean.clone()
            peerBean.serverAddress = endpoint.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
            peerBean.serverPort = endpoint.substringAfterLast(":").toIntOrNull() ?: continue
            peerBean.peerPublicKey = peer["PublicKey"] ?: continue
            peerBean.peerPreSharedKey = peer["PreSharedKey"]
            beans.add(peerBean.applyDefaultValues())
        }
        if (beans.isEmpty()) error("Empty available peer list")
        return beans
    }

    fun parseJSON(json: JSON): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (json is JSONObject) {
            when {
                json.containsKey("method") -> {
                    return listOf(json.parseShadowsocks())
                }
                // v2ray outbound
                json.contains("protocol") -> {
                    val p = parseV2ray5Outbound(json)
                    if (p.isNotEmpty()) {
                        return p
                    }
                    return parseV2RayOutbound(json)
                }
                // sing-box outbound
                json.contains("type") -> {
                    val endpoints = parseSingBoxEndpoint(json)
                    if (endpoints.isNotEmpty()) {
                        return endpoints
                    }
                    return parseSingBoxOutbound(json)
                }
                json.contains("outbounds") || json.contains("endpoints")  -> {
                    json.getArray("outbounds")?.filterIsInstance<JSONObject>()?.forEach { outbound ->
                        if (outbound.contains("protocol")) {
                            val p = parseV2ray5Outbound(outbound)
                            if (p.isNotEmpty()) {
                                proxies.addAll(p)
                            } else {
                                proxies.addAll(parseV2RayOutbound(outbound))
                            }
                        } else if (outbound.contains("type")) {
                            proxies.addAll(parseSingBoxOutbound(outbound))
                        }
                    }
                    // sing-box wireguard endpoint format introduced in 1.11.0-alpha.19
                    json.getArray("endpoints")?.filterIsInstance<JSONObject>()?.forEach { endpoint ->
                        if (endpoint.containsKey("type")) {
                            proxies.addAll(parseSingBoxEndpoint(endpoint))
                        }
                    }
                }
                else -> json.forEach { _, it ->
                    if (it is JSON) {
                        proxies.addAll(parseJSON(it))
                    }
                }
            }
        } else {
            json as JSONArray
            json.forEach {
                if (it is JSON) {
                    proxies.addAll(parseJSON(it))
                }
            }
        }

        proxies.forEach { it.initializeDefaultValues() }
        return proxies
    }

    @Suppress("UNCHECKED_CAST")
    fun parseV2RayOutbound(outbound: JSONObject): List<AbstractBean> {
        // v2ray JSONv4 config, Xray config and JSONv4 config of Exclave's v2ray fork only
        val proxies = ArrayList<AbstractBean>()
        when (val proto = outbound.getString("protocol")?.lowercase()) {
            "vmess", "vless", "trojan", "shadowsocks", "socks", "http", "shadowsocks2022", "shadowsocks-2022" -> {
                val v2rayBean = when (proto) {
                    "vmess" -> VMessBean()
                    "vless" -> VLESSBean()
                    "trojan" -> TrojanBean()
                    "shadowsocks", "shadowsocks2022", "shadowsocks-2022" -> ShadowsocksBean()
                    "socks" -> SOCKSBean()
                    else -> HttpBean()
                }.applyDefaultValues()
                outbound.getObject("streamSettings")?.also { streamSettings ->
                    when (val security = streamSettings.getString("security")?.lowercase()) {
                        "tls", "utls" -> {
                            v2rayBean.security = "tls"
                            var tlsConfig = streamSettings.getObject("tlsSettings")
                            if (security == "utls") {
                                streamSettings.getObject("utlsSettings")?.also {
                                    tlsConfig = it.getObject("tlsConfig")
                                }
                            }
                            tlsConfig?.also { tlsSettings ->
                                tlsSettings.getString("serverName")?.also {
                                    v2rayBean.sni = it
                                }
                                (tlsSettings.getAny("alpn") as? List<String>)?.also {
                                    v2rayBean.alpn = it.joinToString("\n")
                                }
                                tlsSettings.getBoolean("allowInsecure")?.also {
                                    v2rayBean.allowInsecure = it
                                }
                                (tlsSettings.getObject("pinnedPeerCertificateChainSha256") as? List<String>)?.also {
                                    v2rayBean.pinnedPeerCertificateChainSha256 = it.joinToString("\n")
                                    tlsSettings.getBoolean("allowInsecureIfPinnedPeerCertificate")?.also { allowInsecure ->
                                        v2rayBean.allowInsecure = allowInsecure
                                    }
                                }
                                // do not parse "imitate"
                                // do not parse "fingerprint"
                            }
                        }
                        "reality" -> {
                            v2rayBean.security = "reality"
                            streamSettings.getObject("realitySettings")?.also { realitySettings ->
                                realitySettings.getString("serverName")?.also {
                                    v2rayBean.sni = it
                                }
                                realitySettings.getString("publicKey")?.also {
                                    v2rayBean.realityPublicKey = it
                                }
                                realitySettings.getString("shortId")?.also {
                                    v2rayBean.realityShortId = it
                                }
                                realitySettings.getString("spiderX")?.also {
                                    v2rayBean.realitySpiderX = it
                                }
                            }
                        }
                    }
                    when (streamSettings.getString("network")?.lowercase()) {
                        "tcp", "raw" -> {
                            v2rayBean.type = "tcp"
                            (streamSettings.getObject("tcpSettings") ?: streamSettings.getObject("rawSettings"))?.also { tcpSettings ->
                                tcpSettings.getObject("header")?.also { header ->
                                    when (header.getString("type")?.lowercase()) {
                                        "http" -> {
                                            v2rayBean.headerType = "http"
                                            header.getObject("request")?.also { request ->
                                                request.getString("path")?.also {
                                                    v2rayBean.host = it
                                                }
                                                request.getObject("headers")?.also { headers ->
                                                    (headers.getAny("Host") as? List<String>)?.also {
                                                        v2rayBean.host = it.joinToString(",")
                                                    } ?: headers.getString("Host")?.also {
                                                        v2rayBean.host = it
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "kcp", "mkcp" -> {
                            v2rayBean.type = "kcp"
                            streamSettings.getObject("kcpSettings")?.also { kcpSettings ->
                                kcpSettings.getString("seed")?.also {
                                    v2rayBean.mKcpSeed = it
                                }
                                kcpSettings.getObject("header")?.also { header ->
                                    header.getString("type")?.also {
                                        v2rayBean.headerType = it.lowercase()
                                    }
                                }
                            }
                        }
                        "ws", "websocket" -> {
                            v2rayBean.type = "ws"
                            streamSettings.getObject("wsSettings")?.also { wsSettings ->
                                (wsSettings.getAny("headers") as? Map<String, String>)?.forEach { (key, value) ->
                                    when (key.lowercase()) {
                                        "host" -> {
                                            v2rayBean.host = value
                                        }
                                    }
                                }
                                wsSettings.getString("host")?.also {
                                    v2rayBean.host = it // Xray's disgusting handling of Host header
                                }
                                wsSettings.getInteger("maxEarlyData")?.also {
                                    v2rayBean.wsMaxEarlyData = it
                                }
                                wsSettings.getString("earlyDataHeaderName")?.also {
                                    v2rayBean.earlyDataHeaderName = it
                                }
                                wsSettings.getString("path")?.also { path ->
                                    v2rayBean.path = path
                                    // RPRX's smart-assed invention. This of course will break under some conditions.
                                    val u = Libcore.parseURL(path)
                                    u.queryParameter("ed")?.also { ed ->
                                        u.deleteQueryParameter("ed")
                                        v2rayBean.path = u.string
                                        (ed.toIntOrNull())?.also {
                                            v2rayBean.wsMaxEarlyData = it
                                        }
                                        v2rayBean.earlyDataHeaderName = "Sec-WebSocket-Protocol"
                                    }
                                }
                            }
                        }
                        "http", "h2", "h3" -> {
                            v2rayBean.type = "http"
                            streamSettings.getObject("httpSettings")?.also { httpSettings ->
                                // will NOT follow https://github.com/XTLS/Xray-core/commit/0a252ac15d34e7c23a1d3807a89bfca51cbb559b
                                // as streamSettings will likely breaks the compatibility with v2ray
                                httpSettings.getString("host")?.also {
                                    v2rayBean.host = it
                                }
                                httpSettings.getString("path")?.also {
                                    v2rayBean.path = it
                                }
                            }
                        }
                        "quic" -> {
                            v2rayBean.type = "quic"
                            streamSettings.getObject("quicSettings")?.also { quicSettings ->
                                quicSettings.getString("security")?.also {
                                    v2rayBean.quicSecurity = it.lowercase()
                                }
                                quicSettings.getString("key")?.also {
                                    v2rayBean.quicKey = it
                                }
                                quicSettings.getObject("header")?.also { header ->
                                    header.getString("type")?.lowercase()?.also {
                                        v2rayBean.headerType = it.lowercase()
                                    }
                                }
                            }
                        }
                        "grpc", "gun" -> {
                            v2rayBean.type = "grpc"
                            // Xray hijacks the share link standard, uses escaped `serviceName` and some other non-standard `serviceName`s and breaks the compatibility with other implementations.
                            // Fixing the compatibility with Xray will break the compatibility with V2Ray and others.
                            // So do not fix the compatibility with Xray.
                            (streamSettings.getObject("grpcSettings") ?: streamSettings.getObject("gunSettings"))?.also { grpcSettings ->
                                grpcSettings.getString("serviceName")?.also {
                                    v2rayBean.grpcServiceName = it
                                }
                            }
                        }
                        "httpupgrade" -> {
                            v2rayBean.type = "httpupgrade"
                            streamSettings.getObject("httpupgradeSettings")?.also { httpupgradeSettings ->
                                (httpupgradeSettings.getAny("headers") as? Map<String, String>)?.forEach { (key, value) ->
                                    when (key.lowercase()) {
                                        "host" -> {
                                            // Xray's disgusting handling of Host header
                                            v2rayBean.host = value
                                        }
                                    }
                                }
                                httpupgradeSettings.getString("host")?.also {
                                    v2rayBean.host = it
                                }
                                httpupgradeSettings.getString("path")?.also {
                                    v2rayBean.path = it
                                    // RPRX's smart-assed invention. This of course will break under some conditions.
                                    val u = Libcore.parseURL(it)
                                    u.queryParameter("ed")?.also {
                                        u.deleteQueryParameter("ed")
                                        v2rayBean.path = u.string
                                    }
                                }
                            }
                        }
                        "meek" -> {
                            v2rayBean.type = "meek"
                            streamSettings.getObject("meekSettings")?.also { meekSettings ->
                                meekSettings.getString("url")?.also {
                                    v2rayBean.meekUrl = it
                                }
                            }
                        }
                        "mekya" -> {
                            v2rayBean.type = "mekya"
                            streamSettings.getObject("mekyaSettings")?.also { mekyaSettings ->
                                mekyaSettings.getString("url")?.also {
                                    v2rayBean.mekyaUrl = it
                                }
                                mekyaSettings.getObject("kcp")?.also { kcp ->
                                    kcp.getString("seed")?.also {
                                        v2rayBean.mekyaKcpSeed = it
                                    }
                                    kcp.getObject("header")?.also { header ->
                                        header.getString("type")?.also {
                                            v2rayBean.headerType = it.lowercase()
                                        }
                                    }
                                }
                            }
                        }
                        "splithttp", "xhttp" -> {
                            v2rayBean.type = "splithttp"
                            (streamSettings.getObject("splithttpSettings") ?: streamSettings.getObject("xhttpSettings"))?.also { splithttpSettings ->
                                (splithttpSettings.getAny("headers") as? Map<String, String>)?.forEach { (key, value) ->
                                    when (key.lowercase()) {
                                        "host" -> {
                                            // Xray's disgusting handling of Host header
                                            v2rayBean.host = value
                                        }
                                    }
                                }
                                splithttpSettings.getObject("extra")?.also { extra ->
                                    // disgusting `json.RawMessage` from Xray
                                    (extra.getAny("headers") as? Map<String, String>)?.forEach { (key, value) ->
                                        when (key.lowercase()) {
                                            "host" -> {
                                                // Xray's disgusting handling of Host header
                                                v2rayBean.host = value
                                            }
                                        }
                                    }
                                }
                                splithttpSettings.getString("host")?.also {
                                    v2rayBean.host = it
                                }
                                splithttpSettings.getString("path")?.also {
                                    v2rayBean.path = it
                                }
                                splithttpSettings.getString("mode")?.also {
                                    if (it == "stream-up" || it == "packet-up") {
                                        v2rayBean.splithttpMode = it
                                    }
                                }
                            }
                        }
                        "hysteria2", "hy2" -> {
                            v2rayBean.type = "hysteria2"
                            streamSettings.getObject("hy2Settings")?.also { hy2Settings ->
                                hy2Settings.getString("password")?.also {
                                    v2rayBean.hy2Password = it
                                }
                                hy2Settings.getObject("congestion")?.also { congestion ->
                                    congestion.getInteger("up_mbps")?.also {
                                        v2rayBean.hy2UpMbps = it
                                    }
                                    congestion.getInteger("down_mbps")?.also {
                                        v2rayBean.hy2DownMbps = it
                                    }
                                }
                                hy2Settings.getObject("obfs")?.also { obfs ->
                                    obfs.getString("type")?.also { type ->
                                        if (type == "salamander") {
                                            obfs.getString("password")?.also {
                                                v2rayBean.hy2ObfsPassword = it
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> return proxies
                    }
                }
                when (proto) {
                    "vmess" -> {
                        v2rayBean as VMessBean
                        (outbound.getString("tag"))?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            v2rayBean.packetEncoding = when (settings.getString("packetEncoding")?.lowercase()) {
                                "xudp" -> "xudp"
                                "packet" -> "packet"
                                else -> ""
                            }
                            (settings.getArray("vnext")?.get(0) as? JSONObject)?.also { vnext ->
                                vnext.getString("address")?.also {
                                    v2rayBean.serverAddress = it
                                }
                                vnext.getIntFromStringOrInt("port")?.also {
                                    v2rayBean.serverPort = it
                                }
                                (vnext.getArray("users")?.get(0) as? JSONObject)?.also { user ->
                                    user.getString("id")?.also {
                                        v2rayBean.uuid = it
                                    }
                                    user.getString("security")?.also {
                                        v2rayBean.encryption = it.lowercase()
                                    }
                                    user.getInteger("alterId")?.also {
                                        v2rayBean.alterId = it
                                    }
                                    user.getString("experiments")?.also {
                                        if (it.contains("AuthenticatedLength")) {
                                            v2rayBean.experimentalAuthenticatedLength = true
                                        }
                                        if (it.contains("NoTerminationSignal")) {
                                            v2rayBean.experimentalNoTerminationSignal = true
                                        }
                                    }
                                }
                                proxies.add(v2rayBean)
                            }
                        }
                    }
                    "vless" -> {
                        v2rayBean as VLESSBean
                        (outbound.getString("tag"))?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            v2rayBean.packetEncoding = when (settings.getString("packetEncoding")?.lowercase()) {
                                "xudp" -> "xudp"
                                "packet" -> "packet"
                                else -> ""
                            }
                            (settings.getArray("vnext")?.get(0) as? JSONObject)?.also { vnext ->
                                vnext.getString("address")?.also {
                                    v2rayBean.serverAddress = it
                                }
                                vnext.getIntFromStringOrInt("port")?.also {
                                    v2rayBean.serverPort = it
                                }
                                (vnext.getArray("users")?.get(0) as? JSONObject)?.also { user ->
                                    user.getString("id")?.also {
                                        v2rayBean.uuid = it
                                    }
                                    user.getString("encryption")?.also {
                                        v2rayBean.encryption = it.lowercase()
                                    }
                                    user.getString("flow")?.also {
                                        v2rayBean.flow = it
                                        v2rayBean.packetEncoding = "xudp"
                                    }
                                }
                                proxies.add(v2rayBean)
                            }
                        }
                    }
                    "shadowsocks" -> {
                        v2rayBean as ShadowsocksBean
                        outbound.getString("tag")?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            (settings.getArray("servers")?.get(0) as? JSONObject)?.also { server ->
                                settings.getString("plugin")?.also { plugin ->
                                    v2rayBean.plugin = plugin
                                    settings.getString("pluginOpts").also {
                                        v2rayBean.plugin += ";$it"
                                    }
                                }
                                server.getString("address")?.also {
                                    v2rayBean.serverAddress = it
                                }
                                server.getIntFromStringOrInt("port")?.also {
                                    v2rayBean.serverPort = it
                                }
                                server.getString("method")?.also {
                                    v2rayBean.method = it.lowercase()
                                }
                                server.getString("password")?.also {
                                    v2rayBean.password = it
                                }
                                // do not parse "experimentReducedIvHeadEntropy"
                                proxies.add(v2rayBean)
                            }
                        }
                    }
                    "shadowsocks2022" -> {
                        v2rayBean as ShadowsocksBean
                        outbound.getString("tag")?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            settings.getString("address")?.also {
                                v2rayBean.serverAddress = it
                            } ?: return proxies
                            settings.getIntFromStringOrInt("port")?.also {
                                v2rayBean.serverPort = it
                            } ?: return proxies
                            settings.getString("method")?.also {
                                v2rayBean.method = it.lowercase()
                            }
                            settings.getString("psk")?.also { psk ->
                                v2rayBean.password = psk
                                (settings.getAny("ipsk") as? List<String>)?.also { ipsk ->
                                    v2rayBean.password = ipsk.joinToString(":") + ":" + psk
                                }
                            }
                            settings.getString("plugin")?.also { plugin ->
                                v2rayBean.plugin = plugin
                                settings.getString("pluginOpts")?.also {
                                    v2rayBean.plugin += ";$it"
                                }
                            }
                            proxies.add(v2rayBean)
                        }
                    }
                    "shadowsocks-2022" -> {
                        v2rayBean as ShadowsocksBean
                        outbound.getString("tag")?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            settings.getString("address")?.also {
                                v2rayBean.serverAddress = it
                            }
                            settings.getIntFromStringOrInt("port")?.also {
                                v2rayBean.serverPort = it
                            }
                            settings.getString("method")?.also {
                                v2rayBean.method = it.lowercase()
                            }
                            settings.getString("password")?.also {
                                v2rayBean.password = it
                            }
                            settings.getString("plugin")?.also { plugin ->
                                v2rayBean.plugin = plugin
                                settings.getString("pluginOpts").also {
                                    v2rayBean.plugin += ";$it"
                                }
                            }
                            proxies.add(v2rayBean)
                        }
                    }
                    "trojan" -> {
                        v2rayBean as TrojanBean
                        outbound.getString("tag")?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            (settings.getArray("servers")?.get(0) as? JSONObject)?.also { server ->
                                server.getString("address")?.also {
                                    v2rayBean.serverAddress = it
                                }
                                server.getIntFromStringOrInt("port")?.also {
                                    v2rayBean.serverPort = it
                                }
                                server.getString("password")?.also {
                                    v2rayBean.password = it
                                }
                                proxies.add(v2rayBean)
                            }
                        }
                    }
                    "socks" -> {
                        v2rayBean as SOCKSBean
                        outbound.getString("tag")?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            v2rayBean.protocol = when (settings.getString("version")?.lowercase()) {
                                "4" -> SOCKSBean.PROTOCOL_SOCKS4
                                "4a" -> SOCKSBean.PROTOCOL_SOCKS4A
                                else -> SOCKSBean.PROTOCOL_SOCKS5
                            }
                            (settings.getArray("servers")?.get(0) as? JSONObject)?.also { server ->
                                server.getString("address")?.also {
                                    v2rayBean.serverAddress = it
                                }
                                server.getIntFromStringOrInt("port")?.also {
                                    v2rayBean.serverPort = it
                                }
                                (server.getArray("users")?.get(0) as? JSONObject)?.also { user ->
                                    user.getString("username")?.also {
                                        v2rayBean.username = it
                                    }
                                    user.getString("password")?.also {
                                        v2rayBean.password = it
                                    }
                                }
                                proxies.add(v2rayBean)
                            }
                        }
                    }
                    "http" -> {
                        v2rayBean as HttpBean
                        outbound.getString("tag")?.also {
                            v2rayBean.name = it
                        }
                        outbound.getObject("settings")?.also { settings ->
                            (settings.getArray("servers")?.get(0) as? JSONObject)?.also { server ->
                                server.getString("address")?.also {
                                    v2rayBean.serverAddress = it
                                }
                                server.getIntFromStringOrInt("port")?.also {
                                    v2rayBean.serverPort = it
                                }
                                (server.getArray("users")?.get(0) as? JSONObject)?.also { user ->
                                    user.getString("username")?.also {
                                        v2rayBean.username = it
                                    }
                                    user.getString("password")?.also {
                                        v2rayBean.password = it
                                    }
                                }
                                proxies.add(v2rayBean)
                            }
                        }
                    }
                }
            }
            "hysteria2" -> {
                val hysteria2Bean = Hysteria2Bean().applyDefaultValues()
                outbound.getString("tag")?.also {
                    hysteria2Bean.name = it
                }
                outbound.getObject("settings")?.also { settings ->
                    (settings.getArray("servers")?.get(0) as? JSONObject)?.also { server ->
                        server.getString("address")?.also {
                            hysteria2Bean.serverAddress = it
                        }
                        server.getIntFromStringOrInt("port")?.also {
                            hysteria2Bean.serverPorts = it.toString()
                        }
                    }
                }
                outbound.getObject("streamSettings")?.also { streamSettings ->
                    streamSettings.getString("network")?.lowercase()?.also { network ->
                        when (network) {
                            "hysteria2", "hy2" -> {
                                streamSettings.getObject("hy2Settings")?.also { hy2Settings ->
                                    hy2Settings.getString("password")?.also {
                                        hysteria2Bean.auth = it
                                    }
                                    hy2Settings.getObject("congestion")?.also { congestion ->
                                        congestion.getInteger("up_mbps")?.also {
                                            hysteria2Bean.uploadMbps = it
                                        }
                                        congestion.getInteger("down_mbps")?.also {
                                            hysteria2Bean.downloadMbps = it
                                        }
                                    }
                                    hy2Settings.getObject("obfs")?.also { obfs ->
                                        obfs.getString("type")?.also { type ->
                                            if (type == "salamander") {
                                                obfs.getString("password")?.also {
                                                    hysteria2Bean.obfs = it
                                                }
                                            }
                                        }
                                    }
                                    hy2Settings.getString("hopPorts")?.also {
                                        hysteria2Bean.serverPorts = it
                                    }
                                    hy2Settings.getInteger("hopInterval")?.also {
                                        hysteria2Bean.hopInterval = it
                                    }
                                }
                            }
                        }
                    }
                    streamSettings.getString("security")?.lowercase()?.also { security ->
                        when (security) {
                            "tls" -> {
                                streamSettings.getObject("tlsSettings")?.also { tlsSettings ->
                                    tlsSettings.getString("serverName")?.also {
                                        hysteria2Bean.sni = it
                                    }
                                    tlsSettings.getBoolean("allowInsecure")?.also {
                                        hysteria2Bean.allowInsecure = it
                                    }
                                }
                                proxies.add(hysteria2Bean)
                            }
                        }
                    }
                }
            }
            "wireguard" -> {
                val wireguardBean = WireGuardBean().applyDefaultValues()
                outbound.getString("tag")?.also {
                    wireguardBean.name = it
                }
                outbound.getObject("settings")?.also { settings ->
                    settings.getString("secretKey")?.also {
                        // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L126-L148
                        wireguardBean.privateKey = it.replace('_', '/').replace('-', '+').padEnd(44, '=')
                    }
                    // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L75
                    wireguardBean.localAddress = "10.0.0.1/32\nfd59:7153:2388:b5fd:0000:0000:0000:0001/128"
                    (settings.getAny("address") as? List<String>)?.also {
                        wireguardBean.localAddress = it.joinToString("\n")
                    }
                    wireguardBean.mtu = 1420
                    settings.getInteger("mtu")?.takeIf { it > 0 }?.also {
                        wireguardBean.mtu = it
                    }
                    (settings.getAny("reserved") as? List<Int>)?.also {
                        if (it.size == 3) {
                            wireguardBean.reserved = listOf(it[0].toString(), it[1].toString(), it[2].toString()).joinToString(",")
                        }
                    }
                    (settings.getArray("peers"))?.forEach { peer ->
                        proxies.add(wireguardBean.clone().apply {
                            (peer as? JSONObject)?.getString("endpoint")?.also {
                                serverAddress = it.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
                                serverPort = it.substringAfterLast(":").toIntOrNull()
                            }
                            (peer as? JSONObject)?.getString("publicKey")?.also {
                                peerPublicKey = it.replace('_', '/').replace('-', '+').padEnd(44, '=')
                            }
                            (peer as? JSONObject)?.getString("preSharedKey")?.also {
                                peerPreSharedKey = it.replace('_', '/').replace('-', '+').padEnd(44, '=')
                            }
                        })
                    }
                }
            }
            "ssh" -> {
                val sshBean = SSHBean().applyDefaultValues()
                outbound.getObject("settings")?.also { settings ->
                    outbound.getString("tag")?.also {
                        sshBean.name = it
                    }
                    settings.getString("address")?.also {
                        sshBean.serverAddress = it
                    }
                    settings.getIntFromStringOrInt("port")?.also {
                        sshBean.serverPort = it
                    }
                    settings.getString("user")?.also {
                        sshBean.username = it
                    }
                    settings.getString("publicKey")?.also {
                        sshBean.publicKey = it
                    }
                    settings.getString("privateKey")?.also {
                        sshBean.authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
                        sshBean.privateKey = it
                        settings.getString("password")?.also { pass ->
                            sshBean.privateKeyPassphrase = pass
                        }
                    } ?: settings.getString("password")?.also {
                        sshBean.authType = SSHBean.AUTH_TYPE_PASSWORD
                        sshBean.password = it
                    }
                    proxies.add(sshBean)
                }
            }
        }
        return proxies
    }

    @Suppress("UNCHECKED_CAST")
    fun parseSingBoxOutbound(outbound: JSONObject): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()
        when (val type = outbound["type"]) {
            "shadowsocks", "trojan", "vmess", "vless", "socks", "http" -> {
                val v2rayBean = when (type) {
                    "shadowsocks" -> ShadowsocksBean()
                    "trojan" -> TrojanBean()
                    "vmess" -> VMessBean()
                    "vless" -> VLESSBean()
                    "socks" -> SOCKSBean()
                    "http" -> HttpBean()
                    else -> return proxies
                }.applyDefaultValues().apply {
                    outbound["tag"]?.toString()?.also {
                        name = it
                    }
                    outbound.getString("server")?.also {
                        serverAddress = it
                    } ?: return proxies
                    outbound.getInteger("server_port")?.also {
                        serverPort = it
                    } ?: return proxies
                }
                when (type) {
                    "trojan", "vmess", "vless" -> {
                        outbound.getObject("transport")?.also { transport ->
                            when (transport["type"]) {
                                "ws" -> {
                                    v2rayBean.type = "ws"
                                    transport.getString("path")?.also {
                                        v2rayBean.path = it
                                    }
                                    transport.getObject("headers")?.also { headers ->
                                        (headers.getAny("host") as? (List<String>))?.get(0)?.also {
                                            v2rayBean.host = it
                                        } ?: headers.getString("host")?.also {
                                            v2rayBean.host = it
                                        }
                                    }
                                    transport.getInteger("max_early_data")?.also {
                                        v2rayBean.wsMaxEarlyData = it
                                    }
                                    transport.getString("early_data_header_name")?.also {
                                        v2rayBean.earlyDataHeaderName = it
                                    }
                                }
                                "http" -> {
                                    v2rayBean.type = "http"
                                    transport.getString("path")?.also {
                                        v2rayBean.path = it
                                    }
                                    (transport.getAny("host") as? (List<String>))?.also {
                                        v2rayBean.host = it.joinToString(",")
                                    } ?: transport.getString("host")?.also {
                                        v2rayBean.host = it
                                    }
                                }
                                "quic" -> {
                                    v2rayBean.type = "quic"
                                }
                                "grpc" -> {
                                    v2rayBean.type = "grpc"
                                    transport.getString("service_name")?.also {
                                        v2rayBean.grpcServiceName = it
                                    }
                                }
                                "httpupgrade" -> {
                                    v2rayBean.type = "httpupgrade"
                                    transport.getString("host")?.also {
                                        v2rayBean.host = it
                                    }
                                    transport.getString("path")?.also {
                                        v2rayBean.path = it
                                    }
                                }
                                else -> return proxies
                            }
                        }
                    }
                }
                when (type) {
                    "trojan", "vmess", "vless", "http" -> {
                        outbound.getObject("tls")?.also { tls ->
                            (tls.getBoolean("enabled"))?.also { enabled ->
                                if (enabled) {
                                    v2rayBean.security = "tls"
                                    tls.getString("server_name")?.also {
                                        v2rayBean.sni = it
                                    }
                                    tls.getBoolean("insecure")?.also {
                                        v2rayBean.allowInsecure = it
                                    }
                                    (tls.getAny("alpn") as? (List<String>))?.also {
                                        v2rayBean.alpn = it.joinToString("\n")
                                    } ?: tls.getString("alpn")?.also {
                                        v2rayBean.alpn = it
                                    }
                                    tls.getObject("reality")?.also { reality ->
                                        reality.getBoolean("enabled")?.also { enabled ->
                                            if (enabled) {
                                                v2rayBean.security = "reality"
                                                reality.getString("public_key")?.also {
                                                    v2rayBean.realityPublicKey = it
                                                }
                                                reality.getString("short_id")?.also {
                                                    v2rayBean.realityShortId = it
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                when (type) {
                    "socks" -> {
                        v2rayBean as SOCKSBean
                        v2rayBean.protocol = when (outbound.getString("version")) {
                            "4" -> SOCKSBean.PROTOCOL_SOCKS4
                            "4a" -> SOCKSBean.PROTOCOL_SOCKS4A
                            else -> SOCKSBean.PROTOCOL_SOCKS5
                        }
                        outbound.getString("username")?.also {
                            v2rayBean.username = it
                        }
                        outbound.getString("password")?.also {
                            v2rayBean.password = it
                        }
                    }
                    "http" -> {
                        v2rayBean as HttpBean
                        outbound.getString("username")?.also {
                            v2rayBean.username = it
                        }
                        outbound.getString("password")?.also {
                            v2rayBean.password = it
                        }
                    }
                    "shadowsocks" -> {
                        v2rayBean as ShadowsocksBean
                        outbound.getString("method")?.also {
                            v2rayBean.method = it.lowercase()
                        }
                        outbound.getString("password")?.also {
                            v2rayBean.password = it
                        }
                        outbound.getString("plugin")?.also { p ->
                            v2rayBean.plugin = p
                            outbound.getString("plugin_opts")?.also {
                                v2rayBean.plugin += ";$it"
                            }
                        }
                    }
                    "trojan" -> {
                        v2rayBean as TrojanBean
                        outbound.getString("password")?.also {
                            v2rayBean.password = it
                        }
                    }
                    "vmess" -> {
                        v2rayBean as VMessBean
                        outbound.getString("uuid")?.also {
                            v2rayBean.uuid = it
                        }
                        outbound.getString("security")?.also {
                            v2rayBean.security = it
                        }
                        outbound.getInteger("security")?.also {
                            v2rayBean.alterId = it
                        }
                        v2rayBean.packetEncoding = when (outbound.getString("packet_encoding")) {
                            "packetaddr" -> "packet"
                            "xudp" -> "xudp"
                            else -> ""
                        }
                    }
                    "vless" -> {
                        v2rayBean as VLESSBean
                        outbound.getString("uuid")?.also {
                            v2rayBean.uuid = it
                        }
                        outbound.getString("flow")?.also {
                            v2rayBean.flow = it
                        }
                        v2rayBean.packetEncoding = when (outbound.getString("packet_encoding")) {
                            "packetaddr" -> "packet"
                            "xudp", null -> "xudp"
                            else -> ""
                        }
                    }
                    else -> return proxies
                }
                proxies.add(v2rayBean)
            }
            "hysteria2" -> {
                val hysteria2Bean = Hysteria2Bean().applyDefaultValues().apply {
                    outbound["tag"]?.toString()?.also {
                        name = it
                    }
                    outbound.getString("server")?.also {
                        serverAddress = it
                    } ?: return proxies
                    outbound.getInteger("server_port")?.also {
                        serverPorts = it.toString()
                    } ?: return proxies
                    outbound.getString("password")?.also {
                        auth = it
                    }
                    outbound.getObject("tls")?.also { tls ->
                        if (tls.getBoolean("enabled") != true) {
                            return proxies
                        }
                        tls.getString("server_name")?.also {
                            sni = it
                        }
                        tls.getBoolean("insecure")?.also {
                            allowInsecure = it
                        }
                    } ?: return proxies
                    outbound.getObject("obfs")?.also { obfuscation ->
                        if (obfuscation.getString("type") != "salamander") {
                            return proxies
                        }
                        obfuscation.getString("password")?.also {
                            obfs = it
                        }
                    }
                    outbound.getInteger("up_mbps")?.also {
                        uploadMbps = it
                    }
                    outbound.getInteger("down_mbps")?.also {
                        downloadMbps = it
                    }
                }
                proxies.add(hysteria2Bean)
            }
            "hysteria" -> {
                val hysteriaBean = HysteriaBean().applyDefaultValues().apply {
                    outbound["tag"]?.toString()?.also {
                        name = it
                    }
                    outbound.getString("server")?.also {
                        serverAddress = it
                    } ?: return proxies
                    outbound.getInteger("server_port")?.also {
                        serverPorts = it.toString()
                    } ?: return proxies
                    if (outbound.getString("auth")?.isNotBlank() == true) {
                        authPayloadType = HysteriaBean.TYPE_BASE64
                        outbound.getString("auth")?.also {
                            authPayload = it
                        }
                    }
                    if (outbound.getString("auth_str")?.isNotBlank() == true) {
                        authPayloadType = HysteriaBean.TYPE_STRING
                        outbound.getString("auth_str")?.also {
                            authPayload = it
                        }
                    }
                    outbound.getString("obfs")?.also {
                        obfuscation = it
                    }
                    outbound.getObject("tls")?.also { tls ->
                        if (tls.getBoolean("enabled") != true) {
                            return proxies
                        }
                        tls.getString("server_name")?.also {
                            sni = it
                        }
                        tls.getBoolean("insecure")?.also {
                            allowInsecure = it
                        }
                    } ?: return proxies
                    uploadMbps = 10
                    outbound.getInteger("up_mbps")?.also {
                        uploadMbps = it
                    }
                    downloadMbps = 50
                    outbound.getInteger("down_mbps")?.also {
                        downloadMbps = it
                    }
                }
                proxies.add(hysteriaBean)
            }
            "tuic" -> {
                val tuic5Bean = Tuic5Bean().applyDefaultValues().apply {
                    outbound["tag"]?.toString()?.also {
                        name = it
                    }
                    outbound.getString("server")?.also {
                        serverAddress = it
                    } ?: return proxies
                    outbound.getInteger("server_port")?.also {
                        serverPort = it
                    } ?: return proxies
                    outbound.getString("uuid")?.also {
                        uuid = it
                    }
                    outbound.getString("password")?.also {
                        password = it
                    }
                    outbound.getString("congestion_control")?.also {
                        congestionControl = it
                    }
                    outbound.getString("udp_relay_mode")?.also {
                        udpRelayMode = it
                    }
                    outbound.getObject("tls")?.also { tls ->
                        if (tls.getBoolean("enabled") != true) {
                            return proxies
                        }
                        tls.getString("server_name")?.also {
                            sni = it
                        }
                    } ?: return proxies
                }
                proxies.add(tuic5Bean)
            }
            "ssh" -> {
                val sshBean = SSHBean().applyDefaultValues().apply {
                    outbound["tag"]?.toString()?.also {
                        name = it
                    }
                    outbound.getString("server")?.also {
                        serverAddress = it
                    } ?: return proxies
                    outbound.getInteger("server_port")?.also {
                        serverPort = it
                    } ?: return proxies
                    outbound.getString("user")?.also {
                        username = it
                    }
                    if (outbound.getString("password")?.isNotBlank() == true) {
                        authType = SSHBean.AUTH_TYPE_PASSWORD
                        outbound.getString("password")?.also {
                            password = it
                        }
                    }
                    if (outbound.getString("private_key")?.isNotBlank() == true) {
                        authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
                        outbound.getString("private_key")?.also {
                            privateKey = it
                        }
                        outbound.getString("private_key_passphrase")?.also {
                            privateKeyPassphrase = it
                        }
                    }
                    (outbound.getAny("host_key") as? List<String>)?.also {
                        publicKey = it.joinToString("\n")
                    }
                }
                proxies.add(sshBean)
            }
            "wireguard" -> {
                if (outbound.contains("address")) {
                    // wireguard endpoint format introduced in 1.11.0-alpha.19
                    return proxies
                }
                val wireGuardBean = WireGuardBean().applyDefaultValues().apply {
                    outbound["tag"]?.toString()?.also {
                        name = it
                    }
                    outbound.getString("private_key")?.also {
                        privateKey = it
                    }
                    outbound.getString("peer_public_key")?.also {
                        peerPublicKey = it
                    }
                    outbound.getString("pre_shared_key")?.also {
                        peerPreSharedKey = it
                    }
                    mtu = 1408
                    outbound.getInteger("mtu")?.takeIf { it > 0 }?.also {
                        mtu = it
                    }
                    (outbound.getAny("local_address") as? (List<String>))?.also {
                        localAddress = it.joinToString("\n")
                    } ?: outbound.getString("local_address")?.also {
                        localAddress = it
                    } ?: return proxies
                    (outbound.getAny("reserved") as? (List<Int>))?.also {
                        if (it.size == 3) {
                            reserved = listOf(it[0].toString(), it[1].toString(), it[2].toString()).joinToString(",")
                        }
                    } ?: Base64.decode(outbound.getString("reserved"))?.also {
                        if (it.size == 3) {
                            reserved = listOf(it[0].toUByte().toInt().toString(), it[1].toUByte().toInt().toString(), it[2].toUByte().toInt().toString()).joinToString(",")
                        }
                    }
                }
                if (outbound.contains("server")) {
                    outbound.getString("server")?.also {
                        wireGuardBean.serverAddress = it
                    } ?: return proxies
                    outbound.getInteger("server_port")?.also {
                        wireGuardBean.serverPort = it
                    } ?: return proxies
                    proxies.add(wireGuardBean)
                }
                outbound.getArray("peers")?.forEach { json ->
                    val peer = json as? JSONObject
                    proxies.add(wireGuardBean.clone().apply {
                        peer?.getString("server")?.also {
                            serverAddress = it
                        }
                        peer?.getInteger("server_port")?.also {
                            serverPort = it
                        }
                        peer?.getString("public_key")?.also {
                            peerPublicKey = it
                        }
                        peer?.getString("pre_shared_key")?.also {
                            peerPreSharedKey = it
                        }
                        (peer?.getAny("reserved") as? (List<Int>))?.also {
                            if (it.size == 3) {
                                reserved = listOf(it[0].toString(), it[1].toString(), it[2].toString()).joinToString(",")
                            }
                        } ?: Base64.decode(peer?.getString("reserved"))?.also {
                            if (it.size == 3) {
                                reserved = listOf(it[0].toUByte().toInt().toString(), it[1].toUByte().toInt().toString(), it[2].toUByte().toInt().toString()).joinToString(",")
                            }
                        }
                    })
                }
            }
        }
        return proxies
    }

    @Suppress("UNCHECKED_CAST")
    fun parseSingBoxEndpoint(endpoint: JSONObject): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()
        when (endpoint["type"]) {
            "wireguard" -> {
                if (endpoint.contains("local_address")) {
                    // legacy wireguard outbound format
                    return proxies
                }
                val wireGuardBean = WireGuardBean().applyDefaultValues().apply {
                    endpoint["tag"]?.toString()?.also {
                        name = it
                    }
                    endpoint.getString("private_key")?.also {
                        privateKey = it
                    }
                    mtu = 1408
                    endpoint.getInteger("mtu")?.takeIf { it > 0 }?.also {
                        mtu = it
                    }
                    (endpoint.getAny("address") as? (List<String>))?.also {
                        localAddress = it.joinToString("\n")
                    } ?: endpoint.getString("address")?.also {
                        localAddress = it
                    } ?: return proxies
                }
                endpoint.getArray("peers")?.forEach { json ->
                    val peer = json as? JSONObject
                    proxies.add(wireGuardBean.clone().apply {
                        peer?.getString("address")?.also {
                            serverAddress = it
                        }
                        peer?.getInteger("port")?.also {
                            serverPort = it
                        }
                        peer?.getString("public_key")?.also {
                            peerPublicKey = it
                        }
                        peer?.getString("pre_shared_key")?.also {
                            peerPreSharedKey = it
                        }
                        (peer?.getAny("reserved") as? (List<Int>))?.also {
                            if (it.size == 3) {
                                reserved = listOf(it[0].toString(), it[1].toString(), it[2].toString()).joinToString(",")
                            }
                        } ?: Base64.decode(peer?.getString("reserved"))?.also {
                            if (it.size == 3) {
                                reserved = listOf(it[0].toUByte().toInt().toString(), it[1].toUByte().toInt().toString(), it[2].toUByte().toInt().toString()).joinToString(",")
                            }
                        }
                    })
                }
            }
        }
        return proxies
    }

    @Suppress("UNCHECKED_CAST")
    fun parseV2ray5Outbound(outbound: JSONObject): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()
        when (val type = outbound.getString("protocol")) {
            "shadowsocks", "trojan", "vmess", "vless", "socks", "http", "shadowsocks2022" -> {
                val v2rayBean = when (type) {
                    "shadowsocks", "shadowsocks2022" -> ShadowsocksBean()
                    "trojan" -> TrojanBean()
                    "vmess" -> VMessBean()
                    "vless" -> VLESSBean()
                    "socks" -> SOCKSBean()
                    "http" -> HttpBean()
                    else -> return proxies
                }.applyDefaultValues().apply {
                    outbound.getString("tag")?.also {
                        name = it
                    }
                }
                outbound.getObject("streamSettings")?.also { streamSettings ->
                    if (streamSettings.contains("network") || streamSettings.contains("tlsSettings")
                        || streamSettings.contains("tcpSettings") || streamSettings.contains("kcpSettings")
                        || streamSettings.contains("wsSettings") || streamSettings.contains("httpSettings")
                        || streamSettings.contains("grpcSettings") || streamSettings.contains("gunSettings")
                        || streamSettings.contains("quicSettings") || streamSettings.contains("hy2Settings")) { // jsonv4
                        return proxies
                    }
                    when (val security = streamSettings.getString("security")) {
                        null, "none" -> {}
                        "tls", "utls" -> {
                            v2rayBean.security = "tls"
                            val securitySettings = streamSettings.getObject("securitySettings")
                            val tls = if (security == "tls") {
                                securitySettings
                            } else {
                                securitySettings?.get("tlsConfig") as? JSONObject
                                    ?: securitySettings?.get("tls_config") as? JSONObject
                            }
                            tls?.also { tlsConfig ->
                                (tlsConfig["serverName"]?.toString() ?: tlsConfig["server_name"]?.toString())?.also {
                                    v2rayBean.sni = it
                                }
                                (tlsConfig["pinnedPeerCertificateChainSha256"] as? List<String>
                                    ?: tlsConfig["pinned_peer_certificate_chain_sha256"] as? List<String>)?.also {
                                    v2rayBean.pinnedPeerCertificateChainSha256 = it.joinToString("\n")
                                    (tlsConfig["allowInsecureIfPinnedPeerCertificate"] as? Boolean
                                        ?: tlsConfig["allow_insecure_if_pinned_peer_certificate"] as? Boolean)?.also { allowInsecure ->
                                        v2rayBean.allowInsecure = allowInsecure
                                    }
                                }
                                (tlsConfig["nextProtocol"] as? List<String>)?.also {
                                    v2rayBean.alpn = it.joinToString("\n")
                                } ?: (tlsConfig["next_protocol"] as? List<String>)?.also {
                                    v2rayBean.alpn = it.joinToString("\n")
                                }
                                // do not parse "imitate"
                            }
                        }
                        else -> return proxies
                    }
                    when (streamSettings.getString("transport")) {
                        null, "tcp" -> {}
                        "kcp" -> {
                            v2rayBean.type = "kcp"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["seed"]?.toString()?.also {
                                    v2rayBean.mKcpSeed = it
                                }
                                // v2rayBean.headerType
                            }
                        }
                        "ws" -> {
                            v2rayBean.type = "ws"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["path"]?.toString()?.also {
                                    v2rayBean.path = it
                                }
                                (transportSettings["maxEarlyData"]?.toString()?.toInt()
                                    ?: transportSettings["max_early_data"]?.toString()?.toInt())?.also {
                                    v2rayBean.wsMaxEarlyData = it
                                }
                                (transportSettings["earlyDataHeaderName"]?.toString()
                                    ?: transportSettings["early_data_header_name"]?.toString())?.also {
                                    v2rayBean.earlyDataHeaderName = it
                                }
                                (transportSettings["header"] as? Map<String, String>)?.forEach { (key, value) ->
                                    when (key.lowercase()) {
                                        "host" -> {
                                            v2rayBean.host = value
                                        }
                                    }
                                }
                            }
                        }
                        "h2" -> {
                            v2rayBean.type = "http"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["path"]?.toString()?.also {
                                    v2rayBean.path = it
                                }
                                (transportSettings["host"] as? List<String>)?.also {
                                    v2rayBean.host = it.joinToString("\n")
                                }
                            }
                        }
                        "quic" -> {
                            v2rayBean.type = "quic"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["security"]?.toString()?.also {
                                    v2rayBean.quicSecurity = it.lowercase()
                                }
                                transportSettings["key"]?.toString()?.also {
                                    v2rayBean.quicKey = it
                                }
                                // v2rayBean.headerType
                            }
                        }
                        "grpc" -> {
                            v2rayBean.type = "grpc"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                (transportSettings["serviceName"]?.toString()
                                    ?: transportSettings["service_name"]?.toString())?.also {
                                    v2rayBean.grpcServiceName = it
                                }
                                // v2rayBean.headerType
                            }
                        }
                        "httpupgrade" -> {
                            v2rayBean.type = "httpupgrade"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["path"]?.toString()?.also {
                                    v2rayBean.path = it
                                }
                                transportSettings["host"]?.toString()?.also {
                                    v2rayBean.host = it
                                }
                            }
                        }
                        "meek" -> {
                            v2rayBean.type = "meek"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["url"]?.toString()?.also {
                                    v2rayBean.meekUrl = it
                                }
                            }
                        }
                        "mekya" -> {
                            v2rayBean.type = "mekya"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["url"]?.toString()?.also {
                                    v2rayBean.mekyaUrl = it
                                }
                                (transportSettings["kcp"] as? JSONObject)?.also { kcp ->
                                    kcp["seed"]?.toString()?.also {
                                        v2rayBean.mekyaKcpSeed = it
                                    }
                                    // v2rayBean.mekyaKcpHeaderType
                                }
                            }
                        }
                        "hysteria2" -> {
                            v2rayBean.type = "hysteria2"
                            streamSettings.getObject("transportSettings")?.also { transportSettings ->
                                transportSettings["password"]?.toString()?.also {
                                    v2rayBean.hy2Password = it
                                }
                                (transportSettings["congestion"] as? JSONObject)?.also { congestion ->
                                    (congestion["up_mbps"] as? Int ?: congestion["upMbps"] as? Int)?.also {
                                        v2rayBean.hy2UpMbps = it
                                    }
                                    (congestion["down_mbps"] as? Int ?: congestion["downMbps"] as? Int)?.also {
                                        v2rayBean.hy2DownMbps = it
                                    }
                                }

                            }
                        }
                        else -> return proxies
                    }
                }

                (outbound["settings"] as? JSONObject)?.also { settings ->
                    if (settings.containsKey("servers") || settings.containsKey("vnext")) { // jsonv4
                        return proxies
                    }
                    settings["address"]?.toString()?.also {
                        v2rayBean.serverAddress = it
                    } ?: return proxies
                    settings["port"]?.toString()?.toInt()?.also {
                        v2rayBean.serverPort = it
                    } ?: return proxies
                    when (type) {
                        "shadowsocks" -> {
                            v2rayBean as ShadowsocksBean
                            settings["method"]?.toString()?.also {
                                v2rayBean.method = it.lowercase()
                            }
                            settings["password"]?.toString()?.also {
                                v2rayBean.password = it
                            }
                        }
                        "trojan" -> {
                            v2rayBean as TrojanBean
                            settings["password"]?.toString()?.also {
                                v2rayBean.password = it
                            }
                        }
                        "vmess" -> {
                            v2rayBean as VMessBean
                            settings["uuid"]?.toString()?.also {
                                v2rayBean.uuid = it
                            }
                        }
                        "vless" -> {
                            v2rayBean as VMessBean
                            settings["uuid"]?.toString()?.also {
                                v2rayBean.uuid = it
                            }
                        }
                        "shadowsocks2022" -> {
                            v2rayBean as ShadowsocksBean
                            settings["method"]?.toString()?.also {
                                v2rayBean.method = it.lowercase()
                            }
                            settings["psk"]?.toString()?.also { psk ->
                                v2rayBean.password = psk
                                (settings["ipsk"] as? List<String>)?.also { ipsk ->
                                    v2rayBean.password = ipsk.joinToString(":") + ":" + psk

                                }
                            }
                        }
                    }
                    proxies.add(v2rayBean)
                }
            }
            "hysteria2" -> {
                val hysteria2Bean = Hysteria2Bean().applyDefaultValues().apply {
                    outbound.getString("tag")?.also {
                        name = it
                    }
                }
                outbound.getObject("settings")?.also { settings ->
                    (settings["server"] as? List<JSONObject>)?.get(0)?.also { server ->
                        server["address"]?.toString()?.also {
                            hysteria2Bean.serverAddress = it
                        } ?: return proxies
                        (server["port"]?.toString()?.toInt())?.also {
                            hysteria2Bean.serverPorts = it.toString()
                        } ?: return proxies
                    }
                } ?: return proxies
                outbound.getObject("streamSettings")?.also { streamSettings ->
                    if (streamSettings.getString("security") != "tls") {
                        return proxies
                    }
                    if (streamSettings.getString("transport") != "hysteria2") {
                        return proxies
                    }
                    streamSettings.getObject("securitySettings")?. also { securitySettings ->
                        (securitySettings["serverName"]?.toString() ?: securitySettings["server_name"]?.toString())?.also {
                            hysteria2Bean.sni = it
                        }
                    }
                    streamSettings.getObject("transportSettings")?.also { transportSettings ->
                        transportSettings["password"]?.toString()?.also {
                            hysteria2Bean.auth = it
                        }
                        (transportSettings["congestion"] as? JSONObject)?.also { congestion ->
                            (congestion["up_mbps"]?.toString()?.toInt()?: congestion["upMbps"]?.toString()?.toInt())?.also {
                                hysteria2Bean.uploadMbps = it
                            }
                            (congestion["down_mbps"]?.toString()?.toInt() ?: congestion["downMbps"]?.toString()?.toInt())?.also {
                                hysteria2Bean.downloadMbps = it
                            }
                        }
                    }
                    proxies.add(hysteria2Bean)
                }
            }
        }
        return proxies
    }

    @Suppress("UNCHECKED_CAST")
    fun parseClashProxies(yaml: List<Map<String, Any?>>): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()
        // Note: YAML numbers parsed as "Long"
        for (proxy in yaml) {
            when (proxy["type"] as String) {
                "socks5" -> {
                    proxies.add(SOCKSBean().apply {
                        serverAddress = proxy["server"] as String
                        serverPort = proxy["port"].toString().toInt()
                        username = proxy["username"]?.toString()
                        password = proxy["password"]?.toString()
                        if (proxy["tls"] as? Boolean == true) {
                            security = "tls"
                            sni = proxy["sni"]?.toString()
                            if (proxy["skip-cert-verify"] as? Boolean == true) {
                                allowInsecure = true
                            }
                        }
                        name = proxy["name"]?.toString()
                    })
                }
                "http" -> {
                    proxies.add(HttpBean().apply {
                        serverAddress = proxy["server"] as String
                        serverPort = proxy["port"].toString().toInt()
                        username = proxy["username"]?.toString()
                        password = proxy["password"]?.toString()
                        if (proxy["tls"] as? Boolean == true) {
                            security = "tls"
                            sni = proxy["sni"]?.toString()
                            if (proxy["skip-cert-verify"] as? Boolean == true) {
                                allowInsecure = true
                            }
                        }
                        name = proxy["name"]?.toString()
                    })
                }
                "ss" -> {
                    var pluginStr = ""
                    if (proxy.contains("plugin")) {
                        val opts = proxy["plugin-opts"] as Map<String, Any?>
                        val pluginOpts = PluginOptions()
                        fun put(clash: String, origin: String = clash) {
                            opts[clash]?.let {
                                pluginOpts[origin] = it.toString()
                            }
                        }
                        when (proxy["plugin"]) {
                            "obfs" -> {
                                pluginOpts.id = "obfs-local"
                                put("mode", "obfs")
                                put("host", "obfs-host")
                            }
                            "v2ray-plugin" -> {
                                pluginOpts.id = "v2ray-plugin"
                                put("mode")
                                if (opts["tls"] as? Boolean == true) {
                                    pluginOpts["tls"] = null
                                }
                                put("host")
                                put("path")
                                if (opts["mux"] as? Boolean == true) {
                                    pluginOpts["mux"] = "8"
                                }
                            }
                        }
                        pluginStr = pluginOpts.toString(false)
                    }
                    proxies.add(ShadowsocksBean().apply {
                        serverAddress = proxy["server"] as String
                        serverPort = proxy["port"].toString().toInt()
                        password = proxy["password"]?.toString()
                        method = when (proxy["cipher"]) {
                            "dummy" -> "none"
                            else -> (proxy["cipher"] as String).lowercase()
                        }
                        plugin = pluginStr
                        name = proxy["name"]?.toString()
                        fixInvalidParams()
                    })
                }
                "vmess", "vless", "trojan" -> {
                    val bean = when (proxy["type"] as String) {
                        "vmess" -> VMessBean()
                        "vless" -> VLESSBean()
                        "trojan" -> TrojanBean()
                        else -> error("impossible")
                    }
                    if (bean is TrojanBean) {
                        bean.security = "tls"
                    }
                    for (opt in proxy) {
                        when (opt.key) {
                            "name" -> bean.name = opt.value?.toString()
                            "server" -> bean.serverAddress = opt.value as String
                            "port" -> bean.serverPort = opt.value.toString().toInt()
                            "password" -> if (bean is TrojanBean) bean.password = opt.value as String
                            "uuid" -> if (bean is VMessBean || bean is VLESSBean) bean.uuid = opt.value as String
                            "alterId" -> if (bean is VMessBean) bean.alterId = opt.value.toString().toInt()
                            "cipher" -> if (bean is VMessBean) bean.encryption = opt.value as String
                            "flow" -> if (bean is VLESSBean) bean.flow = opt.value as String
                            "packet-encoding" -> if (bean is VMessBean || bean is VLESSBean) {
                                bean.packetEncoding = when (opt.value) {
                                    "packetaddr" -> "packet"
                                    "xudp" -> "xudp"
                                    else -> ""
                                }
                            }
                            "tls" -> if (bean is VMessBean || bean is VLESSBean) {
                                bean.security = if (opt.value as? Boolean == true) "tls" else ""
                            }
                            "servername" -> if (bean is VMessBean || bean is VLESSBean) bean.sni = opt.value?.toString()
                            "sni" -> if (bean is TrojanBean) bean.sni = opt.value?.toString()
                            "skip-cert-verify" -> bean.allowInsecure = opt.value as? Boolean == true
                            "reality-opts" -> for (realityOpt in (opt.value as Map<String, Any>)) {
                                bean.security = "reality"
                                when (realityOpt.key.lowercase()) {
                                    "public-key" -> bean.realityPublicKey = realityOpt.value.toString()
                                    "short-id" -> bean.realityShortId = realityOpt.value.toString()
                                }
                            }
                            "network" -> {
                                when (opt.value) {
                                    "h2" -> bean.type = "http"
                                    "ws", "grpc", "http" -> bean.type = opt.value as String
                                }
                            }
                            "ws-opts" -> for (wsOpt in (opt.value as Map<String, Any>)) {
                                when (wsOpt.key.lowercase()) {
                                    "headers" -> for (wsHeader in (opt.value as Map<String, Any>)) {
                                        when (wsHeader.key.lowercase()) {
                                            "host" -> bean.host = wsHeader.value as String
                                        }
                                    }
                                    "path" -> {
                                        bean.path = wsOpt.value.toString()
                                    }
                                    "max-early-data" -> {
                                        bean.wsMaxEarlyData = wsOpt.value.toString().toInt()
                                    }
                                    "early-data-header-name" -> {
                                        bean.earlyDataHeaderName = wsOpt.value.toString()
                                    }
                                    "v2ray-http-upgrade" -> {
                                        if (wsOpt.value as? Boolean == true) {
                                            bean.type = "httpupgrade"
                                        }
                                    }
                                }
                            }
                            "h2-opts" -> for (h2Opt in (opt.value as Map<String, Any>)) {
                                when (h2Opt.key.lowercase()) {
                                    "host" -> bean.host = (h2Opt.value as List<String>).first()
                                    "path" -> bean.path = h2Opt.value.toString()
                                }
                            }
                            "http-opts" -> for (httpOpt in (opt.value as Map<String, Any>)) {
                                when (httpOpt.key.lowercase()) {
                                    "path" -> bean.path = (httpOpt.value as List<String>).first()
                                }
                            }
                            "grpc-opts" -> for (grpcOpt in (opt.value as Map<String, Any>)) {
                                when (grpcOpt.key.lowercase()) {
                                    "grpc-service-name" -> bean.grpcServiceName = grpcOpt.value.toString()
                                }
                            }
                        }
                    }
                    proxies.add(bean)
                }
                "ssr" -> {
                    val entity = ShadowsocksRBean()
                    for (opt in proxy) {
                        when (opt.key) {
                            "name" -> entity.name = opt.value?.toString()
                            "server" -> entity.serverAddress = opt.value as String
                            "port" -> entity.serverPort = opt.value.toString().toInt()
                            "cipher" -> entity.method = when (opt.value) {
                                "dummy" -> "none"
                                else -> (opt.value as String).lowercase()
                            }
                            "password" -> entity.password = opt.value?.toString()
                            "obfs" -> entity.obfs = opt.value as String
                            "protocol" -> entity.protocol = opt.value as String
                            "obfs-param" -> entity.obfsParam = opt.value?.toString()
                            "protocol-param" -> entity.protocolParam = opt.value?.toString()
                        }
                    }
                    proxies.add(entity)
                }
                "ssh" -> {
                    val bean = SSHBean()
                    for (opt in proxy) {
                        when (opt.key) {
                            "name" -> bean.name = opt.value?.toString()
                            "server" -> bean.serverAddress = opt.value as String
                            "port" -> bean.serverPort = opt.value.toString().toInt()
                            "username" -> bean.username = opt.value?.toString()
                            "password" -> {
                                bean.password = opt.value?.toString()
                                bean.authType = SSHBean.AUTH_TYPE_PASSWORD
                            }
                            "private-key" -> {
                                bean.privateKey = opt.value as String
                                bean.authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
                            }
                            "private-key-passphrase" -> bean.privateKeyPassphrase = opt.value as String
                            "host-key" -> (opt.value as? List<String>)?.also {
                                bean.publicKey = it.joinToString("\n")
                            }
                        }
                    }
                    proxies.add(bean)
                }
                "hysteria" -> {
                    proxies.add(HysteriaBean().apply {
                        serverAddress = proxy["server"] as String
                        serverPorts = proxy["ports"]?.toString() ?: proxy["port"]?.toString()
                        protocol = when (proxy["protocol"]?.toString()) {
                            "faketcp" -> HysteriaBean.PROTOCOL_FAKETCP
                            "wechat-video" -> HysteriaBean.PROTOCOL_WECHAT_VIDEO
                            else -> HysteriaBean.PROTOCOL_UDP
                        }
                        authPayloadType = HysteriaBean.TYPE_STRING
                        authPayload = proxy["auth-str"]?.toString()
                        uploadMbps = proxy["up"]?.toString()?.toIntOrNull()?: 10 // support int only
                        downloadMbps = proxy["down"]?.toString()?.toIntOrNull()?: 50 // support int only
                        sni = proxy["sni"]?.toString()
                        allowInsecure = proxy["skip-cert-verify"] as? Boolean == true
                        obfuscation = proxy["obfs"]?.toString()
                        hopInterval = proxy["hop-interval"]?.toString()?.toInt()
                        name = proxy["name"]?.toString()
                    })
                }
                "hysteria2" -> {
                    proxies.add(Hysteria2Bean().apply {
                        serverAddress = proxy["server"] as String
                        serverPorts = proxy["ports"]?.toString() ?: proxy["port"]?.toString()
                        auth = proxy["password"]?.toString()
                        uploadMbps = proxy["up"]?.toString()?.toIntOrNull()?: 0 // support int only
                        downloadMbps = proxy["down"]?.toString()?.toIntOrNull()?: 0 // support int only
                        sni = proxy["sni"]?.toString()
                        allowInsecure = proxy["skip-cert-verify"] as? Boolean == true
                        obfs = if (proxy["obfs"]?.toString() == "salamander") proxy["obfs-password"]?.toString() else ""
                        hopInterval = proxy["hop-interval"]?.toString()?.toInt()
                        name = proxy["name"]?.toString()
                    })
                }
                "tuic" -> {
                    if (proxy["token"]?.toString() != "") {
                        proxies.add(TuicBean().apply {
                            serverAddress = proxy["server"] as String
                            serverPort = proxy["port"].toString().toInt()
                            token = proxy["token"]?.toString()
                            udpRelayMode = proxy["udp-relay-mode"]?.toString()
                            disableSNI = proxy["disable-sni"] as? Boolean == true
                            reduceRTT = proxy["reduce-rtt"] as? Boolean == true
                            sni = proxy["sni"]?.toString()
                            name = proxy["name"]?.toString()
                        })
                    } else {
                        proxies.add(Tuic5Bean().apply {
                            serverAddress = proxy["server"] as String
                            serverPort = proxy["port"].toString().toInt()
                            uuid = proxy["uuid"]?.toString()
                            password = proxy["password"]?.toString()
                            udpRelayMode = proxy["udp-relay-mode"]?.toString()
                            disableSNI = proxy["disable-sni"] as? Boolean == true
                            zeroRTTHandshake = proxy["reduce-rtt"] as? Boolean == true
                            sni = proxy["sni"]?.toString()
                            name = proxy["name"]?.toString()
                        })
                    }
                }
                "wireguard" -> {
                    val wireGuardBean = WireGuardBean().apply {
                        serverAddress = proxy["server"]?.toString()
                        serverPort = proxy["port"]?.toString()?.toInt()
                        privateKey = proxy["private-key"]?.toString()
                        peerPublicKey = proxy["public-key"]?.toString()
                        peerPreSharedKey = proxy["pre-shared-key"]?.toString() ?: proxy["preshared-key"]?.toString()
                        mtu = proxy["mtu"]?.toString()?.toInt()?.takeIf { it > 0 } ?: 1408
                        localAddress = listOfNotNull(proxy["ip"]?.toString(), proxy["ipv6"]?.toString()).joinToString("\n")
                        name = proxy["name"]?.toString()
                        (proxy["reserved"] as? List<Map<String, Any?>>)?.also {
                            if (it.size == 3) {
                                reserved = listOf(it[0].toString(), it[1].toString(), it[2].toString()).joinToString(",")
                            }
                        } ?: {
                            Base64.decode(proxy["reserved"]?.toString())?.also {
                                if (it.size == 3) {
                                    reserved = listOf(it[0].toUByte().toInt().toString(), it[1].toUByte().toInt().toString(), it[2].toUByte().toInt().toString()).joinToString(",")
                                }
                            }
                        }
                    }
                    if (proxy.containsKey("server")) {
                        proxies.add(wireGuardBean)
                    }
                    (proxy["peers"] as? (List<Map<String, Any?>>))?.forEach {
                        proxies.add(wireGuardBean.clone().apply {
                            serverAddress = it["server"]?.toString()
                            serverPort = it["port"]?.toString()?.toInt()
                            peerPublicKey = it["public-key"]?.toString()
                            peerPreSharedKey = it["pre-shared-key"]?.toString()
                            (proxy["reserved"] as? List<Map<String, Any?>>)?.also {
                                if (it.size == 3) {
                                    reserved = listOf(it[0].toString(), it[1].toString(), it[2].toString()).joinToString(",")
                                }
                            } ?: {
                                Base64.decode(proxy["reserved"]?.toString())?.also {
                                    if (it.size == 3) {
                                        reserved = listOf(it[0].toUByte().toInt().toString(), it[1].toUByte().toInt().toString(), it[2].toUByte().toInt().toString()).joinToString(",")
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
        return proxies
    }
}
