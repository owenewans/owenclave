package io.nekohasekai.sagernet.desktop

import android.util.Log
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Raised when administrator/Polkit authorization for VPN mode is cancelled or unavailable. */
class TunAuthorizationException(message: String, cause: Throwable? = null) :
    TunException(message, cause),
    BaseService.ExpectedException

/** Parameters describing the desired desktop VPN / TUN session. */
data class PrivilegedTunParams(
    val owenclavePid: Long = ProcessHandle.current().pid(),
    val socksPort: Int,
    val socksUsername: String = "",
    val socksPassword: String = "",
    val localDnsPort: Int = 0,
    val mtu: Int = 1500,
    val tunDevice: String = TunSession.DEFAULT_LINUX_DEVICE,
    val enableIpv6: Boolean = false,
    val bypassLan: Boolean = true,
    val bypassServerIps: List<String> = emptyList(),
) {
    internal data class TunnelKey(
        val socksPort: Int,
        val socksUsername: String,
        val socksPassword: String,
        val localDnsPort: Int,
        val mtu: Int,
        val tunDevice: String,
        val enableIpv6: Boolean,
        val bypassLan: Boolean,
    )

    internal val key: TunnelKey
        get() = TunnelKey(
            socksPort = socksPort,
            socksUsername = socksUsername,
            socksPassword = socksPassword,
            localDnsPort = localDnsPort,
            mtu = mtu,
            tunDevice = tunDevice,
            enableIpv6 = enableIpv6,
            bypassLan = bypassLan,
        )
}

/** Abstraction over OS process spawning so privilege-elevation flows are 100% unit-testable. */
interface PrivilegedProcessLauncher {
    fun start(command: List<String>, env: Map<String, String> = emptyMap()): Process
    fun findExecutable(name: String): File?
    fun currentUid(): Int
    fun isWindowsAdmin(): Boolean
}

object SystemPrivilegedProcessLauncher : PrivilegedProcessLauncher {
    override fun start(command: List<String>, env: Map<String, String>): Process {
        val pb = ProcessBuilder(command)
        if (env.isNotEmpty()) {
            pb.environment().putAll(env)
        }
        return pb.start()
    }

    override fun findExecutable(name: String): File? {
        val pathEnv = System.getenv("PATH").orEmpty()
        val dirs = pathEnv.split(File.pathSeparatorChar).filter { it.isNotBlank() } +
            listOf("/usr/bin", "/bin", "/usr/sbin", "/sbin", "/usr/local/bin")
        val names = if (DesktopRuntime.isWindows && !name.endsWith(".exe", ignoreCase = true)) {
            listOf("$name.exe", name)
        } else {
            listOf(name)
        }
        for (dir in dirs) {
            for (candidateName in names) {
                val f = File(dir, candidateName)
                if (f.isFile && f.canExecute()) return f
            }
        }
        return null
    }

    override fun currentUid(): Int {
        if (DesktopRuntime.isWindows) return -1
        return runCatching {
            val proc = ProcessBuilder("id", "-u").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            proc.waitFor(5, TimeUnit.SECONDS)
            out.toIntOrNull() ?: -1
        }.getOrDefault(-1)
    }

    override fun isWindowsAdmin(): Boolean {
        if (!DesktopRuntime.isWindows) return false
        return runCatching {
            val proc = ProcessBuilder("net", "session").redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0
        }.getOrDefault(false)
    }
}

/**
 * Manages the elevated desktop VPN (`tun2socks` + OS routes + loop-free socket bypass)
 * for `ComposeMainActivity` / `ProxyService` when `DataStore.serviceMode == Key.MODE_VPN`.
 *
 * - **Linux**: Launches `owenclave-tun-helper` via `pkexec` (Polkit graphical dialog)
 *   when unprivileged, or directly when `uid == 0` (with graphical `sudo -A` askpass
 *   fallback when no Polkit agent is running). Keeps a single elevated supervisor
 *   open across profile reloads and tears down cleanly on `STOP` or EOF without
 *   prompting a second time.
 * - **macOS**: Prompts via `osascript ... with administrator privileges` when unprivileged.
 * - **Windows**: Prompts via UAC (`Start-Process -Verb RunAs`) when not Administrator.
 */
class DesktopPrivilegedTun(
    private val os: String = DesktopRuntime.os,
    private val dataDir: File = DesktopRuntime.dataDir,
    private val launcher: PrivilegedProcessLauncher = SystemPrivilegedProcessLauncher,
    private val helperLocator: () -> File? = { DesktopRuntime.tunHelperScript() },
    private val tun2socksLocator: () -> File? = { DesktopRuntime.tun2socksBinary() },
    private val authTimeoutMs: Long = 120_000L,
) {

    private val lock = Any()
    private var supervisorProcess: Process? = null
    private var supervisorStdin: BufferedWriter? = null
    private var keepaliveFile: File? = null
    private var statusMonitorFile: File? = null
    private var bypassListFile: File? = null
    private var macHelperPid: Long = 0L
    private var directWinTun: TunSession? = null
    private var activeKey: PrivilegedTunParams.TunnelKey? = null
    private val registeredBypassIps = LinkedHashSet<String>()

    fun isRunning(): Boolean = synchronized(lock) {
        when {
            supervisorProcess != null -> supervisorProcess?.isAlive == true
            directWinTun != null -> directWinTun?.running == true
            keepaliveFile != null -> keepaliveFile?.isFile == true
            else -> false
        }
    }

    /**
     * Pre-registers bypass host routes on an already-running tunnel BEFORE `super.startProcesses()`
     * spawns a reloaded `v2rayPoint` or external `naive` child process so initial outbound packets
     * never race ahead of the `/32` physical gateway route.
     */
    fun prepareBypassIps(ips: List<String>) {
        synchronized(lock) {
            if (!isRunning() || ips.isEmpty()) return
            val stdin = supervisorStdin
            if (os == "linux" && stdin != null) {
                runCatching {
                    var changed = false
                    for (ip in ips) {
                        val clean = ip.trim()
                        if (clean.matches(SAFE_IP_REGEX) && registeredBypassIps.add(clean)) {
                            stdin.write("ADD_BYPASS $clean\n")
                            changed = true
                        }
                    }
                    if (changed) stdin.flush()
                }
            } else if ((os == "darwin" || os == "windows") && keepaliveFile?.isFile == true) {
                val bf = bypassListFile ?: return
                val added = ArrayList<String>()
                for (ip in ips) {
                    val clean = ip.trim()
                    if (clean.matches(SAFE_IP_REGEX) && registeredBypassIps.add(clean)) {
                        added += clean
                    }
                }
                if (added.isNotEmpty()) {
                    runCatching { bf.appendText(added.joinToString("\n", postfix = "\n")) }
                    if (macHelperPid > 0L) {
                        Thread.sleep(300L)
                    }
                }
            }
        }
    }

    /**
     * Ensures the TUN session is running with [params]. If an identical session is
     * already up (e.g. during a profile switch via `SagerNet.reloadService()`),
     * updates server bypass routes and re-attaches child processes in place without
     * dropping the tunnel or re-prompting for administrator authorization.
     */
    fun start(params: PrivilegedTunParams) {
        synchronized(lock) {
            if (isRunning() && activeKey == params.key) {
                val stdin = supervisorStdin
                if (os == "linux" && stdin != null) {
                    val ok = runCatching {
                        for (ip in params.bypassServerIps) {
                            if (registeredBypassIps.add(ip)) {
                                stdin.write("ADD_BYPASS $ip\n")
                            }
                        }
                        stdin.write("ATTACH_CHILDREN\n")
                        stdin.flush()
                        true
                    }.getOrDefault(false)
                    if (ok) {
                        Log.i(TAG, "Reused active VPN tunnel for reloaded profile")
                        return
                    }
                } else if ((os == "darwin" || os == "windows") && keepaliveFile?.isFile == true) {
                    val bf = bypassListFile
                    if (bf != null) {
                        val added = ArrayList<String>()
                        for (ip in params.bypassServerIps) {
                            val clean = ip.trim()
                            if (clean.matches(SAFE_IP_REGEX) && registeredBypassIps.add(clean)) {
                                added += clean
                            }
                        }
                        if (added.isNotEmpty()) {
                            runCatching { bf.appendText(added.joinToString("\n", postfix = "\n")) }
                        }
                        Log.i(TAG, "Reused active $os VPN tunnel for reloaded profile")
                        return
                    }
                } else if ((keepaliveFile?.isFile == true || directWinTun?.running == true) &&
                    registeredBypassIps.containsAll(params.bypassServerIps)
                ) {
                    return
                }
            }

            stopLocked()

            when (os) {
                "linux" -> startLinuxLocked(params)
                "darwin" -> startMacLocked(params)
                "windows" -> startWindowsLocked(params)
                else -> throw TunException("Unsupported desktop OS for VPN mode: $os")
            }
            activeKey = params.key
            registeredBypassIps.clear()
            registeredBypassIps.addAll(params.bypassServerIps)
        }
    }

    /** Tears down the active TUN session, routes, and helper process. Idempotent. */
    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        activeKey = null
        registeredBypassIps.clear()

        val prevStatusFile = statusMonitorFile
        val prevBypassFile = bypassListFile
        val prevMacPid = macHelperPid
        statusMonitorFile = null
        bypassListFile = null
        macHelperPid = 0L

        keepaliveFile?.let { f ->
            runCatching { f.delete() }
        }
        keepaliveFile = null

        directWinTun?.let { tun ->
            runCatching { tun.stop() }
        }
        directWinTun = null

        val stdin = supervisorStdin
        val proc = supervisorProcess
        supervisorStdin = null
        supervisorProcess = null

        if (stdin != null) {
            runCatching {
                stdin.write("STOP\n")
                stdin.flush()
                stdin.close()
            }
        }
        if (proc != null) {
            if (!runCatching { proc.waitFor(5, TimeUnit.SECONDS) }.getOrDefault(false)) {
                runCatching { proc.destroy() }
                if (!runCatching { proc.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)) {
                    runCatching { proc.destroyForcibly() }
                }
            }
        } else if ((os == "darwin" || os == "windows") && prevMacPid > 0L) {
            val waitDeadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < waitDeadline) {
                val alive = runCatching {
                    ProcessHandle.of(prevMacPid).map { it.isAlive }.orElse(false)
                }.getOrDefault(false)
                if (!alive || prevStatusFile?.isFile != true) break
                Thread.sleep(50L)
            }
        }
        prevBypassFile?.let { f -> runCatching { f.delete() } }
        prevStatusFile?.let { f -> runCatching { f.delete() } }
    }

    // ------------------------------------------------------------------- Linux

    internal fun buildLinuxHelperArgs(
        helperFile: File,
        tun2socksFile: File,
        params: PrivilegedTunParams,
    ): List<String> {
        val args = ArrayList<String>()
        args += helperFile.absolutePath
        args += listOf("--pid", params.owenclavePid.toString())
        args += listOf("--socks-port", params.socksPort.toString())
        args += listOf("--dns-port", params.localDnsPort.coerceAtLeast(0).toString())
        args += listOf("--mtu", params.mtu.coerceIn(1280, 9000).toString())
        args += listOf("--tun-dev", params.tunDevice)
        args += listOf("--ipv6", if (params.enableIpv6) "1" else "0")
        args += listOf("--bypass-lan", if (params.bypassLan) "1" else "0")
        args += listOf("--tun2socks", tun2socksFile.absolutePath)
        for (ip in params.bypassServerIps) {
            val trimmed = ip.trim()
            if (trimmed.isNotEmpty()) {
                args += listOf("--bypass-ip", trimmed)
            }
        }
        return args
    }

    private fun startLinuxLocked(params: PrivilegedTunParams) {
        val tun2socks = tun2socksLocator()
            ?: throw TunException("tun2socks binary is missing for ${DesktopRuntime.platformTag}")
        val helper = helperLocator()
            ?: throw TunException("owenclave-tun-helper is missing from the installation")
        runCatching { helper.setExecutable(true, false) }
        runCatching { tun2socks.setExecutable(true, false) }

        val helperArgs = buildLinuxHelperArgs(helper, tun2socks, params)
        val uid = launcher.currentUid()

        if (uid == 0) {
            launchLinuxSupervisor(helperArgs, emptyMap(), params)
            return
        }

        val pkexec = launcher.findExecutable("pkexec")
        if (pkexec != null) {
            try {
                launchLinuxSupervisor(listOf(pkexec.absolutePath) + helperArgs, emptyMap(), params)
                return
            } catch (e: NoPolkitAgentException) {
                Log.w(TAG, "Polkit agent unavailable (${e.message}), checking graphical askpass fallback")
            }
        }

        val sudoAskpass = resolveLinuxAskpassCommand()
        val sudo = launcher.findExecutable("sudo")
        if (sudo != null && sudoAskpass != null) {
            launchLinuxSupervisor(
                command = listOf(sudo.absolutePath, "-A", "-k") + helperArgs,
                env = mapOf("SUDO_ASKPASS" to sudoAskpass.absolutePath),
                params = params,
            )
            return
        }

        if (pkexec == null) {
            throw TunAuthorizationException(
                "pkexec (Polkit) is not installed; install polkit or switch Service Mode to Proxy"
            )
        }
        throw TunAuthorizationException(
            "No Polkit authentication agent is running in this desktop session " +
                "(start polkit-kde-agent, polkit-gnome, or hyprpolkitagent)"
        )
    }

    private class NoPolkitAgentException(message: String) : Exception(message)

    private fun launchLinuxSupervisor(
        command: List<String>,
        env: Map<String, String>,
        params: PrivilegedTunParams,
    ) {
        Log.i(TAG, "Requesting privileged TUN setup: ${command.firstOrNull()}")
        val process = try {
            launcher.start(command, env)
        } catch (e: Exception) {
            throw TunException("Cannot launch privileged helper: ${e.message}", e)
        }

        val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
        val readyLatch = CountDownLatch(1)
        val readyLine = AtomicReference<String?>(null)
        val stderrLines = Collections.synchronizedList(ArrayList<String>())

        val handshake = if (params.socksUsername.isNotEmpty() || params.socksPassword.isNotEmpty()) {
            val u64 = Base64.getEncoder().encodeToString(params.socksUsername.toByteArray(StandardCharsets.UTF_8))
            val p64 = Base64.getEncoder().encodeToString(params.socksPassword.toByteArray(StandardCharsets.UTF_8))
            "AUTH $u64 $p64\n"
        } else {
            "START\n"
        }

        val stderrThread = Thread({
            runCatching {
                BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        if (stderrLines.size < 40) stderrLines.add(line)
                        Log.i("OwenclaveTUN", line)
                    }
                }
            }
        }, "owenclave-tun-stderr").apply {
            isDaemon = true
            start()
        }

        Thread({
            runCatching {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("OWENCLAVE_TUN_READY")) {
                            readyLine.set(line)
                            readyLatch.countDown()
                        } else if (line != "OK") {
                            Log.i("OwenclaveTUN", line)
                        }
                    }
                }
            }
            readyLatch.countDown()
        }, "owenclave-tun-stdout").apply { isDaemon = true }.start()

        runCatching {
            writer.write(handshake)
            writer.flush()
        }

        val completed = readyLatch.await(authTimeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            runCatching { writer.close() }
            runCatching { process.destroyForcibly() }
            throw TunAuthorizationException("VPN authorization timed out")
        }

        val ready = readyLine.get()
        if (ready != null && process.isAlive) {
            supervisorProcess = process
            supervisorStdin = writer
            Log.i(TAG, "VPN tunnel active ($ready)")
            return
        }

        runCatching { writer.close() }
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        val exitCode = if (process.isAlive) {
            runCatching { process.destroyForcibly() }
            -1
        } else {
            process.exitValue()
        }
        runCatching { stderrThread.join(1_500L) }
        val combinedErr = synchronized(stderrLines) { stderrLines.joinToString("; ") }

        if (combinedErr.contains("No authentication agent found", ignoreCase = true)) {
            throw NoPolkitAgentException(combinedErr)
        }
        if (exitCode == 126 ||
            combinedErr.contains("Request dismissed", ignoreCase = true) ||
            combinedErr.contains("cancelled", ignoreCase = true) ||
            combinedErr.contains("canceled", ignoreCase = true)
        ) {
            throw TunAuthorizationException("VPN authorization was cancelled")
        }
        if (exitCode == 127 || combinedErr.contains("Not authorized", ignoreCase = true)) {
            throw TunAuthorizationException(
                "VPN authorization failed" + if (combinedErr.isNotBlank()) ": $combinedErr" else ""
            )
        }
        throw TunException(
            "Failed to start VPN tunnel" +
                if (combinedErr.isNotBlank()) ": $combinedErr" else " (exit code $exitCode)"
        )
    }

    private fun resolveLinuxAskpassCommand(): File? {
        val knownAskpass = listOf(
            "ksshaskpass",
            "lxqt-openssh-askpass",
            "ssh-askpass",
            "/usr/lib/ssh/ssh-askpass",
            "/usr/libexec/openssh/ssh-askpass",
            "/usr/lib/seahorse/ssh-askpass",
        )
        for (candidate in knownAskpass) {
            val f = if (candidate.startsWith("/")) {
                File(candidate).takeIf { it.isFile && it.canExecute() }
            } else {
                launcher.findExecutable(candidate)
            }
            if (f != null) return f
        }

        val zenity = launcher.findExecutable("zenity")
        if (zenity != null) {
            val script = File(File(dataDir, "runtime").apply { mkdirs() }, "owenclave-askpass.sh")
            script.writeText(
                "#!/bin/sh\nexec \"${zenity.absolutePath}\" --password --title=\"Owenclave VPN\"\n"
            )
            script.setExecutable(true, true)
            return script
        }

        val kdialog = launcher.findExecutable("kdialog")
        if (kdialog != null) {
            val script = File(File(dataDir, "runtime").apply { mkdirs() }, "owenclave-askpass.sh")
            script.writeText(
                "#!/bin/sh\nexec \"${kdialog.absolutePath}\" --title \"Owenclave VPN\" --password \"Требуется пароль администратора для запуска VPN-туннеля Owenclave:\"\n"
            )
            script.setExecutable(true, true)
            return script
        }

        return null
    }

    // ------------------------------------------------------------------- macOS

    internal fun buildMacHelperScript(
        keepalive: File,
        statusFile: File,
        bypassFile: File,
        tun2socks: File,
        params: PrivilegedTunParams,
    ): String {
        val authPart = if (params.socksUsername.isNotEmpty() || params.socksPassword.isNotEmpty()) {
            val u = java.net.URLEncoder.encode(params.socksUsername, StandardCharsets.UTF_8).replace("+", "%20")
            val p = java.net.URLEncoder.encode(params.socksPassword, StandardCharsets.UTF_8).replace("+", "%20")
            "$u:$p@"
        } else {
            ""
        }
        val proxyUrl = "socks5://${authPart}127.0.0.1:${params.socksPort}"
        val bypassLanFlag = if (params.bypassLan) "1" else "0"
        val enableIpv6Flag = if (params.enableIpv6) "1" else "0"

        return """
            #!/bin/sh
            export PATH="/usr/sbin:/sbin:/usr/bin:/bin:/usr/local/bin"
            set -eu
            KEEPALIVE="${keepalive.absolutePath}"
            STATUS="${statusFile.absolutePath}"
            BYPASS_FILE="${bypassFile.absolutePath}"
            TUN2SOCKS="${tun2socks.absolutePath}"
            OWNER_PID="${params.owenclavePid}"
            PROXY_URL="$proxyUrl"
            MTU="${params.mtu.coerceIn(1280, 9000)}"
            BYPASS_LAN="$bypassLanFlag"
            ENABLE_IPV6="$enableIpv6Flag"
            T2S_PID=""
            UTUN=""
            PHYS_GW=""
            PHYS_GW6=""
            ADDED_BYPASS_V4=""
            ADDED_BYPASS_V6=""
            LAN_ROUTES_ADDED="0"
            IPV6_ROUTES_ADDED="0"
            NET_SVC=""
            ORIG_DNS_RESTORE=""
            CLEANED_UP="0"

            write_status() {
              printf '%s\n' "${'$'}1" >"${'$'}{STATUS}.tmp"
              chmod 644 "${'$'}{STATUS}.tmp" >/dev/null 2>&1 || true
              mv -f "${'$'}{STATUS}.tmp" "${'$'}STATUS"
            }

            cleanup() {
              [ "${'$'}CLEANED_UP" = "1" ] && return
              CLEANED_UP="1"
              if [ -n "${'$'}NET_SVC" ] && [ -n "${'$'}ORIG_DNS_RESTORE" ]; then
                if [ "${'$'}ORIG_DNS_RESTORE" = "__EMPTY__" ]; then
                  networksetup -setdnsservers "${'$'}NET_SVC" "Empty" >/dev/null 2>&1 || true
                else
                  # shellcheck disable=SC2086
                  networksetup -setdnsservers "${'$'}NET_SVC" ${'$'}ORIG_DNS_RESTORE >/dev/null 2>&1 || true
                fi
                dscacheutil -flushcache >/dev/null 2>&1 || true
                killall -HUP mDNSResponder >/dev/null 2>&1 || true
              fi
              if [ "${'$'}IPV6_ROUTES_ADDED" = "1" ] && [ -n "${'$'}UTUN" ]; then
                route -n delete -inet6 -net ::/1 -interface "${'$'}UTUN" >/dev/null 2>&1 || true
                route -n delete -inet6 -net 8000::/1 -interface "${'$'}UTUN" >/dev/null 2>&1 || true
              fi
              for r in 1.0.0.0/8 2.0.0.0/7 4.0.0.0/6 8.0.0.0/5 16.0.0.0/4 32.0.0.0/3 64.0.0.0/2 128.0.0.0/1 198.18.0.0/15; do
                route -n delete -net "${'$'}r" 198.18.0.1 >/dev/null 2>&1 || route -n delete -net "${'$'}r" >/dev/null 2>&1 || true
              done
              if [ "${'$'}LAN_ROUTES_ADDED" = "1" ] && [ -n "${'$'}PHYS_GW" ]; then
                for lr in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16; do
                  route -n delete -net "${'$'}lr" "${'$'}PHYS_GW" >/dev/null 2>&1 || true
                done
              fi
              for ip in ${'$'}ADDED_BYPASS_V4; do
                [ -n "${'$'}PHYS_GW" ] && route -n delete -host "${'$'}ip" "${'$'}PHYS_GW" >/dev/null 2>&1 || true
              done
              for ip6 in ${'$'}ADDED_BYPASS_V6; do
                [ -n "${'$'}PHYS_GW6" ] && route -n delete -inet6 -host "${'$'}ip6" "${'$'}PHYS_GW6" >/dev/null 2>&1 || true
              done
              if [ -n "${'$'}T2S_PID" ]; then
                kill "${'$'}T2S_PID" >/dev/null 2>&1 || true
                wait "${'$'}T2S_PID" >/dev/null 2>&1 || true
              fi
              rm -f "${'$'}KEEPALIVE" "${'$'}BYPASS_FILE" "${'$'}{STATUS}.tmp" "${'$'}STATUS" >/dev/null 2>&1 || true
            }
            trap '' HUP
            trap cleanup EXIT INT TERM

            sync_bypass_ips() {
              [ -f "${'$'}BYPASS_FILE" ] || return 0
              while IFS= read -r ip || [ -n "${'$'}ip" ]; do
                [ -n "${'$'}ip" ] || continue
                case "${'$'}ip" in
                  *:*)
                    case " ${'$'}ADDED_BYPASS_V6 " in
                      *" ${'$'}ip "*) ;;
                      *)
                        if [ -n "${'$'}PHYS_GW6" ]; then
                          route -n add -inet6 -host "${'$'}ip" "${'$'}PHYS_GW6" >/dev/null 2>&1 || route -n change -inet6 -host "${'$'}ip" "${'$'}PHYS_GW6" >/dev/null 2>&1 || true
                          ADDED_BYPASS_V6="${'$'}ADDED_BYPASS_V6 ${'$'}ip"
                        fi
                        ;;
                    esac
                    ;;
                  *.*)
                    case " ${'$'}ADDED_BYPASS_V4 " in
                      *" ${'$'}ip "*) ;;
                      *)
                        if [ -n "${'$'}PHYS_GW" ]; then
                          route -n add -host "${'$'}ip" "${'$'}PHYS_GW" >/dev/null 2>&1 || route -n change -host "${'$'}ip" "${'$'}PHYS_GW" >/dev/null 2>&1 || true
                          ADDED_BYPASS_V4="${'$'}ADDED_BYPASS_V4 ${'$'}ip"
                        fi
                        ;;
                    esac
                    ;;
                esac
              done <"${'$'}BYPASS_FILE"
            }

            DEF_OUT="${'$'}(route -n get default 2>/dev/null || true)"
            PHYS_IF="${'$'}(printf '%s\n' "${'$'}DEF_OUT" | awk '/interface:/{print ${'$'}2; exit}')"
            PHYS_GW="${'$'}(printf '%s\n' "${'$'}DEF_OUT" | awk '/gateway:/{print ${'$'}2; exit}')"
            DEF6_OUT="${'$'}(route -n get -inet6 default 2>/dev/null || true)"
            PHYS_GW6="${'$'}(printf '%s\n' "${'$'}DEF6_OUT" | awk '/gateway:/{print ${'$'}2; exit}')"
            if [ -z "${'$'}PHYS_IF" ]; then
              write_status "ERROR:cannot detect default network interface"
              exit 1
            fi

            EXISTING="${'$'}(ifconfig -l 2>/dev/null || true)"
            i=0
            while [ "${'$'}i" -lt 32 ]; do
              cand="utun${'$'}i"
              case " ${'$'}EXISTING " in
                *" ${'$'}cand "*) i=${'$'}((i + 1)) ;;
                *) UTUN="${'$'}cand"; break ;;
              esac
            done
            [ -z "${'$'}UTUN" ] && UTUN="utun32"

            sync_bypass_ips
            if [ "${'$'}BYPASS_LAN" = "1" ] && [ -n "${'$'}PHYS_GW" ]; then
              for lr in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16; do
                route -n add -net "${'$'}lr" "${'$'}PHYS_GW" >/dev/null 2>&1 || true
              done
              LAN_ROUTES_ADDED="1"
            fi

            "${'$'}TUN2SOCKS" --device "${'$'}UTUN" --proxy "${'$'}PROXY_URL" --interface "${'$'}PHYS_IF" --loglevel warn &
            T2S_PID="${'$'}!"

            j=0
            while [ "${'$'}j" -lt 50 ]; do
              ifconfig "${'$'}UTUN" >/dev/null 2>&1 && break
              sleep 0.2
              j=${'$'}((j + 1))
            done
            if ! ifconfig "${'$'}UTUN" >/dev/null 2>&1; then
              write_status "ERROR:utun interface ${'$'}UTUN did not appear"
              exit 1
            fi

            ifconfig "${'$'}UTUN" 198.18.0.1 198.18.0.1 up
            ifconfig "${'$'}UTUN" mtu "${'$'}MTU" >/dev/null 2>&1 || true
            for r in 1.0.0.0/8 2.0.0.0/7 4.0.0.0/6 8.0.0.0/5 16.0.0.0/4 32.0.0.0/3 64.0.0.0/2 128.0.0.0/1 198.18.0.0/15; do
              route -n add -net "${'$'}r" 198.18.0.1 >/dev/null || route -n change -net "${'$'}r" 198.18.0.1 >/dev/null
            done

            if [ "${'$'}ENABLE_IPV6" = "1" ]; then
              if ifconfig "${'$'}UTUN" inet6 fdfe:dcba:9876::1 prefixlen 128 up >/dev/null 2>&1; then
                route -n add -inet6 -net ::/1 -interface "${'$'}UTUN" >/dev/null 2>&1 || true
                route -n add -inet6 -net 8000::/1 -interface "${'$'}UTUN" >/dev/null 2>&1 || true
                IPV6_ROUTES_ADDED="1"
              fi
            fi

            if command -v networksetup >/dev/null 2>&1; then
              NET_SVC="${'$'}(networksetup -listnetworkserviceorder 2>/dev/null | awk -v dev="${'$'}PHYS_IF" '
                /^\([0-9]+\) / { sub(/^\([0-9]+\) /, ""); svc=${'$'}0 }
                /Device:/ {
                  if (index(${'$'}0, "Device: " dev ")") > 0 || index(${'$'}0, "Device: " dev ",") > 0) {
                    print svc
                    exit
                  }
                }
              ')"
              if [ -n "${'$'}NET_SVC" ]; then
                CUR_DNS="${'$'}(networksetup -getdnsservers "${'$'}NET_SVC" 2>/dev/null || true)"
                if printf '%s\n' "${'$'}CUR_DNS" | grep -qi "There aren't any"; then
                  ORIG_DNS_RESTORE="__EMPTY__"
                  networksetup -setdnsservers "${'$'}NET_SVC" 1.1.1.1 8.8.8.8 >/dev/null 2>&1 || true
                  dscacheutil -flushcache >/dev/null 2>&1 || true
                  killall -HUP mDNSResponder >/dev/null 2>&1 || true
                elif printf '%s\n' "${'$'}CUR_DNS" | grep -Eq '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.|127\.|9\.9\.9\.9|fe80:)'; then
                  ORIG_DNS_RESTORE="${'$'}(printf '%s\n' "${'$'}CUR_DNS" | tr '\n' ' ')"
                  networksetup -setdnsservers "${'$'}NET_SVC" 1.1.1.1 8.8.8.8 >/dev/null 2>&1 || true
                  dscacheutil -flushcache >/dev/null 2>&1 || true
                  killall -HUP mDNSResponder >/dev/null 2>&1 || true
                fi
              fi
            fi

            write_status "OWENCLAVE_TUN_READY tun=${'$'}UTUN iface=${'$'}PHYS_IF pid=${'$'}${'$'}"
            while [ -f "${'$'}KEEPALIVE" ] && kill -0 "${'$'}OWNER_PID" 2>/dev/null && kill -0 "${'$'}T2S_PID" 2>/dev/null; do
              sync_bypass_ips
              sleep 0.2
            done
        """.trimIndent() + "\n"
    }

    private fun startMacLocked(params: PrivilegedTunParams) {
        val tun2socks = tun2socksLocator()
            ?: throw TunException("tun2socks binary is missing for ${DesktopRuntime.platformTag}")
        runCatching { tun2socks.setExecutable(true, false) }

        val runtimeDir = File(dataDir, "runtime").apply { mkdirs() }
        val token = System.nanoTime().toString()
        val keepalive = File(runtimeDir, "owenclave-tun-$token.keepalive").apply { writeText("1") }
        val statusFile = File(runtimeDir, "owenclave-tun-$token.status").apply { delete() }
        val bypassFile = File(runtimeDir, "owenclave-tun-$token.bypass").apply {
            val initialIps = params.bypassServerIps.map { it.trim() }.filter { it.matches(SAFE_IP_REGEX) }
            writeText(if (initialIps.isEmpty()) "" else initialIps.joinToString("\n", postfix = "\n"))
        }
        val scriptFile = File(runtimeDir, "owenclave-tun-macos.sh")

        scriptFile.writeText(buildMacHelperScript(keepalive, statusFile, bypassFile, tun2socks, params))
        scriptFile.setExecutable(true, true)

        val command = if (launcher.currentUid() == 0) {
            listOf("/bin/sh", scriptFile.absolutePath)
        } else {
            val escaped = scriptFile.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")
            listOf(
                "/usr/bin/osascript",
                "-e",
                "do shell script \"/usr/bin/nohup \\\"$escaped\\\" >/dev/null 2>&1 </dev/null &\" with administrator privileges",
            )
        }

        val proc = launcher.start(command)
        val errHolder = AtomicReference("")
        val errThread = Thread({
            runCatching {
                errHolder.set(proc.errorStream.bufferedReader().use { it.readText() }.trim())
            }
        }, "owenclave-tun-macos").apply {
            isDaemon = true
            start()
        }

        val deadline = System.currentTimeMillis() + authTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (statusFile.isFile) {
                val status = runCatching { statusFile.readText().trim() }.getOrDefault("")
                if (status.startsWith("OWENCLAVE_TUN_READY")) {
                    keepaliveFile = keepalive
                    statusMonitorFile = statusFile
                    bypassListFile = bypassFile
                    macHelperPid = Regex("pid=(\\d+)").find(status)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    if (launcher.currentUid() == 0) supervisorProcess = proc
                    Log.i(TAG, "macOS VPN tunnel active ($status)")
                    return
                }
                if (status.startsWith("ERROR:")) {
                    keepalive.delete()
                    bypassFile.delete()
                    statusFile.delete()
                    throw TunException(status.removePrefix("ERROR:"))
                }
            }
            if (!proc.isAlive && proc.exitValue() != 0) {
                keepalive.delete()
                bypassFile.delete()
                statusFile.delete()
                runCatching { errThread.join(1_500L) }
                val err = errHolder.get()
                if (err.contains("User canceled", ignoreCase = true) || err.contains("-128")) {
                    throw TunAuthorizationException("VPN authorization was cancelled")
                }
                throw TunAuthorizationException("Failed to authorize macOS VPN tunnel: $err")
            }
            Thread.sleep(150)
        }
        keepalive.delete()
        bypassFile.delete()
        statusFile.delete()
        throw TunAuthorizationException("VPN authorization timed out")
    }

    // ----------------------------------------------------------------- Windows

    internal fun buildWindowsHelperScript(
        keepalive: File,
        statusFile: File,
        bypassFile: File,
        tun2socks: File,
        params: PrivilegedTunParams,
    ): String {
        val tunName = params.tunDevice
            .takeIf { it.isNotBlank() && it != TunSession.DEFAULT_LINUX_DEVICE }
            ?: "OwenclaveTUN"
        val authPart = if (params.socksUsername.isNotEmpty() || params.socksPassword.isNotEmpty()) {
            val u = java.net.URLEncoder.encode(params.socksUsername, StandardCharsets.UTF_8).replace("+", "%20")
            val p = java.net.URLEncoder.encode(params.socksPassword, StandardCharsets.UTF_8).replace("+", "%20")
            "$u:$p@"
        } else {
            ""
        }
        val proxyUrl = "socks5://${authPart}127.0.0.1:${params.socksPort}"
        val mtuVal = params.mtu.coerceIn(1280, 9000)
        val bypassLanVal = if (params.bypassLan) 1 else 0
        val ipv6Val = if (params.enableIpv6) 1 else 0

        return """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}keepalive = '${keepalive.absolutePath.replace("'", "''")}'
            ${'$'}statusFile = '${statusFile.absolutePath.replace("'", "''")}'
            ${'$'}bypassFile = '${bypassFile.absolutePath.replace("'", "''")}'
            ${'$'}tun2socks = '${tun2socks.absolutePath.replace("'", "''")}'
            ${'$'}tunName = '${tunName.replace("'", "''")}'
            ${'$'}proxyUrl = '${proxyUrl.replace("'", "''")}'
            ${'$'}mtu = $mtuVal
            ${'$'}bypassLan = $bypassLanVal
            ${'$'}enableIpv6 = $ipv6Val
            ${'$'}ownerPid = ${params.owenclavePid}
            ${'$'}t2s = ${'$'}null
            ${'$'}t2sLog = "${'$'}statusFile.t2s.log"
            ${'$'}tunIdx = 0
            ${'$'}physIdx = 0
            ${'$'}gw = ''
            ${'$'}gw6 = ''
            ${'$'}ipv6Added = ${'$'}false
            ${'$'}addedLan = New-Object 'System.Collections.Generic.List[string]'
            ${'$'}addedBypass = New-Object 'System.Collections.Generic.HashSet[string]'
            ${'$'}utf8NoBom = New-Object System.Text.UTF8Encoding(${'$'}false)

            function Write-Status([string]${'$'}msg) {
              ${'$'}tmp = "${'$'}statusFile.tmp"
              [System.IO.File]::WriteAllText(${'$'}tmp, "${'$'}msg`n", ${'$'}utf8NoBom)
              Move-Item -Force -LiteralPath ${'$'}tmp -Destination ${'$'}statusFile
            }

            function Sync-BypassIps {
              if (-not (Test-Path -LiteralPath ${'$'}bypassFile)) { return }
              foreach (${'$'}raw in (Get-Content -LiteralPath ${'$'}bypassFile -ErrorAction SilentlyContinue)) {
                ${'$'}ip = "${'$'}raw".Trim()
                if (-not ${'$'}ip -or ${'$'}addedBypass.Contains(${'$'}ip)) { continue }
                if (${'$'}ip.Contains(':')) {
                  if (${'$'}gw6 -and ${'$'}physIdx -gt 0) {
                    ${'$'}cidr6 = "${'$'}ip/128"
                    if (-not (Get-NetRoute -DestinationPrefix ${'$'}cidr6 -ErrorAction SilentlyContinue)) {
                      New-NetRoute -DestinationPrefix ${'$'}cidr6 -InterfaceIndex ${'$'}physIdx -NextHop ${'$'}gw6 -RouteMetric 1 -PolicyStore ActiveStore -ErrorAction SilentlyContinue | Out-Null
                      [void]${'$'}addedBypass.Add(${'$'}cidr6)
                    }
                  }
                } elseif (${'$'}ip.Contains('.')) {
                  if (${'$'}gw -and ${'$'}gw -ne '0.0.0.0' -and ${'$'}physIdx -gt 0) {
                    ${'$'}cidr4 = "${'$'}ip/32"
                    if (-not (Get-NetRoute -DestinationPrefix ${'$'}cidr4 -ErrorAction SilentlyContinue)) {
                      New-NetRoute -DestinationPrefix ${'$'}cidr4 -InterfaceIndex ${'$'}physIdx -NextHop ${'$'}gw -RouteMetric 1 -PolicyStore ActiveStore -ErrorAction SilentlyContinue | Out-Null
                      [void]${'$'}addedBypass.Add(${'$'}cidr4)
                    }
                  }
                }
              }
            }

            try {
              ${'$'}defRoute = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
                Where-Object { ${'$'}_.InterfaceAlias -ne ${'$'}tunName -and ${'$'}_.InterfaceAlias -ne 'wintun' -and ${'$'}_.NextHop -ne '0.0.0.0' } |
                Sort-Object { ${'$'}_.RouteMetric + ${'$'}_.InterfaceMetric } |
                Select-Object -First 1
              if (-not ${'$'}defRoute) {
                throw 'Cannot detect physical IPv4 default network interface'
              }
              ${'$'}physIf = ${'$'}defRoute.InterfaceAlias
              ${'$'}physIdx = [int]${'$'}defRoute.InterfaceIndex
              ${'$'}gw = ${'$'}defRoute.NextHop

              ${'$'}defRoute6 = Get-NetRoute -AddressFamily IPv6 -DestinationPrefix '::/0' -ErrorAction SilentlyContinue |
                Where-Object { ${'$'}_.InterfaceAlias -ne ${'$'}tunName -and ${'$'}_.InterfaceAlias -ne 'wintun' -and ${'$'}_.NextHop -ne '::' } |
                Sort-Object { ${'$'}_.RouteMetric + ${'$'}_.InterfaceMetric } |
                Select-Object -First 1
              if (${'$'}defRoute6) { ${'$'}gw6 = ${'$'}defRoute6.NextHop }

              Sync-BypassIps
              if (${'$'}bypassLan -eq 1 -and ${'$'}gw -and ${'$'}gw -ne '0.0.0.0' -and ${'$'}physIdx -gt 0) {
                foreach (${'$'}lp in @('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')) {
                  if (-not (Get-NetRoute -DestinationPrefix ${'$'}lp -ErrorAction SilentlyContinue)) {
                    New-NetRoute -DestinationPrefix ${'$'}lp -InterfaceIndex ${'$'}physIdx -NextHop ${'$'}gw -RouteMetric 1 -PolicyStore ActiveStore -ErrorAction SilentlyContinue | Out-Null
                    [void]${'$'}addedLan.Add(${'$'}lp)
                  }
                }
              }

              ${'$'}workDir = [System.IO.Path]::GetDirectoryName(${'$'}tun2socks)
              ${'$'}t2sArgLine = "--device `"${'$'}tunName`" --proxy `"${'$'}proxyUrl`" --mtu ${'$'}mtu --loglevel warn"
              ${'$'}t2s = Start-Process -FilePath ${'$'}tun2socks -ArgumentList ${'$'}t2sArgLine -WorkingDirectory ${'$'}workDir -RedirectStandardError ${'$'}t2sLog -WindowStyle Hidden -PassThru

              ${'$'}tunAdapter = ${'$'}null
              for (${'$'}i = 0; ${'$'}i -lt 75; ${'$'}i++) {
                if (${'$'}t2s.HasExited) {
                  ${'$'}detail = if (Test-Path -LiteralPath ${'$'}t2sLog) { (Get-Content -LiteralPath ${'$'}t2sLog -ErrorAction SilentlyContinue) -join '; ' } else { '' }
                  throw "tun2socks.exe exited prematurely (code ${'$'}(${'$'}t2s.ExitCode)): ${'$'}detail"
                }
                ${'$'}cand = Get-NetAdapter -Name ${'$'}tunName -IncludeHidden -ErrorAction SilentlyContinue
                if (${'$'}cand -and ${'$'}cand.ifIndex -gt 0 -and (Get-NetIPInterface -InterfaceIndex ${'$'}cand.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue)) {
                  ${'$'}tunAdapter = ${'$'}cand
                  break
                }
                Start-Sleep -Milliseconds 200
              }
              if (-not ${'$'}tunAdapter) {
                throw "Wintun adapter '${'$'}tunName' did not appear within 15s"
              }
              ${'$'}tunIdx = [int]${'$'}tunAdapter.ifIndex

              Set-NetIPInterface -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv4 -Dhcp Disabled -DadTransmits 0 -InterfaceMetric 1 -NlMtuBytes ${'$'}mtu -ErrorAction SilentlyContinue | Out-Null
              Get-NetIPAddress -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv4 -ErrorAction SilentlyContinue | Remove-NetIPAddress -Confirm:${'$'}false -ErrorAction SilentlyContinue
              New-NetIPAddress -InterfaceIndex ${'$'}tunIdx -IPAddress '172.19.0.1' -PrefixLength 30 -PolicyStore ActiveStore -ErrorAction Stop | Out-Null
              for (${'$'}d = 0; ${'$'}d -lt 30; ${'$'}d++) {
                ${'$'}ipObj = Get-NetIPAddress -InterfaceIndex ${'$'}tunIdx -IPAddress '172.19.0.1' -ErrorAction SilentlyContinue
                if (${'$'}ipObj -and ${'$'}ipObj.DadState -eq 'Preferred') { break }
                Start-Sleep -Milliseconds 100
              }
              Set-DnsClientServerAddress -InterfaceIndex ${'$'}tunIdx -ServerAddresses @('1.1.1.1', '8.8.8.8') -ErrorAction SilentlyContinue | Out-Null

              foreach (${'$'}p in @('0.0.0.0/1', '128.0.0.0/1', '198.18.0.0/15')) {
                Get-NetRoute -InterfaceIndex ${'$'}tunIdx -DestinationPrefix ${'$'}p -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:${'$'}false -ErrorAction SilentlyContinue
                New-NetRoute -InterfaceIndex ${'$'}tunIdx -DestinationPrefix ${'$'}p -NextHop '0.0.0.0' -RouteMetric 1 -PolicyStore ActiveStore -ErrorAction Stop | Out-Null
              }

              if (${'$'}enableIpv6 -eq 1) {
                try {
                  Set-NetIPInterface -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv6 -DadTransmits 0 -ErrorAction SilentlyContinue | Out-Null
                  New-NetIPAddress -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv6 -IPAddress 'fdfe:dcba:9876::1' -PrefixLength 126 -PolicyStore ActiveStore -ErrorAction Stop | Out-Null
                  foreach (${'$'}p6 in @('::/1', '8000::/1')) {
                    Get-NetRoute -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv6 -DestinationPrefix ${'$'}p6 -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:${'$'}false -ErrorAction SilentlyContinue
                    New-NetRoute -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv6 -DestinationPrefix ${'$'}p6 -NextHop '::' -RouteMetric 1 -PolicyStore ActiveStore -ErrorAction Stop | Out-Null
                  }
                  ${'$'}ipv6Added = ${'$'}true
                } catch {}
              }

              Clear-DnsClientCache -ErrorAction SilentlyContinue
              Write-Status "OWENCLAVE_TUN_READY tun=${'$'}tunName iface=${'$'}physIf ifIndex=${'$'}physIdx pid=${'$'}PID"

              while ((Test-Path -LiteralPath ${'$'}keepalive) -and (-not ${'$'}t2s.HasExited) -and (Get-Process -Id ${'$'}ownerPid -ErrorAction SilentlyContinue)) {
                Sync-BypassIps
                Start-Sleep -Milliseconds 200
              }
            } catch {
              Write-Status ("ERROR:" + ${'$'}_.Exception.Message)
              exit 1
            } finally {
              ${'$'}ErrorActionPreference = 'Continue'
              if (${'$'}tunIdx -gt 0) {
                foreach (${'$'}p in @('0.0.0.0/1', '128.0.0.0/1', '198.18.0.0/15')) {
                  Get-NetRoute -InterfaceIndex ${'$'}tunIdx -DestinationPrefix ${'$'}p -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:${'$'}false -ErrorAction SilentlyContinue
                }
                if (${'$'}ipv6Added) {
                  foreach (${'$'}p6 in @('::/1', '8000::/1')) {
                    Get-NetRoute -InterfaceIndex ${'$'}tunIdx -AddressFamily IPv6 -DestinationPrefix ${'$'}p6 -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:${'$'}false -ErrorAction SilentlyContinue
                  }
                }
              }
              foreach (${'$'}lp in ${'$'}addedLan) {
                Get-NetRoute -DestinationPrefix ${'$'}lp -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:${'$'}false -ErrorAction SilentlyContinue
              }
              foreach (${'$'}cidr in ${'$'}addedBypass) {
                Get-NetRoute -DestinationPrefix ${'$'}cidr -ErrorAction SilentlyContinue | Remove-NetRoute -Confirm:${'$'}false -ErrorAction SilentlyContinue
              }
              if (${'$'}t2s -and -not ${'$'}t2s.HasExited) {
                Stop-Process -Id ${'$'}t2s.Id -Force -ErrorAction SilentlyContinue
                try { ${'$'}t2s.WaitForExit(3000) | Out-Null } catch {}
              }
              Clear-DnsClientCache -ErrorAction SilentlyContinue
              Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath ${'$'}keepalive, ${'$'}bypassFile, ${'$'}t2sLog, "${'$'}statusFile.tmp", ${'$'}statusFile
            }
        """.trimIndent() + "\r\n"
    }

    private fun startWindowsLocked(params: PrivilegedTunParams) {
        val tun2socks = tun2socksLocator()
            ?: throw TunException("tun2socks.exe is missing for ${DesktopRuntime.platformTag}")
        val wintunDll = File(tun2socks.parentFile, "wintun.dll")
        if (!wintunDll.isFile) {
            throw TunException("wintun.dll is missing next to ${tun2socks.name} in ${tun2socks.parentFile}")
        }

        val runtimeDir = File(dataDir, "runtime").apply { mkdirs() }
        val token = System.nanoTime().toString()
        val keepalive = File(runtimeDir, "owenclave-tun-$token.keepalive").apply { writeText("1") }
        val statusFile = File(runtimeDir, "owenclave-tun-$token.status").apply { delete() }
        val bypassFile = File(runtimeDir, "owenclave-tun-$token.bypass").apply {
            val initialIps = params.bypassServerIps.map { it.trim() }.filter { it.matches(SAFE_IP_REGEX) }
            writeText(if (initialIps.isEmpty()) "" else initialIps.joinToString("\r\n", postfix = "\r\n"))
        }
        val psScript = File(runtimeDir, "owenclave-tun-win.ps1")
        psScript.writeText(buildWindowsHelperScript(keepalive, statusFile, bypassFile, tun2socks, params))

        val isAdmin = launcher.isWindowsAdmin()
        val proc: Process
        val errHolder = AtomicReference("")
        val errThread: Thread

        if (isAdmin) {
            proc = launcher.start(
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    psScript.absolutePath,
                ),
            )
            errThread = Thread({
                runCatching {
                    errHolder.set(proc.errorStream.bufferedReader().use { it.readText() }.trim())
                }
            }, "owenclave-tun-win").apply {
                isDaemon = true
                start()
            }
        } else {
            val qPsPath = psScript.absolutePath.replace("'", "''")
            val elevateCmd = "try { ${'$'}q = [char]34; ${'$'}al = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File ' + ${'$'}q + '$qPsPath' + ${'$'}q; " +
                "Start-Process -FilePath 'powershell.exe' -ArgumentList ${'$'}al -Verb RunAs -WindowStyle Hidden; exit 0 } " +
                "catch { Write-Error ${'$'}_.Exception.Message; exit 126 }"

            val uacProc = launcher.start(
                listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", elevateCmd),
            )
            val errText = uacProc.errorStream.bufferedReader().use { it.readText() }.trim()
            uacProc.waitFor(authTimeoutMs, TimeUnit.MILLISECONDS)
            if (uacProc.exitValue() != 0) {
                keepalive.delete()
                bypassFile.delete()
                statusFile.delete()
                if (uacProc.exitValue() == 126 || errText.contains("canceled", ignoreCase = true) || errText.contains("cancelled", ignoreCase = true)) {
                    throw TunAuthorizationException("VPN authorization was cancelled")
                }
                throw TunAuthorizationException("Failed to elevate VPN helper: $errText")
            }
            proc = uacProc
            errThread = Thread {}
        }

        val waitMs = if (isAdmin) authTimeoutMs.coerceAtLeast(30_000L) else 30_000L
        val deadline = System.currentTimeMillis() + waitMs
        while (System.currentTimeMillis() < deadline) {
            if (statusFile.isFile) {
                val status = runCatching {
                    statusFile.readText(StandardCharsets.UTF_8).removePrefix("\uFEFF").trim()
                }.getOrDefault("")
                if (status.startsWith("OWENCLAVE_TUN_READY")) {
                    keepaliveFile = keepalive
                    statusMonitorFile = statusFile
                    bypassListFile = bypassFile
                    macHelperPid = Regex("pid=(\\d+)").find(status)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    if (isAdmin) supervisorProcess = proc
                    Log.i(TAG, "Windows VPN tunnel active ($status)")
                    return
                }
                if (status.startsWith("ERROR:")) {
                    keepalive.delete()
                    bypassFile.delete()
                    statusFile.delete()
                    throw TunException(status.removePrefix("ERROR:"))
                }
            }
            if (isAdmin && !proc.isAlive && proc.exitValue() != 0) {
                keepalive.delete()
                bypassFile.delete()
                statusFile.delete()
                runCatching { errThread.join(1_500L) }
                val err = errHolder.get()
                throw TunException("Windows VPN helper exited (${proc.exitValue()}): $err")
            }
            Thread.sleep(150)
        }
        keepalive.delete()
        bypassFile.delete()
        statusFile.delete()
        throw TunException("Timed out waiting for Windows Wintun adapter to initialize")
    }

    companion object {
        private const val TAG = "DesktopTUN"
        private val SAFE_IP_REGEX = Regex("^[0-9a-fA-F:.]+$")

        /**
         * Resolves remote proxy server hostnames from [proxy] into concrete IPv4/IPv6
         * addresses while the physical network route is still untouched.
         */
        fun resolveBypassServerIps(proxy: ProxyInstance?): List<String> {
            if (proxy == null) return emptyList()
            val hosts = LinkedHashSet<String>()
            runCatching {
                proxy.profile.requireBean().apply {
                    if (!serverAddress.isNullOrBlank()) hosts.add(serverAddress.trim())
                    if (!finalAddress.isNullOrBlank()) hosts.add(finalAddress.trim())
                }
            }
            if (proxy.isInitialized()) {
                for (indexEntity in proxy.config.index) {
                    for ((_, chainEntity) in indexEntity.chain) {
                        runCatching {
                            val b = chainEntity.requireBean()
                            if (!b.serverAddress.isNullOrBlank()) hosts.add(b.serverAddress.trim())
                            if (!b.finalAddress.isNullOrBlank()) hosts.add(b.finalAddress.trim())
                        }
                    }
                }
            }

            val ips = LinkedHashSet<String>()
            for (rawHost in hosts) {
                val host = rawHost.removeSurrounding("[", "]").trim()
                if (host.isEmpty() || host.equals("localhost", ignoreCase = true)) continue
                runCatching {
                    for (addr in android.net.DnsResolver.resolveDirect(host, timeoutMs = 1_200)) {
                        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress) continue
                        val ip = addr.hostAddress?.substringBefore('%')?.trim().orEmpty()
                        if (ip.isNotEmpty() && !ip.startsWith("198.18.") && !ip.startsWith("198.19.")) {
                            ips.add(ip)
                        }
                    }
                }
            }
            return ips.toList()
        }
    }
}
