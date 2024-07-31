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

package io.nekohasekai.sagernet.fmt.hysteria2

import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import java.io.File

fun parseHysteria2(rawURL: String): Hysteria2Bean {
    var url = rawURL

    // fuck port hopping URL
    val hostPort = url.substringAfter("://").substringAfter("@")
        .substringBefore("?").substringBefore("/")
    var port = ""
    if (!hostPort.endsWith("]") && hostPort.lastIndexOf(":") > 0) {
        port = hostPort.substringAfterLast(":")
    }
    if (port.isNotEmpty() && port.isValidHysteriaMultiPort()) {
        url = url.replace(":$port", ":0")
    }

    val link = Libcore.parseURL(url)
    return Hysteria2Bean().apply {
        name = link.fragment

        serverAddress = link.host
        serverPorts = if (port.isNotEmpty() && port.isValidHysteriaMultiPort()) {
            port
        } else if (link.port > 0) {
            link.port.toString()
        } else {
            "443"
        }

        link.queryParameter("mport")?.also {
            serverPorts = it
        }

        if (link.username.isNotBlank()) {
            auth = link.username
        }

        if (link.password.isNotBlank()) {
            auth += ":" + link.password
        }

        link.queryParameter("sni")?.also {
            sni = it
        }
        link.queryParameter("insecure")?.also {
            allowInsecure = it == "1"
        }
        link.queryParameter("pinSHA256")?.also {
            pinSHA256 = it
        }
        link.queryParameter("obfs")?.also { it ->
            if (it == "salamander") {
                link.queryParameter("obfs-password")?.also {
                    obfs = it
                }
            }
        }
    }
}

fun Hysteria2Bean.toUri(): String {
    if (!serverPorts.isValidHysteriaPort()) {
        error("invalid port: $serverPorts")
    }
    val builder = Libcore.newURL("hysteria2")
    builder.host = serverAddress
    builder.port = if (serverPorts.isValidHysteriaMultiPort()) {
        0 // placeholder
    } else {
        serverPorts.toInt()
    }

    if (auth.isNotBlank()) {
        val a = auth.split(":")
        if (a.size == 2) {
            // https://github.com/apernet/hysteria/blob/c7545cc870e5cc62a187ad03a083920e6bef049f/app/cmd/client.go#L308-L316
            builder.username = a[0]
            builder.password = a[1]
        } else {
            builder.username = auth
        }
    }

    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (allowInsecure) {
        builder.addQueryParameter("insecure", "1")
    }
    if (pinSHA256.isNotBlank()) {
        builder.addQueryParameter("pinSHA256", pinSHA256)
    }
    if (obfs.isNotBlank()) {
        builder.addQueryParameter("obfs", "salamander")
        builder.addQueryParameter("obfs-password", obfs)
    }
    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }
    builder.rawPath = "/"
    val url = builder.string
    if (serverPorts.isValidHysteriaMultiPort()) {
        // fuck port hopping URL
        val port = url.substringAfter("://").substringAfter("@")
            .substringBefore("?").substringBefore("/")
            .substringAfterLast(":")
        return url.replace(":$port/", ":$serverPorts/")
    }
    return url
}

fun Hysteria2Bean.buildHysteria2Config(port: Int, cacheFile: (() -> File)?): String {
    // TODO: Use a YAML library to generate configuration or just use JSON.
    if (!serverPorts.isValidHysteriaPort()) {
        error("invalid port: $serverPorts")
    }
    val hostPort = if (DataStore.hysteriaEnablePortHopping && serverPorts.isValidHysteriaMultiPort()) {
        if (DataStore.tunImplementation == TunImplementation.SYSTEM) {
            // system stack need some protector hacks
            error("Please switch to TUN gVisor stack for Hysteria 2 port hopping.")
        }
        // Hysteria 2 port hopping is incompatible with chain proxy
        if (serverAddress.isIpv6Address()) {
            "[$serverAddress]:$serverPorts"
        } else {
            "$serverAddress:$serverPorts"
        }
    } else {
        wrapUri()
    }
    var conf = "server: \"$hostPort\"\n"
    if (auth.isNotBlank()) {
        conf += "\nauth: \"$auth\"\n"
    }
    conf += "\ntls:\n  insecure: $allowInsecure\n"
    if (sni.isBlank() && !serverAddress.isIpAddress()) {
        if (!serverPorts.isValidHysteriaMultiPort()) {
            sni = serverAddress
        }
    }
    if (sni.isNotBlank()) {
        conf += "  sni: \"$sni\"\n"
    }
    if (caText.isNotBlank() && cacheFile != null) {
        val caFile = cacheFile()
        caFile.writeText(caText)
        conf += "  ca: \"" + caFile.absolutePath + "\"\n"
    }
    if (pinSHA256.isNotBlank()) {
        conf += "  pinSHA256: \"$pinSHA256\"\n"
    }
    conf += "\ntransport:\n  type: udp\n"
    if (DataStore.hysteriaEnablePortHopping && serverPorts.isValidHysteriaMultiPort()) {
        conf += "  udp:\n    hopInterval: " + hopInterval + "s\n"
    }
    conf += "\n"
    if (obfs.isNotBlank()) {
        conf += "obfs:\n  type: salamander\n  salamander:\n    password: \"$obfs\"\n\n"
    }
    conf += "quic:\n  disablePathMTUDiscovery: $disableMtuDiscovery\n"
    if (initStreamReceiveWindow > 0) {
        conf += "  initStreamReceiveWindow: $initStreamReceiveWindow\n"
    }
    if (maxStreamReceiveWindow > 0) {
        conf += "  maxStreamReceiveWindow: $maxStreamReceiveWindow\n"
    }
    if (initConnReceiveWindow > 0) {
        conf += "  initConnReceiveWindow: $initConnReceiveWindow\n"
    }
    if (maxConnReceiveWindow > 0) {
        conf += "  maxConnReceiveWindow: $maxConnReceiveWindow\n"
    }
    conf += "\nbandwidth:\n  up: $uploadMbps mbps\n  down: $downloadMbps mbps\n"
    conf += "\nsocks5:\n  listen: \"$LOCALHOST:$port\"\n"
    conf += "\nlazy: true\n"
    conf += "\nfastOpen: true\n"
    return conf
}
