package io.nekohasekai.sagernet.desktop

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

object DeepLinks {
    val SUPPORTED_URI_SCHEMES: Set<String> = setOf(
        "owenclave",
        "owenkey",
        "happ",
        "clash",
        "sn",
        "exclave",
        "ss",
        "ssr",
        "socks",
        "socks4",
        "socks4a",
        "sock5",
        "sock5h",
        "socks5",
        "socks5h",
        "socks+tls",
        "vmess",
        "vless",
        "trojan",
        "naive+https",
        "naive+quic",
        "hysteria2",
        "hy2",
        "juicity",
        "tuic",
        "wireguard",
        "wg",
        "mierus",
        "quic",
        "anytls",
        "tt",
        "sq",
        "shadowquic",
    )

    fun isSupportedDeepLink(arg: String): Boolean {
        val trimmed = arg.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("-")) return false
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx <= 0) return false
        val scheme = trimmed.substring(0, colonIdx).lowercase()
        return scheme in SUPPORTED_URI_SCHEMES && trimmed.contains("://")
    }

    fun extractDeepLinks(args: Array<String>): List<String> {
        return args.map { it.trim() }.filter { isSupportedDeepLink(it) }
    }
}

object SingleInstanceManager {

    sealed interface AcquireResult {
        class Primary(val handle: Closeable) : AcquireResult
        data object ForwardedToPrimary : AcquireResult
    }

    private class PrimaryInstanceHandle(
        private val raf: RandomAccessFile?,
        private val channel: FileChannel?,
        private val lock: FileLock?,
        private val server: ServerSocket?,
        private val infoFile: File?,
    ) : Closeable {
        override fun close() {
            runCatching { server?.close() }
            runCatching { infoFile?.delete() }
            runCatching { lock?.release() }
            runCatching { channel?.close() }
            runCatching { raf?.close() }
        }
    }

    fun acquireOrForward(
        dataDir: File,
        uris: List<String>,
        onActivate: (List<String>) -> Unit,
    ): AcquireResult {
        dataDir.mkdirs()
        val lockFile = File(dataDir, "gui-instance.lock")
        val infoFile = File(dataDir, "gui-instance.info")

        val raf = runCatching { RandomAccessFile(lockFile, "rw") }.getOrNull()
        val channel = raf?.channel
        val lock = try {
            channel?.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (_: Exception) {
            null
        }

        if (lock == null) {
            runCatching { channel?.close() }
            runCatching { raf?.close() }
            if (forwardToPrimary(infoFile, uris)) {
                return AcquireResult.ForwardedToPrimary
            }
            return AcquireResult.Primary(PrimaryInstanceHandle(null, null, null, null, null))
        }

        val server = runCatching {
            ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        }.getOrNull() ?: return AcquireResult.Primary(
            PrimaryInstanceHandle(raf, channel, lock, null, null),
        )

        val tokenBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val token = tokenBytes.joinToString("") { "%02x".format(it) }
        runCatching {
            infoFile.writeText("${server.localPort}\n$token\n", Charsets.UTF_8)
            runCatching {
                Files.setPosixFilePermissions(
                    infoFile.toPath(),
                    PosixFilePermissions.fromString("rw-------"),
                )
            }
        }

        val handle = PrimaryInstanceHandle(raf, channel, lock, server, infoFile)
        Thread({
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                runCatching { handleClient(client, token, onActivate) }
            }
        }, "owenclave-single-instance").apply {
            isDaemon = true
            start()
        }

        return AcquireResult.Primary(handle)
    }

    private fun handleClient(
        client: Socket,
        expectedToken: String,
        onActivate: (List<String>) -> Unit,
    ) {
        client.use { socket ->
            socket.soTimeout = 3000
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

            val authLine = reader.readLine() ?: return
            if (authLine != "TOKEN $expectedToken") return

            val uris = ArrayList<String>()
            var lineCount = 0
            while (lineCount < 64) {
                val line = reader.readLine() ?: break
                lineCount++
                if (line == "END") break
                if (line.startsWith("URI ")) {
                    val encoded = line.substring(4).trim()
                    val decoded = runCatching {
                        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8).trim()
                    }.getOrNull()
                    if (!decoded.isNullOrEmpty()) {
                        uris.add(decoded)
                    }
                }
            }

            writer.write("OK\n")
            writer.flush()
            onActivate(uris)
        }
    }

    private fun forwardToPrimary(infoFile: File, uris: List<String>): Boolean {
        repeat(8) { attempt ->
            if (infoFile.isFile) {
                val lines = runCatching { infoFile.readLines(Charsets.UTF_8) }.getOrNull()
                val port = lines?.getOrNull(0)?.trim()?.toIntOrNull()
                val token = lines?.getOrNull(1)?.trim()
                if (port != null && port in 1..65535 && !token.isNullOrEmpty()) {
                    val delivered = runCatching {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", port), 1500)
                            socket.soTimeout = 2000
                            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                            writer.write("TOKEN $token\n")
                            for (uri in uris) {
                                val b64 = Base64.getEncoder().encodeToString(uri.toByteArray(Charsets.UTF_8))
                                writer.write("URI $b64\n")
                            }
                            writer.write("END\n")
                            writer.flush()
                            reader.readLine()?.trim() == "OK"
                        }
                    }.getOrDefault(false)
                    if (delivered) return true
                }
            }
            if (attempt < 7) {
                runCatching { Thread.sleep(75) }
            }
        }
        return false
    }
}

object UrlSchemeRegistrar {

    val DESKTOP_MIME_TYPES: List<String> = listOf(
        "x-scheme-handler/owenclave",
        "x-scheme-handler/owenkey",
        "x-scheme-handler/happ",
        "x-scheme-handler/vless",
        "x-scheme-handler/vmess",
        "x-scheme-handler/trojan",
        "x-scheme-handler/ss",
        "x-scheme-handler/ssr",
        "x-scheme-handler/hysteria2",
        "x-scheme-handler/hy2",
        "x-scheme-handler/tuic",
        "x-scheme-handler/juicity",
        "x-scheme-handler/naive+https",
        "x-scheme-handler/naive+quic",
        "x-scheme-handler/anytls",
        "x-scheme-handler/shadowquic",
        "x-scheme-handler/sq",
        "x-scheme-handler/wireguard",
        "x-scheme-handler/wg",
        "x-scheme-handler/socks",
        "x-scheme-handler/socks5",
    )

    fun ensureRegisteredAsync() {
        if (java.lang.Boolean.getBoolean("owenclave.skipSchemeRegister")) return
        Thread({
            runCatching { ensureRegistered() }
        }, "owenclave-scheme-registrar").apply {
            isDaemon = true
            start()
        }
    }

    internal fun buildLinuxDesktopEntry(launcherPath: String): String {
        val escapedExec = if (launcherPath.any { it.isWhitespace() || it == '"' }) {
            "\"" + launcherPath.replace("\"", "\\\"") + "\""
        } else {
            launcherPath
        }
        val mimeLine = DESKTOP_MIME_TYPES.joinToString(";", postfix = ";")
        return buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=Owenclave URL Handler")
            appendLine("Exec=$escapedExec %u")
            appendLine("Icon=owenclave")
            appendLine("NoDisplay=true")
            appendLine("Terminal=false")
            appendLine("Categories=Network;Security;")
            appendLine("StartupWMClass=Owenclave")
            appendLine("MimeType=$mimeLine")
        }
    }

    private fun ensureRegistered() {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        when {
            os.contains("linux") -> registerLinux()
            os.contains("win") -> registerWindows()
        }
    }

    private fun resolveLinuxLauncherPath(): String? {
        val candidates = listOfNotNull(
            System.getProperty("owenclave.launcher.path")?.takeIf { it.isNotBlank() },
            System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() },
            "/usr/bin/owenclave",
            "/opt/owenclave/bin/Owenclave",
        )
        return candidates.firstOrNull { path ->
            val file = File(path)
            file.isFile && file.canExecute()
        }
    }

    private fun registerLinux() {
        val systemDesktop = File("/usr/share/applications/owenclave.desktop")
        val hasSystemSchemeDesktop = systemDesktop.isFile &&
            runCatching { systemDesktop.readText().contains("x-scheme-handler/owenclave") }.getOrDefault(false)

        val userAppsDir = File(
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.local/share",
            "applications",
        )
        val fallbackDesktopFile = File(userAppsDir, "owenclave-url-handler.desktop")

        val desktopId = if (hasSystemSchemeDesktop) {
            if (fallbackDesktopFile.isFile) {
                runCatching { fallbackDesktopFile.delete() }
                runCommandQuietly("update-desktop-database", userAppsDir.absolutePath)
            }
            "owenclave.desktop"
        } else {
            val launcher = resolveLinuxLauncherPath() ?: return
            userAppsDir.mkdirs()
            val desired = buildLinuxDesktopEntry(launcher)
            val current = if (fallbackDesktopFile.isFile) {
                runCatching { fallbackDesktopFile.readText() }.getOrNull()
            } else {
                null
            }
            if (current != desired) {
                fallbackDesktopFile.writeText(desired, Charsets.UTF_8)
                runCommandQuietly("update-desktop-database", userAppsDir.absolutePath)
            }
            fallbackDesktopFile.name
        }

        // Always bind Owenclave's own schemes to Owenclave.
        runCommandQuietly("xdg-mime", "default", desktopId, "x-scheme-handler/owenclave")
        runCommandQuietly("xdg-mime", "default", desktopId, "x-scheme-handler/owenkey")

        // Bind happ:// only if no other handler is currently set.
        val currentHapp = queryCommandOutput("xdg-mime", "query", "default", "x-scheme-handler/happ")
        if (currentHapp.isBlank()) {
            runCommandQuietly("xdg-mime", "default", desktopId, "x-scheme-handler/happ")
        }
    }

    private fun registerWindows() {
        val exe = listOfNotNull(
            System.getProperty("owenclave.launcher.path")?.takeIf { it.isNotBlank() },
            System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() },
            File(System.getProperty("user.dir"), "Owenclave.exe").absolutePath,
            File(System.getProperty("user.dir"), "bin\\Owenclave.exe").absolutePath,
        ).firstOrNull { File(it).isFile } ?: return

        registerWindowsProtocol("owenclave", "Owenclave URL", exe)
        registerWindowsProtocol("owenkey", "Owenclave Key URL", exe)

        val happExists = runCatching {
            val p = ProcessBuilder("reg", "query", "HKCU\\Software\\Classes\\happ")
                .redirectErrorStream(true)
                .start()
            p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
        if (!happExists) {
            registerWindowsProtocol("happ", "Happ Subscription URL", exe)
        }
    }

    private fun registerWindowsProtocol(scheme: String, description: String, exePath: String) {
        val baseKey = "HKCU\\Software\\Classes\\$scheme"
        runCommandQuietly("reg", "add", baseKey, "/ve", "/d", "URL:$description", "/f")
        runCommandQuietly("reg", "add", baseKey, "/v", "URL Protocol", "/d", "", "/f")
        runCommandQuietly("reg", "add", "$baseKey\\DefaultIcon", "/ve", "/d", "\"$exePath\",0", "/f")
        runCommandQuietly(
            "reg",
            "add",
            "$baseKey\\shell\\open\\command",
            "/ve",
            "/d",
            "\"$exePath\" \"%1\"",
            "/f",
        )
    }

    private fun runCommandQuietly(vararg cmd: String) {
        runCatching {
            val proc = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.use { it.readBytes() }
            proc.waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun queryCommandOutput(vararg cmd: String): String {
        return runCatching {
            val proc = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) out.trim() else ""
        }.getOrDefault("")
    }
}

