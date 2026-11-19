package io.nekohasekai.sagernet.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/** Raised when the transparent mode cannot be brought up. */
open class TunException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Transparent (TUN) mode for the desktop client.
 *
 * The core CLI cannot create a TUN device in this fork (on Android the app creates
 * one through `VpnService` and hands the fd to the gomobile library), so desktop
 * transparent mode is assembled from two pieces:
 *
 *  1. the core keeps serving its local SOCKS inbound,
 *  2. `tun2socks` owns the TUN device and pumps it into that SOCKS port.
 *
 * The core's outbound is bound to the physical interface (`bindToDevice`, supported
 * on Linux/macOS/Windows by the core), so its own connections never re-enter the
 * tunnel. Routes, addresses and the device itself are set up with the platform
 * tools following the tun2socks documentation.
 *
 * Everything needs administrator rights: Linux `sudo` (or CAP_NET_ADMIN), macOS
 * `sudo`, Windows an elevated process.
 */
class TunSession(private val log: (String) -> Unit) {

    private var pump: Process? = null
    private val teardown = ArrayList<() -> Unit>()

    @Volatile
    var device: String = ""
        private set

    val running: Boolean get() = pump?.isAlive == true

    /**
     * Starts the pump and configures the OS. Throws [TunException] with a reason.
     *
     * @param perApp Linux only: do not install the global split default routes.
     *   [PerAppRouting] then sends only the marked cgroup traffic into the device.
     */
    fun start(
        primaryInterface: String,
        mtu: Int,
        socksPort: Int,
        requestedDevice: String = "",
        ipv6: Boolean = false,
        perApp: Boolean = false,
    ) {
        val binary = DesktopRuntime.tun2socksBinary()
            ?: throw TunException(
                "tun2socks is not available for ${DesktopRuntime.platformTag}; " +
                    "run `./run desktop tun2socks download`"
            )
        if (DesktopRuntime.isWindows && !File(binary.parentFile, "wintun.dll").isFile) {
            throw TunException(
                "wintun.dll is missing next to ${binary.name}; download it from https://www.wintun.net/ " +
                    "and put it into ${binary.parentFile}"
            )
        }

        try {
            when (DesktopRuntime.os) {
                "windows" -> startWindows(binary, primaryInterface, mtu, socksPort, ipv6)
                "darwin" -> startMac(binary, primaryInterface, mtu, socksPort, ipv6)
                else -> startLinux(binary, primaryInterface, mtu, socksPort, requestedDevice, ipv6, perApp)
            }
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun stop() {
        for (action in teardown.asReversed()) {
            runCatching { action() }.onFailure { log("tun cleanup: ${it.message}") }
        }
        teardown.clear()
        pump?.let { process ->
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        pump = null
        device = ""
    }

    // ------------------------------------------------------------------ linux

    private fun startLinux(
        binary: File,
        primaryInterface: String,
        mtu: Int,
        socksPort: Int,
        requestedDevice: String,
        ipv6: Boolean,
        perApp: Boolean,
    ) {
        val name = requestedDevice.ifBlank { DEFAULT_LINUX_DEVICE }
        device = name

        runQuiet("ip", "tuntap", "add", "mode", "tun", "dev", name)
        teardown.add { runQuiet("ip", "link", "delete", name) }

        run("ip", "addr", "add", TUN_ADDRESS, "dev", name)
        run("ip", "link", "set", "dev", name, "up", "mtu", mtu.toString())

        if (perApp) {
            // No global routes: PerAppRouting installs the fwmark rule and the
            // default route in its own table, so only the listed processes are
            // captured. IPv6 split routes would capture everything, so they are
            // skipped too.
            log("transparent mode: $name up, per-app routing (no global routes)")
        } else {
            // Split defaults are more specific than the real default route, so the
            // original one stays untouched and simply takes over again on teardown.
            run("ip", "route", "add", "0.0.0.0/1", "dev", name, "metric", "1")
            teardown.add { runQuiet("ip", "route", "del", "0.0.0.0/1", "dev", name) }
            run("ip", "route", "add", "128.0.0.0/1", "dev", name, "metric", "1")
            teardown.add { runQuiet("ip", "route", "del", "128.0.0.0/1", "dev", name) }

            if (ipv6) setupIpv6Linux(name)
        }

        // tun2socks receives packets from other interfaces; loose rp_filter is the
        // documented requirement.
        runQuiet("sysctl", "-q", "-w", "net.ipv4.conf.all.rp_filter=0")
        runQuiet("sysctl", "-q", "-w", "net.ipv4.conf.$name.rp_filter=0")

        startPump(binary, name, primaryInterface, socksPort)
        if (!perApp) {
            log("transparent mode: $name up, $TUN_ADDRESS, default via the tunnel (physical: $primaryInterface)")
        }
    }

    /**
     * The Android `enableVPNInterfaceIPv6Address` row: add the tunnel's IPv6
     * address and the split default routes. Best effort, because a host without
     * IPv6 still gets a working IPv4 tunnel; the failure is logged.
     */
    private fun setupIpv6Linux(name: String) {
        try {
            run("ip", "-6", "addr", "add", "$TUN_ADDRESS6/128", "dev", name)
            teardown.add { runQuiet("ip", "-6", "addr", "del", "$TUN_ADDRESS6/128", "dev", name) }
            run("ip", "-6", "route", "add", "::/1", "dev", name, "metric", "1")
            teardown.add { runQuiet("ip", "-6", "route", "del", "::/1", "dev", name) }
            run("ip", "-6", "route", "add", "8000::/1", "dev", name, "metric", "1")
            teardown.add { runQuiet("ip", "-6", "route", "del", "8000::/1", "dev", name) }
            log("transparent mode: IPv6 $TUN_ADDRESS6 routed into $name")
        } catch (e: Exception) {
            log("transparent mode: IPv6 setup failed, continuing with IPv4 only: ${e.message}")
        }
    }

    // ------------------------------------------------------------------- macos

    private fun startMac(binary: File, primaryInterface: String, mtu: Int, socksPort: Int, ipv6: Boolean) {
        // macOS assigns utun devices, so a free name has to be picked up front and
        // tun2socks has to create the device before it can be configured.
        val name = freeUtun()
        device = name
        startPump(binary, name, primaryInterface, socksPort)
        waitForDevice(name)

        run("ifconfig", name, "198.18.0.1", "198.18.0.1", "up")
        runQuiet("ifconfig", name, "mtu", mtu.toString())
        for (route in MAC_ROUTES) {
            run("route", "add", "-net", route, "198.18.0.1")
            // a for loop variable is a fresh val per iteration, so capturing it here
            // deletes exactly the route that was just added
            teardown.add { runQuiet("route", "delete", "-net", route) }
        }
        if (ipv6) setupIpv6Mac(name)
        log("transparent mode: $name up, all IPv4 traffic routed into the tunnel (physical: $primaryInterface)")
    }

    private fun setupIpv6Mac(name: String) {
        try {
            run("ifconfig", name, "inet6", TUN_ADDRESS6, "prefixlen", "128", "up")
            run("route", "add", "-inet6", "-net", "::/1", "-interface", name)
            teardown.add { runQuiet("route", "delete", "-inet6", "-net", "::/1", "-interface", name) }
            run("route", "add", "-inet6", "-net", "8000::/1", "-interface", name)
            teardown.add { runQuiet("route", "delete", "-inet6", "-net", "8000::/1", "-interface", name) }
            log("transparent mode: IPv6 $TUN_ADDRESS6 routed into $name")
        } catch (e: Exception) {
            log("transparent mode: IPv6 setup failed, continuing with IPv4 only: ${e.message}")
        }
    }

    // ----------------------------------------------------------------- windows

    private fun startWindows(binary: File, primaryInterface: String, mtu: Int, socksPort: Int, ipv6: Boolean) {
        val name = WINDOWS_DEVICE
        device = name
        startPump(binary, name, primaryInterface, socksPort)
        waitForDevice(name)

        val setupPs = buildString {
            append("\$ErrorActionPreference = 'Stop'; ")
            append("\$idx = [int](Get-NetAdapter -Name '$name' -IncludeHidden).ifIndex; ")
            append("Set-NetIPInterface -InterfaceIndex \$idx -AddressFamily IPv4 -Dhcp Disabled -DadTransmits 0 -InterfaceMetric 1 -NlMtuBytes $mtu -ErrorAction SilentlyContinue | Out-Null; ")
            append("Get-NetIPAddress -InterfaceIndex \$idx -AddressFamily IPv4 -ErrorAction SilentlyContinue | Remove-NetIPAddress -Confirm:\$false -ErrorAction SilentlyContinue; ")
            append("New-NetIPAddress -InterfaceIndex \$idx -IPAddress '198.18.0.1' -PrefixLength 15 -PolicyStore ActiveStore | Out-Null; ")
            append("Set-DnsClientServerAddress -InterfaceIndex \$idx -ServerAddresses @('1.1.1.1','8.8.8.8') -ErrorAction SilentlyContinue | Out-Null; ")
            append("foreach (\$p in @('0.0.0.0/1','128.0.0.0/1')) { ")
            append("Get-NetRoute -InterfaceIndex \$idx -DestinationPrefix \$p -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:\$false -ErrorAction SilentlyContinue; ")
            append("New-NetRoute -InterfaceIndex \$idx -DestinationPrefix \$p -NextHop '0.0.0.0' -RouteMetric 1 -PolicyStore ActiveStore | Out-Null ")
            append("}; ")
            append("Clear-DnsClientCache -ErrorAction SilentlyContinue")
        }
        run("powershell", "-NoProfile", "-NonInteractive", "-Command", setupPs)
        teardown.add {
            val cleanPs = "Get-NetAdapter -Name '$name' -ErrorAction SilentlyContinue | ForEach-Object { " +
                "Remove-NetRoute -InterfaceIndex \$_.ifIndex -DestinationPrefix '0.0.0.0/1' -Confirm:\$false -ErrorAction SilentlyContinue; " +
                "Remove-NetRoute -InterfaceIndex \$_.ifIndex -DestinationPrefix '128.0.0.0/1' -Confirm:\$false -ErrorAction SilentlyContinue " +
                "}; Clear-DnsClientCache -ErrorAction SilentlyContinue"
            runQuiet("powershell", "-NoProfile", "-NonInteractive", "-Command", cleanPs)
        }
        if (ipv6) setupIpv6Windows(name)
        log("transparent mode: $name up, split default routes via the tunnel (physical: $primaryInterface)")
    }

    private fun setupIpv6Windows(name: String) {
        try {
            val ipv6Ps = buildString {
                append("\$ErrorActionPreference = 'Stop'; ")
                append("\$idx = [int](Get-NetAdapter -Name '$name').ifIndex; ")
                append("New-NetIPAddress -InterfaceIndex \$idx -AddressFamily IPv6 -IPAddress '$TUN_ADDRESS6' -PrefixLength 126 -PolicyStore ActiveStore | Out-Null; ")
                append("foreach (\$p in @('::/1','8000::/1')) { ")
                append("Remove-NetRoute -InterfaceIndex \$idx -AddressFamily IPv6 -DestinationPrefix \$p -Confirm:\$false -ErrorAction SilentlyContinue; ")
                append("New-NetRoute -InterfaceIndex \$idx -AddressFamily IPv6 -DestinationPrefix \$p -NextHop '::' -RouteMetric 1 -PolicyStore ActiveStore | Out-Null ")
                append("}")
            }
            run("powershell", "-NoProfile", "-NonInteractive", "-Command", ipv6Ps)
            teardown.add {
                val cleanPs = "Get-NetAdapter -Name '$name' -ErrorAction SilentlyContinue | ForEach-Object { " +
                    "Remove-NetRoute -InterfaceIndex \$_.ifIndex -AddressFamily IPv6 -DestinationPrefix '::/1' -Confirm:\$false -ErrorAction SilentlyContinue; " +
                    "Remove-NetRoute -InterfaceIndex \$_.ifIndex -AddressFamily IPv6 -DestinationPrefix '8000::/1' -Confirm:\$false -ErrorAction SilentlyContinue " +
                    "}"
                runQuiet("powershell", "-NoProfile", "-NonInteractive", "-Command", cleanPs)
            }
            log("transparent mode: IPv6 $TUN_ADDRESS6 routed into $name")
        } catch (e: Exception) {
            log("transparent mode: IPv6 setup failed, continuing with IPv4 only: ${e.message}")
        }
    }

    // -------------------------------------------------------------------- pump

    private fun startPump(binary: File, dev: String, primaryInterface: String, socksPort: Int) {
        val deviceArg = if (DesktopRuntime.isWindows) WINDOWS_DEVICE else dev
        val command = buildList {
            add(binary.absolutePath)
            add("--device")
            add(deviceArg)
            add("--proxy")
            add("socks5://127.0.0.1:$socksPort")
            if (!DesktopRuntime.isWindows) {
                add("--interface")
                add(primaryInterface)
            }
            add("--loglevel")
            add("info")
        }
        log("starting ${binary.name}: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .directory(binary.parentFile)
            .redirectErrorStream(true)
            .start()
        pump = process
        Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { log("[tun2socks] $it") } }
        }.apply { isDaemon = true }.start()
        Thread {
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            if (running && code != 0) log("tun2socks exited with code $code")
        }.apply { isDaemon = true }.start()
    }

    private fun waitForDevice(name: String) {
        val deadline = System.currentTimeMillis() + DEVICE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (pump?.isAlive != true) throw TunException("tun2socks terminated while creating the device")
            val visible = when (DesktopRuntime.os) {
                "windows" -> runQuiet(
                    "powershell",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    "(Get-NetAdapter -Name '$name' -IncludeHidden -ErrorAction SilentlyContinue).Name",
                ).isNotEmpty()
                else -> runQuiet("ifconfig", name).isNotEmpty()
            }
            if (visible) return
            Thread.sleep(200)
        }
        throw TunException("the TUN device $name did not appear in time")
    }

    private fun freeUtun(): String {
        val existing = runQuiet("ifconfig", "-l")
        for (index in 0 until 32) {
            val candidate = "utun$index"
            if (!existing.split(Regex("\\s+")).contains(candidate)) return candidate
        }
        return "utun32"
    }

    // ----------------------------------------------------------------- helpers

    private fun run(vararg command: String): String {
        val output = execute(command.toList())
        return output
    }

    private fun runQuiet(vararg command: String): String = runCatching { execute(command.toList()) }.getOrDefault("")

    private fun execute(command: List<String>): String {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            throw TunException("cannot run ${command.first()}: ${e.message}", e)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw TunException("${command.joinToString(" ")} timed out")
        }
        val code = process.exitValue()
        if (code != 0) {
            val permission = output.contains("Permission denied", true) ||
                output.contains("Operation not permitted", true) ||
                output.contains("Access is denied", true) ||
                output.contains("requires elevation", true)
            val hint = if (permission) {
                when (DesktopRuntime.os) {
                    "windows" -> " (start Owenclave as Administrator)"
                    "darwin" -> " (start Owenclave with sudo)"
                    else -> " (start Owenclave as root, or grant CAP_NET_ADMIN)"
                }
            } else {
                ""
            }
            throw TunException(
                "transparent mode needs administrator rights$hint: " +
                    "${command.joinToString(" ")} failed with $output"
            )
        }
        return output
    }

    companion object {

        /** Address of the tunnel, matching the ranges used by the tun2socks docs. */
        const val TUN_ADDRESS = "198.18.0.1/15"

        /**
         * IPv6 address of the tunnel when `enableVPNInterfaceIPv6Address` is on.
         * It is a unique local address, the same range the Android app uses for
         * its fake DNS pools, so it never collides with a public prefix.
         */
        const val TUN_ADDRESS6 = "fdfe:dcba:9876::1"

        /** Windows names the adapter itself. */
        const val WINDOWS_DEVICE = "wintun"

        /** Interface name used on Linux unless the settings override it. */
        const val DEFAULT_LINUX_DEVICE = "owenclave-tun"

        private const val DEVICE_TIMEOUT_MS = 15_000L

        /** The routes the tun2socks documentation adds on macOS. */
        private val MAC_ROUTES = listOf(
            "1.0.0.0/8",
            "2.0.0.0/7",
            "4.0.0.0/6",
            "8.0.0.0/5",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/2",
            "128.0.0.0/1",
            "198.18.0.0/15",
        )

        /**
         * Interface the core's outbound is bound to (and that tun2socks uses as its
         * physical interface), so tunneled traffic never loops back into the tunnel.
         */
        fun primaryInterface(): String = when (DesktopRuntime.os) {
            "windows" -> windowsPrimary().first
            "darwin" -> macPrimary().first
            else -> linuxPrimary().first
        }

        fun defaultGateway(): String = when (DesktopRuntime.os) {
            "windows" -> windowsPrimary().second
            "darwin" -> macPrimary().second
            else -> linuxPrimary().second
        }

        private fun output(command: List<String>): String = runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(15, TimeUnit.SECONDS)
            text
        }.getOrDefault("")

        private fun linuxPrimary(): Pair<String, String> {
            val text = output(listOf("ip", "route", "show", "default"))
            val line = text.lineSequence().firstOrNull { it.contains("default") }.orEmpty()
            val gateway = Regex("via\\s+(\\S+)").find(line)?.groupValues?.get(1).orEmpty()
            val interfaceName = Regex("dev\\s+(\\S+)").find(line)?.groupValues?.get(1).orEmpty()
            if (interfaceName.isEmpty()) {
                throw TunException("cannot detect the default network interface: ${text.trim()}")
            }
            return interfaceName to gateway
        }

        private fun macPrimary(): Pair<String, String> {
            val text = output(listOf("route", "-n", "get", "default"))
            val interfaceName = Regex("interface:\\s+(\\S+)").find(text)?.groupValues?.get(1).orEmpty()
            val gateway = Regex("gateway:\\s+(\\S+)").find(text)?.groupValues?.get(1).orEmpty()
            if (interfaceName.isEmpty()) {
                throw TunException("cannot detect the default network interface: ${text.trim()}")
            }
            return interfaceName to gateway
        }

        private fun windowsPrimary(): Pair<String, String> {
            // Two single property reads. The previous version asked PowerShell for a
            // formatted string built inside ForEach-Object with $_, and on the CI runner
            // PowerShell answered with a parse error whose text contains a "|"; the
            // parser below then took that error as the interface name. Asking for one
            // property at a time keeps the output to a single line, and the effective
            // route metric includes the interface metric.
            val route = "(Get-NetRoute -DestinationPrefix 0.0.0.0/0 | " +
                "Sort-Object -Property RouteMetric, InterfaceMetric | Select-Object -First 1)"
            val interfaceName = powershellValue("$route.InterfaceAlias")
            val gateway = powershellValue("$route.NextHop")
            if (interfaceName.isBlank() || interfaceName.contains('+')) {
                throw TunException(
                    "cannot detect the default network interface" +
                        if (interfaceName.isBlank()) "" else ": $interfaceName"
                )
            }
            return interfaceName to gateway
        }

        /** Runs a PowerShell expression and returns its first line of real output. */
        private fun powershellValue(script: String): String {
            val text = output(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", script))
            if (text.contains("At line:") || text.contains("CategoryInfo")) return ""
            return text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        }

    }

}
