package io.nekohasekai.sagernet.desktop.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.desktop.RoutingRule
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Mihomo / Clash.Meta compatible REST controller + Owenclave Web Management UI
 * HTTP server (`external-controller`).
 */
class WebControllerServer(
    private val engine: DaemonEngine,
) {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private var httpServer: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(16) { r ->
        Thread(r, "owenclave-web").apply { isDaemon = true }
    }

    val boundPort: Int
        get() = httpServer?.address?.port ?: 0

    fun start() {
        val (host, port) = parseHostPort(engine.config.externalController)
        val server = HttpServer.create(InetSocketAddress(host, port), 64)
        server.executor = executor
        server.createContext("/") { exchange ->
            runCatching { handle(exchange) }.onFailure { err ->
                runCatching {
                    sendJson(
                        exchange,
                        500,
                        JsonObject().apply { addProperty("message", err.message ?: "internal error") },
                    )
                }
            }
        }
        server.start()
        httpServer = server
        println("[controller] listening on http://$host:${server.address.port} (Web UI: http://$host:${server.address.port}/ui/)")
    }

    fun stop() {
        httpServer?.stop(0)
        httpServer = null
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        addCommonHeaders(exchange)
        val rawMethod = exchange.requestMethod.uppercase()
        if (rawMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }
        val method = if (rawMethod == "HEAD") "GET" else rawMethod

        val rawPath = exchange.requestURI.rawPath ?: "/"
        val query = parseQuery(exchange.requestURI.rawQuery)

        // Unauthenticated container health endpoint (exposes zero sensitive config)
        if (method == "GET" && rawPath == "/healthz") {
            val ok = engine.isCoreRunning()
            sendJson(
                exchange,
                if (ok) 200 else 503,
                JsonObject().apply {
                    addProperty("ok", ok)
                    addProperty("coreRunning", ok)
                },
            )
            return
        }

        // Web UI static HTML shell & bundled assets (Google Sans Flex variable font + SVG favicon)
        if (method == "GET" && rawPath == "/ui") {
            redirect(exchange, "/ui/")
            return
        }
        if (method == "GET" && (rawPath == "/ui/" || rawPath == "/ui/index.html")) {
            sendHtml(exchange, 200, WebDashboardHtml.INDEX_HTML)
            return
        }
        if (method == "GET" && (rawPath == "/ui/favicon.svg" || rawPath == "/favicon.ico")) {
            val svgBytes = WebDashboardHtml.FAVICON_SVG.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "image/svg+xml; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "public, max-age=86400")
            exchange.sendResponseHeaders(200, svgBytes.size.toLong())
            exchange.responseBody.use { it.write(svgBytes) }
            return
        }
        if (method == "GET" && rawPath == "/ui/fonts/google-sans-flex.ttf") {
            val fontBytes = javaClass.classLoader
                ?.getResourceAsStream("android_res/font/google_sans_flex.ttf")
                ?.use { it.readBytes() }
            if (fontBytes != null) {
                exchange.responseHeaders.add("Content-Type", "font/ttf")
                exchange.responseHeaders.add("Cache-Control", "public, max-age=86400")
                exchange.sendResponseHeaders(200, fontBytes.size.toLong())
                exchange.responseBody.use { it.write(fontBytes) }
                return
            }
        }

        // Browser navigation to root `/` redirects to `/ui/`
        if (method == "GET" && rawPath == "/") {
            val accept = exchange.requestHeaders.getFirst("Accept").orEmpty()
            val hasAuth = !exchange.requestHeaders.getFirst("Authorization").isNullOrBlank() ||
                query.containsKey("token") || query.containsKey("secret")
            if ("text/html" in accept && !hasAuth) {
                redirect(exchange, "/ui/")
                return
            }
        }

        // Enforce constant-time Bearer / query token check on all controller API routes
        if (!isAuthorized(exchange, query)) {
            sendJson(exchange, 401, JsonObject().apply { addProperty("message", "Unauthorized") })
            return
        }

        val segments = rawPath.trim('/').takeIf { it.isNotEmpty() }
            ?.split('/')
            ?.map { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            .orEmpty()

        when {
            segments.isEmpty() && method == "GET" -> {
                sendJson(
                    exchange,
                    200,
                    JsonObject().apply {
                        addProperty("hello", "mihomo")
                        addProperty("service", "owenclave")
                        addProperty("version", BuildConfig.VERSION_NAME)
                    },
                )
            }

            segments == listOf("version") && method == "GET" -> {
                sendJson(
                    exchange,
                    200,
                    JsonObject().apply {
                        addProperty("meta", true)
                        addProperty("premium", true)
                        addProperty("version", "Owenclave ${BuildConfig.VERSION_NAME}")
                    },
                )
            }

            segments == listOf("configs") && method == "GET" -> {
                sendJson(exchange, 200, buildConfigsJson())
            }

            segments == listOf("configs") && (method == "PATCH" || method == "PUT") -> {
                val body = readJsonBody(exchange)
                if (method == "PUT" && query["force"] == "true" && body.size() == 0) {
                    engine.reload()
                } else {
                    engine.patchConfig(body)
                }
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }

            segments == listOf("proxies") && method == "GET" -> {
                sendJson(exchange, 200, JsonObject().apply { add("proxies", buildMihomoProxiesMap()) })
            }

            segments.size == 2 && segments[0] == "proxies" && method == "GET" -> {
                val name = segments[1]
                val entry = buildMihomoProxiesMap().getAsJsonObject(name)
                if (entry == null) {
                    sendJson(exchange, 404, JsonObject().apply { addProperty("message", "Proxy not found") })
                } else {
                    sendJson(exchange, 200, entry)
                }
            }

            segments.size == 2 && segments[0] == "proxies" && method == "PUT" -> {
                val groupName = segments[1]
                val body = readJsonBody(exchange)
                val choice = body.get("name")?.asString?.trim().orEmpty()
                if (choice.isEmpty() || !engine.selectInGroup(groupName, choice)) {
                    sendJson(
                        exchange,
                        400,
                        JsonObject().apply { addProperty("message", "Invalid group '$groupName' or proxy '$choice'") },
                    )
                } else {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                }
            }

            segments.size == 3 && segments[0] == "proxies" && segments[2] == "delay" && method == "GET" -> {
                val name = segments[1]
                val testUrl = query["url"]?.takeIf { it.isNotBlank() } ?: DaemonEngine.DEFAULT_DELAY_URL
                val timeout = query["timeout"]?.toIntOrNull() ?: 5000
                runCatching { engine.measureDelay(name, testUrl, timeout) }
                    .onSuccess { delay ->
                        sendJson(exchange, 200, JsonObject().apply { addProperty("delay", delay) })
                    }
                    .onFailure { err ->
                        sendJson(
                            exchange,
                            504,
                            JsonObject().apply { addProperty("message", err.message ?: "Request timeout") },
                        )
                    }
            }

            segments == listOf("providers", "proxies") && method == "GET" -> {
                val providers = JsonObject()
                engine.subscriptionsList().forEach { sub ->
                    providers.add(
                        sub.displayName,
                        JsonObject().apply {
                            addProperty("name", sub.displayName)
                            addProperty("type", "Proxy")
                            addProperty("vehicleType", "HTTP")
                            addProperty("updatedAt", java.time.Instant.ofEpochMilli(sub.lastUpdated.coerceAtLeast(0L)).toString())
                        },
                    )
                }
                sendJson(exchange, 200, JsonObject().apply { add("providers", providers) })
            }

            segments.size == 3 && segments[0] == "providers" && segments[1] == "proxies" && method == "PUT" -> {
                val name = segments[2]
                runCatching { engine.refreshSubscription(name) }
                    .onSuccess {
                        exchange.sendResponseHeaders(204, -1)
                        exchange.close()
                    }
                    .onFailure { err ->
                        sendJson(exchange, 404, JsonObject().apply { addProperty("message", err.message ?: "Not found") })
                    }
            }

            segments == listOf("rules") && method == "GET" -> {
                val arr = JsonArray()
                engine.compiledRuleViews().forEach { r ->
                    arr.add(
                        JsonObject().apply {
                            addProperty("type", r.type)
                            addProperty("payload", r.payload)
                            addProperty("proxy", r.proxy)
                        },
                    )
                }
                sendJson(exchange, 200, JsonObject().apply { add("rules", arr) })
            }

            segments == listOf("connections") && method == "GET" -> {
                sendJson(
                    exchange,
                    200,
                    JsonObject().apply {
                        addProperty("downloadTotal", 0L)
                        addProperty("uploadTotal", 0L)
                        add("connections", JsonArray())
                    },
                )
            }

            segments == listOf("connections") && method == "DELETE" -> {
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }

            segments == listOf("traffic") && method == "GET" -> {
                sendJson(exchange, 200, JsonObject().apply {
                    addProperty("up", 0)
                    addProperty("down", 0)
                })
            }

            segments == listOf("logs") && method == "GET" -> {
                val arr = JsonArray()
                engine.recentLogs(200).forEach { item ->
                    arr.add(
                        JsonObject().apply {
                            addProperty("time", item.time)
                            addProperty("type", item.type)
                            addProperty("payload", item.payload)
                        },
                    )
                }
                sendJson(exchange, 200, JsonObject().apply { add("logs", arr) })
            }

            // ---------------- Owenclave Web Management API ----------------

            segments == listOf("api", "state") && method == "GET" -> {
                sendJson(exchange, 200, buildFullStateJson())
            }

            segments == listOf("api", "profiles", "http") && method == "POST" -> {
                val body = readJsonBody(exchange)
                val tls = body.get("tls")?.asBoolean == true ||
                    body.get("protocol")?.asString?.equals("https", ignoreCase = true) == true
                val host = body.get("host")?.asString.orEmpty()
                val port = body.get("port")?.asInt ?: if (tls) 443 else 8080
                val username = body.get("username")?.asString.orEmpty()
                val password = body.get("password")?.asString.orEmpty()
                val sni = body.get("sni")?.asString.orEmpty()
                val allowInsecure = body.get("allowInsecure")?.asBoolean == true
                val udpOverTcp = body.get("udpOverTcp")?.asBoolean == true
                val name = body.get("name")?.asString.orEmpty()
                val selectAfter = body.get("select")?.let { !it.isJsonNull && it.asBoolean } ?: true

                runCatching {
                    engine.addHttpProxy(
                        tls = tls,
                        host = host,
                        port = port,
                        username = username,
                        password = password,
                        sni = sni,
                        allowInsecure = allowInsecure,
                        udpOverTcp = udpOverTcp,
                        name = name,
                        selectAfter = selectAfter,
                    )
                }.onSuccess { profile ->
                    sendJson(
                        exchange,
                        201,
                        JsonObject().apply {
                            addProperty("id", profile.id)
                            addProperty("name", profile.displayName)
                            addProperty("protocol", profile.protocolName)
                            addProperty("address", profile.address)
                            DaemonEngine.shareUriOf(profile)?.let { addProperty("shareUri", it) }
                        },
                    )
                }.onFailure { err ->
                    sendJson(exchange, 400, JsonObject().apply { addProperty("message", err.message ?: "Invalid HTTP proxy") })
                }
            }

            segments == listOf("api", "profiles", "import") && method == "POST" -> {
                val body = readJsonBody(exchange)
                val text = body.get("text")?.asString.orEmpty()
                val raw = body.get("raw")?.asBoolean == true
                val name = body.get("name")?.asString.orEmpty()
                runCatching { engine.importProfiles(text, nameHint = name, keepAsRaw = raw) }
                    .onSuccess { list ->
                        sendJson(
                            exchange,
                            201,
                            JsonObject().apply {
                                addProperty("imported", list.size)
                            },
                        )
                    }
                    .onFailure { err ->
                        sendJson(exchange, 400, JsonObject().apply { addProperty("message", err.message ?: "Import failed") })
                    }
            }

            segments == listOf("api", "profiles", "select") && method == "POST" -> {
                val body = readJsonBody(exchange)
                val target = body.get("id")?.asString ?: body.get("name")?.asString.orEmpty()
                if (!engine.selectProfileByIdOrName(target)) {
                    sendJson(exchange, 404, JsonObject().apply { addProperty("message", "Profile not found") })
                } else {
                    sendJson(exchange, 200, JsonObject().apply { addProperty("ok", true) })
                }
            }

            segments.size == 3 && segments[0] == "api" && segments[1] == "profiles" && method == "DELETE" -> {
                if (!engine.removeProfile(segments[2])) {
                    sendJson(exchange, 404, JsonObject().apply { addProperty("message", "Manual profile not found") })
                } else {
                    sendJson(exchange, 200, JsonObject().apply { addProperty("ok", true) })
                }
            }

            segments == listOf("api", "subscriptions") && method == "POST" -> {
                val body = readJsonBody(exchange)
                val name = body.get("name")?.asString.orEmpty()
                val url = body.get("url")?.asString.orEmpty()
                val ua = body.get("userAgent")?.asString.orEmpty()
                val hwid = body.get("sendHwid")?.asBoolean == true
                runCatching { engine.addSubscription(name = name, url = url, userAgent = ua, sendHwid = hwid) }
                    .onSuccess { sub ->
                        sendJson(
                            exchange,
                            201,
                            JsonObject().apply {
                                addProperty("id", sub.id)
                                addProperty("name", sub.displayName)
                                addProperty("profileCount", sub.profileCount)
                            },
                        )
                    }
                    .onFailure { err ->
                        sendJson(exchange, 400, JsonObject().apply { addProperty("message", err.message ?: "Subscription error") })
                    }
            }

            segments == listOf("api", "subscriptions", "refresh-all") && method == "POST" -> {
                engine.refreshAllSubscriptions()
                sendJson(exchange, 200, JsonObject().apply { addProperty("ok", true) })
            }

            segments.size == 4 && segments[0] == "api" && segments[1] == "subscriptions" && segments[3] == "refresh" && method == "POST" -> {
                runCatching { engine.refreshSubscription(segments[2]) }
                    .onSuccess { sub ->
                        sendJson(exchange, 200, JsonObject().apply { addProperty("profileCount", sub.profileCount) })
                    }
                    .onFailure { err ->
                        sendJson(exchange, 400, JsonObject().apply { addProperty("message", err.message ?: "Refresh failed") })
                    }
            }

            segments.size == 3 && segments[0] == "api" && segments[1] == "subscriptions" && method == "DELETE" -> {
                if (!engine.removeSubscription(segments[2])) {
                    sendJson(exchange, 404, JsonObject().apply { addProperty("message", "Subscription not found") })
                } else {
                    sendJson(exchange, 200, JsonObject().apply { addProperty("ok", true) })
                }
            }

            segments == listOf("api", "rules") && method == "POST" -> {
                val body = readJsonBody(exchange)
                val rule = RoutingRule(
                    name = body.get("name")?.asString.orEmpty(),
                    domains = body.get("domains")?.asString.orEmpty(),
                    ip = body.get("ip")?.asString.orEmpty(),
                    port = body.get("port")?.asString.orEmpty(),
                    network = body.get("network")?.asString.orEmpty(),
                    target = body.get("target")?.asString?.lowercase()?.takeIf { it in setOf("proxy", "direct", "block") } ?: "proxy",
                )
                runCatching { engine.addUserRule(rule) }
                    .onSuccess { added -> sendJson(exchange, 201, added.toJson()) }
                    .onFailure { err ->
                        sendJson(exchange, 400, JsonObject().apply { addProperty("message", err.message ?: "Invalid rule") })
                    }
            }

            segments.size == 3 && segments[0] == "api" && segments[1] == "rules" && method == "DELETE" -> {
                if (!engine.removeUserRule(segments[2])) {
                    sendJson(exchange, 404, JsonObject().apply { addProperty("message", "Rule not found") })
                } else {
                    sendJson(exchange, 200, JsonObject().apply { addProperty("ok", true) })
                }
            }

            segments == listOf("api", "reload") && method == "POST" -> {
                engine.reload()
                sendJson(exchange, 200, JsonObject().apply { addProperty("ok", true) })
            }

            else -> {
                sendJson(exchange, 404, JsonObject().apply { addProperty("message", "Not found") })
            }
        }
    }

    private fun buildConfigsJson(): JsonObject = JsonObject().apply {
        addProperty("port", engine.config.httpPort)
        addProperty("socks-port", engine.config.socksPort)
        addProperty("redir-port", 0)
        addProperty("tproxy-port", engine.config.tproxyPort)
        addProperty("mixed-port", engine.config.mixedPort)
        addProperty("allow-lan", engine.config.allowLan)
        addProperty("bind-address", engine.config.effectiveListenAddress())
        addProperty("mode", engine.config.mode)
        addProperty("fallback-to-direct", engine.config.fallbackToDirect)
        addProperty("health-check-interval", engine.config.healthCheckInterval)
        addProperty("log-level", engine.config.logLevel)
        addProperty("ipv6", engine.config.ipv6)
        addProperty("interface-name", engine.config.interfaceName)
    }

    private fun buildMihomoProxiesMap(): JsonObject {
        val map = JsonObject()
        map.add(
            "DIRECT",
            JsonObject().apply {
                addProperty("name", "DIRECT")
                addProperty("type", "Direct")
                addProperty("udp", true)
                add("history", delayHistoryJson("DIRECT"))
            },
        )
        map.add(
            "REJECT",
            JsonObject().apply {
                addProperty("name", "REJECT")
                addProperty("type", "Reject")
                addProperty("udp", true)
                add("history", JsonArray())
            },
        )
        for (profile in engine.allProfiles()) {
            map.add(
                profile.displayName,
                JsonObject().apply {
                    addProperty("name", profile.displayName)
                    addProperty("type", DaemonEngine.mihomoTypeOf(profile))
                    addProperty("udp", true)
                    add("history", delayHistoryJson(profile.displayName))
                },
            )
        }
        for (group in engine.groupViews()) {
            map.add(
                group.name,
                JsonObject().apply {
                    addProperty("name", group.name)
                    addProperty("type", group.type)
                    addProperty("now", group.now)
                    addProperty("udp", true)
                    add("all", JsonArray().apply { group.all.forEach { add(it) } })
                    add("history", delayHistoryJson(group.name))
                },
            )
        }
        return map
    }

    private fun delayHistoryJson(name: String): JsonArray = JsonArray().apply {
        engine.delayHistoryOf(name).forEach { rec ->
            add(
                JsonObject().apply {
                    addProperty("time", rec.time)
                    addProperty("delay", rec.delay)
                },
            )
        }
    }

    private fun buildFullStateJson(): JsonObject {
        val active = engine.activeProfile()
        val profiles = engine.allProfiles()
        return JsonObject().apply {
            addProperty("version", BuildConfig.VERSION_NAME)
            addProperty("running", engine.isCoreRunning())
            addProperty("inFallback", engine.isInDirectFallback())
            addProperty("startedAt", engine.startedAtMillis)
            engine.lastCoreError()?.let { addProperty("lastError", it) }
            add("configs", buildConfigsJson())
            if (active != null) {
                add(
                    "activeProfile",
                    JsonObject().apply {
                        addProperty("id", active.id)
                        addProperty("name", active.displayName)
                        addProperty("protocol", active.protocolName)
                        addProperty("address", active.address)
                    },
                )
            }
            add(
                "groups",
                JsonArray().apply {
                    engine.groupViews().forEach { g ->
                        add(
                            JsonObject().apply {
                                addProperty("name", g.name)
                                addProperty("type", g.type)
                                addProperty("now", g.now)
                                add("all", JsonArray().apply { g.all.forEach { add(it) } })
                            },
                        )
                    }
                },
            )
            add(
                "profiles",
                JsonArray().apply {
                    profiles.forEach { p ->
                        val delays = engine.delayHistoryOf(p.displayName)
                        val healthState = when (engine.nodeHealthOf(p.id)) {
                            true -> "alive"
                            false -> "dead"
                            null -> "unknown"
                        }
                        add(
                            JsonObject().apply {
                                addProperty("id", p.id)
                                addProperty("name", p.displayName)
                                addProperty("protocol", p.protocolName)
                                addProperty("address", p.address)
                                addProperty("selected", active?.id == p.id)
                                addProperty("removable", p.subscriptionId == null)
                                addProperty("health", healthState)
                                p.subscriptionId?.let { addProperty("subscriptionId", it) }
                                delays.lastOrNull()?.let { addProperty("delay", it.delay) }
                                DaemonEngine.shareUriOf(p)?.let { addProperty("shareUri", it) }
                            },
                        )
                    }
                },
            )
            add(
                "subscriptions",
                JsonArray().apply {
                    engine.subscriptionsList().forEach { s ->
                        add(
                            JsonObject().apply {
                                addProperty("id", s.id)
                                addProperty("name", s.displayName)
                                addProperty("url", s.url)
                                addProperty("profileCount", s.profileCount)
                                addProperty("lastUpdated", s.lastUpdated)
                                s.lastError?.let { addProperty("lastError", it) }
                            },
                        )
                    }
                },
            )
            add(
                "rules",
                JsonArray().apply {
                    engine.compiledRuleViews().forEach { r ->
                        add(
                            JsonObject().apply {
                                addProperty("id", r.id)
                                addProperty("type", r.type)
                                addProperty("payload", r.payload)
                                addProperty("proxy", r.proxy)
                                addProperty("userDefined", r.userDefined)
                            },
                        )
                    }
                },
            )
            add(
                "logs",
                JsonArray().apply {
                    engine.recentLogs(150).forEach { l ->
                        add(
                            JsonObject().apply {
                                addProperty("time", l.time)
                                addProperty("type", l.type)
                                addProperty("payload", l.payload)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun isAuthorized(exchange: HttpExchange, query: Map<String, String>): Boolean {
        val expected = engine.config.secret
        if (expected.isEmpty()) {
            val method = exchange.requestMethod.uppercase()
            if (method !in setOf("GET", "HEAD", "OPTIONS")) {
                val origin = exchange.requestHeaders.getFirst("Origin")?.trim().orEmpty()
                if (origin.isNotEmpty()) {
                    val host = exchange.requestHeaders.getFirst("Host")?.trim().orEmpty()
                    val originHost = runCatching { java.net.URI(origin).authority.orEmpty() }.getOrDefault("")
                    if (originHost.isEmpty() || !originHost.equals(host, ignoreCase = true)) {
                        return false
                    }
                }
            }
            return true
        }

        val authHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty()
        val provided = when {
            authHeader.startsWith("Bearer ", ignoreCase = true) -> authHeader.substring(7).trim()
            query.containsKey("token") -> query.getValue("token")
            query.containsKey("secret") -> query.getValue("secret")
            else -> return false
        }
        return MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun addCommonHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            set("Access-Control-Allow-Origin", "*")
            set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
            set("Access-Control-Allow-Headers", "Authorization, Content-Type")
            set("X-Content-Type-Options", "nosniff")
        }
    }

    private fun redirect(exchange: HttpExchange, location: String) {
        exchange.responseHeaders.set("Location", location)
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }

    private fun sendHtml(exchange: HttpExchange, status: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.set("X-Frame-Options", "SAMEORIGIN")
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
            exchange.responseHeaders.set("Content-Length", bytes.size.toString())
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun sendJson(exchange: HttpExchange, status: Int, body: JsonObject) {
        val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
            exchange.responseHeaders.set("Content-Length", bytes.size.toString())
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun readJsonBody(exchange: HttpExchange): JsonObject {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        exchange.requestBody.use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BODY_BYTES) throw IllegalArgumentException("request body exceeds 2 MiB limit")
                out.write(buf, 0, n)
            }
        }
        val text = out.toString(StandardCharsets.UTF_8).trim()
        if (text.isEmpty()) return JsonObject()
        val parsed = JsonParser.parseString(text)
        return if (parsed.isJsonObject) parsed.asJsonObject else JsonObject()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq < 0) {
                map[URLDecoder.decode(pair, StandardCharsets.UTF_8)] = ""
            } else {
                val k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8)
                val v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8)
                map[k] = v
            }
        }
        return map
    }

    private fun parseHostPort(spec: String): Pair<String, Int> {
        val trimmed = spec.trim()
        val colon = trimmed.lastIndexOf(':')
        if (colon < 0) return "0.0.0.0" to (trimmed.toIntOrNull() ?: 9090)
        val host = trimmed.substring(0, colon).trim().removeSurrounding("[", "]").ifEmpty { "0.0.0.0" }
        val port = trimmed.substring(colon + 1).trim().toIntOrNull() ?: 9090
        return host to port
    }

    companion object {
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024
    }

}

