/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.fmt.tuic

import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.RootCAProvider
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore
import java.io.File

fun TuicBean.toUri(): String {
    val builder = Libcore.newURL("tuic")
    builder.host = serverAddress
    builder.port = serverPort
    builder.username = token
    builder.addQueryParameter("version", "4")
    builder.addQueryParameter("udp_relay_mode", udpRelayMode)
    builder.addQueryParameter("congestion_control", congestionController)
    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (alpn.isNotBlank()) {
        builder.addQueryParameter("alpn", alpn.split("\n").joinToString(","))
    }
    if (disableSNI) {
        builder.addQueryParameter("disable_sni", "1")
    }
    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }
    builder.addQueryParameter("udp_relay-mode", udpRelayMode)
    builder.addQueryParameter("congestion_controller", congestionController)
    return builder.string
}

fun TuicBean.buildTuicConfig(port: Int, cacheFile: (() -> File)?): String {
    return JSONObject().also {
        it["relay"] = JSONObject().also {
            if (sni.isNotBlank()) {
                it["server"] = sni
                it["ip"] = finalAddress
            } else if (serverAddress.isIpAddress()) {
                it["server"] = finalAddress
            } else {
                it["server"] = serverAddress
                it["ip"] = finalAddress
            }
            it["port"] = finalPort
            it["token"] = token

            if (caText.isNotBlank() && cacheFile != null) {
                val caFile = cacheFile()
                caFile.writeText(caText)
                it["certificates"] = JSONArray().apply {
                    put(caFile.absolutePath)
                }
            } else if (DataStore.providerRootCA == RootCAProvider.SYSTEM && caText.isBlank()) {
                it["certificates"] = JSONArray().apply {
                    // workaround tuic can't load Android system root certificates without forking it
                    File("/system/etc/security/cacerts").listFiles()?.forEach { put(it) }
                }
            }

            it["udp_relay_mode"] = udpRelayMode
            if (alpn.isNotBlank()) {
                it["alpn"] = JSONArray(alpn.split("\n"))
            }
            it["congestion_controller"] = congestionController
            it["disable_sni"] = disableSNI
            it["reduce_rtt"] = reduceRTT
            it["max_udp_relay_packet_size"] = mtu
        }
        it["local"] = JSONObject().also {
            it["ip"] = LOCALHOST
            it["port"] = port
        }
        it["log_level"] = if (DataStore.enableLog) "debug" else "info"
    }.toStringPretty()
}