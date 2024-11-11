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

package io.nekohasekai.sagernet.fmt.v2ray

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore

fun parseV2Ray(link: String): StandardV2RayBean {
    if (!link.contains("@")) {
        return parseV2RayN(link)
    }

    val url = Libcore.parseURL(link)
    val bean = when (url.scheme) {
        "vmess" -> VMessBean()
        "vless" -> VLESSBean()
        "trojan" -> TrojanBean()
        else -> error("impossible")
    }

    bean.serverAddress = url.host
    bean.serverPort = url.port
    bean.name = url.fragment

    if (bean is VMessBean && url.password.isNotBlank()) {
        // https://github.com/v2fly/v2fly-github-io/issues/26
        var protocol = url.username
        bean.type = protocol
        bean.alterId = url.password.substringAfterLast('-').toInt()
        bean.uuid = url.password.substringBeforeLast('-')

        if (protocol.endsWith("+tls")) {
            bean.security = "tls"
            protocol = protocol.substring(0, protocol.length - 4)

            url.queryParameter("tlsServerName")?.let {
                if (it.isNotBlank()) {
                    bean.sni = it
                }
            }
        }

        when (protocol) {
            "tcp" -> {
                url.queryParameter("type")?.let { type ->
                    if (type == "http") {
                        bean.headerType = "http"
                        url.queryParameter("host")?.let {
                            bean.host = it
                        }
                    }
                }
            }
            "http" -> {
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("host")?.let {
                    bean.host = it.split("|").joinToString(",")
                }
            }
            "ws" -> {
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("host")?.let {
                    bean.host = it
                }
            }
            "kcp" -> {
                url.queryParameter("type")?.let {
                    bean.headerType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mKcpSeed = it
                }
            }
            "quic" -> {
                url.queryParameter("security")?.let {
                    bean.quicSecurity = it
                }
                url.queryParameter("key")?.let {
                    bean.quicKey = it
                }
                url.queryParameter("type")?.let {
                    bean.headerType = it
                }
            }
            else -> error("unknown protocol: $protocol")
        }
    } else {
        // https://github.com/XTLS/Xray-core/issues/91
        // https://github.com/XTLS/Xray-core/discussions/716

        if (bean is TrojanBean) {
            bean.password = url.username
            if (url.password.isNotBlank()) {
                // https://github.com/trojan-gfw/igniter/issues/318
                bean.password += ":" + url.password
            }
        } else {
            bean.uuid = url.username
        }

        val protocol = url.queryParameter("type") ?: "tcp"
        bean.type = protocol

        if (bean is TrojanBean) {
            bean.security = url.queryParameter("security") ?: "tls"
        } else {
            bean.security = url.queryParameter("security") ?: "none"
        }
        when (bean.security) {
            "tls" -> {
                if (bean is TrojanBean) {
                    bean.sni = url.queryParameter("sni") ?: url.queryParameter("peer")
                } else {
                    url.queryParameter("sni")?.let {
                        bean.sni = it
                    }
                }
                url.queryParameter("alpn")?.let {
                    bean.alpn = it.split(",").joinToString("\n")
                }
                if (bean is VLESSBean) {
                    url.queryParameter("flow")?.let {
                        bean.flow = it
                        bean.packetEncoding = "xudp"
                    }
                }
                if (bean is TrojanBean) {
                    // bad format from where?
                    url.queryParameter("allowInsecure")?.let {
                        if (it == "1" || it.lowercase() == "true") {
                            bean.allowInsecure = true // non-standard
                        }
                    }
                    url.queryParameter("insecure")?.let {
                        if (it == "1" || it.lowercase() == "true") {
                            bean.allowInsecure = true // non-standard
                        }
                    }
                }
                //url.queryParameter("fp")?.let {} // do not support this intentionally
            }
            "reality" -> {
                url.queryParameter("sni")?.let {
                    bean.sni = it
                }
                url.queryParameter("pbk")?.let {
                    bean.realityPublicKey = it
                }
                url.queryParameter("sid")?.let {
                    bean.realityShortId = it
                }
                url.queryParameter("spx")?.let {
                    bean.realitySpiderX = it
                }
                url.queryParameter("fp")?.let {
                    bean.realityFingerprint = it
                }
                if (bean is VLESSBean) {
                    url.queryParameter("flow")?.let {
                        bean.flow = it
                        bean.packetEncoding = "xudp"
                    }
                }
            }
            "none" -> {}
            else -> error("unknown security: " + bean.security)
        }
        when (protocol) {
            "tcp" -> {
                url.queryParameter("headerType")?.let { headerType ->
                    if (headerType == "http") {
                        bean.headerType = headerType
                        url.queryParameter("host")?.let {
                            bean.host = it
                        }
                        url.queryParameter("path")?.let {
                            bean.path = it
                        }
                    }
                }
            }
            "kcp" -> {
                url.queryParameter("headerType")?.let {
                    bean.headerType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mKcpSeed = it
                }
            }
            "http" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let {
                    bean.path = it
                }
            }
            "xhttp", "splithttp" -> {
                bean.type = "splithttp"
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                when (val mode = url.queryParameter("mode")) {
                    "" -> bean.splithttpMode = "auto"
                    "auto", "stream-up", "packet-up" -> bean.splithttpMode = mode
                }
            }
            "httpupgrade" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let { path ->
                    // RPRX's smart-assed invention. This of course will break under some conditions.
                    bean.path = path
                    val u = Libcore.parseURL(path)
                    u.queryParameter("ed")?.let {
                        u.deleteQueryParameter("ed")
                        bean.path = u.string
                    }
                }
            }
            "ws" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let { path ->
                    // RPRX's smart-assed invention. This of course will break under some conditions.
                    bean.path = path
                    val u = Libcore.parseURL(path)
                    u.queryParameter("ed")?.let { ed ->
                        u.deleteQueryParameter("ed")
                        bean.path = u.string
                        bean.wsMaxEarlyData = ed.toIntOrNull()
                        bean.earlyDataHeaderName = "Sec-WebSocket-Protocol"
                    }
                }
            }
            "quic" -> {
                url.queryParameter("headerType")?.let {
                    bean.headerType = it
                }
                url.queryParameter("quicSecurity")?.let { quicSecurity ->
                    bean.quicSecurity = quicSecurity
                    url.queryParameter("key")?.let {
                        bean.quicKey = it
                    }
                }
            }
            "grpc" -> {
                url.queryParameter("serviceName")?.let {
                    // Xray hijacks the share link standard, uses escaped `serviceName` and some other non-standard `serviceName`s and breaks the compatibility with other implementations.
                    // Fixing the compatibility with Xray will break the compatibility with V2Ray and others.
                    // So do not fix the compatibility with Xray.
                    bean.grpcServiceName = it
                }
            }
            "meek" -> {
                // https://github.com/v2fly/v2ray-core/discussions/2638
                url.queryParameter("url")?.let {
                    bean.meekUrl = it
                }
            }
            "mekya" -> {
                // not a standard
                url.queryParameter("headerType")?.let {
                    bean.mekyaKcpHeaderType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mekyaKcpSeed = it
                }
                url.queryParameter("url")?.let {
                    bean.mekyaUrl = it
                }
            }
            else -> error("unknown type: " + bean.type)
        }

    }

    Logs.d(formatObject(bean))

    return bean
}

fun parseV2RayN(link: String): VMessBean {
    val result = link.substringAfter("vmess://").decodeBase64UrlSafe()
    if (result.contains("= vmess")) {
        return parseCsvVMess(result)
    }
    val bean = VMessBean()
    val json = JSONObject(result)

    bean.serverAddress = json.getStr("add") ?: ""
    bean.serverPort = json.getInt("port") ?: 0
    bean.encryption = json.getStr("scy") ?: ""
    bean.uuid = json.getStr("id") ?: ""
    bean.alterId = json.getInt("aid") ?: 0
    when (val net = json.getStr("net") ?: "tcp") {
        "h2" -> bean.type = "http"
        "" -> bean.type = "tcp"
        "xhttp" -> bean.type = "splithttp" // There is no "xhttp" as of v2rayNG 1.9.16. Just in case.
        "tcp", "kcp", "ws", "quic", "grpc", "httpupgrade", "splithttp" -> bean.type = net
        else -> error("unknown net: $net")
    }
    bean.headerType = json.getStr("type") ?: "none"
    bean.host = json.getStr("host") ?: ""
    val path = json.getStr("path") ?: ""

    bean.path = path
    if (bean.type == "ws") {
        // RPRX's smart-assed invention. This of course will break under some conditions.
        val u = Libcore.parseURL(path)
        u.queryParameter("ed")?.let { ed ->
            u.deleteQueryParameter("ed")
            bean.path = u.string
            bean.wsMaxEarlyData = ed.toIntOrNull()
            bean.earlyDataHeaderName = "Sec-WebSocket-Protocol"
        }
    }
    if (bean.type == "httpupgrade") {
        // RPRX's smart-assed invention. This of course will break under some conditions.
        val u = Libcore.parseURL(path)
        u.queryParameter("ed")?.let {
            u.deleteQueryParameter("ed")
            bean.path = u.string
        }
    }

    when (bean.type) {
        "quic" -> {
            bean.quicSecurity = bean.host
            bean.quicKey = bean.path
            bean.host = ""
        }
        "kcp" -> {
            bean.mKcpSeed = bean.path
        }
        "grpc" -> {
            // Xray hijacks the share link standard, uses escaped `serviceName` and some other non-standard `serviceName`s and breaks the compatibility with other implementations.
            // Fixing the compatibility with Xray will break the compatibility with V2Ray and others.
            // So do not fix the compatibility with Xray.
            bean.grpcServiceName = bean.path
            bean.host = ""
        }
    }

    bean.name = json.getStr("ps") ?: ""
    bean.sni = json.getStr("sni") ?: bean.host
    bean.alpn = json.getStr("alpn")?.split(",")?.joinToString("\n")
    when (val security = json.getStr("tls") ?: "none") {
        "tls", "reality", "none" -> bean.security = security
        "" -> bean.security = "none"
        else -> error("unknown tls: $security")
    }
    bean.realityFingerprint = json.getStr("fp")
    // bean.utlsFingerprint = ? // do not support this intentionally

    if (json.getInt("v", 2) < 2) {
        when (bean.type) {
            "ws" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.host.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.host = host
            }
            "h2" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.host.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.host = host
            }
        }
    }

    return bean

}

private fun parseCsvVMess(csv: String): VMessBean {

    val args = csv.split(",")

    val bean = VMessBean()

    bean.serverAddress = args[1]
    bean.serverPort = args[2].toInt()
    bean.encryption = args[3]
    bean.uuid = args[4].replace("\"", "")

    args.subList(5, args.size).forEach {

        when {
            it == "over-tls=true" -> bean.security = "tls"
            it.startsWith("tls-host=") -> bean.host = it.substringAfter("=")
            it.startsWith("obfs=") -> bean.type = it.substringAfter("=")
            it.startsWith("obfs-path=") || it.contains("Host:") -> {
                runCatching {
                    bean.path = it.substringAfter("obfs-path=\"").substringBefore("\"obfs")
                }
                runCatching {
                    bean.host = it.substringAfter("Host:").substringBefore("[")
                }

            }

        }

    }

    return bean

}

fun VMessBean.toV2rayN(): String {

    return "vmess://" + JSONObject().also {

        it["v"] = 2
        it["ps"] = name
        it["add"] = serverAddress
        it["port"] = serverPort
        it["id"] = this.uuidOrGenerate()
        it["aid"] = alterId
        it["net"] = when (type) {
            "tcp", "kcp", "ws", "httpupgrade", "quic", "grpc", "splithttp" -> type
            "http" -> "h2"
            else -> return "" // error("V2rayN format does not support $type")
        }
        it["host"] = when (type) {
            "tcp", "ws", "httpupgrade", "http", "splithttp" -> host
            "quic" -> quicSecurity
            else -> ""
        }
        it["path"] = when (type) {
            "ws", "httpupgrade", "http", "splithttp" -> path
            "quic" -> quicKey
            "kcp" -> mKcpSeed
            "grpc" -> grpcServiceName
            else -> ""
        }
        it["type"] = when (type) {
            "tcp", "kcp", "quic" -> headerType
            else -> ""
        }
        it["tls"] = when (security) {
            "tls", "reality" -> security
            "none", "" -> ""
            else -> ""
        }
        it["sni"] = sni
        it["alpn"] = alpn.listByLineOrComma().joinToString(",")
        it["scy"] = encryption
        it["fp"] = when (security) {
            "reality" -> realityFingerprint
            "tls" -> "" // do not support this intentionally
            else -> ""
        }

    }.toString().let { Base64.encode(it) }

}

fun StandardV2RayBean.toUri(): String {
    if (this is VMessBean && alterId > 0) return toV2rayN()

    val builder = Libcore.newURL(
        if (this is VMessBean) "vmess" else if (this is VLESSBean) "vless" else "trojan"
    )
    builder.host = serverAddress
    builder.port = serverPort
    if (this is TrojanBean) {
        builder.username = password
    } else {
        builder.username = this.uuidOrGenerate()
    }

    builder.addQueryParameter("type", when (type) {
        "splithttp" -> "xhttp"
        "tcp", "kcp", "ws", "http", "httpupgrade", "quic", "grpc", "meek", "meyka" -> type
        else -> return "" // error("VMessAEAD/VLESS format does not support $type")
    })

    if (this !is TrojanBean) {
        builder.addQueryParameter("encryption", encryption)
    }

    when (type) {
        "tcp" -> {
            if (headerType == "http") {
                builder.addQueryParameter("headerType", headerType)

                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
        "kcp" -> {
            if (headerType.isNotBlank() && headerType != "none") {
                builder.addQueryParameter("headerType", headerType)
            }
            if (mKcpSeed.isNotBlank()) {
                builder.addQueryParameter("seed", mKcpSeed)
            }
        }
        "ws", "http", "httpupgrade" -> {
            if (host.isNotBlank()) {
                builder.addQueryParameter("host", host)
            }
            if (path.isNotBlank()) {
                builder.addQueryParameter("path", path)
            }
        }
        "splithttp" -> {
            if (host.isNotBlank()) {
                builder.addQueryParameter("host", host)
            }
            if (path.isNotBlank()) {
                builder.addQueryParameter("path", path)
            }
            if (splithttpMode != "auto") {
                builder.addQueryParameter("mode", splithttpMode)
            }
        }
        "quic" -> {
            if (headerType.isNotBlank() && headerType != "none") {
                builder.addQueryParameter("headerType", headerType)
            }
            if (quicSecurity.isNotBlank() && quicSecurity != "none") {
                builder.addQueryParameter("quicSecurity", quicSecurity)
                builder.addQueryParameter("key", quicKey)
            }
        }
        "grpc" -> {
            if (grpcServiceName.isNotBlank()) {
                builder.addQueryParameter("serviceName", grpcServiceName)
            }
        }
        "meek" -> {
            // https://github.com/v2fly/v2ray-core/discussions/2638
            if (meekUrl.isNotBlank()) {
                builder.addQueryParameter("url", meekUrl)
            }
        }
        "mekya" -> {
            // not a standard
            if (headerType != "none") {
                builder.addQueryParameter("headerType", mekyaKcpHeaderType)
            }
            if (mekyaKcpSeed.isNotBlank()) {
                builder.addQueryParameter("seed", mekyaKcpSeed)
            }
            if (mekyaUrl.isNotBlank()) {
                builder.addQueryParameter("url", mekyaUrl)
            }
        }
    }

    when (security) {
        "tls" -> {
            builder.addQueryParameter("security", security)
            if (sni.isNotBlank()) {
                builder.addQueryParameter("sni", sni)
            }
            if (alpn.isNotBlank()) {
                builder.addQueryParameter("alpn", alpn.listByLineOrComma().joinToString(","))
            }
            if (allowInsecure) {
                // bad format from where?
                builder.addQueryParameter("allowInsecure", "1")
            }
            if (this is VLESSBean && flow.isNotBlank()) {
                builder.addQueryParameter("flow", flow.removeSuffix("-udp443"))
            }
        }
        "reality" -> {
            builder.addQueryParameter("security", security)
            if (sni.isNotBlank()) {
                builder.addQueryParameter("sni", sni)
            }
            if (realityPublicKey.isNotBlank()) {
                builder.addQueryParameter("pbk", realityPublicKey)
            }
            if (realityShortId.isNotBlank()) {
                builder.addQueryParameter("sid", realityShortId)
            }
            if (realitySpiderX.isNotBlank()) {
                builder.addQueryParameter("spx", realitySpiderX)
            }
            if (realityFingerprint.isNotBlank()) {
                builder.addQueryParameter("fp", realityFingerprint)
            }
            if (this is VLESSBean && flow.isNotBlank()) {
                builder.addQueryParameter("flow", flow.removeSuffix("-udp443"))
            }
        }
        "none" -> if (this is TrojanBean) {
            builder.addQueryParameter("security", security)
        }
    }

    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }

    return builder.string

}