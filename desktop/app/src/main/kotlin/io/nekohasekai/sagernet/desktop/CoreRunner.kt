package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

/**
 * Supervises the desktop proxy runtime: the Go core process and the external
 * engines (NaiveProxy) the core dials into through a local SOCKS listener.
 *
 * This mirrors the Android architecture, where `bg/proto/V2RayInstance.kt`
 * starts the in-process core plus the plugin binaries and wires them together
 * through a local SOCKS listener.
 */
class CoreRunner(
    private val systemProxy: SystemProxy = SystemProxy(),
    private val workDir: File = DesktopRuntime.dataDir,
    private val log: (String) -> Unit,
) {

    private var tunSession: TunSession? = null
    private var perAppRouting: PerAppRouting? = null
    private var coreProcess: Process? = null
    private var coreConfigFile: File = File(workDir, "core.json")
    private val plugins = ExternalPlugins { line -> log(line) }

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var startCount: Int = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    val socksPort: Int get() = settings?.socksPort ?: 0

    val corePid: Long? get() = coreProcess?.takeIf { it.isAlive }?.pid()

    /** True when both the core process and every started external plugin are alive. */
    val healthy: Boolean get() = running && coreProcess?.isAlive == true && plugins.allAlive

    /** Whether any external plugin process is still running. */
    fun pluginAlive(): Boolean = plugins.alive

    private var settings: DesktopSettings? = null

    /** Starts [profile]; throws with a human readable message when it cannot. */
    fun start(
        profile: Profile,
        settings: DesktopSettings,
        rules: List<RoutingRule> = emptyList(),
        bindInterfaceOverride: String? = null,
        serverInbounds: ServerInboundOptions? = null,
        extraProfiles: List<Profile> = emptyList(),
        balancers: List<BalancerSpec> = emptyList(),
        primaryBalancer: BalancerSpec? = null,
    ) {
        stop()
        this.settings = settings
        lastError = null

        val tunInterface = if (settings.tunEnabled) TunSession.primaryInterface() else null
        val outboundInterface = bindInterfaceOverride?.takeIf { it.isNotBlank() } ?: tunInterface
        val config = DesktopConfigBuilder.build(
            profile,
            settings,
            rules,
            outboundInterface,
            serverInbounds,
            extraProfiles,
            balancers,
            primaryBalancer,
        )

        // External engines have to be listening before the core tries to dial
        // into their local SOCKS inbound.
        try {
            plugins.start(config.plugins) { port, process, what ->
                awaitPort(port, process, timeoutMillis = 20_000, what = what)
            }
        } catch (e: Exception) {
            stop()
            throw e
        }

        startCore(profile, config.json, settings)

        if (tunInterface != null) {
            // The Android `proxyApps` row: route only the listed processes through
            // the TUN device (Linux cgroup v2 + nftables, see PerAppRouting).
            val perAppProcesses = parseHostList(settings.perAppProcesses)
            val perAppRequested = settings.value(Key.PROXY_APPS) == "true"
            val perApp = perAppRequested && perAppProcesses.isNotEmpty()
            if (perAppRequested && perAppProcesses.isEmpty()) {
                log("per-app routing is on but the process list is empty; all traffic goes through the tunnel")
            }

            val session = TunSession { line -> log(line) }
            // The Android `enableVPNInterfaceIPv6Address` row adds the IPv6 address
            // and split default routes to the desktop TUN device.
            val ipv6 = settings.value(Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS) == "true"
            try {
                session.start(
                    tunInterface,
                    settings.tunMtu,
                    settings.socksPort,
                    settings.tunInterface,
                    ipv6,
                    perApp,
                )
            } catch (e: Exception) {
                stop()
                throw e
            }
            tunSession = session
            log("transparent mode ready on ${session.device}")

            if (perApp) {
                val routing = PerAppRouting(log = { line -> log(line) })
                try {
                    routing.install(perAppProcesses, session.device)
                } catch (e: Exception) {
                    stop()
                    throw e
                }
                perAppRouting = routing
            }
        } else if (settings.systemProxyEnabled) {
            // The desktop "System proxy" service mode: point the OS proxy at the
            // local HTTP port, remembering (and restoring) the previous state.
            val bypass = parseHostList(settings.value(Key.HTTP_PROXY_EXCEPTION))
            val message = systemProxy.apply(
                host = "127.0.0.1",
                httpPort = settings.httpPort,
                socksPort = settings.socksPort,
                bypass = bypass,
            )
            log(message)
        }
    }

    private fun startCore(profile: Profile, config: String, settings: DesktopSettings) {
        val binary = DesktopRuntime.coreBinary()
            ?: throw UnsupportedProfileException("owenclave-core is not available for ${DesktopRuntime.platformTag}")

        workDir.mkdirs()
        coreConfigFile = File(workDir, "core.json")
        coreConfigFile.writeText(config)
        log("core config written to ${coreConfigFile.absolutePath}")

        log("starting ${binary.name}")
        val pb = ProcessBuilder(binary.absolutePath, "run", "-c", coreConfigFile.absolutePath)
            .directory(workDir)
            .redirectErrorStream(true)
        DesktopRuntime.assetDir()?.let { assets ->
            pb.environment()["V2RAY_LOCATION_ASSET"] = assets.absolutePath
        }
        val startupLines = java.util.Collections.synchronizedList(ArrayList<String>())
        val process = pb.start()
        coreProcess = process
        pumpOutput("core", process, startupLines)

        // The core is ready when the local SOCKS inbound accepts connections.
        try {
            awaitPort(settings.socksPort, process, timeoutMillis = 20_000, what = "core", startupLines = startupLines)
        } catch (e: Exception) {
            process.destroyForcibly()
            runCatching { process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
            coreProcess = null
            throw e
        }

        running = true
        startCount += 1
        DataStore.startedProfile = profile.id.hashCode().toLong()
        SagerNet.started = true
        log("connected: ${profile.displayName} (${profile.protocolName})")

        // Watch for unexpected exits.
        Thread {
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            if (running) {
                running = false
                SagerNet.started = false
                DataStore.startedProfile = 0L
                lastError = "core exited with code $code"
                log("core exited unexpectedly with code $code")
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        SagerNet.started = false
        DataStore.startedProfile = 0L

        // The marks and the policy route go before the device disappears.
        perAppRouting?.let { routing ->
            log("stopping per-app routing")
            routing.remove()
        }
        perAppRouting = null

        // The routes and the device have to go before the core disappears.
        tunSession?.let { session ->
            log("stopping transparent mode")
            session.stop()
        }
        tunSession = null

        // The OS proxy is put back whether or not a TUN session existed; the call
        // is a no-op when this run never touched it.
        val restore = systemProxy.restore()
        if (restore.restored || systemProxy.hasBackup()) log(restore.message)

        plugins.stop()

        coreProcess?.let { process ->
            log("stopping core")
            process.destroy()
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                runCatching { process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
            }
        }
        coreProcess = null
    }

    /**
     * Fetches [url] through the local SOCKS inbound of the running core, which
     * proves the whole chain (client -> core -> upstream proxy) works.
     */
    fun fetch(url: String, timeoutMillis: Int = 15_000): HttpResult {
        val port = settings?.socksPort ?: throw IllegalStateException("core is not running")
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        val connection = URL(url).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Owenclave/${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME}")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult(code, body)
        } finally {
            connection.disconnect()
        }
    }

    fun testConnection(url: String = "https://www.gstatic.com/generate_204", timeoutMillis: Int = 15_000): String {
        val result = fetch(url, timeoutMillis)
        return "HTTP ${result.code} from $url"
    }

    data class HttpResult(val code: Int, val body: String)

    private fun pumpOutput(tag: String, process: Process, capture: MutableList<String>? = null) {
        Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (capture != null && capture.size < 20) {
                        capture.add(line)
                    }
                    log("[$tag] $line")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun awaitPort(
        port: Int,
        process: Process,
        timeoutMillis: Long,
        what: String,
        startupLines: List<String> = emptyList(),
    ) {
        val useProcFs = File("/proc/net/tcp").canRead()
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                Thread.sleep(50)
                val detail = synchronized(startupLines) {
                    startupLines.takeLast(6).joinToString(" | ")
                }
                val suffix = if (detail.isNotBlank()) ": $detail" else ""
                throw UnsupportedProfileException("$what terminated during startup (exit code ${process.exitValue()})$suffix")
            }
            if (useProcFs && io.nekohasekai.sagernet.bg.ProxyService.isLoopbackTcpListeningLinux(port)) {
                return
            }
            if (!useProcFs) {
                val connected = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                    }
                    true
                }.getOrDefault(false)
                if (connected) return
            }
            Thread.sleep(100)
        }
        throw UnsupportedProfileException("$what did not start listening on 127.0.0.1:$port in time")
    }

    companion object {

        private val recentPorts = java.util.ArrayDeque<Int>(64)

        @Synchronized
        fun freePort(): Int {
            val loopback = java.net.InetAddress.getByName("127.0.0.1")
            repeat(32) {
                val candidate = runCatching {
                    ServerSocket().use { s ->
                        s.reuseAddress = false
                        s.bind(InetSocketAddress(loopback, 0), 1)
                        s.localPort
                    }
                }.getOrDefault(0)
                if (candidate <= 0 || recentPorts.contains(candidate)) return@repeat
                val allFree = runCatching {
                    ServerSocket().use { s ->
                        s.reuseAddress = false
                        s.bind(InetSocketAddress(candidate), 1)
                    }
                    java.net.DatagramSocket(null).use { u ->
                        u.reuseAddress = false
                        u.bind(InetSocketAddress(loopback, candidate))
                    }
                    java.net.DatagramSocket(null).use { u ->
                        u.reuseAddress = false
                        u.bind(InetSocketAddress(candidate))
                    }
                    true
                }.getOrDefault(false)
                if (allFree) {
                    if (recentPorts.size >= 64) recentPorts.removeFirst()
                    recentPorts.addLast(candidate)
                    return candidate
                }
            }
            return ServerSocket(0).use { it.localPort }
        }

    }

}
