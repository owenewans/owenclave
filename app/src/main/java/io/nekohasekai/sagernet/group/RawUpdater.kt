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
import cn.hutool.json.*
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.gson.gson
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
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig
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
                    USER_AGENT
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

        if (text.contains("proxies:")) {
            try {
                // clash
                for (proxy in (Yaml().apply {
                    addTypeDescription(TypeDescription(String::class.java, "str"))
                }.loadAs(text, Map::class.java)["proxies"] as? (List<Map<String, Any?>>) ?: error(
                    app.getString(R.string.no_proxies_found_in_file)
                ))) {
                    // Note: YAML numbers parsed as "Long"
                    when (proxy["type"] as String) {
                        "socks5" -> {
                            proxies.add(SOCKSBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                if (proxy["tls"]?.toString() == "true") {
                                    security = "tls"
                                    sni = proxy["sni"]?.toString()
                                    if (proxy["skip-cert-verify"]?.toString() == "true") {
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
                                if (proxy["tls"]?.toString() == "true") {
                                    security = "tls"
                                    sni = proxy["sni"]?.toString()
                                    if (proxy["skip-cert-verify"]?.toString() == "true") {
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
                                        if (opts["tls"]?.toString() == "true") {
                                            pluginOpts["tls"] = null
                                        }
                                        put("host")
                                        put("path")
                                        if (opts["mux"]?.toString() == "true") {
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
                                method = clashCipher(proxy["cipher"] as String)
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
                                        if (opt.value as String == "packetaddr") {
                                            bean.packetEncoding = "packet"
                                        } else if (opt.value as String == "xudp") {
                                            bean.packetEncoding = "xudp"
                                        }
                                    }
                                    "tls" -> if (bean is VMessBean || bean is VLESSBean) {
                                        bean.security = if (opt.value?.toString() == "true") "tls" else ""
                                    }
                                    "servername" -> if (bean is VMessBean || bean is VLESSBean) bean.sni = opt.value?.toString()
                                    "sni" -> if (bean is TrojanBean) bean.sni = opt.value?.toString()
                                    "skip-cert-verify" -> bean.allowInsecure = opt.value?.toString() == "true"
                                    "reality-opts" -> for (realityOpt in (opt.value as Map<String, Any>)) {
                                        bean.security = "reality"
                                        when (realityOpt.key.lowercase()) {
                                            "public-key" -> bean.realityPublicKey = realityOpt.value.toString()
                                            "short-id" -> bean.realityShortId = realityOpt.value.toString()
                                        }
                                    }
                                    "network" -> bean.type = opt.value as String
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
                                                if (wsOpt.value.toString() == "true") {
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
                                    "cipher" -> entity.method = clashCipher(opt.value as String)
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
                                }
                            }
                            proxies.add(bean)
                        }
                        "hysteria" -> {
                            proxies.add(HysteriaBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPorts = proxy["port"].toString()
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
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                                obfuscation = proxy["obfs"]?.toString()
                                name = proxy["name"]?.toString()
                            })
                        }
                        "hysteria2" -> {
                            proxies.add(Hysteria2Bean().apply {
                                serverAddress = proxy["server"] as String
                                serverPorts = proxy["port"].toString()
                                auth = proxy["password"]?.toString()
                                uploadMbps = proxy["up"]?.toString()?.toIntOrNull()?: 0 // support int only
                                downloadMbps = proxy["down"]?.toString()?.toIntOrNull()?: 0 // support int only
                                sni = proxy["sni"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                                obfs = if (proxy["obfs"]?.toString() == "salamander") proxy["obfs-password"]?.toString() else ""
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
                                    disableSNI = proxy["disable-sni"]?.toString() == "true"
                                    reduceRTT = proxy["reduce-rtt"]?.toString() == "true"
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
                                    disableSNI = proxy["disable-sni"]?.toString() == "true"
                                    zeroRTTHandshake = proxy["reduce-rtt"]?.toString() == "true"
                                    sni = proxy["sni"]?.toString()
                                    name = proxy["name"]?.toString()
                                })
                            }
                        }
                    }
                }
                proxies.forEach { it.initializeDefaultValues() }
                return proxies
            } catch (e: YAMLException) {
                Logs.w(e)
            }
        } else if (text.contains("[Interface]")) {
            // wireguard
            try {
                proxies.addAll(parseWireGuard(text))
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            val json = JSONUtil.parse(text)
            return parseJSON(json)
        } catch (ignored: JSONException) {
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
        } catch (ignored: Exception) {
        }

        return null
    }

    fun clashCipher(cipher: String): String {
        return when (cipher) {
            "dummy" -> "none"
            else -> cipher
        }
    }

    fun parseWireGuard(conf: String): List<WireGuardBean> {
        val ini = Ini(StringReader(conf))
        val iface = ini["Interface"] ?: error("Missing 'Interface' selection")
        val bean = WireGuardBean().applyDefaultValues()
        val localAddresses = iface.getAll("Address")
        if (localAddresses.isNullOrEmpty()) error("Empty address in 'Interface' selection")
        bean.localAddress = localAddresses.flatMap { it.split(",") }.let { address ->
            address.joinToString("\n") { it.substringBefore("/") }
        }
        bean.privateKey = iface["PrivateKey"]
        val peers = ini.getAll("Peer")
        if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")
        val beans = mutableListOf<WireGuardBean>()
        for (peer in peers) {
            val endpoint = peer["Endpoint"]
            if (endpoint.isNullOrBlank() || !endpoint.contains(":")) {
                continue
            }

            val peerBean = bean.clone()
            peerBean.serverAddress = endpoint.substringBeforeLast(":")
            peerBean.serverPort = endpoint.substringAfterLast(":").toIntOrNull() ?: continue
            peerBean.peerPublicKey = peer["PublicKey"] ?: continue
            peerBean.peerPreSharedKey = peer["PresharedKey"]
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
                json.containsKey("protocol") -> {
                    val v2rayConfig = gson.fromJson(
                        json.toString(), V2RayConfig.OutboundObject::class.java
                    ).apply { init() }
                    return parseOutbound(v2rayConfig)
                }
                json.containsKey("outbound") -> {
                    val v2rayConfig = gson.fromJson(
                        json.getJSONObject("outbound").toString(),
                        V2RayConfig.OutboundObject::class.java
                    ).apply { init() }
                    return parseOutbound(v2rayConfig)
                }
                json.containsKey("outbounds") -> {/*   val fakedns = json["fakedns"]
                       if (fakedns is JSONObject) {
                           json["fakedns"] = JSONArray().apply {
                               add(fakedns)
                           }
                       }

                       val routing = json["routing"]
                       if (routing is JSONObject) {
                           val rules = routing["rules"]
                           if (rules is JSONArray) {
                               rules.filterIsInstance<JSONObject>().forEach {
                                   val inboundTag = it["inboundTag"]
                                   if (inboundTag is String) {
                                       it["inboundTag"] = JSONArray().apply {
                                           add(inboundTag)
                                       }
                                   }
                               }
                           }
                       }

                       try {
                           gson.fromJson(
                               json.toString(),
                               V2RayConfig::class.java
                           ).apply { init() }
                       } catch (e: Exception) {
                           Logs.w(e)*/
                    json.getJSONArray("outbounds").filterIsInstance<JSONObject>().forEach {
                        val v2rayConfig = gson.fromJson(
                            it.toString(), V2RayConfig.OutboundObject::class.java
                        ).apply { init() }

                        proxies.addAll(parseOutbound(v2rayConfig))
                    }/* null
                 }?.outbounds?.forEach {
                     proxies.addAll(parseOutbound(it))
                 }*/
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

    fun parseOutbound(outboundObject: V2RayConfig.OutboundObject): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        with(outboundObject) {
            // v2ray JSONv4 config or Xray config only
            when (protocol) {
                "vmess", "vless", "trojan", "shadowsocks", "socks", "http" -> {
                    val v2rayBean = when (protocol) {
                        "vmess" -> VMessBean()
                        "vless" -> VLESSBean()
                        "trojan" -> TrojanBean()
                        "shadowsocks" -> ShadowsocksBean()
                        "socks" -> SOCKSBean()
                        else -> HttpBean()
                    }.applyDefaultValues()
                    streamSettings?.apply {
                        v2rayBean.security = security ?: v2rayBean.security
                        when (security) {
                            "tls" -> {
                                tlsSettings?.apply {
                                    serverName?.also {
                                        v2rayBean.sni = it
                                    }
                                    alpn?.also {
                                        v2rayBean.alpn = it.joinToString(",")
                                    }
                                    allowInsecure?.also {
                                        v2rayBean.allowInsecure = it
                                    }
                                }
                            }
                            "reality" -> {
                                realitySettings?.apply {
                                    serverName?.also {
                                        v2rayBean.sni = it
                                    }
                                    publicKey?.also {
                                        v2rayBean.realityPublicKey = it
                                    }
                                    shortId?.also {
                                        v2rayBean.realityShortId = it
                                    }
                                }
                            }
                        }
                        v2rayBean.type = network ?: v2rayBean.type
                        when (network) {
                            "tcp" -> {
                                tcpSettings?.header?.apply {
                                    when (type) {
                                        "http" -> {
                                            v2rayBean.headerType = "http"
                                            request?.apply {
                                                path?.also {
                                                    v2rayBean.path = it.joinToString(",")
                                                }
                                                headers?.forEach { (key, value) ->
                                                    when (key.lowercase()) {
                                                        "host" -> {
                                                            when {
                                                                value.valueX != null -> {
                                                                    v2rayBean.host = value.valueX
                                                                }
                                                                value.valueY != null -> {
                                                                    v2rayBean.host = value.valueY.joinToString(
                                                                        ","
                                                                    )
                                                                }
                                                            }
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
                                kcpSettings?.apply {
                                    header?.type?.also {
                                        v2rayBean.headerType = it
                                    }
                                    seed?.also {
                                        v2rayBean.mKcpSeed = it
                                    }
                                }
                            }
                            "ws", "websocket" -> {
                                v2rayBean.type = "ws"
                                wsSettings?.apply {
                                    headers?.forEach { (key, value) ->
                                        when (key.lowercase()) {
                                            "host" -> {
                                                v2rayBean.host = value
                                            }
                                        }
                                    }
                                    path?.also {
                                        v2rayBean.path = it
                                    }
                                    maxEarlyData?.also {
                                        v2rayBean.wsMaxEarlyData = it
                                    }
                                }
                            }
                            "http", "h2" -> {
                                v2rayBean.type = "http"
                                httpSettings?.apply {
                                    host?.also {
                                        v2rayBean.host = it.joinToString(",")
                                    }
                                    path?.also {
                                        v2rayBean.path = it
                                    }
                                }
                            }
                            "quic" -> {
                                quicSettings?.apply {
                                    security?.also {
                                        v2rayBean.quicSecurity = it
                                    }
                                    key?.also {
                                        v2rayBean.quicKey = it
                                    }
                                    header?.type?.also {
                                        v2rayBean.headerType = it
                                    }
                                }
                            }
                            "grpc", "gun" -> {
                                v2rayBean.type = "grpc"
                                gunSettings?.serviceName?.also {
                                    v2rayBean.grpcServiceName = it
                                }
                                grpcSettings?.serviceName?.also {
                                    v2rayBean.grpcServiceName = it
                                }
                            }
                            "httpupgrade" -> {
                                httpupgradeSettings?.apply {
                                    host?.also {
                                        v2rayBean.host = it
                                    }
                                    path?.also {
                                        v2rayBean.path = it
                                    }
                                }
                            }
                            "splithttp" -> {
                                splithttpSettings?.apply {
                                    host?.also {
                                        v2rayBean.host = it
                                    }
                                    path?.also {
                                        v2rayBean.path = it
                                    }
                                }
                            }
                            "hysteria2", "hy2" -> {
                                v2rayBean.type = "hysteria2"
                                hy2Settings?.apply {
                                    password?.also {
                                        v2rayBean.hy2Password = it
                                    }
                                    congestion?.apply {
                                        up_mbps?.also {
                                            v2rayBean.hy2UpMbps = it
                                        }
                                        down_mbps?.also {
                                            v2rayBean.hy2DownMbps = it
                                        }
                                    }
                                    obfs?.apply {
                                        type?.also {
                                            if (type == "salamander") v2rayBean.hy2ObfsPassword = it
                                        }
                                    }
                                }
                            }
                        }
                    }
                    when (protocol) {
                        "vmess" -> {
                            v2rayBean as VMessBean
                            (settings.value as? V2RayConfig.VMessOutboundConfigurationObject)?.vnext?.forEach {
                                val vmessBean = v2rayBean.clone().apply {
                                    serverAddress = it.address
                                    serverPort = it.port
                                }
                                for (user in it.users) {
                                    proxies.add(vmessBean.clone().apply {
                                        uuid = user.id
                                        encryption = user.security
                                        alterId = user.alterId
                                        name = tag ?: (displayName() + " - ${user.security} - ${user.id}")
                                    })
                                }
                            }
                        }
                        "vless" -> {
                            v2rayBean as VLESSBean
                            (settings.value as? V2RayConfig.VLESSOutboundConfigurationObject)?.vnext?.forEach {
                                val vlessBean = v2rayBean.clone().apply {
                                    serverAddress = it.address
                                    serverPort = it.port
                                }
                                for (user in it.users) {
                                    proxies.add(vlessBean.clone().apply {
                                        uuid = user.id
                                        encryption = user.encryption
                                        flow = user.flow
                                        name = tag ?: (displayName() + " - ${user.id}")
                                    })
                                }
                            }
                        }
                        "shadowsocks" -> {
                            v2rayBean as ShadowsocksBean
                            (settings.value as? V2RayConfig.ShadowsocksOutboundConfigurationObject)?.servers?.forEach {
                                proxies.add(v2rayBean.clone().apply {
                                    name = tag
                                    serverAddress = it.address
                                    serverPort = it.port
                                    method = it.method
                                    password = it.password
                                })
                            }
                        }
                        "trojan" -> {
                            v2rayBean as TrojanBean
                            (settings.value as? V2RayConfig.TrojanOutboundConfigurationObject)?.servers?.forEach {
                                proxies.add(v2rayBean.clone().apply {
                                    name = tag
                                    serverAddress = it.address
                                    serverPort = it.port
                                    password = it.password
                                })
                            }
                        }
                        "socks" -> {
                            v2rayBean as SOCKSBean
                            (settings.value as? V2RayConfig.SocksOutboundConfigurationObject)?.version?.also {
                                v2rayBean.protocol = when (it) {
                                    "4" -> SOCKSBean.PROTOCOL_SOCKS4
                                    "4a" -> SOCKSBean.PROTOCOL_SOCKS4A
                                    else -> SOCKSBean.PROTOCOL_SOCKS5
                                }
                            }
                            (settings.value as? V2RayConfig.SocksOutboundConfigurationObject)?.servers?.forEach {
                                val socksBean = v2rayBean.clone().apply {
                                    serverAddress = it.address
                                    serverPort = it.port
                                }
                                if (it.users.isNullOrEmpty()) {
                                    proxies.add(socksBean)
                                } else for (user in it.users) proxies.add(socksBean.clone().apply {
                                    username = user.user
                                    password = user.pass
                                    name = tag ?: (displayName() + " - $username")
                                })
                            }
                        }
                        "http" -> {
                            v2rayBean as HttpBean
                            (settings.value as? V2RayConfig.HTTPOutboundConfigurationObject)?.servers?.forEach {
                                val httpBean = v2rayBean.clone().apply {
                                    serverAddress = it.address
                                    serverPort = it.port
                                }
                                if (it.users.isNullOrEmpty()) {
                                    proxies.add(httpBean)
                                } else for (user in it.users) proxies.add(httpBean.clone().apply {
                                    username = user.user
                                    password = user.pass
                                    name = tag ?: (displayName() + " - $username")
                                })
                            }
                        }
                    }
                }
                "hysteria2" -> {
                    val hysteria2Bean = Hysteria2Bean().applyDefaultValues()
                    streamSettings?.apply {
                        when (network) {
                            "hysteria2", "hy2" -> {
                                hy2Settings?.apply {
                                    password?.also {
                                        hysteria2Bean.auth = it
                                    }
                                    congestion?.apply {
                                        up_mbps?.also {
                                            hysteria2Bean.uploadMbps = it
                                        }
                                        down_mbps?.also {
                                            hysteria2Bean.downloadMbps = it
                                        }
                                    }
                                    obfs?.apply {
                                        type?.also {
                                            if (type == "salamander") hysteria2Bean.obfs = it
                                        }
                                    }
                                }
                            }
                        }
                        when (security) {
                            "tls" -> {
                                tlsSettings?.apply {
                                    serverName?.also {
                                        hysteria2Bean.sni = it
                                    }
                                    allowInsecure?.also {
                                        hysteria2Bean.allowInsecure = it
                                    }
                                }
                            }
                        }
                        (settings.value as? V2RayConfig.Hysteria2OutboundConfigurationObject)?.servers?.forEach {
                            hysteria2Bean.serverAddress = it.address
                            hysteria2Bean.serverPorts = it.port.toString()
                        }
                        proxies.add(hysteria2Bean)
                    }
                }
            }
            Unit
        }

        return proxies
    }

}
