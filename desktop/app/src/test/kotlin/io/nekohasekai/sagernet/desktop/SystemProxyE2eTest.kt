package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opt-in local end-to-end check of the system proxy mode: it sets the *real*
 * GNOME/KDE proxy through `CoreRunner` + [SystemProxy], fetches through the local
 * HTTP inbound with the endpoint the OS proxy setting now points at, and verifies
 * the original gsettings values come back byte for byte.
 *
 * It is skipped unless `-Dowenclave.e2e.systemProxy=true` is passed, because it
 * touches the developer machine's proxy settings (the production restore path
 * puts them back, and a crash is covered by the stale-backup restore):
 *
 * ```sh
 * ./gradlew :desktop:app:test --tests '*SystemProxyE2eTest*' \
 *   -Dowenclave.e2e.systemProxy=true -i
 * ```
 */
class SystemProxyE2eTest {

    private val keys = listOf(
        listOf("org.gnome.system.proxy", "mode"),
        listOf("org.gnome.system.proxy.http", "host"),
        listOf("org.gnome.system.proxy.http", "port"),
        listOf("org.gnome.system.proxy.https", "host"),
        listOf("org.gnome.system.proxy.https", "port"),
        listOf("org.gnome.system.proxy.socks", "host"),
        listOf("org.gnome.system.proxy.socks", "port"),
        listOf("org.gnome.system.proxy", "ignore-hosts"),
    )

    private fun gsettings(vararg args: String): String {
        val process = ProcessBuilder(listOf("gsettings") + args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return text.trim()
    }

    private fun snapshot(): Map<String, String> =
        keys.associate { it.joinToString(" ") to gsettings("get", it[0], it[1]) }

    private fun curl(url: String, proxy: String?): Pair<Int, String> {
        val command = mutableListOf("curl", "-sS", "--max-time", "20", "-o", "-", "-w", "\n%{http_code}", url)
        if (proxy != null) {
            command.add(1, "-x")
            command.add(2, proxy)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val lines = output.trim().lines()
        val code = lines.lastOrNull()?.trim()?.toIntOrNull() ?: -1
        return code to lines.dropLast(1).joinToString("\n")
    }

    @Test
    fun `real gnome round trip with curl through the os proxy`() {
        assumeTrue(
            System.getProperty("owenclave.e2e.systemProxy") == "true",
            "opt-in: pass -Dowenclave.e2e.systemProxy=true",
        )
        assumeTrue(
            SystemProxy().unavailableReason() == null,
            "no supported system proxy backend on this host",
        )
        val before = snapshot()
        println("[e2e] before: $before")

        val httpServer = SelfTest.LocalHttpServer("owenclave-e2e-ok")
        val core = DesktopRuntime.coreBinary() ?: error("core binary missing")
        val serverPort = CoreRunner.freePort()
        val serverConfig = File(DesktopRuntime.dataDir, "e2e-ss-server.json")
        serverConfig.writeText(
            """
            {
              "log": { "loglevel": "warning" },
              "inbounds": [ { "tag": "ss-in", "listen": "127.0.0.1", "port": $serverPort,
                "protocol": "shadowsocks",
                "settings": { "method": "aes-256-gcm", "password": "e2e", "network": "tcp,udp" } } ],
              "outbounds": [ { "tag": "direct", "protocol": "freedom" } ]
            }
            """.trimIndent()
        )
        val serverProcess = ProcessBuilder(core.absolutePath, "run", "-c", serverConfig.absolutePath)
            .directory(DesktopRuntime.dataDir).redirectErrorStream(true).start()
        Thread {
            runCatching { serverProcess.inputStream.bufferedReader().forEachLine { println("[ss] $it") } }
        }.apply { isDaemon = true }.start()
        SelfTest.awaitPort(serverPort)

        val bean = ShadowsocksBean().apply {
            serverAddress = "127.0.0.1"
            this.serverPort = serverPort
            method = "aes-256-gcm"
            password = "e2e"
            name = "e2e"
        }
        bean.initializeDefaultValues()

        val proxyDir = createTempDirectory("owenclave-e2e-proxy").toFile()
        val systemProxy = SystemProxy(dataDir = proxyDir)
        val settings = DesktopSettings(
            socksPort = CoreRunner.freePort(),
            httpPort = CoreRunner.freePort(),
            logLevel = LogLevel.WARNING,
            bypassPrivateNetworks = false,
            serviceMode = DesktopSettings.SERVICE_SYSTEM,
        )
        val client = CoreRunner(systemProxy = systemProxy) { line -> println("[client] $line") }

        try {
            client.start(Profile(name = "e2e", bean = bean), settings)
            println("[e2e] system proxy applied through SystemProxy: backup=${systemProxy.hasBackup()}")

            val osHost = gsettings("get", "org.gnome.system.proxy.http", "host").trim('\'')
            val osPort = gsettings("get", "org.gnome.system.proxy.http", "port")
            val osMode = gsettings("get", "org.gnome.system.proxy", "mode")
            println("[e2e] OS proxy now mode=$osMode host=$osHost port=$osPort")
            assertEquals("'manual'", osMode)
            assertEquals("'127.0.0.1'", "'$osHost'")
            assertEquals(settings.httpPort.toString(), osPort)

            // Direct through the client's HTTP inbound.
            val direct = curl("http://127.0.0.1:${httpServer.port}/", null)
            println("[e2e] direct curl: HTTP ${direct.first} body='${direct.second}'")
            // Through the endpoint the OS proxy setting points at.
            val viaOsProxy = curl("http://127.0.0.1:${httpServer.port}/", "http://$osHost:$osPort")
            println("[e2e] curl via OS proxy $osHost:$osPort: HTTP ${viaOsProxy.first} body='${viaOsProxy.second}'")
            assertEquals(200, viaOsProxy.first)
            assertEquals("owenclave-e2e-ok", viaOsProxy.second.trim())

            // Browser-style request through the client.
            val externalViaProxy = curl("http://example.com/", "http://$osHost:$osPort")
            println("[e2e] curl via OS proxy to example.com: HTTP ${externalViaProxy.first}")
        } finally {
            runCatching { client.stop() }
            runCatching { serverProcess.destroy() }
            httpServer.stop()
        }

        val after = snapshot()
        println("[e2e] after: $after")
        assertEquals(before, after, "the original gsettings values must be back exactly")
        check(!systemProxy.hasBackup()) { "the backup must be consumed" }
    }
}
