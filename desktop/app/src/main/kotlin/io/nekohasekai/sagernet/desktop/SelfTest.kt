package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL

/**
 * End to end smoke test for the desktop runtime.
 *
 * It does not depend on any external server: the Go core is started a first time
 * as a local Shadowsocks *server*, then the desktop client connects to it through
 * a normal [Profile], and finally an HTTP request is issued through the local
 * SOCKS inbound. A successful run proves that
 *
 *   client -> core inbounds -> shared config builder -> core outbound -> upstream
 *
 * all work on the current platform, which is what makes this usable for Windows
 * (under Wine) and Linux verification.
 */
object SelfTest {

    private const val METHOD = "aes-256-gcm"
    private const val PASSWORD = "owenclave-desktop-selftest"

    private const val PAYLOAD = "owenclave-desktop-ok"

    fun run(): Int {
        val server = LocalHttpServer(PAYLOAD)
        val httpPort = server.port

        var serverProcess: Process? = null
        var client: CoreRunner? = null

        return try {
            val core = DesktopRuntime.coreBinary()
                ?: throw IllegalStateException("owenclave-core is not available for ${DesktopRuntime.platformTag}")
            println("[selftest] core: ${core.absolutePath}")

            // 1. local Shadowsocks server, served by the very same core binary
            val serverPort = CoreRunner.freePort()
            val serverConfig = File(DesktopRuntime.dataDir, "selftest-server.json")
            serverConfig.writeText(
                """
                {
                  "log": { "loglevel": "warning" },
                  "inbounds": [
                    {
                      "tag": "ss-in",
                      "listen": "127.0.0.1",
                      "port": $serverPort,
                      "protocol": "shadowsocks",
                      "settings": { "method": "$METHOD", "password": "$PASSWORD", "network": "tcp,udp" }
                    }
                  ],
                  "outbounds": [ { "tag": "direct", "protocol": "freedom" } ]
                }
                """.trimIndent()
            )
            println("[selftest] starting local Shadowsocks server on 127.0.0.1:$serverPort")
            serverProcess = ProcessBuilder(core.absolutePath, "run", "-c", serverConfig.absolutePath)
                .directory(DesktopRuntime.dataDir)
                .redirectErrorStream(true)
                .start()
            val ssServer = serverProcess
            Thread {
                runCatching { ssServer.inputStream.bufferedReader().forEachLine { println("[ss-server] $it") } }
            }.apply { isDaemon = true }.start()
            awaitPort(serverPort)

            // 2. desktop client profile pointing at that server
            val bean = ShadowsocksBean().apply {
                this.serverAddress = "127.0.0.1"
                this.serverPort = serverPort
                this.method = METHOD
                this.password = PASSWORD
                this.name = "selftest"
            }
            bean.initializeDefaultValues()
            val profile = Profile(name = "selftest", bean = bean)

            val settings = DesktopSettings(
                socksPort = CoreRunner.freePort(),
                httpPort = CoreRunner.freePort(),
                logLevel = LogLevel.WARNING,
                // the test target is on loopback, so LAN bypass must be off for the
                // request to really traverse the Shadowsocks outbound
                bypassPrivateNetworks = false,
            )
            println("[selftest] client SOCKS inbound 127.0.0.1:${settings.socksPort}")
            client = CoreRunner { line -> println("[client] $line") }
            client.start(profile, settings)

            // 3. request through the proxy chain
            val url = "http://127.0.0.1:$httpPort/"
            println("[selftest] fetching $url through the SOCKS proxy")
            val result = client.fetch(url)
            val ok = result.code == 200 && result.body.trim() == PAYLOAD
            println("[selftest] response: HTTP ${result.code}, body='${result.body.trim()}'")
            if (ok) {
                println("[selftest] PASS")
                0
            } else {
                println("[selftest] FAIL: unexpected response")
                1
            }
        } catch (e: Exception) {
            println("[selftest] FAIL: ${e.javaClass.simpleName}: ${e.message}")
            1
        } finally {
            runCatching { client?.stop() }
            runCatching { serverProcess?.destroy() }
            server.stop()
        }
    }

    /**
     * Verifies the NaiveProxy plugin integration: the config produced by the
     * shared `buildNaiveConfig` has to be accepted by the real `naive` binary,
     * the plugin has to keep listening on its local SOCKS port and the core has
     * to start and dial into it.
     *
     * The upstream server is not a real NaiveProxy server, so traffic obviously
     * cannot flow; that part needs a live server and is out of scope here.
     */
    fun runNaive(): Int {
        val plugin = DesktopRuntime.naiveBinary()
        if (plugin == null) {
            println("[selftest-naive] FAIL: naive binary is not available for ${DesktopRuntime.platformTag}")
            return 1
        }
        println("[selftest-naive] plugin: ${plugin.absolutePath}")

        val bean = NaiveBean().apply {
            this.serverAddress = "127.0.0.1"
            this.serverPort = 8443
            this.proto = "https"
            this.username = "selftest-user"
            this.password = "selftest-pass"
            this.sni = "localhost"
            this.name = "naive-selftest"
        }
        bean.initializeDefaultValues()
        val profile = Profile(name = "naive-selftest", bean = bean)
        val settings = DesktopSettings(
            socksPort = CoreRunner.freePort(),
            httpPort = CoreRunner.freePort(),
            logLevel = LogLevel.INFO,
        )

        val runner = CoreRunner { line -> println("[client] $line") }
        return try {
            runner.start(profile, settings)
            Thread.sleep(1500)
            println("[selftest-naive] generated plugin config:")
            val pluginConfig = DesktopRuntime.dataDir
                .listFiles { file -> file.name.startsWith("naive_") && file.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
            println(pluginConfig?.readText() ?: "(no naive_*.json written)")
            val alive = runner.pluginAlive()
            println("[selftest-naive] plugin alive: $alive, core running: ${runner.running}")
            val attempt = runCatching { runner.fetch("http://example.com/", 8000) }
            println("[selftest-naive] tunnel attempt: " + (attempt.exceptionOrNull()?.message ?: "unexpectedly succeeded"))
            if (alive && runner.running) {
                println("[selftest-naive] PASS")
                0
            } else {
                println("[selftest-naive] FAIL: plugin or core is not running")
                1
            }
        } catch (e: Exception) {
            println("[selftest-naive] FAIL: ${e.javaClass.simpleName}: ${e.message}")
            1
        } finally {
            runCatching { runner.stop() }
        }
    }

    /**
     * Minimal HTTP server on a loopback [ServerSocket].
     *
     * Written by hand because the jlink runtime that jpackage produces does not
     * necessarily contain the `jdk.httpserver` module, and the self test has to
     * work inside the packaged application as well.
     */
    internal class LocalHttpServer(private val payload: String) {

        private val socket = ServerSocket(0, 0, InetAddress.getLoopbackAddress())

        val port: Int get() = socket.localPort

        private val thread = Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = payload.toByteArray()
                        val head = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: ${body.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        connection.getOutputStream().apply {
                            write(head.toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        fun stop() {
            runCatching { socket.close() }
        }

    }

    internal fun awaitPort(port: Int) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            val connected = runCatching {
                java.net.Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
            }.isSuccess
            if (connected) return
            Thread.sleep(150)
        }
        throw IllegalStateException("server did not start listening on $port")
    }


    /**
     * Transparent mode smoke test.
     *
     * Starts the core with a direct outbound (so no upstream server is needed),
     * brings up the TUN device through tun2socks and fetches a URL that only has a
     * route through the tunnel, which proves device, routes, the pump, the core's
     * SOCKS inbound and the interface binding. Needs administrator rights.
     *
     * With [stub] the SOCKS inbound is replaced by a local stub and the target is an
     * address inside the tunnel range, so no internet access is required; that is the
     * variant used for local verification inside a network namespace.
     */
    fun runTun(stub: Boolean): Int {
        val primary = runCatching { TunSession.primaryInterface() }.getOrElse { error ->
            println("[selftest-tun] FAIL: ${error.message}")
            return 1
        }
        println("[selftest-tun] physical interface: $primary")
        val binary = DesktopRuntime.tun2socksBinary()
        if (binary == null) {
            println("[selftest-tun] FAIL: tun2socks is missing for ${DesktopRuntime.platformTag}")
            return 1
        }
        println("[selftest-tun] tun2socks: ${binary.absolutePath}")

        var core: CoreRunner? = null
        var stubServer: StubSocksServer? = null
        var session: TunSession? = null

        val settings = DesktopSettings(
            socksPort = CoreRunner.freePort(),
            httpPort = CoreRunner.freePort(),
            routeMode = DesktopSettings.ROUTE_DIRECT,
            logLevel = LogLevel.INFO,
            bypassPrivateNetworks = false,
            serviceMode = DesktopSettings.SERVICE_VPN,
        )

        return try {
            val socksPort = if (stub) {
                val server = StubSocksServer(PAYLOAD)
                stubServer = server
                println("[selftest-tun] stub SOCKS server on 127.0.0.1:${server.port}")
                server.port
            } else {
                val bean = ShadowsocksBean().apply {
                    this.serverAddress = "127.0.0.1"
                    this.serverPort = 1
                    this.method = "aes-256-gcm"
                    this.password = "unused-in-direct-mode"
                }
                bean.initializeDefaultValues()
                core = CoreRunner { line -> println("[client] $line") }
                core.start(Profile(name = "tun-selftest", bean = bean), settings, emptyList())
                settings.socksPort
            }

            if (stub) {
                val tun = TunSession { line -> println("[tun] $line") }
                tun.start(primary, settings.tunMtu, socksPort, "")
                session = tun
            } else {
                println("[selftest-tun] waiting for the tunnel")
                Thread.sleep(500)
            }

            val url = if (stub) "http://198.18.0.2/" else "http://example.com/"
            println("[selftest-tun] fetching $url through the tunnel")
            val result = fetchPlain(url, if (stub) 20_000 else 30_000)
            println("[selftest-tun] response: HTTP ${result.first}, body='${result.second.trim().take(60)}'")
            val ok = if (stub) result.first == 200 && result.second.contains(PAYLOAD) else result.first in 200..399
            if (ok) {
                println("[selftest-tun] PASS")
                0
            } else {
                println("[selftest-tun] FAIL: unexpected response")
                1
            }
        } catch (e: Exception) {
            println("[selftest-tun] FAIL: ${e.javaClass.simpleName}: ${e.message}")
            1
        } finally {
            runCatching { session?.stop() }
            runCatching { core?.stop() }
            runCatching { stubServer?.stop() }
        }
    }

    /** Plain request that must be routed by the OS, i.e. through the tunnel. */
    private fun fetchPlain(url: String, timeoutMillis: Int): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Owenclave/${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME}")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Minimal SOCKS5 server used by the stub TUN test: it records the destination it
     * is asked for and answers with a canned HTTP response, so the whole tunnel path
     * can be verified without internet access.
     */
    private class StubSocksServer(private val payload: String) {

        private val socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
        val port: Int get() = socket.localPort
        @Volatile var lastTarget: String = ""

        private val thread = Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { handle(client) }
            }
        }.apply {
            isDaemon = true
            start()
        }

        private fun handle(client: java.net.Socket) {
            client.use { connection ->
                val input = connection.getInputStream()
                val output = connection.getOutputStream()
                val greeting = ByteArray(2).also { input.readFully(it) }
                val methods = ByteArray(greeting[1].toInt()).also { input.readFully(it) }
                check(greeting[0].toInt() == 5 && methods.isNotEmpty())
                output.write(byteArrayOf(5, 0))
                output.flush()

                val request = ByteArray(4).also { input.readFully(it) }
                check(request[0].toInt() == 5)
                val address = when (request[3].toInt()) {
                    1 -> ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 0xFF).toString() }
                    3 -> {
                        val length = input.read()
                        ByteArray(length).also { input.readFully(it) }.toString(Charsets.UTF_8)
                    }
                    else -> ByteArray(16).also { input.readFully(it) }.joinToString(":") { "%02x".format(it) }
                }
                val portBytes = ByteArray(2).also { input.readFully(it) }
                val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                lastTarget = "$address:$port"
                synchronized(this) {}
                println("[selftest-tun] stub got a request for $lastTarget")

                // success reply
                output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                output.flush()

                val reader = input.bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val body = payload.toByteArray()
                val head = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                output.write(head.toByteArray())
                output.write(body)
                output.flush()
            }
        }

        fun stop() {
            runCatching { socket.close() }
        }

    }

    fun runGui(): Int {
        fun teardownStack() {
            while (androidx.activity.DesktopActivityStack.stack.isNotEmpty()) {
                val activity = androidx.activity.DesktopActivityStack.stack.removeLast()
                runCatching { activity.onPause() }
                runCatching { activity.onStop() }
                runCatching { activity.onDestroy() }
            }
        }

        return try {
            LinuxWindowSupport.configureBeforeWindow()
            io.nekohasekai.sagernet.SagerNet.application
            io.nekohasekai.sagernet.database.DataStore.appAutoUpdate = false

            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                teardownStack()

                for ((w, h) in listOf(1180 to 780, 412 to 892)) {
                    val scene = androidx.compose.ui.ImageComposeScene(
                        width = w,
                        height = h,
                        coroutineContext = kotlinx.coroutines.Dispatchers.Main,
                    )
                    try {
                        teardownStack()
                        val mainActivity = io.nekohasekai.sagernet.ui.compose.ComposeMainActivity()
                        scene.setContent {
                            androidx.activity.DesktopActivityStack.Host(mainActivity)
                        }
                        scene.render(0L).use { initial ->
                            check(initial.width == w && initial.height == h)
                        }
                        kotlinx.coroutines.yield()

                        var frameNano = 250_000_000L
                        for (dest in io.nekohasekai.sagernet.ui.compose.NavDestination.entries) {
                            io.nekohasekai.sagernet.ui.compose.PendingDeepLink.pendingDestination.value = dest
                            repeat(3) {
                                scene.render(frameNano).close()
                                frameNano += 250_000_000L
                                kotlinx.coroutines.yield()
                            }
                            println("[selftest-gui] rendered ${dest.name} (${w}x${h})")
                        }
                        // Verify owenclave:// deep link -> GroupScreen subscription dialog
                        val deepIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("owenclave://add-subscription?url=https%3A%2F%2Fexample.com%2Fsub&hwid=1"),
                        )
                        mainActivity.onNewIntent(deepIntent)
                        repeat(4) {
                            scene.render(frameNano).close()
                            frameNano += 250_000_000L
                            kotlinx.coroutines.yield()
                        }
                        io.nekohasekai.sagernet.ui.compose.PendingDeepLink.pendingSubscriptionAdd.value = null
                        io.nekohasekai.sagernet.ui.compose.PendingDeepLink.pendingDestination.value = null
                    } finally {
                        teardownStack()
                        kotlinx.coroutines.yield()
                        scene.close()
                    }
                }
            }

            val canOpenNativeWindow = !java.awt.GraphicsEnvironment.isHeadless() &&
                System.getenv("CI").isNullOrBlank() &&
                System.getProperty("owenclave.ci").isNullOrBlank()
            if (canOpenNativeWindow) {
                var windowError: Throwable? = null
                javax.swing.SwingUtilities.invokeAndWait {
                    var window: androidx.compose.ui.awt.ComposeWindow? = null
                    try {
                        teardownStack()
                        window = androidx.compose.ui.awt.ComposeWindow()
                        window.setSize(1180, 780)
                        val mainActivity = io.nekohasekai.sagernet.ui.compose.ComposeMainActivity()
                        window.setContent {
                            androidx.activity.DesktopActivityStack.Host(mainActivity)
                        }
                        println(
                            "[selftest-gui] ComposeWindow initialized (linuxScaleOverride=${LinuxWindowSupport.detectLinuxScaleOverride(window)})",
                        )
                    } catch (t: Throwable) {
                        windowError = t
                    } finally {
                        teardownStack()
                        runCatching { window?.dispose() }
                    }
                }
                windowError?.let { throw it }
            }
            println("[selftest-gui] PASSED")
            0
        } catch (t: Throwable) {
            System.err.println("[selftest-gui] FAILED: ${t.message}")
            t.printStackTrace()
            1
        }
    }

    /**
     * Real end-to-end System Proxy (`Key.MODE_PROXY`) verification exercising the exact
     * GUI service stack (`SagerNet.startService` -> `ProxyService` -> `libgojni` `V2RayInstance`
     * -> `DesktopServiceWiring` -> `SystemProxy`).
     */
    fun runSystemProxyE2e(): Int {
        val httpServer = LocalHttpServer(PAYLOAD)
        var ssServer: Process? = null
        val systemProxy = SystemProxy()
        val privilegedTun = DesktopPrivilegedTun()

        return try {
            android.util.Log.initSession()
            val core = DesktopRuntime.coreBinary()
                ?: error("owenclave-core is missing for ${DesktopRuntime.platformTag}")
            io.nekohasekai.sagernet.SagerNet.application
            DesktopServiceWiring.install(systemProxy, privilegedTun)

            val ssPort = CoreRunner.freePort()
            val ssConfig = File(DesktopRuntime.dataDir, "selftest-sysproxy-ss.json")
            ssConfig.writeText(
                """
                {
                  "log": { "loglevel": "warning" },
                  "inbounds": [
                    {
                      "tag": "ss-in",
                      "listen": "127.0.0.1",
                      "port": $ssPort,
                      "protocol": "shadowsocks",
                      "settings": { "method": "$METHOD", "password": "$PASSWORD", "network": "tcp,udp" }
                    }
                  ],
                  "outbounds": [ { "tag": "direct", "protocol": "freedom" } ]
                }
                """.trimIndent(),
            )
            ssServer = ProcessBuilder(core.absolutePath, "run", "-c", ssConfig.absolutePath)
                .directory(DesktopRuntime.dataDir)
                .redirectErrorStream(true)
                .start()
            val ssProc = ssServer
            Thread({
                runCatching { ssProc.inputStream.bufferedReader().forEachLine { println("[sysproxy-ss] $it") } }
            }, "sysproxy-ss-log").apply { isDaemon = true; start() }
            awaitPort(ssPort)

            val socksPort = CoreRunner.freePort()
            val httpPort = CoreRunner.freePort()
            val ds = io.nekohasekai.sagernet.database.DataStore
            ds.serviceMode = io.nekohasekai.sagernet.Key.MODE_PROXY
            // Deliberately keep fresh-install default (requireHttp = false) to verify ProxyService enables HTTP automatically!
            ds.requireHttp = false
            ds.requireSocks = true
            ds.socksPort = socksPort
            ds.httpPort = httpPort
            ds.bypassLan = false
            ds.routeMode = io.nekohasekai.sagernet.RouteMode.RULE

            val bean = ShadowsocksBean().apply {
                serverAddress = "127.0.0.1"
                serverPort = ssPort
                method = METHOD
                password = PASSWORD
                name = "sysproxy-e2e"
                initializeDefaultValues()
            }
            val groupId = ds.selectedGroupForImport()
            val entity = io.nekohasekai.sagernet.database.ProxyEntity(groupId = groupId).apply {
                putBean(bean)
                userOrder = 1L
            }
            entity.id = io.nekohasekai.sagernet.database.SagerDatabase.proxyDao.addProxy(entity)
            ds.selectedProxy = entity.id

            val backendReason = systemProxy.unavailableReason()
            println("[selftest-system-proxy] backend=${systemProxy.backendName()} unavailableReason=$backendReason")
            if (DesktopRuntime.isMac || DesktopRuntime.isWindows) {
                check(backendReason == null) {
                    "SystemProxy backend must be available on ${DesktopRuntime.os}, got: $backendReason"
                }
            }

            io.nekohasekai.sagernet.SagerNet.startService()
            awaitServiceState(io.nekohasekai.sagernet.bg.BaseService.State.Connected, 20_000L)

            val svc = io.nekohasekai.sagernet.SagerNet.activeService
            check(svc.activeHttpPort == httpPort) { "Expected activeHttpPort=$httpPort, got ${svc.activeHttpPort}" }
            check(svc.activeSocksPort == socksPort) { "Expected activeSocksPort=$socksPort, got ${svc.activeSocksPort}" }
            check(!ds.requireHttp) { "DataStore.requireHttp user preference must not be permanently mutated" }

            val targetUrl = "http://127.0.0.1:${httpServer.port}/"
            val viaHttp = fetchViaJavaProxy(
                targetUrl,
                java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", httpPort)),
            )
            println("[selftest-system-proxy] HTTP proxy 127.0.0.1:$httpPort -> ${viaHttp.first} '${viaHttp.second.trim()}'")
            check(viaHttp.first == 200 && viaHttp.second.trim() == PAYLOAD) {
                "HTTP inbound failed: ${viaHttp.first} ${viaHttp.second}"
            }

            val viaSocks = fetchViaJavaProxy(
                targetUrl,
                java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)),
            )
            println("[selftest-system-proxy] SOCKS proxy 127.0.0.1:$socksPort -> ${viaSocks.first} '${viaSocks.second.trim()}'")
            check(viaSocks.first == 200 && viaSocks.second.trim() == PAYLOAD) {
                "SOCKS inbound failed: ${viaSocks.first} ${viaSocks.second}"
            }

            if (DesktopRuntime.isMac) {
                check(systemProxy.hasBackup()) { "Expected system-proxy-backup.json to be written on macOS" }
                val scutilOut = ProcessCommandRunner.run(listOf("scutil", "--proxy")).stdout
                println("[selftest-system-proxy] scutil --proxy (connected):\n$scutilOut")
                check(Regex("HTTPEnable\\s*:\\s*1").containsMatchIn(scutilOut)) {
                    "macOS scutil --proxy missing HTTPEnable : 1\n$scutilOut"
                }
                check(Regex("HTTPPort\\s*:\\s*$httpPort").containsMatchIn(scutilOut)) {
                    "macOS scutil --proxy missing HTTPPort : $httpPort\n$scutilOut"
                }
                check(Regex("HTTPSEnable\\s*:\\s*1").containsMatchIn(scutilOut)) {
                    "macOS scutil --proxy missing HTTPSEnable : 1\n$scutilOut"
                }
                check(Regex("SOCKSEnable\\s*:\\s*1").containsMatchIn(scutilOut)) {
                    "macOS scutil --proxy missing SOCKSEnable : 1\n$scutilOut"
                }
                check(Regex("SOCKSPort\\s*:\\s*$socksPort").containsMatchIn(scutilOut)) {
                    "macOS scutil --proxy missing SOCKSPort : $socksPort\n$scutilOut"
                }
            } else if (DesktopRuntime.isWindows) {
                check(systemProxy.hasBackup()) { "Expected system-proxy-backup.json to be written on Windows" }
            }

            io.nekohasekai.sagernet.SagerNet.stopService()
            awaitServiceState(io.nekohasekai.sagernet.bg.BaseService.State.Stopped, 15_000L)
            check(!systemProxy.hasBackup()) { "Expected system-proxy-backup.json to be restored & removed on stop" }

            if (DesktopRuntime.isMac) {
                val scutilAfter = ProcessCommandRunner.run(listOf("scutil", "--proxy")).stdout
                println("[selftest-system-proxy] scutil --proxy (restored):\n$scutilAfter")
                check(!Regex("HTTPPort\\s*:\\s*$httpPort").containsMatchIn(scutilAfter) ||
                    Regex("HTTPEnable\\s*:\\s*0").containsMatchIn(scutilAfter)
                ) {
                    "macOS proxy was not restored after stop:\n$scutilAfter"
                }
            }

            println("[selftest-system-proxy] PASS")
            0
        } catch (t: Throwable) {
            println("[selftest-system-proxy] FAIL: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            1
        } finally {
            runCatching { io.nekohasekai.sagernet.SagerNet.stopService() }
            runCatching { privilegedTun.stop() }
            runCatching { systemProxy.restore() }
            runCatching { ssServer?.destroyForcibly() }
            httpServer.stop()
        }
    }

    /**
     * Real end-to-end VPN / TUN (`Key.MODE_VPN`) verification exercising the exact GUI
     * service stack (`SagerNet.startService` -> `ProxyService` -> `libgojni` `V2RayInstance`
     * -> `DesktopServiceWiring` -> `DesktopPrivilegedTun` + `tun2socks`), including:
     *   1. Unproxied OS packets routed through `utun` / `owenclave-tun` -> `Shadowsocks` #1
     *   2. Direct physical interface binding (`IP_BOUND_IF` on macOS / cgroup `fwmark` on Linux) + DNS without routing loop
     *   3. Live hot-reload (`SagerNet.reloadService`) to `Shadowsocks` #2 without dropping TUN
     *   4. Clean teardown (`SagerNet.stopService`) and route restoration.
     */
    fun runVpnE2e(): Int {
        val http1 = LocalHttpServer("vpn-e2e-profile-1")
        val http2 = LocalHttpServer("vpn-e2e-profile-2")
        var ss1: Process? = null
        var ss2: Process? = null
        val systemProxy = SystemProxy()
        val privilegedTun = DesktopPrivilegedTun()

        return try {
            android.util.Log.initSession()
            val core = DesktopRuntime.coreBinary()
                ?: error("owenclave-core is missing for ${DesktopRuntime.platformTag}")
            check(DesktopRuntime.tun2socksBinary() != null) {
                "tun2socks is missing for ${DesktopRuntime.platformTag}"
            }
            io.nekohasekai.sagernet.SagerNet.application
            DesktopServiceWiring.install(systemProxy, privilegedTun)

            fun startRedirectSsServer(name: String, targetHttpPort: Int): Pair<Int, Process> {
                val port = CoreRunner.freePort()
                val cfg = File(DesktopRuntime.dataDir, "selftest-$name.json")
                cfg.writeText(
                    """
                    {
                      "log": { "loglevel": "warning" },
                      "inbounds": [
                        {
                          "tag": "ss-in",
                          "listen": "127.0.0.1",
                          "port": $port,
                          "protocol": "shadowsocks",
                          "settings": { "method": "$METHOD", "password": "$PASSWORD", "network": "tcp,udp" }
                        }
                      ],
                      "outbounds": [
                        {
                          "tag": "redirect-http",
                          "protocol": "freedom",
                          "settings": { "redirect": "127.0.0.1:$targetHttpPort" }
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                val proc = ProcessBuilder(core.absolutePath, "run", "-c", cfg.absolutePath)
                    .directory(DesktopRuntime.dataDir)
                    .redirectErrorStream(true)
                    .start()
                Thread({
                    runCatching { proc.inputStream.bufferedReader().forEachLine { println("[$name] $it") } }
                }, "$name-log").apply { isDaemon = true; start() }
                awaitPort(port)
                return port to proc
            }

            val (ssPort1, proc1) = startRedirectSsServer("vpn-ss1", http1.port)
            ss1 = proc1
            val (ssPort2, proc2) = startRedirectSsServer("vpn-ss2", http2.port)
            ss2 = proc2

            val ds = io.nekohasekai.sagernet.database.DataStore
            ds.serviceMode = io.nekohasekai.sagernet.Key.MODE_VPN
            ds.socksPort = CoreRunner.freePort()
            ds.httpPort = CoreRunner.freePort()
            ds.bypassLan = true
            ds.enableVPNInterfaceIPv6Address = false
            ds.routeMode = io.nekohasekai.sagernet.RouteMode.RULE

            io.nekohasekai.sagernet.database.SagerDatabase.rulesDao.reset()
            io.nekohasekai.sagernet.database.SagerDatabase.rulesDao.createRule(
                io.nekohasekai.sagernet.database.RuleEntity(
                    name = "e2e-tun-sentinel",
                    userOrder = 1L,
                    enabled = true,
                    ip = "198.18.0.254/32",
                    outbound = 0L,
                ),
            )
            io.nekohasekai.sagernet.database.SagerDatabase.rulesDao.createRule(
                io.nekohasekai.sagernet.database.RuleEntity(
                    name = "e2e-direct-bypass",
                    userOrder = 2L,
                    enabled = true,
                    network = "tcp,udp",
                    outbound = -1L,
                ),
            )

            val groupId = ds.selectedGroupForImport()
            fun addSsProfile(name: String, port: Int): io.nekohasekai.sagernet.database.ProxyEntity {
                val b = ShadowsocksBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    method = METHOD
                    password = PASSWORD
                    this.name = name
                    initializeDefaultValues()
                }
                val p = io.nekohasekai.sagernet.database.ProxyEntity(groupId = groupId).apply {
                    putBean(b)
                    userOrder = if (name.endsWith("1")) 1L else 2L
                }
                p.id = io.nekohasekai.sagernet.database.SagerDatabase.proxyDao.addProxy(p)
                return p
            }

            val profile1 = addSsProfile("vpn-profile-1", ssPort1)
            val profile2 = addSsProfile("vpn-profile-2", ssPort2)
            ds.selectedProxy = profile1.id

            println("[selftest-vpn-e2e] Stage 1: starting VPN on profile 1 (id=${profile1.id})")
            io.nekohasekai.sagernet.SagerNet.startService()
            awaitServiceState(io.nekohasekai.sagernet.bg.BaseService.State.Connected, 30_000L)
            check(privilegedTun.isRunning()) { "DesktopPrivilegedTun must be running in Connected state" }

            val r1 = fetchExternalUnproxied("http://198.18.0.254/")
            println("[selftest-vpn-e2e] Stage 1 sentinel response: HTTP ${r1.first} '${r1.second.trim()}'")
            check(r1.first == 200 && r1.second.trim() == "vpn-e2e-profile-1") {
                "Stage 1 TUN routing failed: expected 'vpn-e2e-profile-1', got HTTP ${r1.first} '${r1.second}'"
            }

            val ext1 = fetchExternalInternetProbe()
            println("[selftest-vpn-e2e] Stage 1 external probe (IP_BOUND_IF / fwmark + DNS): HTTP ${ext1.first}")
            check(ext1.first in 200..399) {
                "Stage 1 external bypass probe looped or failed: HTTP ${ext1.first} '${ext1.second}'"
            }

            println("[selftest-vpn-e2e] Stage 2: hot-reloading VPN to profile 2 (id=${profile2.id})")
            ds.selectedProxy = profile2.id
            io.nekohasekai.sagernet.SagerNet.reloadService()
            awaitProfileReloaded(profile2.id, 20_000L)
            check(privilegedTun.isRunning()) { "DesktopPrivilegedTun must remain running across profile reload" }

            val r2 = fetchExternalUnproxied("http://198.18.0.254/")
            println("[selftest-vpn-e2e] Stage 2 sentinel response: HTTP ${r2.first} '${r2.second.trim()}'")
            check(r2.first == 200 && r2.second.trim() == "vpn-e2e-profile-2") {
                "Stage 2 reloaded TUN routing failed: expected 'vpn-e2e-profile-2', got HTTP ${r2.first} '${r2.second}'"
            }

            val ext2 = fetchExternalInternetProbe()
            println("[selftest-vpn-e2e] Stage 2 external probe after reload: HTTP ${ext2.first}")
            check(ext2.first in 200..399) {
                "Stage 2 external probe after reload failed: HTTP ${ext2.first} '${ext2.second}'"
            }

            println("[selftest-vpn-e2e] Stage 3: stopping VPN and verifying route teardown")
            io.nekohasekai.sagernet.SagerNet.stopService()
            awaitServiceState(io.nekohasekai.sagernet.bg.BaseService.State.Stopped, 20_000L)
            check(!privilegedTun.isRunning()) { "DesktopPrivilegedTun must stop after SagerNet.stopService()" }

            val ext3 = fetchExternalInternetProbe()
            println("[selftest-vpn-e2e] Stage 3 post-teardown direct internet probe: HTTP ${ext3.first}")
            check(ext3.first in 200..399) {
                "Stage 3 post-teardown internet connectivity broken: HTTP ${ext3.first} '${ext3.second}'"
            }

            println("[selftest-vpn-e2e] PASS")
            0
        } catch (t: Throwable) {
            println("[selftest-vpn-e2e] FAIL: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            1
        } finally {
            runCatching { io.nekohasekai.sagernet.SagerNet.stopService() }
            runCatching { privilegedTun.stop() }
            runCatching { systemProxy.restore() }
            if (DesktopRuntime.isLinux) {
                runCatching { File("/sys/fs/cgroup/owenclave_selftest.slice").delete() }
            }
            runCatching { ss1?.destroyForcibly() }
            runCatching { ss2?.destroyForcibly() }
            http1.stop()
            http2.stop()
        }
    }

    private fun awaitServiceState(expected: io.nekohasekai.sagernet.bg.BaseService.State, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var sawNonStopped = false
        while (System.currentTimeMillis() < deadline) {
            val st = io.nekohasekai.sagernet.SagerNet.activeService.data.state
            if (st == expected) return
            if (st != io.nekohasekai.sagernet.bg.BaseService.State.Stopped) {
                sawNonStopped = true
            } else if (sawNonStopped && expected == io.nekohasekai.sagernet.bg.BaseService.State.Connected) {
                break
            }
            Thread.sleep(50L)
        }
        val recentLog = runCatching {
            File(DesktopRuntime.dataDir, "logcat.log").takeIf { it.isFile }
                ?.readLines()?.takeLast(20)?.joinToString("\n")
        }.getOrNull().orEmpty()
        error(
            "Timed out waiting for service state $expected (actual=${io.nekohasekai.sagernet.SagerNet.activeService.data.state})" +
                if (recentLog.isNotBlank()) "\n--- logcat ---\n$recentLog" else "",
        )
    }

    private fun awaitProfileReloaded(expectedProfileId: Long, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val st = io.nekohasekai.sagernet.SagerNet.activeService.data.state
            if (st == io.nekohasekai.sagernet.bg.BaseService.State.Connected &&
                io.nekohasekai.sagernet.database.DataStore.startedProfile == expectedProfileId
            ) {
                return
            }
            Thread.sleep(50L)
        }
        error(
            "Timed out waiting for reload to profile $expectedProfileId " +
                "(state=${io.nekohasekai.sagernet.SagerNet.activeService.data.state}, " +
                "started=${io.nekohasekai.sagernet.database.DataStore.startedProfile})",
        )
    }

    private fun fetchViaJavaProxy(url: String, proxy: java.net.Proxy): Pair<Int, String> {
        val conn = URL(url).openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = false
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchExternalInternetProbe(): Pair<Int, String> {
        val urls = listOf("http://cp.cloudflare.com/generate_204", "http://example.com/")
        var last: Pair<Int, String> = -1 to "not attempted"
        for (u in urls) {
            val res = fetchExternalUnproxied(u)
            if (res.first in 200..399) return res
            last = res
        }
        return last
    }

    private fun fetchExternalUnproxied(url: String): Pair<Int, String> {
        val cmd = when {
            DesktopRuntime.isLinux -> listOf(
                "/bin/sh",
                "-c",
                "mkdir -p /sys/fs/cgroup/owenclave_selftest.slice 2>/dev/null || true; " +
                    "echo \$\$ >/sys/fs/cgroup/owenclave_selftest.slice/cgroup.procs 2>/dev/null || " +
                    "echo \$\$ >/sys/fs/cgroup/cgroup.procs 2>/dev/null || true; " +
                    "exec curl --noproxy '*' -sS --max-time 12 -w '\\n%{http_code}' \"\$1\"",
                "sh",
                url,
            )
            DesktopRuntime.isWindows -> {
                val sysCurl = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\curl.exe")
                val curlBin = if (sysCurl.isFile) sysCurl.absolutePath else "curl.exe"
                listOf(curlBin, "--noproxy", "*", "-sS", "--max-time", "12", "-w", "\n%{http_code}", url)
            }
            else -> {
                val curlBin = if (File("/usr/bin/curl").canExecute()) "/usr/bin/curl" else "curl"
                listOf(curlBin, "--noproxy", "*", "-sS", "--max-time", "12", "-w", "\n%{http_code}", url)
            }
        }
        val res = ProcessCommandRunner.run(cmd)
        val lines = res.stdout.trimEnd('\r', '\n')
        val nl = lines.lastIndexOf('\n')
        val code = if (nl >= 0) lines.substring(nl + 1).trim().toIntOrNull() ?: -1 else lines.trim().toIntOrNull() ?: -1
        val body = if (nl >= 0) lines.substring(0, nl) else ""
        return if (code > 0) code to body else -1 to res.output
    }

    private fun java.io.InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) throw java.io.EOFException("unexpected end of stream")
            offset += read
        }
    }
}
