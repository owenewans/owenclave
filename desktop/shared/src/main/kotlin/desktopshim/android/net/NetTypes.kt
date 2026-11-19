package android.net

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executor

class Uri private constructor(private val raw: String) {
    val scheme: String?
        get() = raw.substringBefore(":", "").takeIf { it.isNotEmpty() && raw.contains(":") }

    val authority: String?
        get() {
            val afterScheme = raw.substringAfter("://", "")
            if (afterScheme.isEmpty()) return null
            return afterScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        }

    val host: String?
        get() {
            val auth = authority ?: return null
            val hostPort = auth.substringAfterLast("@")
            return if (hostPort.startsWith("[")) {
                hostPort.substringBefore("]") + "]"
            } else {
                hostPort.substringBefore(":")
            }
        }

    val port: Int
        get() {
            val auth = authority ?: return -1
            val hostPort = auth.substringAfterLast("@")
            val portStr = if (hostPort.startsWith("[")) {
                hostPort.substringAfter("]:", "")
            } else {
                hostPort.substringAfter(":", "")
            }
            return portStr.toIntOrNull() ?: -1
        }

    val path: String?
        get() {
            val afterScheme = raw.substringAfter("://", "")
            if (afterScheme.isEmpty()) {
                val p = raw.substringAfter(":", "").substringBefore("?").substringBefore("#")
                return p.ifEmpty { null }
            }
            val slashIdx = afterScheme.indexOf('/')
            if (slashIdx < 0) return ""
            return afterScheme.substring(slashIdx).substringBefore("?").substringBefore("#")
        }

    val query: String?
        get() {
            if (!raw.contains("?")) return null
            return raw.substringAfter("?").substringBefore("#")
        }

    val fragment: String?
        get() {
            if (!raw.contains("#")) return null
            return URLDecoder.decode(raw.substringAfter("#"), Charsets.UTF_8)
        }

    val encodedFragment: String?
        get() = if (raw.contains("#")) raw.substringAfter("#") else null

    fun getQueryParameter(key: String): String? {
        val q = query ?: return null
        for (pair in q.split('&')) {
            if (pair.isEmpty()) continue
            val k = URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8)
            if (k == key) {
                val v = if (pair.contains('=')) pair.substringAfter('=') else ""
                return URLDecoder.decode(v, Charsets.UTF_8)
            }
        }
        return null
    }

    fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val v = getQueryParameter(key)?.lowercase() ?: return defaultValue
        return v != "false" && v != "0"
    }

    fun buildUpon(): Builder = Builder(this)

    fun toFileOrNull(): File? {
        return when (scheme) {
            "file" -> File(java.net.URI(raw))
            null -> File(raw)
            else -> File(raw.removePrefix("content://"))
        }
    }

    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    class Builder(base: Uri? = null) {
        private var scheme: String? = base?.scheme
        private var authority: String? = base?.authority
        private var path: String? = base?.path
        private val queryParams = ArrayList<Pair<String, String>>()
        private var fragment: String? = base?.fragment

        fun scheme(s: String?): Builder { scheme = s; return this }
        fun authority(a: String?): Builder { authority = a; return this }
        fun encodedAuthority(a: String?): Builder { authority = a; return this }
        fun path(p: String?): Builder { path = p; return this }
        fun appendPath(segment: String): Builder {
            val encoded = URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")
            path = (path?.trimEnd('/') ?: "") + "/" + encoded
            return this
        }
        fun appendQueryParameter(key: String, value: String?): Builder {
            val k = URLEncoder.encode(key, Charsets.UTF_8)
            val v = URLEncoder.encode(value.orEmpty(), Charsets.UTF_8)
            queryParams.add(k to v)
            return this
        }
        fun fragment(f: String?): Builder { fragment = f; return this }
        fun encodedFragment(f: String?): Builder { fragment = f; return this }

        fun build(): Uri {
            val sb = StringBuilder()
            if (!scheme.isNullOrEmpty()) {
                sb.append(scheme).append("://")
            }
            if (!authority.isNullOrEmpty()) {
                sb.append(authority)
            }
            if (!path.isNullOrEmpty()) {
                if (!path!!.startsWith("/")) sb.append("/")
                sb.append(path)
            }
            if (queryParams.isNotEmpty()) {
                sb.append("?")
                sb.append(queryParams.joinToString("&") { "${it.first}=${it.second}" })
            }
            if (!fragment.isNullOrEmpty()) {
                sb.append("#").append(fragment)
            }
            return Uri(sb.toString())
        }
    }

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())
    }
}

class Network {
    fun getAllByName(host: String): Array<InetAddress> = InetAddress.getAllByName(host)
}

class LinkAddress(val address: InetAddress, val prefixLength: Int)

class LinkProperties {
    val linkAddresses: List<LinkAddress> = emptyList()
}

class NetworkCapabilities {
    companion object {
        const val TRANSPORT_CELLULAR = 0
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_BLUETOOTH = 2
        const val TRANSPORT_ETHERNET = 3
        const val TRANSPORT_VPN = 4
        const val TRANSPORT_WIFI_AWARE = 5
        const val TRANSPORT_LOWPAN = 6
        const val TRANSPORT_USB = 8
        const val TRANSPORT_THREAD = 9
        const val TRANSPORT_SATELLITE = 10
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_NOT_RESTRICTED = 13
        const val NET_CAPABILITY_VALIDATED = 16
        const val NET_CAPABILITY_CAPTIVE_PORTAL = 17
    }

    val transportInfo: Any? = null
    fun hasTransport(transportType: Int): Boolean = transportType == TRANSPORT_ETHERNET
}

class NetworkRequest {
    class Builder {
        fun addCapability(capability: Int): Builder = this
        fun removeCapability(capability: Int): Builder = this
        fun build(): NetworkRequest = NetworkRequest()
    }
}

class ConnectivityManager {
    open class NetworkCallback(val flags: Int = 0) {
        companion object {
            const val FLAG_INCLUDE_LOCATION_INFO = 1
        }
        open fun onAvailable(network: Network) {}
        open fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {}
        open fun onLost(network: Network) {}
    }

    val activeNetwork: Network? = Network()
    fun getNetworkCapabilities(network: Network?): NetworkCapabilities? = NetworkCapabilities()
    fun getLinkProperties(network: Network?): LinkProperties? = LinkProperties()
    fun getConnectionOwnerUid(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int = -1
    fun registerBestMatchingNetworkCallback(request: NetworkRequest, callback: NetworkCallback, handler: Handler) {}
    fun requestNetwork(request: NetworkRequest, callback: NetworkCallback, handler: Handler) {}
    fun requestNetwork(request: NetworkRequest, callback: NetworkCallback) {}
    fun registerDefaultNetworkCallback(callback: NetworkCallback, handler: Handler) {}
    fun registerDefaultNetworkCallback(callback: NetworkCallback) {}
    fun unregisterNetworkCallback(callback: NetworkCallback) {}
}

class DnsResolver(context: Context? = null, looper: Any? = null) {
    companion object {
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        const val FLAG_NO_RETRY = 1

        private val singleton = DnsResolver()

        @JvmStatic
        fun getInstance(): DnsResolver = singleton

        private val IPV4_LITERAL = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        private val HEX_COLON_LITERAL = Regex("^[0-9a-fA-F:.]+$")

        private data class CacheKey(val domain: String, val qtype: Int)
        private data class CacheEntry(val addresses: List<InetAddress>, val createdAtMs: Long)

        private val dnsCache = java.util.concurrent.ConcurrentHashMap<CacheKey, CacheEntry>()
        private const val FRESH_TTL_MS = 60_000L
        private const val STALE_TTL_MS = 3_600_000L

        private val FALLBACK_UPSTREAMS = listOf(
            InetSocketAddress("9.9.9.9", 53),
            InetSocketAddress("1.1.1.1", 53),
            InetSocketAddress("8.8.8.8", 53),
        )

        private val TUNNEL_HIJACK_PUBLIC_DNS = setOf(
            "1.1.1.1",
            "1.0.0.1",
            "8.8.8.8",
            "8.8.4.4",
        )

        @Volatile
        private var physicalDnsOverride: List<InetSocketAddress>? = null

        @JvmStatic
        fun setPhysicalDnsOverride(ips: List<String>?) {
            physicalDnsOverride = ips
                ?.mapNotNull { raw -> parseLiteralIp(raw.trim())?.let { InetSocketAddress(it, 53) } }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
        }

        @JvmStatic
        fun snapshotPhysicalBypassDnsIps(): List<String> {
            val out = LinkedHashSet<String>()
            fun maybeAddIp(rawIp: String) {
                val addr = parseLiteralIp(rawIp.trim()) ?: return
                val text = addr.hostAddress?.substringBefore('%').orEmpty()
                if (!addr.isLoopbackAddress &&
                    !addr.isAnyLocalAddress &&
                    !addr.isLinkLocalAddress &&
                    !text.startsWith("198.18.") &&
                    !text.startsWith("198.19.") &&
                    text !in TUNNEL_HIJACK_PUBLIC_DNS
                ) {
                    out.add(text)
                }
            }
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("win")) {
                runCatching {
                    val ps = ProcessBuilder(
                        "powershell",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        "(Get-DnsClientServerAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | " +
                            "Where-Object { \$_.InterfaceAlias -notlike '*Owenclave*' -and \$_.InterfaceAlias -notlike '*wintun*' -and \$_.InterfaceAlias -notlike '*Loopback*' }).ServerAddresses",
                    ).redirectErrorStream(true).start()
                    val stdout = ps.inputStream.bufferedReader().use { it.readText() }
                    ps.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
                    stdout.lineSequence().forEach { maybeAddIp(it) }
                }
            } else {
                val resolvConf = File("/etc/resolv.conf")
                if (resolvConf.isFile) {
                    runCatching {
                        resolvConf.useLines { lines ->
                            for (raw in lines) {
                                val line = raw.substringBefore('#').trim()
                                if (!line.startsWith("nameserver")) continue
                                maybeAddIp(line.removePrefix("nameserver"))
                            }
                        }
                    }
                }
            }
            out.add("9.9.9.9")
            return out.toList()
        }

        /**
         * Resolves [rawDomain] directly over physical UDP/TCP DNS sockets (bypassing OS loopback
         * stubs such as `127.0.0.53` `systemd-resolved`, which route back into the active TUN
         * interface during profile reloads and cause recursive DNS deadlocks).
         *
         * Strictly bounded by [timeoutMs] per upstream socket so unreachable servers can never
         * stall `ProxyService` or `DesktopPrivilegedTun`.
         */
        @JvmStatic
        fun resolveDirect(
            rawDomain: String,
            nsType: Int? = null,
            timeoutMs: Int = 1_200,
            customServers: List<InetSocketAddress>? = null,
        ): List<InetAddress> {
            val cleaned = rawDomain.trim().removeSurrounding("[", "]").trimEnd('.').trim()
            if (cleaned.isEmpty()) return emptyList()

            if (cleaned.equals("localhost", ignoreCase = true)) {
                val loopbacks = listOf(
                    InetAddress.getByAddress("localhost", byteArrayOf(127, 0, 0, 1)),
                    InetAddress.getByAddress("localhost", ByteArray(16).apply { this[15] = 1 }),
                )
                return filterByType(loopbacks, nsType)
            }

            parseLiteralIp(cleaned)?.let { literal ->
                return filterByType(listOf(literal), nsType)
            }

            lookupHostsFile(cleaned)?.takeIf { it.isNotEmpty() }?.let { fromHosts ->
                return filterByType(fromHosts, nsType)
            }

            val qtypes = when (nsType) {
                TYPE_A -> listOf(TYPE_A)
                TYPE_AAAA -> listOf(TYPE_AAAA)
                else -> listOf(TYPE_A, TYPE_AAAA)
            }
            val servers = customServers ?: discoverNonLoopbackDnsServers()
            val now = System.currentTimeMillis()
            val results = ArrayList<InetAddress>()
            val staleFallback = ArrayList<InetAddress>()

            for (qt in qtypes) {
                val key = CacheKey(cleaned.lowercase(), qt)
                val cached = dnsCache[key]
                if (cached != null && now - cached.createdAtMs <= FRESH_TTL_MS) {
                    results.addAll(cached.addresses)
                    continue
                }
                if (cached != null && now - cached.createdAtMs <= STALE_TTL_MS) {
                    staleFallback.addAll(cached.addresses)
                }

                var resolvedForType: List<InetAddress>? = null
                for (server in servers) {
                    val resp = queryRfc1035(cleaned, qt, server, timeoutMs.coerceIn(200, 5_000))
                    if (resp != null) {
                        resolvedForType = resp.first
                        // Authoritative answer (including NXDOMAIN rcode=3 or NODATA rcode=0) stops fallback chain.
                        if (resp.first.isNotEmpty() || resp.second == 0 || resp.second == 3) {
                            break
                        }
                    }
                }
                if (!resolvedForType.isNullOrEmpty()) {
                    dnsCache[key] = CacheEntry(resolvedForType, System.currentTimeMillis())
                    results.addAll(resolvedForType)
                }
            }

            if (results.isNotEmpty()) return results
            if (staleFallback.isNotEmpty()) return staleFallback

            // Bounded fallback only for single-label LAN hostnames (e.g. mDNS/NetBIOS without dots).
            if (!cleaned.contains('.')) {
                val fut = java.util.concurrent.CompletableFuture.supplyAsync {
                    runCatching { InetAddress.getAllByName(cleaned).toList() }.getOrDefault(emptyList())
                }
                val fallback = runCatching {
                    fut.get(800L, java.util.concurrent.TimeUnit.MILLISECONDS)
                }.getOrDefault(emptyList())
                return filterByType(fallback, nsType)
            }

            return emptyList()
        }

        private fun filterByType(addrs: List<InetAddress>, nsType: Int?): List<InetAddress> = when (nsType) {
            TYPE_A -> addrs.filter { it.address.size == 4 }
            TYPE_AAAA -> addrs.filter { it.address.size == 16 }
            else -> addrs
        }

        private fun parseLiteralIp(host: String): InetAddress? {
            IPV4_LITERAL.matchEntire(host)?.let { m ->
                val bytes = ByteArray(4)
                for (i in 1..4) {
                    val v = m.groupValues[i].toIntOrNull() ?: return null
                    if (v !in 0..255) return null
                    bytes[i - 1] = v.toByte()
                }
                return runCatching { InetAddress.getByAddress(host, bytes) }.getOrNull()
            }
            val withoutZone = host.substringBefore('%')
            if (withoutZone.contains(':') && HEX_COLON_LITERAL.matches(withoutZone)) {
                return runCatching { InetAddress.getByName(withoutZone) }.getOrNull()
            }
            return null
        }

        private fun lookupHostsFile(domain: String): List<InetAddress>? {
            val os = System.getProperty("os.name", "").lowercase()
            val hostsFile = if (os.contains("win")) {
                File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\drivers\\etc\\hosts")
            } else {
                File("/etc/hosts")
            }
            if (!hostsFile.isFile) return null
            val out = ArrayList<InetAddress>()
            runCatching {
                hostsFile.useLines { lines ->
                    for (rawLine in lines) {
                        val line = rawLine.substringBefore('#').trim()
                        if (line.isEmpty()) continue
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size < 2) continue
                        if (parts.drop(1).any { it.equals(domain, ignoreCase = true) }) {
                            parseLiteralIp(parts[0])?.let { out.add(it) }
                        }
                    }
                }
            }
            return out.takeIf { it.isNotEmpty() }
        }

        private fun discoverNonLoopbackDnsServers(): List<InetSocketAddress> {
            physicalDnsOverride?.let { overrideList ->
                if (overrideList.isNotEmpty()) return overrideList
            }
            val servers = LinkedHashSet<InetSocketAddress>()
            val resolvConf = File("/etc/resolv.conf")
            if (resolvConf.isFile) {
                runCatching {
                    resolvConf.useLines { lines ->
                        for (raw in lines) {
                            val line = raw.substringBefore('#').trim()
                            if (!line.startsWith("nameserver")) continue
                            val ipStr = line.removePrefix("nameserver").trim()
                            val addr = parseLiteralIp(ipStr) ?: continue
                            val text = addr.hostAddress.orEmpty()
                            if (!addr.isLoopbackAddress &&
                                !addr.isAnyLocalAddress &&
                                !text.startsWith("198.18.") &&
                                !text.startsWith("198.19.")
                            ) {
                                servers.add(InetSocketAddress(addr, 53))
                            }
                        }
                    }
                }
            }
            servers.addAll(FALLBACK_UPSTREAMS)
            return servers.toList()
        }

        internal fun buildDnsQueryPacket(domain: String, qtype: Int, txId: Int): ByteArray? {
            val labels = domain.trimEnd('.').split('.')
            if (labels.isEmpty() || labels.any { it.isEmpty() || it.length > 63 }) return null
            val out = java.io.ByteArrayOutputStream(64)
            out.write((txId ushr 8) and 0xFF)
            out.write(txId and 0xFF)
            out.write(0x01) // RD = 1
            out.write(0x00)
            out.write(0x00); out.write(0x01) // QDCOUNT = 1
            out.write(0x00); out.write(0x00) // ANCOUNT = 0
            out.write(0x00); out.write(0x00) // NSCOUNT = 0
            out.write(0x00); out.write(0x00) // ARCOUNT = 0
            for (label in labels) {
                val ascii = java.net.IDN.toASCII(label).toByteArray(Charsets.US_ASCII)
                if (ascii.isEmpty() || ascii.size > 63) return null
                out.write(ascii.size)
                out.write(ascii)
            }
            out.write(0)
            out.write((qtype ushr 8) and 0xFF)
            out.write(qtype and 0xFF)
            out.write(0x00); out.write(0x01) // QCLASS = IN
            return out.toByteArray()
        }

        internal fun parseDnsResponsePacket(
            domain: String,
            expectedTxId: Int,
            expectedType: Int,
            buf: ByteArray,
            len: Int,
        ): Triple<List<InetAddress>, Int, Boolean>? {
            if (len < 12) return null
            fun u16(off: Int): Int = ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)
            val rxId = u16(0)
            if (rxId != (expectedTxId and 0xFFFF)) return null
            val flags = u16(2)
            if ((flags and 0x8000) == 0) return null // must be response
            val truncated = (flags and 0x0200) != 0
            val rcode = flags and 0x000F
            val qdCount = u16(4)
            val anCount = u16(6)

            fun skipName(start: Int): Int {
                var pos = start
                var jumps = 0
                while (pos < len) {
                    val b = buf[pos].toInt() and 0xFF
                    if (b == 0) return pos + 1
                    if ((b and 0xC0) == 0xC0) {
                        return if (pos + 1 < len) pos + 2 else -1
                    }
                    if ((b and 0xC0) != 0) return -1
                    pos += 1 + b
                    if (++jumps > 64) return -1
                }
                return -1
            }

            var offset = 12
            repeat(qdCount) {
                offset = skipName(offset)
                if (offset < 0 || offset + 4 > len) return null
                offset += 4
            }

            val out = ArrayList<InetAddress>()
            repeat(anCount) {
                offset = skipName(offset)
                if (offset < 0 || offset + 10 > len) return Triple(out, rcode, truncated)
                val rtype = u16(offset)
                val rclass = u16(offset + 2)
                val rdlen = u16(offset + 8)
                offset += 10
                if (offset + rdlen > len) return Triple(out, rcode, truncated)
                if (rclass == 1 && rtype == expectedType) {
                    if ((rtype == TYPE_A && rdlen == 4) || (rtype == TYPE_AAAA && rdlen == 16)) {
                        val rawIp = buf.copyOfRange(offset, offset + rdlen)
                        runCatching { InetAddress.getByAddress(domain, rawIp) }.getOrNull()?.let { out.add(it) }
                    }
                }
                offset += rdlen
            }
            return Triple(out, rcode, truncated)
        }

        private fun queryRfc1035(
            domain: String,
            qtype: Int,
            server: InetSocketAddress,
            timeoutMs: Int,
        ): Pair<List<InetAddress>, Int>? {
            val txId = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 65535)
            val query = buildDnsQueryPacket(domain, qtype, txId) ?: return null

            val udpResult = runCatching {
                DatagramSocket().use { socket ->
                    socket.soTimeout = timeoutMs
                    socket.send(DatagramPacket(query, query.size, server))
                    val rxBuf = ByteArray(4096)
                    val packet = DatagramPacket(rxBuf, rxBuf.size)
                    socket.receive(packet)
                    parseDnsResponsePacket(domain, txId, qtype, rxBuf, packet.length)
                }
            }.getOrNull()

            if (udpResult != null && !udpResult.third) {
                return udpResult.first to udpResult.second
            }

            // Retry over TCP if UDP was truncated (TC=1) or blocked.
            if (udpResult?.third == true) {
                return runCatching {
                    java.net.Socket().use { tcp ->
                        tcp.connect(server, timeoutMs)
                        tcp.soTimeout = timeoutMs
                        val out = tcp.getOutputStream()
                        out.write((query.size ushr 8) and 0xFF)
                        out.write(query.size and 0xFF)
                        out.write(query)
                        out.flush()
                        val inp = java.io.DataInputStream(tcp.getInputStream())
                        val respLen = inp.readUnsignedShort()
                        val rxBuf = ByteArray(respLen)
                        inp.readFully(rxBuf)
                        val parsed = parseDnsResponsePacket(domain, txId, qtype, rxBuf, respLen)
                        parsed?.let { it.first to it.second }
                    }
                }.getOrNull()
            }

            return null
        }
    }

    class DnsException(val code: Int, cause: Throwable?) : Exception(cause)

    interface Callback<T> {
        fun onAnswer(answer: T, rcode: Int)
        fun onError(error: DnsException)
    }

    fun query(
        network: Network?,
        domain: String,
        nsType: Int,
        flags: Int,
        executor: Executor,
        cancellationSignal: CancellationSignal?,
        callback: Callback<in Collection<InetAddress>>,
    ) {
        executor.execute {
            try {
                val all = resolveDirect(domain, nsType = nsType, timeoutMs = 1_500)
                callback.onAnswer(all, 0)
            } catch (e: Exception) {
                callback.onError(DnsException(0, e))
            }
        }
    }

    fun query(
        network: Network?,
        domain: String,
        flags: Int,
        executor: Executor,
        cancellationSignal: CancellationSignal?,
        callback: Callback<in Collection<InetAddress>>,
    ) {
        executor.execute {
            try {
                val all = resolveDirect(domain, nsType = null, timeoutMs = 1_500)
                callback.onAnswer(all, 0)
            } catch (e: Exception) {
                callback.onError(DnsException(0, e))
            }
        }
    }

    fun rawQuery(
        network: Network?,
        query: ByteArray,
        flags: Int,
        executor: Executor,
        cancellationSignal: CancellationSignal?,
        callback: Callback<in ByteArray>,
    ) {
        executor.execute {
            try {
                val servers = discoverNonLoopbackDnsServers()
                var lastErr: Exception? = null
                for (server in servers) {
                    try {
                        DatagramSocket().use { socket ->
                            socket.soTimeout = 1_500
                            socket.send(DatagramPacket(query, query.size, server))
                            val buf = ByteArray(4096)
                            val packet = DatagramPacket(buf, buf.size)
                            socket.receive(packet)
                            callback.onAnswer(buf.copyOf(packet.length), 0)
                            return@execute
                        }
                    } catch (e: Exception) {
                        lastErr = e
                    }
                }
                throw lastErr ?: IllegalStateException("No DNS server reachable")
            } catch (e: Exception) {
                callback.onError(DnsException(0, e))
            }
        }
    }
}

open class VpnService : Service() {
    companion object {
        const val SERVICE_INTERFACE = "android.net.VpnService"
        @JvmStatic
        fun prepare(context: Context): Intent? = null
    }
    open fun protect(socket: Int): Boolean = true

    open inner class Builder {
        fun setSession(session: String): Builder = this
        fun setConfigureIntent(intent: PendingIntent): Builder = this
        fun setMtu(mtu: Int): Builder = this
        fun addAddress(address: String, prefixLength: Int): Builder = this
        fun addDnsServer(address: String): Builder = this
        fun addRoute(address: String, prefixLength: Int): Builder = this
        fun addDisallowedApplication(packageName: String): Builder = this
        fun addAllowedApplication(packageName: String): Builder = this
        fun setMetered(isMetered: Boolean): Builder = this
        fun allowBypass(): Builder = this
        fun establish(): ParcelFileDescriptor? = null
    }
}

