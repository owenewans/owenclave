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

package io.nekohasekai.sagernet.fmt.trojan_go

import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

fun parseTrojanGo(server: String): TrojanGoBean {
    val link = Libcore.parseURL(server)

    return TrojanGoBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username
        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("type")?.let { lType ->
            type = lType

            when (type) {
                "ws" -> {
                    link.queryParameter("host")?.let {
                        host = it
                    }
                    link.queryParameter("path")?.let {
                        path = it
                    }
                }
                else -> {
                }
            }
        }
        link.queryParameter("encryption")?.let {
            encryption = it
        }
        link.queryParameter("plugin")?.let {
            plugin = it
        }
        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
    }
}

fun TrojanGoBean.toUri(): String {
    val builder = Libcore.newURL("trojan-go")
    builder.host = serverAddress
    builder.port = serverPort
    builder.username = password

    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (type.isNotBlank() && type != "original") {
        builder.addQueryParameter("type", type)

        when (type) {
            "ws" -> {
                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
    }
    if (encryption.isNotBlank() && encryption != "none") {
        builder.addQueryParameter("encryption", encryption)
    }
    if (plugin.isNotBlank() && PluginConfiguration(plugin).selected.isNotBlank()) {
        var p = PluginConfiguration(plugin).selected
        if (PluginConfiguration(plugin).getOptions().toString().isNotBlank()) {
            p += ";" + PluginConfiguration(plugin).getOptions().toString()
        }
        builder.addQueryParameter("plugin", p)
    }

    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }

    return builder.string
}

fun TrojanGoBean.buildTrojanGoConfig(port: Int): String {
    return JSONObject().also { conf ->
        conf["run_type"] = "client"
        conf["local_addr"] = LOCALHOST
        conf["local_port"] = port
        conf["remote_addr"] = finalAddress
        conf["remote_port"] = finalPort
        conf["password"] = JSONArray().apply {
            add(password)
        }
        conf["log_level"] = if (DataStore.enableLog) 0 else 2
        if (mux) conf["mux"] = JSONObject().also {
            it["enabled"] = true
            it["concurrency"] = muxConcurrency
        }

        when (type) {
            "original" -> {
            }
            "ws" -> conf["websocket"] = JSONObject().also {
                it["enabled"] = true
                it["host"] = host
                it["path"] = path
            }
        }

        var servername = sni
        if (servername.isBlank()) {
            servername = serverAddress
        }

        conf["ssl"] = JSONObject().also {
            if (servername.isNotBlank()) it["sni"] = servername
            if (allowInsecure) it["verify"] = false
            if (utlsFingerprint.isNotBlank()) it["fingerprint"] = utlsFingerprint
        }

        when {
            encryption == "none" -> {
            }
            encryption.startsWith("ss;") -> conf["shadowsocks"] = JSONObject().also {
                it["enabled"] = true
                it["method"] = encryption.substringAfter(";").substringBefore(":")
                it["password"] = encryption.substringAfter(":")
            }
        }

        if (plugin.isNotBlank()) {
            val pluginConfiguration = PluginConfiguration(plugin ?: "")
            PluginManager.init(pluginConfiguration)?.let { (path, opts, _) ->
                conf["transport_plugin"] = JSONObject().also {
                    it["enabled"] = true
                    it["type"] = "shadowsocks"
                    it["command"] = path
                    it["option"] = opts.toString()
                }
            }
        }
    }.toStringPretty()
}
