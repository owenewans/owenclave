package io.nekohasekai.sagernet.fmt.wireguard

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

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
