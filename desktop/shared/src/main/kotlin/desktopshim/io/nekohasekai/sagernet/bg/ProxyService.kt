package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Intent
import android.net.Network
import android.os.PowerManager
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

open class ProxyService : Service(),
    BaseService.Interface,
    LocalResolver {
    override val data = BaseService.Data(this)
    override val tag: String get() = "SagerNetProxyService"
    override fun createNotification(profileName: String): ServiceNotification =
        ServiceNotification(this, profileName, "service-proxy", true)
    override var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    override var underlyingNetwork: Network? = null
    private var networkListenerIsRunning = false

    /** Local DNS port bound by `v2rayPoint` (`TAG_DNS_IN`) for the current session, or 0. */
    @Volatile
    var activeDnsPort: Int = 0
        private set

    /** Local SOCKS5 port bound by `v2rayPoint` for the current session, or 0. */
    @Volatile
    var activeSocksPort: Int = 0
        private set

    /** Local HTTP proxy port bound by `v2rayPoint` for the current session, or 0. */
    @Volatile
    var activeHttpPort: Int = 0
        private set

    /** True when the current session is in `MODE_PROXY` with unauthenticated local HTTP+SOCKS inbounds ready for OS proxy setup. */
    @Volatile
    var activeSystemProxyEligible: Boolean = false
        private set

    /** Non-tunnel physical DNS IPs (macOS/Windows) that must receive `/32` physical gateway bypass routes while VPN is active. */
    @Volatile
    var activePhysicalDnsBypassIps: List<String> = emptyList()
        private set

    /** Preserves the active TUN DNS port across profile reloads so `TunnelKey` stays identical. */
    @Volatile
    private var lastBoundDnsPort: Int = 0

    @Volatile
    private var lastPhysicalDnsBypassIps: List<String> = emptyList()

    @Volatile
    private var lastPhysicalInterface: String = ""

    private data class ServiceConfigOverrides(
        val requireSocks: Boolean,
        val socksUdp: Boolean,
        val requireHttp: Boolean,
        val trafficSniffing: Boolean,
        val hijackDns: Boolean,
        val requireDnsInbound: Boolean,
        val localDnsPort: Int,
    )

    private var savedServiceOverrides: ServiceConfigOverrides? = null

    private fun restoreServiceOverrides() {
        val saved = savedServiceOverrides ?: return
        savedServiceOverrides = null
        DataStore.requireSocks = saved.requireSocks
        DataStore.socksUDP = saved.socksUdp
        DataStore.requireHttp = saved.requireHttp
        DataStore.trafficSniffing = saved.trafficSniffing
        DataStore.hijackDns = saved.hijackDns
        DataStore.requireDnsInbound = saved.requireDnsInbound
        DataStore.localDNSPort = saved.localDnsPort
    }

    override suspend fun preInit() {
        restoreServiceOverrides()
        savedServiceOverrides = ServiceConfigOverrides(
            requireSocks = DataStore.requireSocks,
            socksUdp = DataStore.socksUDP,
            requireHttp = DataStore.requireHttp,
            trafficSniffing = DataStore.trafficSniffing,
            hijackDns = DataStore.hijackDns,
            requireDnsInbound = DataStore.requireDnsInbound,
            localDnsPort = DataStore.localDNSPort,
        )

        if (DataStore.serviceMode == Key.MODE_VPN) {
            val preferredDnsPort = lastBoundDnsPort.takeIf { it in 1..65535 }
                ?: DataStore.localDNSPort.takeIf { it in 1..65535 }
                ?: 6450
            val dnsPort = if (awaitLoopbackTcpAndUdpFree(preferredDnsPort, 400L)) {
                preferredDnsPort
            } else {
                allocateLoopbackDnsPort()
            }
            activeDnsPort = dnsPort
            lastBoundDnsPort = dnsPort
            DataStore.requireSocks = true
            DataStore.socksUDP = true
            DataStore.trafficSniffing = true
            DataStore.hijackDns = true
            DataStore.requireDnsInbound = true
            DataStore.localDNSPort = dnsPort
            activeSocksPort = DataStore.socksPort
            activeHttpPort = if (DataStore.requireHttp) DataStore.httpPort else 0
            activeSystemProxyEligible = false

            if (isMacOs || isWindows) {
                if (lastPhysicalDnsBypassIps.isEmpty()) {
                    lastPhysicalDnsBypassIps = android.net.DnsResolver.snapshotPhysicalBypassDnsIps()
                }
                activePhysicalDnsBypassIps = lastPhysicalDnsBypassIps
                android.net.DnsResolver.setPhysicalDnsOverride(activePhysicalDnsBypassIps)
            } else {
                activePhysicalDnsBypassIps = emptyList()
            }

            if (isMacOs) {
                val physIf = withContext(Dispatchers.IO) { detectMacPhysicalInterface() }
                if (physIf.isNotEmpty()) {
                    runCatching { libexclavecore.Libexclavecore.setenv("OWENCLAVE_PHYSICAL_IF", physIf) }
                }
            } else if (isWindows) {
                val physIf = withContext(Dispatchers.IO) { detectWindowsPhysicalIfIndex() }
                if (physIf.isNotEmpty()) {
                    runCatching { libexclavecore.Libexclavecore.setenv("OWENCLAVE_PHYSICAL_IF", physIf) }
                }
            }
        } else {
            lastBoundDnsPort = 0
            lastPhysicalDnsBypassIps = emptyList()
            activePhysicalDnsBypassIps = emptyList()
            android.net.DnsResolver.setPhysicalDnsOverride(null)
            if (isMacOs || isWindows) {
                runCatching { libexclavecore.Libexclavecore.unsetenv("OWENCLAVE_PHYSICAL_IF") }
            }
            if (DataStore.serviceMode == Key.MODE_PROXY) {
                DataStore.requireSocks = true
                DataStore.requireHttp = true
            }
            activeSocksPort = if (DataStore.requireSocks) DataStore.socksPort else 0
            activeHttpPort = if (DataStore.requireHttp) DataStore.httpPort else 0
            activeDnsPort = if (DataStore.requireDnsInbound) DataStore.localDNSPort else 0
            activeSystemProxyEligible = DataStore.serviceMode == Key.MODE_PROXY &&
                activeHttpPort in 1..65535 &&
                activeSocksPort in 1..65535 &&
                DataStore.httpUsername.isEmpty() &&
                DataStore.httpPassword.isEmpty()
        }

        networkListenerIsRunning = true
        DefaultNetworkListener.start(this) {
            if (networkListenerIsRunning) {
                SagerNet.reloadNetwork(it)
                underlyingNetwork = it
            }
        }
    }

    override suspend fun startProcesses() {
        restoreServiceOverrides()
        val proxy = data.proxy!!
        withContext(Dispatchers.IO) {
            onPreStart?.invoke(this@ProxyService)
        }
        proxy.v2rayPoint.withLocalResolver(this)
        if (isMacOs || isWindows) {
            proxy.v2rayPoint.withAlternativeSystemDialer("")
        }
        super.startProcesses()
        if (proxy.pluginConfigs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (port in proxy.pluginConfigs.keys) {
                    awaitLocalPort(port, 5_000L)
                }
            }
        }
        withContext(Dispatchers.IO) {
            onStarted?.invoke(this@ProxyService)
        }
    }

    override fun killProcesses() {
        restoreServiceOverrides()
        data.proxy?.v2rayPoint?.withLocalResolver(null)
        super.killProcesses()
        terminateChildPluginProcesses()
        networkListenerIsRunning = false
        GlobalScope.launch(Dispatchers.Default) { DefaultNetworkListener.stop(this) }
    }

    override fun stopRunner(restart: Boolean, msg: String?, keepState: Boolean) {
        data.notification?.destroy()
        data.notification = null
        if (data.state == BaseService.State.Stopping) return

        data.changeState(BaseService.State.Stopping)

        runOnMainDispatcher {
            val job = data.connectingJob
            if (job != null) {
                job.cancel()
                withTimeoutOrNull(1_500L) { job.join() }
                data.connectingJob = null
            }

            // When disconnecting (not reloading a profile), tear down the OS TUN / SystemProxy
            // on Dispatchers.IO BEFORE closing v2rayPoint ports and BEFORE reporting State.Stopped,
            // so UI never freezes and rapid Disconnect -> Connect never races with teardown.
            if (!restart) {
                lastBoundDnsPort = 0
                lastPhysicalDnsBypassIps = emptyList()
                activePhysicalDnsBypassIps = emptyList()
                android.net.DnsResolver.setPhysicalDnsOverride(null)
                if (isMacOs || isWindows) {
                    runCatching { libexclavecore.Libexclavecore.unsetenv("OWENCLAVE_PHYSICAL_IF") }
                }
                withContext(Dispatchers.IO) {
                    runCatching { onStopped?.invoke() }.onFailure { Logs.w(it) }
                }
            }

            withContext(Dispatchers.IO) {
                killProcesses()
            }

            if (data.closeReceiverRegistered) {
                unregisterReceiver(data.receiver)
                data.closeReceiverRegistered = false
            }
            data.binder.profilePersisted(listOfNotNull(data.proxy).map { it.profile.id })
            data.proxy = null

            val startedProfileId = DataStore.startedProfile
            val startTime = DataStore.connectionStart
            if (startedProfileId > 0 && startTime > 0) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                if (elapsed > 0) {
                    val entity = SagerDatabase.proxyDao.getById(startedProfileId)
                    if (entity != null) {
                        entity.connectedTime += elapsed
                        SagerDatabase.proxyDao.updateConnectedTime(startedProfileId, entity.connectedTime)
                    }
                }
            }
            DataStore.connectionStart = 0L
            DataStore.startedProfile = 0L
            if (!keepState) DataStore.currentProfile = 0L

            data.changeState(BaseService.State.Stopped, msg)

            if (restart) {
                startRunner()
            } else {
                super<Service>.stopSelf()
            }
        }
    }

    override fun stopSelf() {
        runCatching { onStopped?.invoke() }.onFailure { Logs.w(it) }
        super.stopSelf()
    }

    override fun acquireWakeLock() {
        wakeLock = SagerNet.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:proxy")
            .apply { acquire() }
    }

    override fun onBind(intent: Intent) = super<BaseService.Interface>.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        super<BaseService.Interface>.onStartCommand(intent, flags, startId)

    override fun onDestroy() {
        runCatching { onStopped?.invoke() }.onFailure { Logs.w(it) }
        super<Service>.onDestroy()
        data.binder.close()
    }

    private suspend fun awaitLocalPort(port: Int, timeoutMs: Long) {
        val useProcNet = File("/proc/net/tcp").canRead() || File("/proc/net/tcp6").canRead()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ready = if (useProcNet) {
                isLoopbackTcpListeningLinux(port)
            } else {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 150)
                    }
                    true
                }.getOrDefault(false)
            }
            if (ready) return
            delay(30L)
        }
    }

    private suspend fun awaitLoopbackTcpAndUdpFree(port: Int, timeoutMs: Long): Boolean {
        if (isLoopbackTcpAndUdpFree(port)) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(40L)
            if (isLoopbackTcpAndUdpFree(port)) return true
        }
        return false
    }

    internal fun isLoopbackTcpAndUdpFree(port: Int): Boolean {
        val lo = InetAddress.getByName("127.0.0.1")
        return runCatching {
            ServerSocket().use { tcp ->
                tcp.reuseAddress = true
                tcp.bind(InetSocketAddress(lo, port), 1)
            }
            DatagramSocket(null).use { udp ->
                udp.reuseAddress = true
                udp.bind(InetSocketAddress(lo, port))
            }
            true
        }.getOrDefault(false)
    }

    private fun allocateLoopbackDnsPort(): Int {
        val lo = InetAddress.getByName("127.0.0.1")
        repeat(8) {
            val port = runCatching {
                ServerSocket(0, 1, lo).use { it.localPort }
            }.getOrDefault(0)
            if (port > 0 && isLoopbackTcpAndUdpFree(port)) return port
        }
        return 0
    }

    private fun terminateChildPluginProcesses() {
        runCatching {
            val pluginChildren = ProcessHandle.current().children()
                .filter { handle ->
                    val cmd = handle.info().command().orElse("")
                    val name = File(cmd).name.lowercase()
                    name in PLUGIN_EXECUTABLE_NAMES
                }
                .toList()
            for (child in pluginChildren) {
                runCatching { child.destroy() }
            }
            for (child in pluginChildren) {
                val exited = runCatching {
                    child.onExit().get(250L, TimeUnit.MILLISECONDS)
                    true
                }.getOrDefault(false)
                if (!exited && child.isAlive) {
                    runCatching { child.destroyForcibly() }
                }
            }
        }
    }

    private fun detectMacPhysicalInterface(): String {
        val routeCmd = if (File("/sbin/route").canExecute()) "/sbin/route" else "route"
        val fromRoute = runCatching {
            val proc = ProcessBuilder(routeCmd, "-n", "get", "default").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(3, TimeUnit.SECONDS)
            Regex("interface:\\s+(\\S+)").find(out)?.groupValues?.get(1)?.trim().orEmpty()
        }.getOrDefault("")
        if (fromRoute.isNotEmpty() && !fromRoute.startsWith("utun", ignoreCase = true)) {
            lastPhysicalInterface = fromRoute
            return fromRoute
        }
        val scutilCmd = if (File("/usr/sbin/scutil").canExecute()) "/usr/sbin/scutil" else "scutil"
        val fromScutil = runCatching {
            val proc = ProcessBuilder(scutilCmd, "--nwi").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(3, TimeUnit.SECONDS)
            Regex("^\\s*(en\\d+|bridge\\d+|pdp_ip\\d+)\\s*:", RegexOption.MULTILINE)
                .find(out)?.groupValues?.get(1)?.trim().orEmpty()
        }.getOrDefault("")
        if (fromScutil.isNotEmpty()) {
            lastPhysicalInterface = fromScutil
            return fromScutil
        }
        return lastPhysicalInterface
    }

    private fun detectWindowsPhysicalIfIndex(): String {
        val fromRoute = runCatching {
            val proc = ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "(Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | " +
                    "Where-Object { \$_.InterfaceAlias -notlike '*Owenclave*' -and \$_.InterfaceAlias -notlike '*wintun*' -and \$_.NextHop -ne '0.0.0.0' } | " +
                    "Sort-Object { \$_.RouteMetric + \$_.InterfaceMetric } | Select-Object -First 1).InterfaceIndex",
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(5, TimeUnit.SECONDS)
            out.lineSequence().map { it.trim() }.firstOrNull { it.toIntOrNull()?.let { n -> n > 0 } == true }.orEmpty()
        }.getOrDefault("")
        if (fromRoute.isNotEmpty()) {
            lastPhysicalInterface = fromRoute
            return fromRoute
        }
        return lastPhysicalInterface
    }

    companion object {
        private val PLUGIN_EXECUTABLE_NAMES = setOf("naive", "naive.exe", "libnaive.so")
        private val osLower = System.getProperty("os.name", "").lowercase()
        private val isMacOs = osLower.contains("mac") || osLower.contains("darwin")
        private val isWindows = osLower.contains("win")

        @Volatile
        var onPreStart: ((ProxyService) -> Unit)? = null

        @Volatile
        var onStarted: ((ProxyService) -> Unit)? = null

        @Volatile
        var onStopped: (() -> Unit)? = null

        /**
         * Passively checks `/proc/net/tcp` and `/proc/net/tcp6` for a `TCP_LISTEN` (`0A`) socket
         * on `127.0.0.1:[port]`, `::ffff:127.0.0.1:[port]`, `::1:[port]`, or wildcard `:[port]`
         * without opening a dummy TCP connection (which otherwise triggers a premature SOCKS5
         * handshake error & 32s preamble retry inside NaiveProxy).
         */
        @JvmStatic
        fun isLoopbackTcpListeningLinux(port: Int): Boolean {
            return isLoopbackTcpListeningProcNet(File("/proc/net/tcp"), port) ||
                isLoopbackTcpListeningProcNet(File("/proc/net/tcp6"), port)
        }

        @JvmStatic
        fun isLoopbackTcpListeningProcNet(procNetFile: File, port: Int): Boolean {
            if (!procNetFile.canRead()) return false
            val hexPort = "%04X".format(port and 0xFFFF)
            val validHosts = setOf(
                "0100007F",
                "00000000",
                "0000000000000000FFFF00000100007F",
                "00000000000000000000000001000000",
                "00000000000000000000000000000000",
            )
            return runCatching {
                procNetFile.useLines { lines ->
                    lines.drop(1).any { raw ->
                        val cols = raw.trim().split(Regex("\\s+"))
                        if (cols.size < 4 || cols[3] != "0A") return@any false
                        val local = cols[1]
                        val colon = local.indexOf(':')
                        if (colon <= 0) return@any false
                        val hostHex = local.substring(0, colon).uppercase()
                        val portHex = local.substring(colon + 1).uppercase()
                        portHex == hexPort && hostHex in validHosts
                    }
                }
            }.getOrDefault(false)
        }
    }
}
