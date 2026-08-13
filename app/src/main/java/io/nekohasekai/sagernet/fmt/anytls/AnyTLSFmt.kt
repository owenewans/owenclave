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

package io.nekohasekai.sagernet.fmt.anytls

import io.nekohasekai.sagernet.ktx.*
import libexclavecore.Libexclavecore

fun parseAnyTLS(url: String): AnyTLSBean {
    val link = Libexclavecore.parseURL(url)
    if (link.queryParameter("security") == "reality") {
        // We don't parse parameters which do not exist in the specification. v2rayN (?) and others
        // invented `security=reality` without changing the scheme. Ignoring `security=reality` and
        // parsing the link as normal AnyTLS will make users unable to connect to the server.
        // Explicitly ban `security=reality` from importing.
        error("anytls must use tls")
    }
    return AnyTLSBean().apply {
        name = link.fragment
        serverAddress = link.host
        serverPort = when {
            !link.hasPort() -> 443
            else -> link.port
        }
        password = link.username
        security = "tls"
        link.queryParameter("sni")?.also {
            sni = it
        }
        link.queryParameter("insecure")?.takeIf { it == "1" }?.also {
            allowInsecure = true
        }
    }
}

fun AnyTLSBean.toUri(): String? {
    if (security != "tls") {
        error("anytls must use tls")
    }
    val builder = Libexclavecore.newURL("anytls")
    builder.setHostPort(serverAddress, serverPort)
    if (password.isNotEmpty()) {
        builder.username = password
    }
    builder.rawPath = "/"
    if (sni.isNotEmpty()) {
        builder.addQueryParameter("sni", sni)
    }
    // as pinned certificate is not exportable, only add `insecure=1` if pinned certificate is not used
    if (pinnedPeerCertificateChainSha256.isEmpty() && pinnedPeerCertificatePublicKeySha256.isEmpty() &&
        pinnedPeerCertificateSha256.isEmpty() && serverNameToVerify.listByLineOrComma().isEmpty() && allowInsecure) {
        builder.addQueryParameter("insecure", "1")
    }
    if (name.isNotEmpty()) {
        builder.fragment = name
    }
    return builder.string
}
