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

package io.nekohasekai.sagernet.fmt.naive

import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.joinHostPort
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

fun parseNaive(link: String): NaiveBean {
    val proto = link.substringAfter("+").substringBefore(":")
    val url = Libcore.parseURL(link)
    return NaiveBean().also {
        it.proto = proto
    }.apply {
        serverAddress = url.host
        serverPort = url.port
        username = url.username
        password = url.password
        extraHeaders = url.queryParameter("extra-headers")?.unUrlSafe()?.replace("\r\n", "\n")
        insecureConcurrency = url.queryParameter("insecure-concurrency")?.toIntOrNull()
        name = url.fragment
        initializeDefaultValues()
    }
}

fun NaiveBean.toUri(proxyOnly: Boolean = false): String {
    val builder = Libcore.newURL(if (proxyOnly) proto else "naive+$proto")
    if (sni.isNotBlank() && proxyOnly) {
        builder.host = sni
    } else {
        builder.host = serverAddress
    }
    if (proxyOnly) {
        builder.port = finalPort
    } else {
        builder.port = serverPort
    }
    if (username.isNotBlank()) {
        builder.username = username
        if (password.isNotBlank()) {
            builder.password = password
        }
    }
    if (!proxyOnly) {
        if (extraHeaders.isNotBlank()) {
            builder.addQueryParameter("extra-headers", extraHeaders)
        }
        if (name.isNotBlank()) {
            builder.setRawFragment(name.urlSafe())
        }
        if (insecureConcurrency > 0) {
            builder.addQueryParameter("insecure-concurrency", "$insecureConcurrency")
        }
        if (sni.isNotBlank()) {
            builder.addQueryParameter("sni", sni)
        }
    }
    return builder.string
}

fun NaiveBean.buildNaiveConfig(port: Int): String {
    return JSONObject().also {
        it["listen"] = "socks://" + joinHostPort(LOCALHOST, port)
        it["proxy"] = toUri(true)
        if (extraHeaders.isNotBlank()) {
            it["extra-headers"] = extraHeaders.split("\n").joinToString("\r\n")
        }
        if (sni.isNotBlank()) {
            it["host-resolver-rules"] = "MAP $sni $finalAddress"
        } else {
            if (!serverAddress.isIpAddress()) {
                it["host-resolver-rules"] = "MAP $serverAddress $finalAddress"
            } else {
                // https://github.com/MatsuriDayo/NekoBoxForAndroid/blob/1b022eb2f1d6a939531d8ccdc5b3fa5495f1a2ee/app/src/main/java/io/nekohasekai/sagernet/fmt/naive/NaiveFmt.kt#L69-L71
                // for naive, using IP as SNI name hardly happens
                // and host-resolver-rules cannot resolve the SNI problem
                // so do nothing
            }
        }
        if (DataStore.enableLog) {
            it["log"] = ""
        }
        if (insecureConcurrency > 0) {
            it["insecure-concurrency"] = insecureConcurrency
        }
        if (noPostQuantum) {
            it["no-post-quantum"] = true
        }
    }.toStringPretty()
}