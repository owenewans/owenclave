package io.nekohasekai.sagernet.fmt.olcrtc

import java.net.URLDecoder
import java.net.URLEncoder

fun parseOLCRTCLink(link: String): OLCRTCBean {
    val bean = OLCRTCBean()
    val uri = link.removePrefix("olcrtc://")

    // Extract MIMO/name after '$'
    val dollarIdx = uri.indexOf('$')
    val name = if (dollarIdx >= 0) URLDecoder.decode(uri.substring(dollarIdx + 1), "UTF-8") else ""
    val beforeDollar = if (dollarIdx >= 0) uri.substring(0, dollarIdx) else uri

    // Extract encryptionKey after '#'
    val hashIdx = beforeDollar.indexOf('#')
    val encKey = if (hashIdx >= 0) {
        beforeDollar.substring(hashIdx + 1)
    } else ""
    val rest = if (hashIdx >= 0) beforeDollar.substring(0, hashIdx) else beforeDollar

    // Extract roomId after '@'
    val atIdx = rest.indexOf('@')
    val roomId = if (atIdx >= 0) rest.substring(atIdx + 1) else ""
    val authAndTransport = if (atIdx >= 0) rest.substring(0, atIdx) else rest

    // Extract transport after '?'
    val qIdx = authAndTransport.indexOf('?')
    val auth = if (qIdx >= 0) authAndTransport.substring(0, qIdx) else authAndTransport
    val transport = if (qIdx >= 0) {
        val afterQ = authAndTransport.substring(qIdx + 1)
        val ltIdx = afterQ.indexOf('<')
        if (ltIdx >= 0) afterQ.substring(0, ltIdx) else afterQ
    } else "datachannel"

    bean.name = name
    bean.authProvider = URLDecoder.decode(auth, "UTF-8")
    bean.transport = URLDecoder.decode(transport, "UTF-8")
    bean.roomId = URLDecoder.decode(roomId, "UTF-8")
    bean.encryptionKey = encKey

    return bean
}

fun OLCRTCBean.toUri(): String {
    val sb = StringBuilder("olcrtc://")
    sb.append(URLEncoder.encode(authProvider, "UTF-8"))
    sb.append("?")
    sb.append(URLEncoder.encode(transport, "UTF-8"))
    sb.append("@")
    sb.append(URLEncoder.encode(roomId, "UTF-8"))
    sb.append("#")
    sb.append(encryptionKey)
    if (name != null && name.isNotEmpty()) {
        sb.append("$")
        sb.append(URLEncoder.encode(name, "UTF-8"))
    }
    return sb.toString()
}
