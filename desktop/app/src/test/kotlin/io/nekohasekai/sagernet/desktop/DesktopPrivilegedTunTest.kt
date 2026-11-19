package io.nekohasekai.sagernet.desktop

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPrivilegedTunTest {

    private class FakeInteractiveProcess(
        private val exitCodeOnStop: Int = 0,
        private val immediateExitCode: Int? = null,
        private val stderrText: String = "",
        private val emitReadyOnHandshake: Boolean = true,
    ) : Process() {
        val receivedLines = CopyOnWriteArrayList<String>()
        private val stdoutPipeOut = PipedOutputStream()
        private val stdoutPipeIn = PipedInputStream(stdoutPipeOut, 8192)
        private val stdinPipeIn = PipedInputStream(8192)
        private val stdinPipeOut = PipedOutputStream(stdinPipeIn)
        private val doneLatch = CountDownLatch(1)

        @Volatile
        private var alive = true

        @Volatile
        private var code = exitCodeOnStop

        init {
            if (immediateExitCode != null) {
                code = immediateExitCode
                alive = false
                runCatching { stdoutPipeOut.close() }
                doneLatch.countDown()
            } else {
                Thread({
                    try {
                        stdinPipeIn.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                            for (line in lines) {
                                receivedLines.add(line)
                                when {
                                    (line == "START" || line.startsWith("AUTH ")) && emitReadyOnHandshake -> {
                                        stdoutPipeOut.write(
                                            "OWENCLAVE_TUN_READY iface=wlan0 tun=owenclave-tun\n"
                                                .toByteArray(StandardCharsets.UTF_8)
                                        )
                                        stdoutPipeOut.flush()
                                    }
                                    line.startsWith("ADD_BYPASS ") || line == "ATTACH_CHILDREN" -> {
                                        stdoutPipeOut.write("OK\n".toByteArray(StandardCharsets.UTF_8))
                                        stdoutPipeOut.flush()
                                    }
                                    line == "STOP" -> {
                                        break
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        alive = false
                        runCatching { stdoutPipeOut.close() }
                        doneLatch.countDown()
                    }
                }, "fake-tun-helper").apply { isDaemon = true }.start()
            }
        }

        override fun getOutputStream(): OutputStream = stdinPipeOut
        override fun getInputStream(): InputStream = stdoutPipeIn
        override fun getErrorStream(): InputStream =
            ByteArrayInputStream(stderrText.toByteArray(StandardCharsets.UTF_8))

        override fun waitFor(): Int {
            doneLatch.await()
            return code
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
            doneLatch.await(timeout, unit)

        override fun exitValue(): Int {
            if (alive) throw IllegalThreadStateException("process has not exited")
            return code
        }

        override fun destroy() {
            alive = false
            runCatching { stdinPipeOut.close() }
            runCatching { stdoutPipeOut.close() }
            doneLatch.countDown()
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = alive
    }

    @Test
    fun `linux unprivileged start invokes pkexec, reuses session on reload, and stops without second prompt`() {
        val tempDir = Files.createTempDirectory("owenclave-tun-test").toFile()
        try {
            val helperFile = tempDir.resolve("owenclave-tun-helper").apply {
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }
            val tun2socksFile = tempDir.resolve("tun2socks").apply {
                writeText("bin")
                setExecutable(true)
            }
            val pkexecFile = tempDir.resolve("pkexec").apply {
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }

            val spawnedCommands = CopyOnWriteArrayList<List<String>>()
            var fakeProc: FakeInteractiveProcess? = null

            val launcher = object : PrivilegedProcessLauncher {
                override fun start(command: List<String>, env: Map<String, String>): Process {
                    spawnedCommands.add(command)
                    return FakeInteractiveProcess().also { fakeProc = it }
                }

                override fun findExecutable(name: String) =
                    if (name == "pkexec") pkexecFile else null

                override fun currentUid(): Int = 1000
                override fun isWindowsAdmin(): Boolean = false
            }

            val tun = DesktopPrivilegedTun(
                os = "linux",
                dataDir = tempDir,
                launcher = launcher,
                helperLocator = { helperFile },
                tun2socksLocator = { tun2socksFile },
                authTimeoutMs = 5_000L,
            )

            val socksUser = "socks-secret-user-alpha"
            val socksPass = "socks-secret-pass-bravo"

            tun.start(
                PrivilegedTunParams(
                    owenclavePid = 4242L,
                    socksPort = 2080,
                    socksUsername = socksUser,
                    socksPassword = socksPass,
                    localDnsPort = 6450,
                    mtu = 1500,
                    enableIpv6 = false,
                    bypassLan = true,
                    bypassServerIps = listOf("198.51.100.10"),
                )
            )

            assertTrue(tun.isRunning())
            assertEquals(1, spawnedCommands.size)
            val cmd = spawnedCommands.single()
            assertEquals(pkexecFile.absolutePath, cmd[0])
            assertEquals(helperFile.absolutePath, cmd[1])
            assertTrue(cmd.containsAll(listOf("--pid", "4242", "--socks-port", "2080", "--dns-port", "6450")))
            assertTrue(cmd.containsAll(listOf("--bypass-ip", "198.51.100.10")))
            // Credentials must NEVER be leaked via command-line arguments:
            assertFalse(cmd.any { it.contains(socksUser) || it.contains(socksPass) }, "Credentials leaked in cmd: $cmd")

            val expectedAuth = "AUTH " +
                Base64.getEncoder().encodeToString(socksUser.toByteArray()) + " " +
                Base64.getEncoder().encodeToString(socksPass.toByteArray())
            assertTrue(fakeProc!!.receivedLines.contains(expectedAuth))

            // Simulate profile switch (SagerNet.reloadService): same ports/MTU, new server IP.
            tun.start(
                PrivilegedTunParams(
                    owenclavePid = 4242L,
                    socksPort = 2080,
                    socksUsername = socksUser,
                    socksPassword = socksPass,
                    localDnsPort = 6450,
                    mtu = 1500,
                    enableIpv6 = false,
                    bypassLan = true,
                    bypassServerIps = listOf("198.51.100.10", "203.0.113.25"),
                )
            )
            // Must NOT spawn a second pkexec prompt!
            assertEquals(1, spawnedCommands.size)
            val deadline = System.currentTimeMillis() + 2_000L
            while (!fakeProc!!.receivedLines.contains("ATTACH_CHILDREN") && System.currentTimeMillis() < deadline) {
                Thread.sleep(15)
            }
            assertTrue(fakeProc!!.receivedLines.contains("ADD_BYPASS 203.0.113.25"))
            assertTrue(fakeProc!!.receivedLines.contains("ATTACH_CHILDREN"))

            tun.stop()
            assertFalse(tun.isRunning())
            assertTrue(fakeProc!!.receivedLines.contains("STOP"))
            assertEquals(1, spawnedCommands.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `polkit dialog dismissal throws TunAuthorizationException instead of silently succeeding`() {
        val tempDir = Files.createTempDirectory("owenclave-tun-cancel").toFile()
        try {
            val helperFile = tempDir.resolve("owenclave-tun-helper").apply { writeText(""); setExecutable(true) }
            val tun2socksFile = tempDir.resolve("tun2socks").apply { writeText(""); setExecutable(true) }
            val pkexecFile = tempDir.resolve("pkexec").apply { writeText(""); setExecutable(true) }

            val launcher = object : PrivilegedProcessLauncher {
                override fun start(command: List<String>, env: Map<String, String>): Process =
                    FakeInteractiveProcess(
                        immediateExitCode = 126,
                        stderrText = "Error executing command as another user: Request dismissed\n",
                    )

                override fun findExecutable(name: String) = if (name == "pkexec") pkexecFile else null
                override fun currentUid(): Int = 1000
                override fun isWindowsAdmin(): Boolean = false
            }

            val tun = DesktopPrivilegedTun(
                os = "linux",
                dataDir = tempDir,
                launcher = launcher,
                helperLocator = { helperFile },
                tun2socksLocator = { tun2socksFile },
                authTimeoutMs = 5_000L,
            )

            val err = assertFailsWith<TunAuthorizationException> {
                tun.start(PrivilegedTunParams(socksPort = 2080))
            }
            assertTrue(err.message.orEmpty().contains("cancelled", ignoreCase = true))
            assertFalse(tun.isRunning())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to sudo graphical askpass when no polkit agent is running`() {
        val tempDir = Files.createTempDirectory("owenclave-tun-askpass").toFile()
        try {
            val helperFile = tempDir.resolve("owenclave-tun-helper").apply { writeText(""); setExecutable(true) }
            val tun2socksFile = tempDir.resolve("tun2socks").apply { writeText(""); setExecutable(true) }
            val pkexecFile = tempDir.resolve("pkexec").apply { writeText(""); setExecutable(true) }
            val sudoFile = tempDir.resolve("sudo").apply { writeText(""); setExecutable(true) }
            val askpassFile = tempDir.resolve("ksshaskpass").apply { writeText(""); setExecutable(true) }

            val calls = CopyOnWriteArrayList<Pair<List<String>, Map<String, String>>>()

            val launcher = object : PrivilegedProcessLauncher {
                override fun start(command: List<String>, env: Map<String, String>): Process {
                    calls.add(command to env)
                    return if (command.first() == pkexecFile.absolutePath) {
                        FakeInteractiveProcess(
                            immediateExitCode = 127,
                            stderrText = "Error executing command as another user: No authentication agent found.\n",
                        )
                    } else {
                        FakeInteractiveProcess()
                    }
                }

                override fun findExecutable(name: String) = when (name) {
                    "pkexec" -> pkexecFile
                    "sudo" -> sudoFile
                    "ksshaskpass" -> askpassFile
                    else -> null
                }

                override fun currentUid(): Int = 1000
                override fun isWindowsAdmin(): Boolean = false
            }

            val tun = DesktopPrivilegedTun(
                os = "linux",
                dataDir = tempDir,
                launcher = launcher,
                helperLocator = { helperFile },
                tun2socksLocator = { tun2socksFile },
                authTimeoutMs = 5_000L,
            )

            tun.start(PrivilegedTunParams(socksPort = 2080))
            assertTrue(tun.isRunning())
            assertEquals(2, calls.size)
            assertEquals(sudoFile.absolutePath, calls[1].first[0])
            assertEquals("-A", calls[1].first[1])
            assertEquals(askpassFile.absolutePath, calls[1].second["SUDO_ASKPASS"])
            tun.stop()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `bundled linux helper script has valid POSIX shell syntax`() {
        val devRoot = System.getProperty("owenclave.devRoot") ?: return
        val script = java.io.File(devRoot, "packaging/linux/owenclave-tun-helper")
        assertTrue(script.isFile, "Expected packaging/linux/owenclave-tun-helper to exist")
        val shCheck = ProcessBuilder("sh", "-n", script.absolutePath)
            .redirectErrorStream(true)
            .start()
        val out = shCheck.inputStream.bufferedReader().readText()
        assertEquals(0, shCheck.waitFor(), "sh -n failed: $out")
    }

    @Test
    fun `generated macOS helper script has valid POSIX shell syntax and updates bypass IPs without re-prompting`() {
        val tempDir = Files.createTempDirectory("owenclave-tun-mac").toFile()
        try {
            val tun2socksFile = tempDir.resolve("tun2socks").apply { writeText(""); setExecutable(true) }
            val osascriptCalls = CopyOnWriteArrayList<List<String>>()

            val launcher = object : PrivilegedProcessLauncher {
                override fun start(command: List<String>, env: Map<String, String>): Process {
                    osascriptCalls.add(command)
                    // Simulate osascript launching the background helper which writes OWENCLAVE_TUN_READY
                    Thread {
                        Thread.sleep(60)
                        val runtimeDir = tempDir.resolve("runtime")
                        runtimeDir.listFiles { f -> f.name.endsWith(".keepalive") }?.firstOrNull()?.let { ka ->
                            val status = java.io.File(ka.parentFile, ka.name.replace(".keepalive", ".status"))
                            status.writeText("OWENCLAVE_TUN_READY tun=utun7 iface=en0\n")
                        }
                    }.apply { isDaemon = true }.start()
                    return FakeInteractiveProcess(immediateExitCode = 0)
                }

                override fun findExecutable(name: String) = null
                override fun currentUid(): Int = 501
                override fun isWindowsAdmin(): Boolean = false
            }

            val tun = DesktopPrivilegedTun(
                os = "darwin",
                dataDir = tempDir,
                launcher = launcher,
                tun2socksLocator = { tun2socksFile },
                authTimeoutMs = 5_000L,
            )

            tun.start(
                PrivilegedTunParams(
                    socksPort = 2080,
                    enableIpv6 = true,
                    bypassLan = true,
                    bypassServerIps = listOf("203.0.113.10", "2001:db8::1"),
                ),
            )
            assertTrue(tun.isRunning())
            assertEquals(1, osascriptCalls.size)
            assertEquals("/usr/bin/osascript", osascriptCalls[0][0])
            assertTrue(
                osascriptCalls[0][2].contains("/usr/bin/nohup") && osascriptCalls[0][2].contains("</dev/null &"),
                "osascript must detach via nohup and redirect stdin from /dev/null: ${osascriptCalls[0]}",
            )

            val scriptFile = tempDir.resolve("runtime/owenclave-tun-macos.sh")
            assertTrue(scriptFile.isFile)
            val scriptText = scriptFile.readText()
            assertTrue(scriptText.contains("export PATH=\"/usr/sbin:/sbin:/usr/bin:/bin:/usr/local/bin\""))
            assertTrue(scriptText.contains("networksetup -setdnsservers"))
            assertTrue(scriptText.contains("trap '' HUP"), "macOS helper must ignore SIGHUP when osascript exits")
            assertTrue(scriptText.contains("trap cleanup EXIT INT TERM"))
            assertTrue(
                scriptText.contains("route -n add -net \"\$r\" 198.18.0.1"),
                "macOS utun IFF_POINTOPOINT routes must target peer gateway 198.18.0.1",
            )

            val shCheck = ProcessBuilder("sh", "-n", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val shOut = shCheck.inputStream.bufferedReader().readText()
            assertEquals(0, shCheck.waitFor(), "macOS helper sh -n failed: $shOut")

            // Reloading with a new server IP appends to the bypass file without invoking osascript a second time!
            tun.start(
                PrivilegedTunParams(
                    socksPort = 2080,
                    enableIpv6 = true,
                    bypassLan = true,
                    bypassServerIps = listOf("198.51.100.42"),
                ),
            )
            assertEquals(1, osascriptCalls.size, "Profile switch must not trigger a second osascript password dialog")
            val bypassFile = tempDir.resolve("runtime").listFiles { f -> f.name.endsWith(".bypass") }?.single()
            assertTrue(bypassFile != null && bypassFile.readText().contains("198.51.100.42"))

            tun.stop()
            assertFalse(tun.isRunning())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `log shim rotates previous session logcat and classifies naive chromium stderr levels`() {
        val tempDir = Files.createTempDirectory("owenclave-log-test").toFile()
        try {
            val logcat = tempDir.resolve("logcat.log")
            logcat.writeText("11-06 11:17:58.128 E/Owenclave: stale crash from previous session\n")

            val active = Log.rotateSessionLogFile(tempDir)
            assertEquals("", active.readText())
            val prev = tempDir.resolve("logcat.prev.log")
            assertTrue(prev.isFile)
            assertTrue(prev.readText().contains("stale crash from previous session"))

            assertEquals(
                'I',
                Log.classifyStderrLevel("[1111/112349.123456:INFO:naive_proxy_bin.cc(120)] Proxying via HTTPS example.com:443"),
            )
            assertEquals(
                'W',
                Log.classifyStderrLevel("[1111/112349.123456:WARNING:cert_verify_proc.cc(80)] Warning detail"),
            )
            assertEquals(
                'E',
                Log.classifyStderrLevel("[1111/112349.123456:ERROR:ssl_client_socket_impl.cc(990)] handshake failed"),
            )

            val captured = CopyOnWriteArrayList<String>()
            val listener: (String) -> Unit = { captured.add(it) }
            Log.addListener(listener)
            try {
                Log.e("naive", "[1111/112349.123456:INFO:socks5_server_socket.cc(90)] Connection 1 established")
                assertTrue(captured.any { it.contains("I/naive:") }, "Expected INFO stderr line to be emitted as I/naive:")
                assertFalse(captured.any { it.contains("E/naive:") }, "Expected INFO stderr line not to be emitted as E/naive:")
                val sessionFile = Log.initSession()
                assertTrue(
                    sessionFile.absolutePath.contains("test-data"),
                    "Unit test logs must write to isolated test-data dir, got: ${sessionFile.absolutePath}",
                )
            } finally {
                Log.removeListener(listener)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `clearDeadLoopbackProxy disables stale dead loopback proxy but preserves live proxy`() {
        val tempDir = Files.createTempDirectory("owenclave-dead-proxy").toFile()
        try {
            val host = FakeHost()
            host.gnome["org.gnome.system.proxy/mode"] = "'manual'"
            host.gnome["org.gnome.system.proxy.http/host"] = "'127.0.0.1'"
            host.gnome["org.gnome.system.proxy.http/port"] = "7890"
            host.gnome["org.gnome.system.proxy.https/host"] = "'127.0.0.1'"
            host.gnome["org.gnome.system.proxy.https/port"] = "7890"
            host.gnome["org.gnome.system.proxy.socks/host"] = "'127.0.0.1'"
            host.gnome["org.gnome.system.proxy.socks/port"] = "7890"

            val proxy = SystemProxy(runner = host, os = "linux", dataDir = tempDir)

            // Live port (e.g. another proxy still actively running) is left untouched.
            assertFalse(proxy.clearDeadLoopbackProxy(portProbe = { it == 7890 }))
            assertEquals("'manual'", host.gnome["org.gnome.system.proxy/mode"])

            // Dead loopback port (closed client left stale gsettings) is switched to 'none'.
            assertTrue(proxy.clearDeadLoopbackProxy(portProbe = { false }))
            assertEquals("'none'", host.gnome["org.gnome.system.proxy/mode"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `DnsResolver resolveDirect resolves RFC1035 records with bounded timeout and passive proc net tcp detects listener`() {
        // 1. Literal IPv4/IPv6 fast-path requires zero DNS servers.
        val lit4 = android.net.DnsResolver.resolveDirect("150.241.92.78", customServers = emptyList())
        assertEquals(listOf("150.241.92.78"), lit4.map { it.hostAddress })
        val lit6 = android.net.DnsResolver.resolveDirect("[2001:db8::42]", customServers = emptyList())
        assertEquals(1, lit6.size)

        // 2. Local RFC1035 mock UDP DNS server returning CNAME + A record (compressed pointer 0xC00C).
        java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1")).use { mockDns ->
            val serverAddr = java.net.InetSocketAddress("127.0.0.1", mockDns.localPort)
            val responder = Thread({
                val buf = ByteArray(512)
                while (!mockDns.isClosed) {
                    val pkt = java.net.DatagramPacket(buf, buf.size)
                    try {
                        mockDns.receive(pkt)
                    } catch (_: Exception) {
                        break
                    }
                    val req = buf.copyOf(pkt.length)
                    val out = java.io.ByteArrayOutputStream()
                    // Header: copy txId, QR=1 + RD=1 + RA=1 (0x8180), QD=1, AN=1
                    out.write(req[0].toInt()); out.write(req[1].toInt())
                    out.write(0x81); out.write(0x80)
                    out.write(0x00); out.write(0x01)
                    out.write(0x00); out.write(0x01)
                    out.write(0x00); out.write(0x00)
                    out.write(0x00); out.write(0x00)
                    // Copy question section verbatim
                    out.write(req, 12, req.size - 12)
                    val qtype = ((req[req.size - 4].toInt() and 0xFF) shl 8) or (req[req.size - 3].toInt() and 0xFF)
                    if (qtype == android.net.DnsResolver.TYPE_A) {
                        // Compressed name pointer 0xC00C, TYPE=A(1), CLASS=IN(1), TTL=60, RDLEN=4, IP=203.0.113.77
                        out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3C, 0x00, 0x04))
                        out.write(byteArrayOf(203.toByte(), 0, 113, 77))
                    } else {
                        // NODATA for AAAA: ANCOUNT=0
                        val bytes = out.toByteArray()
                        bytes[7] = 0
                        val resp = java.net.DatagramPacket(bytes, bytes.size, pkt.socketAddress)
                        mockDns.send(resp)
                        continue
                    }
                    val bytes = out.toByteArray()
                    mockDns.send(java.net.DatagramPacket(bytes, bytes.size, pkt.socketAddress))
                }
            }, "mock-rfc1035-dns").apply {
                isDaemon = true
                start()
            }

            val resolved = android.net.DnsResolver.resolveDirect(
                rawDomain = "reload-test.owenclave.example",
                nsType = android.net.DnsResolver.TYPE_A,
                timeoutMs = 500,
                customServers = listOf(serverAddr),
            )
            assertEquals(listOf("203.0.113.77"), resolved.map { it.hostAddress })
        }

        // 3. Blackhole UDP server returns within bounded timeout instead of hanging 30s.
        java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1")).use { blackhole ->
            val blackholeAddr = java.net.InetSocketAddress("127.0.0.1", blackhole.localPort)
            val t0 = System.currentTimeMillis()
            val empty = android.net.DnsResolver.resolveDirect(
                rawDomain = "unreachable-node.owenclave.example",
                nsType = android.net.DnsResolver.TYPE_A,
                timeoutMs = 250,
                customServers = listOf(blackholeAddr),
            )
            val elapsed = System.currentTimeMillis() - t0
            assertTrue(empty.isEmpty())
            assertTrue(elapsed < 2_500L, "Expected bounded timeout < 2.5s, took ${elapsed}ms")
        }

        // 4. Passive /proc/net/tcp(6) detection never opens a client socket on the listener.
        if (java.io.File("/proc/net/tcp").canRead() || java.io.File("/proc/net/tcp6").canRead()) {
            java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { srv ->
                assertTrue(
                    io.nekohasekai.sagernet.bg.ProxyService.isLoopbackTcpListeningLinux(srv.localPort),
                )
            }
        }
    }

    @Test
    fun `generated Windows helper script uses locale-independent NetTCPIP cmdlets, requires wintun dll, and reloads bypass IPs without re-prompting UAC`() {
        val tempDir = Files.createTempDirectory("owenclave-tun-win").toFile()
        try {
            val tun2socksFile = tempDir.resolve("tun2socks.exe").apply { writeText("stub") }
            val uacCalls = CopyOnWriteArrayList<List<String>>()

            val launcher = object : PrivilegedProcessLauncher {
                override fun start(command: List<String>, env: Map<String, String>): Process {
                    uacCalls.add(command)
                    Thread {
                        Thread.sleep(60)
                        val runtimeDir = tempDir.resolve("runtime")
                        runtimeDir.listFiles { f -> f.name.endsWith(".keepalive") }?.firstOrNull()?.let { ka ->
                            val status = java.io.File(ka.parentFile, ka.name.replace(".keepalive", ".status"))
                            // Include UTF-8 BOM to verify BOM-tolerant parsing on Windows PowerShell 5.1
                            status.writeText("\uFEFFOWENCLAVE_TUN_READY tun=OwenclaveTUN iface=Ethernet ifIndex=12\r\n")
                        }
                    }.apply { isDaemon = true }.start()
                    return FakeInteractiveProcess(immediateExitCode = 0)
                }

                override fun findExecutable(name: String) = null
                override fun currentUid(): Int = -1
                override fun isWindowsAdmin(): Boolean = false
            }

            val tun = DesktopPrivilegedTun(
                os = "windows",
                dataDir = tempDir,
                launcher = launcher,
                tun2socksLocator = { tun2socksFile },
                authTimeoutMs = 5_000L,
            )

            // Missing wintun.dll next to tun2socks.exe must fail fast with a clear TunException
            val missingErr = assertFailsWith<TunException> {
                tun.start(PrivilegedTunParams(socksPort = 2080))
            }
            assertTrue(missingErr.message.orEmpty().contains("wintun.dll"))

            // Place wintun.dll alongside tun2socks.exe and verify UAC launch + reload
            tempDir.resolve("wintun.dll").writeText("stub-dll")
            tun.start(
                PrivilegedTunParams(
                    socksPort = 2080,
                    socksUsername = "win-user",
                    socksPassword = "win-pass",
                    enableIpv6 = true,
                    bypassLan = true,
                    bypassServerIps = listOf("203.0.113.10"),
                ),
            )
            assertTrue(tun.isRunning())
            assertEquals(1, uacCalls.size)

            val psFile = tempDir.resolve("runtime/owenclave-tun-win.ps1")
            assertTrue(psFile.isFile)
            val psText = psFile.readText()
            assertFalse(psText.contains("netsh "), "Windows helper must not rely on localized netsh commands")
            assertFalse(psText.contains("route.exe"), "Windows helper must not redirect native route.exe stderr under Stop preference")
            assertFalse(psText.contains("wintun://"), "tun2socks device driver must not use unsupported wintun:// scheme")
            assertFalse(psText.contains("--interface"), "Windows loopback tun2socks must not pass --interface (avoids Start-Process space splitting and loopback IP_UNICAST_IF)")
            assertTrue(psText.contains("DadTransmits 0"))
            assertTrue(psText.contains("New-NetIPAddress"))
            assertTrue(psText.contains("Set-DnsClientServerAddress"))
            assertTrue(psText.contains("New-NetRoute"))
            assertTrue(psText.contains("win-user:win-pass@127.0.0.1:2080"))

            // Reloading with a new server IP appends to .bypass without triggering a second UAC prompt!
            tun.start(
                PrivilegedTunParams(
                    socksPort = 2080,
                    socksUsername = "win-user",
                    socksPassword = "win-pass",
                    enableIpv6 = true,
                    bypassLan = true,
                    bypassServerIps = listOf("198.51.100.77"),
                ),
            )
            assertEquals(1, uacCalls.size, "Profile switch on Windows must not trigger a second UAC dialog")
            val bypassFile = tempDir.resolve("runtime").listFiles { f -> f.name.endsWith(".bypass") }?.single()
            assertTrue(bypassFile != null && bypassFile.readText().contains("198.51.100.77"))

            tun.stop()
            assertFalse(tun.isRunning())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

