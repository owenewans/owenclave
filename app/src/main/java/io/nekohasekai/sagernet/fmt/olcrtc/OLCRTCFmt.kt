package io.nekohasekai.sagernet.fmt.olcrtc

import java.net.URLDecoder
import java.net.URLEncoder

fun parseOLCRTCLink(link: String): OLCRTCBean {
    val bean = OLCRTCBean()
    val uri = link.removePrefix("olcrtc://")

    val hashIdx = uri.indexOf('#')
    val encKey = if (hashIdx >= 0) {
        uri.substring(hashIdx + 1).substringBefore('$')
    } else ""
    val rest = if (hashIdx >= 0) uri.substring(0, hashIdx) else uri

    val atIdx = rest.indexOf('@')
    val roomId = if (atIdx >= 0) rest.substring(atIdx + 1) else ""
    val authAndTransport = if (atIdx >= 0) rest.substring(0, atIdx) else rest

    val qIdx = authAndTransport.indexOf('?')
    val auth = if (qIdx >= 0) authAndTransport.substring(0, qIdx) else authAndTransport
    val transport = if (qIdx >= 0) {
        val afterQ = authAndTransport.substring(qIdx + 1)
        val ltIdx = afterQ.indexOf('<')
        if (ltIdx >= 0) afterQ.substring(0, ltIdx) else afterQ
    } else "datachannel"

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
    return sb.toString()
}
