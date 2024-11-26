package io.nekohasekai.sagernet.fmt.wireguard

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.joinHostPort
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore
import org.ini4j.Ini
import java.io.StringWriter

fun parseV2rayNWireGuard(server: String): AbstractBean {
    val link = Libcore.parseURL(server)
    return WireGuardBean().apply {
        serverAddress = link.host
        serverPort = link.port
        privateKey = link.username
        link.queryParameter("address")?.also {
            localAddress = it.split(",").joinToString("\n")
        }
        link.queryParameter("publickey")?.let {
            peerPublicKey = it
        }
        link.queryParameter("presharedkey")?.let {
            peerPreSharedKey = it
        }
        link.queryParameter("mtu")?.let {
            mtu = it.toInt()
        }
        link.queryParameter("reserved")?.let {
            reserved = it
        }
        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
    }
}

fun WireGuardBean.toConf(): String {
    val ini = Ini().apply {
        config.isEscape = false
    }
    if (localAddress.isNotBlank()) {
        ini.add("Interface", "Address", localAddress.listByLineOrComma().joinToString(", "))
    } else {
        // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L75
        ini.add("Interface", "Address", "10.0.0.1/32, fd59:7153:2388:b5fd:0000:0000:0000:0001/128")
    }
    if (mtu > 0) {
        ini.add("Interface", "MTU", mtu)
    }
    ini.add("Interface", "PrivateKey", privateKey)
    ini.add("Peer", "Endpoint", joinHostPort(serverAddress, serverPort))
    ini.add("Peer", "PublicKey", peerPublicKey)
    if (peerPreSharedKey.isNotBlank()) {
        ini.add("Peer", "PreSharedKey", peerPreSharedKey)
    }
    val conf = StringWriter()
    ini.store(conf)
    return conf.toString()
}

fun WireGuardBean.toV2rayN(): String {
    val builder = Libcore.newURL("wireguard")
    builder.host = serverAddress
    builder.port = serverPort
    builder.username = privateKey
    if (localAddress.isNotBlank()) {
        builder.addQueryParameter("address", localAddress.listByLineOrComma().joinToString(","))
    }
    if (peerPublicKey.isNotBlank()) {
        builder.addQueryParameter("publickey", peerPublicKey)
    }
    if (peerPreSharedKey.isNotBlank()) {
        builder.addQueryParameter("presharedkey", peerPreSharedKey)
    }
    if (mtu > 0) {
        builder.addQueryParameter("mtu", mtu.toString())
    }
    if (reserved.isNotBlank()) {
        builder.addQueryParameter("reserved", reserved.listByLineOrComma().joinToString(","))
    }
    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }
    return builder.string
}
