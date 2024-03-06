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

package io.nekohasekai.sagernet.fmt.brook

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore

fun parseBrook(text: String): AbstractBean {
    val link = Libcore.parseURL(text)

    val bean = if (link.host == "socks5") SOCKSBean() else BrookBean()
    bean.name = link.queryParameter("name")

    // "Do not omit the port under any circumstances"
    when (link.host) {
        "server" -> {
            bean as BrookBean
            bean.protocol = ""

            val server = link.queryParameter("server")
                ?: error("Invalid brook server url (Missing server parameter): $text")

            bean.serverAddress = server.substringBeforeLast(":").unwrapHost()
            bean.serverPort = server.substringAfterLast(":").toInt()
            bean.password = link.queryParameter("password")
                ?: error("Invalid brook server url (Missing password parameter): $text")
        }
        "wsserver" -> {
            bean as BrookBean
            bean.protocol = "ws"


            var wsserver = (link.queryParameter("wsserver")
                ?: error("Invalid brook wsserver url (Missing wsserver parameter): $text")).substringAfter(
                "://"
            )
            if (wsserver.contains("/")) {
                bean.wsPath = "/" + wsserver.substringAfter("/")
                wsserver = wsserver.substringBefore("/")
            }
            bean.serverAddress = wsserver.substringBeforeLast(":").unwrapHost()
            bean.serverPort = wsserver.substringAfterLast(":").toInt()
            if (link.queryParameter("address") != "") {
                bean.serverAddress = link.queryParameter("address")?.substringBeforeLast(":")?.unwrapHost()
                bean.serverPort = link.queryParameter("address")?.substringAfterLast(":")?.toInt()
            }
            bean.password = link.queryParameter("password")
                ?: error("Invalid brook wsserver url (Missing password parameter): $text")
            if (link.queryParameter("udpovertcp") == "true") {
                bean.udpovertcp = true
            }
            if (link.queryParameter("withoutBrookProtocol") == "true") {
                bean.withoutBrookProtocol = true
            }

        }
        "wssserver" -> {
            bean as BrookBean
            bean.protocol = "wss"


            var wsserver = (link.queryParameter("wssserver")
                ?: error("Invalid brook wssserver url (Missing wssserver parameter): $text")).substringAfter(
                "://"
            )
            if (wsserver.contains("/")) {
                bean.wsPath = "/" + wsserver.substringAfter("/")
                wsserver = wsserver.substringBefore("/")
            }
            bean.serverAddress = wsserver.substringBeforeLast(":").unwrapHost()
            bean.serverPort = wsserver.substringAfterLast(":").toInt()
            if (link.queryParameter("address") != "") {
                bean.sni = bean.serverAddress
                bean.serverAddress = link.queryParameter("address")?.substringBeforeLast(":")?.unwrapHost()
                bean.serverPort = link.queryParameter("address")?.substringAfterLast(":")?.toInt()
            }
            bean.password = link.queryParameter("password")
                ?: error("Invalid brook wssserver url (Missing password parameter): $text")
            if (link.queryParameter("udpovertcp") == "true") {
                bean.udpovertcp = true
            }
            if (link.queryParameter("withoutBrookProtocol") == "true") {
                bean.withoutBrookProtocol = true
            }
            if (link.queryParameter("insecure") == "true") {
                bean.insecure = true
            }
            if (link.queryParameter("tlsfingerprint") == "chrome") {
                bean.tlsfingerprint = "chrome"
            }
            if (link.queryParameter("fragment") != "") {
                bean.fragment = link.queryParameter("fragment")
            }

        }
        "quicserver" -> {
            bean as BrookBean
            bean.protocol = "quic"


            var quicserver = (link.queryParameter("quicserver")
                ?: error("Invalid brook quicserver url (Missing quicserver parameter): $text")).substringAfter(
                "://"
            )
            bean.serverAddress = quicserver.substringBeforeLast(":").unwrapHost()
            bean.serverPort = quicserver.substringAfterLast(":").toInt()
            if (link.queryParameter("address") != "") {
                bean.sni = bean.serverAddress
                bean.serverAddress = link.queryParameter("address")?.substringBeforeLast(":")?.unwrapHost()
                bean.serverPort = link.queryParameter("address")?.substringAfterLast(":")?.toInt()
            }
            bean.password = link.queryParameter("password")
                ?: error("Invalid brook quicserver url (Missing password parameter): $text")
            if (link.queryParameter("udpovertcp") == "true") {
                bean.udpovertcp = true
            }
            if (link.queryParameter("withoutBrookProtocol") == "true") {
                bean.withoutBrookProtocol = true
            }
            if (link.queryParameter("insecure") == "true") {
                bean.insecure = true
            }

        }
        "socks5" -> {
            bean as SOCKSBean

            val socks5 = (link.queryParameter("socks5")
                ?: error("Invalid brook socks5 url (Missing socks5 parameter): $text")).substringAfter(
                "://"
            )

            bean.serverAddress = socks5.substringBeforeLast(":").substringBeforeLast("]").substringAfter("[")
            bean.serverPort = socks5.substringAfterLast(":").toInt()

            link.queryParameter("username")?.also { username ->
                bean.username = username

                link.queryParameter("password")?.also { password ->
                    bean.password = password
                }
            }
        }
    }

    return bean
}

fun BrookBean.toUri(): String {
    val builder = Libcore.newURL("brook")
    var serverString = wrapOriginUri()
    var addressString = wrapOriginUri()
    when (protocol) {
        "ws" -> {
            builder.host = "wsserver"
            if (wsPath.isNotBlank()) {
                if (!wsPath.startsWith("/")) {
                    serverString += "/"
                }
                serverString += wsPath.pathSafe()
            }
            builder.addQueryParameter("wsserver", "ws://" + serverString)
            if (withoutBrookProtocol) {
                builder.addQueryParameter("withoutBrookProtocol", "true")
            }
        }
        "wss" -> {
            builder.host = "wssserver"
            if (sni.isNotBlank()) {
                serverString = sni + ":$serverPort"
            }
            if (wsPath.isNotBlank()) {
                if (!wsPath.startsWith("/")) {
                    serverString += "/"
                }
                serverString += wsPath.pathSafe()
            }
            builder.addQueryParameter("wssserver", "wss://" + serverString)
            if (sni.isNotBlank()) {
                builder.addQueryParameter("address", addressString)
            }
            if (withoutBrookProtocol) {
                builder.addQueryParameter("withoutBrookProtocol", "true")
            }
            if (insecure) {
                builder.addQueryParameter("insecure", "true")
            }
            if (tlsfingerprint.isNotBlank()) {
                builder.addQueryParameter("tlsfingerprint", tlsfingerprint)
            }
            if (fragment.isNotBlank()) {
                builder.addQueryParameter("fragment", fragment)
            }
        }
        "quic" -> {
            builder.host = "quicserver"
            if (sni.isNotBlank()) {
                serverString = sni + ":$serverPort"
            }
            builder.addQueryParameter("quicserver", "quic://" + serverString)
            if (sni.isNotBlank()) {
                builder.addQueryParameter("address", addressString)
            }
            if (withoutBrookProtocol) {
                builder.addQueryParameter("withoutBrookProtocol", "true")
            }
            if (insecure) {
                builder.addQueryParameter("insecure", "true")
            }
        }
        else -> {
            builder.host = "server"
            builder.addQueryParameter("server", serverString)
        }
    }
    if (password.isNotBlank()) {
        builder.addQueryParameter("password", password)
    }
    if (udpovertcp) {
        builder.addQueryParameter("udpovertcp", "true")
    }
    if (name.isNotBlank()) {
        builder.addQueryParameter("name", name)
    }
    return builder.string
}


fun BrookBean.toInternalUri(): String {
    val builder = Libcore.newURL("brook")
    val serverString = internalUri()
    val addressString = wrapUri()
    when (protocol) {
        "ws" -> {
            builder.host = "wsserver"
            builder.addQueryParameter("wsserver", addressString)
            if (withoutBrookProtocol) {
                builder.addQueryParameter("withoutBrookProtocol", "true")
            }
        }
        "wss" -> {
            builder.host = "wssserver"
            builder.addQueryParameter("wssserver", serverString)
            if (withoutBrookProtocol) {
                builder.addQueryParameter("withoutBrookProtocol", "true")
            }
            if (insecure) {
                builder.addQueryParameter("insecure", "true")
            }
            if (tlsfingerprint.isNotBlank()) {
                builder.addQueryParameter("tlsfingerprint", tlsfingerprint)
            }
            if (fragment.isNotBlank()) {
                builder.addQueryParameter("fragment", fragment)
            }
            builder.addQueryParameter("address", addressString)
        }
        "quic" -> {
            builder.host = "quicserver"
            builder.addQueryParameter("quicserver", serverString)
            if (withoutBrookProtocol) {
                builder.addQueryParameter("withoutBrookProtocol", "true")
            }
            if (insecure) {
                builder.addQueryParameter("insecure", "true")
            }
            builder.addQueryParameter("address", addressString)
        }
        else -> {
            builder.host = "server"
            builder.addQueryParameter("server", addressString)
        }
    }
    if (password.isNotBlank()) {
        builder.addQueryParameter("password", password)
    }
    if (udpovertcp) {
        builder.addQueryParameter("udpovertcp", "true")
    }
    if (name.isNotBlank()) {
        builder.addQueryParameter("name", name)
    }
    return builder.string
}

fun BrookBean.wrapUriWithOriginHost0(): String {
    if (sni.isNotBlank()) {
        return sni + ":$finalPort"
    } else if (serverAddress.isIpv6Address()) {
        return "[$serverAddress]:$finalPort"
    } else {
        return "$serverAddress:$finalPort"
    }
}

fun BrookBean.internalUri(): String {
    var server = when (protocol) {
        "ws" -> "ws://" + wrapUriWithOriginHost()
        "wss" -> "wss://" + wrapUriWithOriginHost0()
        "quic" -> "quic://" + wrapUriWithOriginHost0()
        else -> return wrapUri()
    }
    if (protocol.startsWith("ws") && wsPath.isNotBlank()) {
        if (!wsPath.startsWith("/")) {
            server += "/"
        }
        server += wsPath.pathSafe()
    }
    return server
}
