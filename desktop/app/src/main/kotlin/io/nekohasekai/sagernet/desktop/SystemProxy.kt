package io.nekohasekai.sagernet.desktop

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.File

/** Raised when the system proxy cannot be set or restored. */
class SystemProxyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a [SystemProxy.restore] call did. */
data class ProxyRestoreResult(
    /** True when a backup existed and was consumed (even if some fields were kept). */
    val restored: Boolean,
    val backend: String,
    /** Fields the restore deliberately left alone because the user changed them. */
    val kept: List<String>,
    val message: String,
)

/**
 * Points the operating system's proxy at the desktop client's local HTTP port and
 * puts back what was there before.
 *
 * Design decisions, and why:
 *
 *  * **A backup file is written before anything is touched.** [apply] snapshots
 *    the current OS state, stores it as JSON in [backupFile] (inside
 *    [DesktopRuntime.dataDir]) and only then runs the platform commands.
 *  * **The backup is consumed on restore.** [restore] reverts only the fields
 *    whose current value still equals the value this client set; a field the user
 *    changed by hand while connected is left alone and reported through
 *    [ProxyRestoreResult.kept]. A restore deletes the backup, so it is idempotent:
 *    calling it twice is a no-op.
 *  * **A crash cannot leave the OS proxied forever.** The app restores the backup
 *    on disconnect, from a JVM shutdown hook on exit, and on the next start when a
 *    stale backup is found ([restoreStale]). If the process is killed hard
 *    (`SIGKILL`) the next start still finds the file.
 *  * **Calling [apply] twice is safe.** When the OS still holds exactly the values
 *    this client set, the existing backup (with the *original* values) is kept
 *    instead of being overwritten with our own settings.
 *
 * Every OS command goes through the injected [CommandRunner], so the whole layer
 * is unit tested with a fake and never touches the developer machine.
 *
 * @param os one of `linux`, `windows`, `darwin` (defaults to [DesktopRuntime.os]).
 */
class SystemProxy(
    private val runner: CommandRunner = ProcessCommandRunner,
    private val os: String = DesktopRuntime.os,
    private val dataDir: File = DesktopRuntime.dataDir,
) {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val backupFile: File get() = File(dataDir, BACKUP_FILE)

    fun hasBackup(): Boolean = backupFile.isFile

    /** The detected backend id (`gnome`, `kde`, `wininet`, `macos`), or null. */
    fun backendName(): String? = runCatching { detectBackend() }.getOrNull()?.id

    /** Null when the system proxy can be set on this host, otherwise the reason. */
    fun unavailableReason(): String? {
        val backend = runCatching { detectBackend() }.getOrNull() ?: return unsupportedReason()
        return backend.availability()
    }

    /**
     * Points the OS proxy at `host:httpPort` (and the SOCKS proxy at
     * `host:socksPort`, where the platform has one). Throws [SystemProxyException]
     * with a specific reason when no backend is available or a command fails.
     */
    fun apply(
        host: String,
        httpPort: Int,
        socksPort: Int,
        bypass: List<String> = emptyList(),
    ): String {
        val backend = detectBackend() ?: throw SystemProxyException(unsupportedReason())
        if (backupFile.isFile) {
            val existing = readBackup()
            val now = runCatching { backend.read() }.getOrDefault(emptyMap())
            val alreadyApplied = existing != null &&
                existing.os == os &&
                existing.backend == backend.id &&
                existing.applied.isNotEmpty() &&
                existing.applied.all { (key, value) -> now[key] == value }
            if (alreadyApplied) {
                return "the system proxy already points at $host:$httpPort (${backend.id})"
            }
            // Stale backup from a crashed run (or a hand edit): put the remembered
            // state back first, so the original values are never lost. A failure
            // here aborts the apply on purpose instead of overwriting the backup.
            restoreInternal()
        }

        val previous = backend.read()
        val applied = backend.desired(host, httpPort, socksPort, bypass)
        writeBackup(backend.id, host, httpPort, socksPort, previous, applied)
        try {
            backend.apply(applied)
        } catch (e: Exception) {
            // Do not leave a backup that would restore a state we never reached.
            runCatching { backend.apply(previous) }
            deleteBackup()
            throw if (e is SystemProxyException) e
            else SystemProxyException("cannot set the system proxy via ${backend.id}: ${e.message}", e)
        }
        return "system proxy set to $host:$httpPort (SOCKS $host:$socksPort) via ${backend.id}"
    }

    /** Puts the remembered state back. Idempotent and never clobbers hand edits. */
    fun restore(): ProxyRestoreResult = if (!backupFile.isFile) {
        ProxyRestoreResult(false, backendName().orEmpty(), emptyList(), "no system proxy backup")
    } else {
        try {
            restoreInternal()
        } catch (e: Exception) {
            // The backup is kept on purpose: the next start retries it.
            ProxyRestoreResult(
                false,
                readBackup()?.backend.orEmpty(),
                emptyList(),
                "cannot restore the system proxy: ${e.message}",
            )
        }
    }

    /** Restores a backup left behind by a previous run, or null when there is none. */
    fun restoreStale(): ProxyRestoreResult? = if (backupFile.isFile) restore() else null

    /**
     * When starting VPN/TUN mode, another proxy client (e.g. FlClash/Clash) may have
     * exited while leaving the OS system proxy in manual mode pointing at a dead
     * loopback port (`127.0.0.1:7890`). Browsers using system proxy settings would
     * fail with connection-refused on loopback instead of routing through the TUN.
     * Clears manual mode if every configured loopback proxy port is closed.
     */
    fun clearDeadLoopbackProxy(portProbe: (Int) -> Boolean = Companion::isLoopbackPortOpen): Boolean {
        val backend = detectBackend() ?: return false
        val state = runCatching { backend.read() }.getOrDefault(emptyMap())
        if (state.isEmpty()) return false

        fun isDeadLoopback(hostRaw: String?, portRaw: String?): Boolean? {
            val host = hostRaw?.trim()?.trim('\'', '"').orEmpty().lowercase()
            if (host.isEmpty()) return null
            if (host !in LOOPBACK_HOSTS) return false
            val port = portRaw?.trim()?.trim('\'', '"')?.toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return !portProbe(port)
        }

        fun parseUrlLoopbackDead(urlRaw: String?): Boolean? {
            val cleaned = urlRaw?.trim()?.trim('\'', '"').orEmpty()
            if (cleaned.isEmpty()) return null
            val withoutScheme = cleaned.substringAfter("://")
            val hostPort = withoutScheme.substringBefore('/')
            val colon = hostPort.lastIndexOf(':')
            if (colon <= 0) return false
            val host = hostPort.substring(0, colon).trim('[', ']')
            val port = hostPort.substring(colon + 1)
            return isDeadLoopback(host, port)
        }

        val disablePatch: Map<String, String?> = when (backend.id) {
            "gnome" -> {
                if (state["gnome:mode"]?.trim('\'') == "manual") {
                    val checks = listOfNotNull(
                        isDeadLoopback(state["gnome:http-host"], state["gnome:http-port"]),
                        isDeadLoopback(state["gnome:https-host"], state["gnome:https-port"]),
                        isDeadLoopback(state["gnome:socks-host"], state["gnome:socks-port"]),
                    )
                    if (checks.isNotEmpty() && checks.all { it }) mapOf("gnome:mode" to "'none'") else null
                } else null
            }
            "kde" -> {
                if (state["ProxyType"] == "1") {
                    val checks = listOfNotNull(
                        parseUrlLoopbackDead(state["httpProxy"]),
                        parseUrlLoopbackDead(state["httpsProxy"]),
                        parseUrlLoopbackDead(state["socksProxy"]),
                    )
                    if (checks.isNotEmpty() && checks.all { it }) mapOf("ProxyType" to "0") else null
                } else null
            }
            "wininet" -> {
                if (state["win:ProxyEnable"] == "0x1") {
                    val entries = state["win:ProxyServer"].orEmpty().split(';').filter { it.isNotBlank() }
                    val checks = entries.mapNotNull { entry ->
                        val hp = entry.substringAfter('=')
                        parseUrlLoopbackDead(hp)
                    }
                    if (checks.isNotEmpty() && checks.all { it }) mapOf("win:ProxyEnable" to "0x0") else null
                } else null
            }
            "macos" -> {
                val activeKinds = listOf("web", "secureweb", "socks").filter {
                    state["mac:$it.enabled"].equals("Yes", ignoreCase = true)
                }
                if (activeKinds.isNotEmpty()) {
                    val checks = activeKinds.mapNotNull { kind ->
                        isDeadLoopback(state["mac:$kind.server"], state["mac:$kind.port"])
                    }
                    if (checks.size == activeKinds.size && checks.all { it }) {
                        activeKinds.associate { "mac:$it.enabled" to "No" }
                    } else null
                } else null
            }
            else -> null
        } ?: return false

        runCatching { backend.apply(disablePatch) }.onFailure { return false }
        return true
    }

    // ------------------------------------------------------------- backup file

    private data class Backup(
        val os: String,
        val backend: String,
        val previous: Map<String, String?>,
        val applied: Map<String, String?>,
    )

    private fun writeBackup(
        backend: String,
        host: String,
        httpPort: Int,
        socksPort: Int,
        previous: Map<String, String?>,
        applied: Map<String, String?>,
    ) {
        dataDir.mkdirs()
        val root = JsonObject().apply {
            addProperty("version", BACKUP_VERSION)
            addProperty("os", os)
            addProperty("backend", backend)
            addProperty("host", host)
            addProperty("httpPort", httpPort)
            addProperty("socksPort", socksPort)
            add("previous", stringMap(previous))
            add("applied", stringMap(applied))
        }
        backupFile.writeText(gson.toJson(root))
    }

    private fun readBackup(): Backup? {
        if (!backupFile.isFile) return null
        return runCatching {
            val root = gson.fromJson(backupFile.readText(), JsonObject::class.java) ?: return null
            Backup(
                os = root.get("os")?.asString.orEmpty(),
                backend = root.get("backend")?.asString.orEmpty(),
                previous = readStringMap(root.getAsJsonObject("previous")),
                applied = readStringMap(root.getAsJsonObject("applied")),
            )
        }.getOrNull()
    }

    private fun stringMap(map: Map<String, String?>): JsonObject = JsonObject().apply {
        map.forEach { (key, value) ->
            if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
        }
    }

    private fun readStringMap(json: JsonObject?): Map<String, String?> {
        val result = LinkedHashMap<String, String?>()
        json?.entrySet()?.forEach { (key, value) ->
            result[key] = if (value.isJsonNull) null else value.asString
        }
        return result
    }

    private fun deleteBackup() {
        runCatching { backupFile.delete() }
    }

    private fun restoreInternal(): ProxyRestoreResult {
        val backup = readBackup()
            ?: return ProxyRestoreResult(false, "", emptyList(), "no system proxy backup")
        if (backup.os != os) {
            deleteBackup()
            return ProxyRestoreResult(
                false,
                backup.backend,
                emptyList(),
                "ignored a system proxy backup from ${backup.os}: it cannot be restored on $os",
            )
        }
        val backend = backendFor(backup.backend) ?: run {
            deleteBackup()
            return ProxyRestoreResult(
                false,
                backup.backend,
                emptyList(),
                "unknown system proxy backend \"${backup.backend}\"",
            )
        }
        val now = runCatching { backend.read() }.getOrDefault(emptyMap())
        val revert = LinkedHashMap<String, String?>()
        val kept = ArrayList<String>()
        for ((key, value) in backup.applied) {
            // Only put a field back when the OS still holds the value we set.
            if (now[key] == value) revert[key] = backup.previous[key] else kept += key
        }
        if (revert.isNotEmpty()) backend.apply(revert)
        deleteBackup()
        return ProxyRestoreResult(
            true,
            backend.id,
            kept,
            buildString {
                append("system proxy restored via ${backend.id}")
                if (kept.isNotEmpty()) {
                    append("; kept values changed by hand: ${kept.joinToString(", ")}")
                }
            },
        )
    }

    // --------------------------------------------------------------- backends

    private fun detectBackend(): SystemProxyBackend? {
        val candidates = when (os) {
            "windows" -> listOf(WindowsBackend())
            "darwin" -> listOf(MacBackend())
            else -> listOf(GnomeBackend(), KdeBackend())
        }
        return candidates.firstOrNull { it.availability() == null }
    }

    private fun backendFor(id: String): SystemProxyBackend? = when (id) {
        "gnome" -> GnomeBackend()
        "kde" -> KdeBackend()
        "wininet" -> WindowsBackend()
        "macos" -> MacBackend()
        else -> null
    }

    private fun unsupportedReason(): String = when (os) {
        "windows" -> "the Windows proxy backend (reg.exe / WinINet) is not available"
        "darwin" -> "the macOS proxy backend (networksetup) is not available"
        else -> "no supported system proxy backend: this needs GNOME " +
            "(the org.gnome.system.proxy gsettings schema) or KDE (kwriteconfig5/6)"
    }

    /**
     * One platform's proxy implementation.
     *
     * [read] returns one entry per addressable field (null = the field does not
     * exist yet); [desired] returns the values for the local client; [apply] writes
     * a whole map at once so composite platforms (macOS sets server and port with a
     * single command) can stay correct when only part of the state is reverted.
     */
    private interface SystemProxyBackend {
        val id: String

        /** Null when the backend is usable, otherwise a human readable reason. */
        fun availability(): String?

        fun read(): Map<String, String?>

        fun desired(host: String, httpPort: Int, socksPort: Int, bypass: List<String>): Map<String, String?>

        fun apply(values: Map<String, String?>)
    }

    // ------------------------------------------------------------------ gnome

    private inner class GnomeBackend : SystemProxyBackend {

        override val id = "gnome"
        private val base = "org.gnome.system.proxy"

        override fun availability(): String? =
            if (runner.run(listOf("gsettings", "get", base, "mode")).ok) null
            else "gsettings cannot read $base mode (no GNOME proxy schema)"

        override fun read(): Map<String, String?> = linkedMapOf(
            "gnome:mode" to get(base, "mode"),
            "gnome:http-host" to get("$base.http", "host"),
            "gnome:http-port" to get("$base.http", "port"),
            "gnome:https-host" to get("$base.https", "host"),
            "gnome:https-port" to get("$base.https", "port"),
            "gnome:socks-host" to get("$base.socks", "host"),
            "gnome:socks-port" to get("$base.socks", "port"),
            "gnome:ignore-hosts" to get(base, "ignore-hosts"),
        )

        override fun desired(
            host: String,
            httpPort: Int,
            socksPort: Int,
            bypass: List<String>,
        ): Map<String, String?> = linkedMapOf<String, String?>().apply {
            put("gnome:mode", "'manual'")
            put("gnome:http-host", "'$host'")
            put("gnome:http-port", httpPort.toString())
            put("gnome:https-host", "'$host'")
            put("gnome:https-port", httpPort.toString())
            put("gnome:socks-host", "'$host'")
            put("gnome:socks-port", socksPort.toString())
            if (bypass.isNotEmpty()) {
                put("gnome:ignore-hosts", bypass.joinToString(", ", "[", "]") { "'$it'" })
            }
        }

        override fun apply(values: Map<String, String?>) {
            for ((key, value) in values) {
                val target = target(key) ?: continue
                if (value == null) {
                    command(listOf("gsettings", "reset", target.first, target.second))
                } else {
                    command(listOf("gsettings", "set", target.first, target.second, value))
                }
            }
        }

        private fun get(schema: String, key: String): String? {
            val result = runner.run(listOf("gsettings", "get", schema, key))
            return if (result.ok) result.stdout.trim() else null
        }

        private fun target(key: String): Pair<String, String>? = when (key) {
            "gnome:mode" -> base to "mode"
            "gnome:http-host" -> "$base.http" to "host"
            "gnome:http-port" -> "$base.http" to "port"
            "gnome:https-host" -> "$base.https" to "host"
            "gnome:https-port" -> "$base.https" to "port"
            "gnome:socks-host" -> "$base.socks" to "host"
            "gnome:socks-port" -> "$base.socks" to "port"
            "gnome:ignore-hosts" -> base to "ignore-hosts"
            else -> null
        }
    }

    // -------------------------------------------------------------------- kde

    private inner class KdeBackend : SystemProxyBackend {

        override val id = "kde"
        private val file = "kioslaverc"
        private val group = "Proxy Settings"

        private val writer: String? by lazy { firstOnPath("kwriteconfig6", "kwriteconfig5") }
        private val reader: String? by lazy { firstOnPath("kreadconfig6", "kreadconfig5") }

        override fun availability(): String? = when {
            writer == null -> "kwriteconfig5/6 not found (KDE)"
            reader == null -> "kreadconfig5/6 not found (KDE)"
            else -> null
        }

        override fun read(): Map<String, String?> = listOf(
            "ProxyType", "httpProxy", "httpsProxy", "socksProxy", "NoProxyFor",
        ).associateWith { key ->
            val result = runner.run(
                listOf(reader!!, "--file", file, "--group", group, "--key", key)
            )
            if (result.ok) result.stdout.trim() else null
        }

        override fun desired(
            host: String,
            httpPort: Int,
            socksPort: Int,
            bypass: List<String>,
        ): Map<String, String?> = linkedMapOf<String, String?>().apply {
            put("ProxyType", "1")
            put("httpProxy", "http://$host:$httpPort")
            put("httpsProxy", "http://$host:$httpPort")
            put("socksProxy", "socks://$host:$socksPort")
            if (bypass.isNotEmpty()) put("NoProxyFor", bypass.joinToString(", "))
        }

        override fun apply(values: Map<String, String?>) {
            for ((key, value) in values) {
                command(
                    listOf(writer!!, "--file", file, "--group", group, "--key", key, value.orEmpty())
                )
            }
        }

        private fun firstOnPath(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            val result = runner.run(listOf("sh", "-c", "command -v $name"))
            if (result.ok) result.stdout.trim().lineSequence().firstOrNull { it.isNotBlank() } else null
        }
    }

    // ------------------------------------------------------------------ macOS

    private inner class MacBackend : SystemProxyBackend {

        override val id = "macos"

        private val service: String by lazy { resolveService() }

        override fun availability(): String? {
            if (!runner.run(listOf("networksetup", "-listnetworkserviceorder")).ok) {
                return "networksetup is not available"
            }
            return runCatching { service }.fold({ null }, { it.message })
        }

        override fun read(): Map<String, String?> {
            val web = readProxy("web")
            val secure = readProxy("secureweb")
            val socks = readProxy("socks")
            return linkedMapOf(
                "mac:web.enabled" to web["Enabled"],
                "mac:web.server" to web["Server"],
                "mac:web.port" to web["Port"],
                "mac:secureweb.enabled" to secure["Enabled"],
                "mac:secureweb.server" to secure["Server"],
                "mac:secureweb.port" to secure["Port"],
                "mac:socks.enabled" to socks["Enabled"],
                "mac:socks.server" to socks["Server"],
                "mac:socks.port" to socks["Port"],
                "mac:bypass" to readBypass(),
            )
        }

        override fun desired(
            host: String,
            httpPort: Int,
            socksPort: Int,
            bypass: List<String>,
        ): Map<String, String?> = linkedMapOf<String, String?>().apply {
            put("mac:web.enabled", "Yes")
            put("mac:web.server", host)
            put("mac:web.port", httpPort.toString())
            put("mac:secureweb.enabled", "Yes")
            put("mac:secureweb.server", host)
            put("mac:secureweb.port", httpPort.toString())
            put("mac:socks.enabled", "Yes")
            put("mac:socks.server", host)
            put("mac:socks.port", socksPort.toString())
            if (bypass.isNotEmpty()) put("mac:bypass", bypass.joinToString(" "))
        }

        override fun apply(values: Map<String, String?>) {
            val current = runCatching { read() }.getOrDefault(emptyMap())
            fun value(key: String): String? = values[key] ?: current[key]

            applyProxy(
                prefix = "web",
                values = values,
                setProxy = listOf("networksetup", "-setwebproxy", service),
                setState = listOf("networksetup", "-setwebproxystate", service),
                server = value("mac:web.server").orEmpty(),
                port = value("mac:web.port").orEmpty(),
                enabled = value("mac:web.enabled"),
            )
            applyProxy(
                prefix = "secureweb",
                values = values,
                setProxy = listOf("networksetup", "-setsecurewebproxy", service),
                setState = listOf("networksetup", "-setsecurewebproxystate", service),
                server = value("mac:secureweb.server").orEmpty(),
                port = value("mac:secureweb.port").orEmpty(),
                enabled = value("mac:secureweb.enabled"),
            )
            applyProxy(
                prefix = "socks",
                values = values,
                setProxy = listOf("networksetup", "-setsocksfirewallproxy", service),
                setState = listOf("networksetup", "-setsocksfirewallproxystate", service),
                server = value("mac:socks.server").orEmpty(),
                port = value("mac:socks.port").orEmpty(),
                enabled = value("mac:socks.enabled"),
            )

            if (values.containsKey("mac:bypass")) {
                val domains = value("mac:bypass").orEmpty().split(" ").filter { it.isNotBlank() }
                val args = if (domains.isEmpty()) listOf("Empty") else domains
                command(listOf("networksetup", "-setproxybypassdomains", service) + args)
            }
        }

        private fun applyProxy(
            prefix: String,
            values: Map<String, String?>,
            setProxy: List<String>,
            setState: List<String>,
            server: String,
            port: String,
            enabled: String?,
        ) {
            val touched = values.keys.any { it.startsWith("mac:$prefix.") }
            if (!touched) return
            val addressTouched = values.containsKey("mac:$prefix.server") ||
                values.containsKey("mac:$prefix.port")
            if (addressTouched) {
                if (server.isNotBlank()) {
                    command(setProxy + listOf(server, port.ifBlank { "0" }))
                } else {
                    // A proxy that was off before may still carry a stale server;
                    // clearing it is best effort (switching the state off is what
                    // matters and always happens below).
                    runner.run(setProxy + listOf("", "0"))
                }
            }
            if (enabled != null) {
                command(setState + if (enabled.equals("Yes", ignoreCase = true)) "on" else "off")
            }
        }

        private fun readProxy(kind: String): Map<String, String> {
            val option = when (kind) {
                "web" -> "-getwebproxy"
                "secureweb" -> "-getsecurewebproxy"
                else -> "-getsocksfirewallproxy"
            }
            val result = runner.run(listOf("networksetup", option, service))
            if (!result.ok) return emptyMap()
            return result.stdout.lineSequence()
                .mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
                }
                .toMap()
        }

        private fun readBypass(): String? {
            val result = runner.run(listOf("networksetup", "-getproxybypassdomains", service))
            if (!result.ok) return null
            val lines = result.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty() || lines.first().startsWith("There aren't any")) return ""
            return lines.joinToString(" ")
        }

        private fun resolveService(): String {
            val order = runner.run(listOf("networksetup", "-listnetworkserviceorder")).stdout
            val primaryDevice = Regex("interface:\\s+(\\S+)")
                .find(runner.run(listOf("route", "-n", "get", "default")).stdout)
                ?.groupValues?.get(1)
                ?.takeIf { !it.startsWith("utun", ignoreCase = true) }
                ?: Regex("^\\s*(en\\d+)\\s*:", RegexOption.MULTILINE)
                    .find(runner.run(listOf("scutil", "--nwi")).stdout)
                    ?.groupValues?.get(1)

            val services = ArrayList<Pair<String, String?>>()
            var current: String? = null
            order.lineSequence().forEach { line ->
                val text = line.trim()
                val name = Regex("^\\(\\d+\\)\\s+(.+)$").find(text)?.groupValues?.get(1)
                if (name != null) {
                    current = name
                    services += name to null
                    return@forEach
                }
                val device = Regex("Device:\\s*([^,)]+)").find(text)?.groupValues?.get(1)?.trim()
                if (device != null && current != null) {
                    val index = services.indexOfLast { it.first == current }
                    if (index >= 0) services[index] = current to device
                }
            }
            val chosen = (if (primaryDevice != null) services.firstOrNull { it.second == primaryDevice } else null)
                ?: services.firstOrNull { (svc, _) ->
                    svc.contains("Wi-Fi", ignoreCase = true) ||
                        svc.contains("Ethernet", ignoreCase = true) ||
                        svc.contains("LAN", ignoreCase = true)
                }
                ?: services.firstOrNull()
            return chosen?.first
                ?: throw SystemProxyException("cannot resolve the active network service from networksetup")
        }
    }

    // ---------------------------------------------------------------- windows

    private inner class WindowsBackend : SystemProxyBackend {

        override val id = "wininet"
        private val path = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

        override fun availability(): String? {
            val probe = runner.run(listOf("reg", "query", path))
            return if (probe.ok || probe.output.contains("HKEY")) {
                null
            } else {
                "reg.exe / the WinINet registry key is not available: ${probe.output}"
            }
        }

        override fun read(): Map<String, String?> = linkedMapOf(
            "win:ProxyEnable" to query("ProxyEnable"),
            "win:ProxyServer" to query("ProxyServer"),
            "win:ProxyOverride" to query("ProxyOverride"),
        )

        override fun desired(
            host: String,
            httpPort: Int,
            socksPort: Int,
            bypass: List<String>,
        ): Map<String, String?> = linkedMapOf<String, String?>().apply {
            put("win:ProxyEnable", "0x1")
            put(
                "win:ProxyServer",
                "http=$host:$httpPort;https=$host:$httpPort;socks=$host:$socksPort",
            )
            if (bypass.isNotEmpty()) put("win:ProxyOverride", bypass.joinToString(";"))
        }

        override fun apply(values: Map<String, String?>) {
            for ((key, value) in values) {
                val name = key.removePrefix("win:")
                if (value == null) {
                    runner.run(listOf("reg", "delete", path, "/v", name, "/f"))
                } else {
                    val type = if (name == "ProxyEnable") "REG_DWORD" else "REG_SZ"
                    command(
                        listOf("reg", "add", path, "/v", name, "/t", type, "/d", value, "/f")
                    )
                }
            }
            refresh()
        }

        private fun query(name: String): String? {
            val result = runner.run(listOf("reg", "query", path, "/v", name))
            if (!result.ok) return null
            val line = result.stdout.lineSequence().firstOrNull { it.trim().startsWith(name) } ?: return null
            val parts = line.trim().split(Regex("\\s+"))
            return if (parts.size >= 3) parts.drop(2).joinToString(" ") else null
        }

        /** Tells WinINet (and every app using it) that the settings changed. */
        private fun refresh() {
            // Avoid double quotes in the argument string: CreateProcessW / powershell.exe CRT
            // argv parsing strips unescaped `"` before PowerShell parses `-Command`.
            val script = buildString {
                append("${'$'}q = [char]34; ")
                append("Add-Type -Namespace WinInet -Name Native -MemberDefinition ")
                append("('[DllImport(' + ${'$'}q + 'wininet.dll' + ${'$'}q + ', SetLastError=true)] ")
                append("public static extern bool InternetSetOption(IntPtr h, int o, IntPtr b, int l);'); ")
                append("[WinInet.Native]::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0) | Out-Null; ")
                append("[WinInet.Native]::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0) | Out-Null")
            }
            command(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", script))
        }
    }

    private fun command(command: List<String>) {
        val result = runner.run(command)
        if (!result.ok) {
            throw SystemProxyException(
                "${command.joinToString(" ")} failed (${result.exitCode}): ${result.output}"
            )
        }
    }

    companion object {

        const val BACKUP_FILE = "system-proxy-backup.json"

        const val BACKUP_VERSION = 1

        private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")

        fun isLoopbackPortOpen(port: Int): Boolean = runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 150)
            }
            true
        }.getOrDefault(false)

    }

}
