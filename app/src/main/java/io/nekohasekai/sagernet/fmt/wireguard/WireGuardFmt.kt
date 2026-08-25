/******************************************************************************
 *                                                                            *
 * Copyright (C) 2024  dyhkwong                                               *
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

package io.nekohasekai.sagernet.fmt.wireguard

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.joinHostPort
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.queryParameter
import libexclavecore.Libexclavecore
import com.sshtools.jini.INI
import com.sshtools.jini.INIWriter
import java.io.StringWriter
import kotlin.io.encoding.Base64
import kotlin.jvm.optionals.getOrNull

fun parseWireGuard(server: String): WireGuardBean {
    val link = Libexclavecore.parseURL(server)
    return WireGuardBean().apply {
        serverAddress = link.host
        serverPort = when {
            !link.hasPort() -> error("invalid port")
            else -> link.port
        }
        if (link.username.isNotEmpty()) {
            privateKey = parseXrayStupidKey(link.username)
            // v2rayNG style link
            // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L75
            localAddress = "10.0.0.1/32\nfd59:7153:2388:b5fd:0000:0000:0000:0001/128"
        }
        (link.queryParameter("privatekey") ?: link.queryParameter("privateKey")) ?.let {
            privateKey = parseXrayStupidKey(it)
        }
        (link.queryParameter("address") ?: link.queryParameter("ip")) ?.takeIf { it.isNotEmpty() }?.also {
            // TODO: validate address
            localAddress = it.split(",").joinToString("\n")
        }
        (link.queryParameter("publickey") ?: link.queryParameter("publicKey")) ?.let {
            peerPublicKey = parseXrayStupidKey(it)
        }
        (link.queryParameter("presharedkey") ?: link.queryParameter("preSharedKey")) ?.let {
            peerPreSharedKey = parseXrayStupidKey(it)
        }
        link.queryParameter("mtu")?.toIntOrNull()?.takeIf { it > 0 }?.let {
            mtu = it
        }
        link.queryParameter("reserved")?.let {
            reserved = it
        }
        link.fragment?.let {
            name = it
        }
    }
}

private fun parseXrayStupidKey(str: String): String {
    // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L126-L148
    val key = if (str.length == 64) {
        str.hexToByteArray()
    } else if (str.contains("-") || str.contains("_")) {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(str)
    } else {
        Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(str)
    }
    require(key.size == 32)
    return Base64.encode(key)
}

fun parseWireGuardConfig(conf: String): List<WireGuardBean> {
    val beans = mutableListOf<WireGuardBean>()
    val ini = try {
        INI.fromString(conf)
    } catch (_: Exception) {
        return beans
    }
    val iface = ini.sectionOr("Interface").getOrNull() ?: return beans
    val wgBean = WireGuardBean().apply {
        localAddress = iface.getAllOr("Address").getOrNull()
            ?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: return beans
        privateKey = iface.getOr("PrivateKey").get()
        require(Base64.decode(privateKey).size == 32)
        mtu = iface.getOr("MTU").getOrNull()?.toInt()?.takeIf { it > 0 } ?: 1420
    }
    val peers = ini.allSectionsOr("Peer").getOrNull() ?: return beans
    for (peer in peers) {
        val endpoint = peer.getOr("Endpoint").getOrNull()
        if (endpoint.isNullOrEmpty() || !endpoint.contains(":")) {
            continue
        }
        beans.add(wgBean.applyDefaultValues().clone().apply {
            val hostPort = Libexclavecore.splitHostPort(endpoint)
            serverAddress = hostPort.host
            serverPort = hostPort.port
            peerPublicKey = peer.getOr("PublicKey").get()
            require(Base64.decode(peerPublicKey).size == 32)
            peerPreSharedKey = peer.getOr("PreSharedKey").getOrNull()
            if (!peerPreSharedKey.isNullOrEmpty()) {
                require(Base64.decode(peerPreSharedKey).size == 32)
            }
            keepaliveInterval = peer.getOr("PersistentKeepalive").getOrNull()?.toIntOrNull()?.takeIf { it > 0 }
        })
    }
    return beans
}

fun WireGuardBean.toConf(): String {
    val ini = INI.create()
    val iface = ini.create("Interface")
    iface.put("Address", localAddress.listByLineOrComma())
    if (mtu > 0) {
        iface.put("MTU", mtu)
    }
    require(Base64.decode(privateKey).size == 32)
    iface.put("PrivateKey", privateKey)
    val peer = ini.create("Peer")
    peer.put("Endpoint", joinHostPort(serverAddress, serverPort))
    require(Base64.decode(peerPublicKey).size == 32)
    peer.put("PublicKey", peerPublicKey)
    if (peerPreSharedKey.isNotEmpty()) {
        require(Base64.decode(peerPreSharedKey).size == 32)
        peer.put("PreSharedKey", peerPreSharedKey)
    }
    if (keepaliveInterval > 0) {
        peer.put("PersistentKeepalive", keepaliveInterval)
    }
    val conf = StringWriter()
    INIWriter.Builder()
        .withIndent(0)
        .withStringQuoteMode(INIWriter.StringQuoteMode.NEVER)
        .build()
        .write(ini, conf)
    return conf.toString()
}
