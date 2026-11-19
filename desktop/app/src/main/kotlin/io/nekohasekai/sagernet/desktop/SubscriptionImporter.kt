package io.nekohasekai.sagernet.desktop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.group.parseClashProxies
import io.nekohasekai.sagernet.group.parseSingBoxOutbound
import io.nekohasekai.sagernet.group.parseV2Ray5Outbound
import io.nekohasekai.sagernet.group.parseV2RayOutbound
import io.nekohasekai.sagernet.ktx.HappLink
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.decodeBase64
import io.nekohasekai.sagernet.ktx.parseShareLinks
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Subscription import for the desktop client.
 *
 * Parsing itself is the shared Android code (`ktx/Formats.kt` for share links and
 * the `group` parsers for Clash YAML / V2Ray / sing-box documents); only the
 * document level walking and the HTTP download are desktop specific, because the
 * Android `RawUpdater` is tied to Room, WorkManager and the app context.
 *
 * Downloading tries, in order: through the running core (when a profile is
 * connected, which also gets around local network filtering), a JDK HTTP client
 * pinned to HTTP/1.1 (subscription servers behind CDNs often break the HTTP/2
 * upgrade) and finally the classic `HttpURLConnection`. All failures are reported
 * together so the UI can show a real reason instead of "java http error".
 */
object SubscriptionImporter {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    private val userAgent: String get() = "Owenclave/${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME}"

    /**
     * Downloads [url].
     *
     * @param socksPort local SOCKS port of a connected core; when set the request
     *   is sent through the tunnel.
     * @param headers extra request headers, used for HWID reporting.
     * @param customUserAgent optional User-Agent override (e.g. Mihomo mode).
     */
    fun download(
        url: String,
        socksPort: Int? = null,
        headers: Map<String, String> = emptyMap(),
        customUserAgent: String? = null,
    ): String {
        val resolvedUrl = resolveHappUrl(url.trim())
        if (!resolvedUrl.startsWith("http://", ignoreCase = true) &&
            !resolvedUrl.startsWith("https://", ignoreCase = true)
        ) {
            return resolvedUrl
        }
        val ua = customUserAgent?.takeIf { it.isNotBlank() } ?: userAgent
        val problems = ArrayList<String>()
        if (socksPort != null) {
            runCatching { fetchWithUrlConnection(resolvedUrl, socksPort, headers, ua) }
                .onSuccess { return it }
                .onFailure { problems += "via the connected profile: ${describe(it)}" }
        }
        runCatching { fetchWithHttpClient(resolvedUrl, headers, ua) }
            .onSuccess { return it }
            .onFailure { problems += "http/1.1: ${describe(it)}" }
        runCatching { fetchWithUrlConnection(resolvedUrl, null, headers, ua) }
            .onSuccess { return it }
            .onFailure { problems += "direct: ${describe(it)}" }
        throw IOException("cannot download the subscription: ${problems.joinToString("; ")}")
    }

    private fun resolveHappUrl(url: String): String {
        if (!HappLink.isHappCryptLink(url)) return url
        return HappLink.decrypt(url) ?: throw IOException("failed to decrypt happ:// link")
    }

    /** Flattens an exception chain so the reason is visible in the UI. */
    fun describe(error: Throwable): String {
        val parts = ArrayList<String>()
        var current: Throwable? = error
        while (current != null && parts.size < 4) {
            val item = current
            parts += buildString {
                append(item.javaClass.simpleName)
                item.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            }
            current = item.cause?.takeIf { it !== item }
        }
        return parts.joinToString(" <- ")
    }

    private fun fetchWithHttpClient(url: String, headers: Map<String, String>, ua: String): String {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", ua)
            .header("Accept", "*/*")
        headers.forEach { (key, value) -> builder.header(key, value) }
        val response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        requireSuccess(response.statusCode())
        return decodeBody(response.body(), response.headers().firstValue("content-encoding").orElse(""))
    }

    private fun fetchWithUrlConnection(url: String, socksPort: Int?, headers: Map<String, String>, ua: String): String {
        val connection = (if (socksPort != null) {
            URL(url).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
        } else {
            URL(url).openConnection()
        }) as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", ua)
        connection.setRequestProperty("Accept", "*/*")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return try {
            val code = connection.responseCode
            requireSuccess(code)
            decodeBody(connection.inputStream.readBytes(), connection.getHeaderField("Content-Encoding").orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun requireSuccess(code: Int) {
        if (code !in 200..299) {
            throw IOException(
                "the server answered HTTP $code" +
                    if (code in 300..399) " (redirect was not followed)" else ""
            )
        }
    }

    private fun decodeBody(body: ByteArray, encoding: String): String {
        val bytes = when {
            encoding.contains("gzip", ignoreCase = true) ->
                GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
            encoding.contains("deflate", ignoreCase = true) ->
                InflaterInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
            else -> body
        }
        return String(bytes, Charsets.UTF_8)
    }

    /** Parses share links, Clash YAML, V2Ray JSON, sing-box JSON and V2Ray v5 JSON. */
    fun parse(text: String): List<AbstractBean> = parseRaw(text).map { it.applyDefaultValues() }

    private fun parseRaw(text: String): List<AbstractBean> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (HappLink.isHappCryptLink(trimmed)) {
            val decrypted = HappLink.decrypt(trimmed)
            if (decrypted != null && decrypted != trimmed) {
                return parseRaw(decrypted)
            }
        }

        // Delegate to the shared Android RawUpdater parser first (Clash YAML with
        // YAMLConstructor, V2Ray v4/v5, sing-box outbounds & endpoints, SIP008,
        // WireGuard .conf, base64 and plain share links).
        runCatching { RawUpdater.parseRaw(text) }
            .onFailure { Logs.d("shared RawUpdater.parseRaw failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        runCatching { parseShareLinks(text) }
            .onFailure { Logs.d("share link parse failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching { parseJsonDocument(trimmed) }
                .onFailure { Logs.d("json parse failed: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        runCatching { parseYamlDocument(trimmed) }
            .onFailure { Logs.d("yaml parse failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Most subscription servers return the share link list base64 encoded; the
        // Android `RawUpdater.parseRaw` decodes the whole document the same way. This
        // runs last so a readable document is never misread as base64.
        runCatching { parseShareLinks(text.decodeBase64()) }
            .onFailure { Logs.d("base64 share link parse failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return emptyList()
    }

    private fun parseJsonDocument(text: String): List<AbstractBean> {
        val root = JsonParser.parseString(text)
        val beans = ArrayList<AbstractBean>()
        val objects = ArrayList<JsonObject>()
        when {
            root.isJsonArray -> root.asJsonArray.forEach { if (it.isJsonObject) objects.add(it.asJsonObject) }
            root.isJsonObject -> {
                val json = root.asJsonObject
                listOf("outbounds", "endpoints", "proxies").forEach { key ->
                    json.getAsJsonArray(key)?.forEach { if (it.isJsonObject) objects.add(it.asJsonObject) }
                }
                if (objects.isEmpty()) objects.add(json)
            }
        }
        for (outbound in objects) {
            if (outbound.has("tag") && outbound.get("tag").isJsonPrimitive &&
                outbound.get("tag").asString in listOf("direct", "block", "dns", "dns-out", "api")
            ) {
                continue
            }
            for (parser in listOf(::parseV2RayOutbound, ::parseSingBoxOutbound, ::parseV2Ray5Outbound)) {
                val parsed = runCatching { parser(outbound) }.getOrNull()
                if (!parsed.isNullOrEmpty()) {
                    beans.addAll(parsed)
                    break
                }
            }
        }
        return beans
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlDocument(text: String): List<AbstractBean> {
        val document = Yaml().load<Any?>(text) as? Map<String, Any?> ?: return emptyList()
        val proxies = document["proxies"] as? List<Any?> ?: return emptyList()
        val typed = proxies.filterIsInstance<Map<String, Any?>>()
        if (typed.isEmpty()) return emptyList()
        return parseClashProxies(typed)
    }

}
