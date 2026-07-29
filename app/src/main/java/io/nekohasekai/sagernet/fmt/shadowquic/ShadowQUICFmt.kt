/******************************************************************************
 *                                                                            *
 * Copyright (C) 2025  dyhkwong                                               *
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

package io.nekohasekai.sagernet.fmt.shadowquic

import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.queryParameter
import libexclavecore.Libexclavecore
import kotlin.text.ifEmpty

// https://github.com/RealBikiniBottom/QuicProxy/discussions/2
// https://github.com/spongebob888/shadowquic/discussions/160
// third-party share link standard endorsed by the ShadowQUIC author
fun parseShadowQUIC(url: String): ShadowQUICBean {
    val link = Libexclavecore.parseURL(url)
    return ShadowQUICBean().apply {
        name = link.fragment
        serverAddress = link.host.ifEmpty { error("empty host") }
        serverPort = link.port.takeIf { it > 0 } ?: 443
        username = link.username.ifEmpty { error("missing username") }
        password = link.password.ifEmpty { error("missing password") }
        sni = link.queryParameter("sni")?.ifEmpty { error("missing sni") } ?: error("missing sni")
        udpOverStream = when (link.queryParameter("udp_mode")) {
            "datagram" -> false
            "stream" -> true
            else -> true
        }
        zeroRTT = link.hasQueryParameter("zero_rtt")
        link.queryParameter("alpn")?.also {
            alpn = it.split(",").joinToString("\n")
        }
    }
}

fun ShadowQUICBean.toUri(): String? {
    val builder = Libexclavecore.newURL("sq").apply {
        if (name.isNotEmpty()) {
            fragment = name
        }
        setHostPort(serverAddress.ifEmpty { error("empty server address") }, serverPort)
        addQueryParameter("sni", sni.ifEmpty { error("missing sni") })
        addQueryParameter("udp_mode", if (udpOverStream) "stream" else "datagram")
        if (zeroRTT) {
            addQueryParameter("zero_rtt", "true")
        }
        if (alpn.listByLineOrComma().isNotEmpty()) {
            addQueryParameter("alpn", alpn.listByLineOrComma().joinToString(","))
        }
    }
    builder.username = username.ifEmpty { error("missing username") }
    builder.password = password.ifEmpty { error("missing password") }
    return builder.string
}
