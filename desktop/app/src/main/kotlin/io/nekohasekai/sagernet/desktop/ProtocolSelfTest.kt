package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * End to end smoke tests for the core-native protocols.
 *
 * The desktop client used to understand only a handful of protocols. These
 * tests start the very same core binary the client ships as a local **server**
 * (one inbound of the protocol under test, a freedom outbound), point a desktop
 * [Profile] of that protocol at it and fetch an HTTP page through the core's
 * local SOCKS inbound:
 *
 *   client SOCKS -> shared ConfigBuilder -> core outbound -> core inbound -> HTTP
 *
 * A pass proves the protocol that used to be refused now works end to end. Only
 * protocols the core can also *serve* are covered; the client-only ones
 * (tuic, juicity, mieru, shadowquic, snell, ssh, trusttunnel, http3) need a real
 * upstream server and are validated by `ProtocolConfigTest` instead.
 */
object ProtocolSelfTest {

    private const val UUID = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private const val PASSWORD = "owenclave-protocol-selftest"
    private const val SS_METHOD = "aes-256-gcm"
    private const val PAYLOAD = "owenclave-protocol-ok"
    private const val TLS_DOMAIN = "localhost"

    private class Case(
        val name: String,
        /** Generate a self-signed certificate before building the server inbound. */
        val certificate: Boolean = false,
        val inbound: (Int, File) -> String,
        val bean: (Int) -> AbstractBean,
    )

    private val cases = listOf(
        Case(
            "vless",
            inbound = { port, _ -> """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"vless","settings":{"clients":[{"id":"$UUID"}],"decryption":"none"}}""" },
            bean = { port ->
                VLESSBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    uuid = UUID
                    encryption = "none"
                    type = "tcp"
                    security = "none"
                }
            },
        ),
        Case(
            "vmess",
            inbound = { port, _ -> """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"vmess","settings":{"clients":[{"id":"$UUID","alterId":0}]}}""" },
            bean = { port ->
                VMessBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    uuid = UUID
                    alterId = 0
                    encryption = "auto"
                    type = "tcp"
                    security = "none"
                }
            },
        ),
        Case(
            "trojan",
            inbound = { port, _ -> """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"trojan","settings":{"clients":[{"password":"$PASSWORD"}]}}""" },
            bean = { port ->
                TrojanBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    password = PASSWORD
                    type = "tcp"
                    security = "none"
                }
            },
        ),
        Case(
            "anytls",
            certificate = true,
            inbound = { port, certBase ->
                val certificates = tlsCertificates(certBase)
                """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"anytls",""" +
                    """"settings":{"users":[{"password":"$PASSWORD"}]},""" +
                    """"streamSettings":{"security":"tls","tlsSettings":{"certificates":[$certificates]}}}"""
            },
            bean = { port ->
                AnyTLSBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    password = PASSWORD
                    security = "tls"
                    sni = TLS_DOMAIN
                    allowInsecure = true
                }
            },
        ),
        Case(
            "shadowsocks",
            inbound = { port, _ -> """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"shadowsocks","settings":{"method":"$SS_METHOD","password":"$PASSWORD","network":"tcp,udp"}}""" },
            bean = { port ->
                ShadowsocksBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    method = SS_METHOD
                    password = PASSWORD
                }
            },
        ),
        Case(
            "socks",
            inbound = { port, _ -> """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"socks","settings":{"auth":"noauth","udp":true}}""" },
            bean = { port ->
                SOCKSBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    protocol = SOCKSBean.PROTOCOL_SOCKS5
                }
            },
        ),
        Case(
            "http",
            inbound = { port, _ -> """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"http","settings":{}}""" },
            bean = { port ->
                HttpBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                }
            },
        ),
        Case(
            "https",
            certificate = true,
            inbound = { port, certBase ->
                val certificates = tlsCertificates(certBase)
                """{"tag":"in","listen":"127.0.0.1","port":$port,"protocol":"http",""" +
                    """"settings":{"accounts":[{"user":"proxyuser","pass":"$PASSWORD"}]},""" +
                    """"streamSettings":{"security":"tls","tlsSettings":{"alpn":["http/1.1"],"certificates":[$certificates]}}}"""
            },
            bean = { port ->
                HttpBean().apply {
                    serverAddress = "127.0.0.1"
                    serverPort = port
                    username = "proxyuser"
                    password = PASSWORD
                    security = "tls"
                    sni = TLS_DOMAIN
                    allowInsecure = true
                }
            },
        ),
    )

    val names: List<String> get() = cases.map { it.name }

    /**
     * Runs every case, or the one named by [only].
     *
     * @return `0` when all selected cases pass, `1` on failure, `2` for an
     *   unknown protocol name.
     */
    fun run(only: String? = null): Int {
        val selected = when {
            only.isNullOrBlank() -> cases
            else -> cases.filter { it.name == only }
        }
        if (selected.isEmpty()) {
            println("[selftest-protocol] unknown protocol '$only', known: ${names.joinToString(", ")}")
            return 2
        }
        var failed = 0
        selected.forEach { case -> if (runOne(case) != 0) failed++ }
        println("[selftest-protocol] ${selected.size - failed}/${selected.size} passed")
        return if (failed == 0) 0 else 1
    }

    private fun runOne(case: Case): Int {
        val server = SelfTest.LocalHttpServer(PAYLOAD)
        val httpPort = server.port

        var serverProcess: Process? = null
        var client: CoreRunner? = null

        return try {
            val core = DesktopRuntime.coreBinary()
                ?: throw IllegalStateException("owenclave-core is not available for ${DesktopRuntime.platformTag}")

            val certBase = File(DesktopRuntime.dataDir, "selftest-${case.name}-cert")
            if (case.certificate) generateCertificate(core, certBase)

            val serverPort = CoreRunner.freePort()
            val serverConfig = File(DesktopRuntime.dataDir, "selftest-${case.name}-server.json")
            serverConfig.writeText(
                """
                {
                  "log": { "loglevel": "warning" },
                  "inbounds": [ ${case.inbound(serverPort, certBase)} ],
                  "outbounds": [ { "tag": "direct", "protocol": "freedom" } ]
                }
                """.trimIndent()
            )
            println("[selftest-${case.name}] local ${case.name} server on 127.0.0.1:$serverPort")
            serverProcess = ProcessBuilder(core.absolutePath, "run", "-c", serverConfig.absolutePath)
                .directory(DesktopRuntime.dataDir)
                .redirectErrorStream(true)
                .start()
            val runningServer = serverProcess
            Thread {
                runCatching { runningServer.inputStream.bufferedReader().forEachLine { println("[${case.name}-server] $it") } }
            }.apply { isDaemon = true }.start()
            SelfTest.awaitPort(serverPort)

            val bean = case.bean(serverPort).apply { name = "${case.name}-selftest" }
            bean.initializeDefaultValues()
            val settings = DesktopSettings(
                socksPort = CoreRunner.freePort(),
                httpPort = CoreRunner.freePort(),
                logLevel = LogLevel.WARNING,
                // the target is on loopback, so the LAN bypass must stay off
                bypassPrivateNetworks = false,
            )
            client = CoreRunner { line -> println("[client] $line") }
            client.start(Profile(name = case.name, bean = bean), settings)

            val url = "http://127.0.0.1:$httpPort/"
            println("[selftest-${case.name}] fetching $url through the SOCKS proxy")
            val result = client.fetch(url)
            val ok = result.code == 200 && result.body.trim() == PAYLOAD
            println("[selftest-${case.name}] response: HTTP ${result.code}, body='${result.body.trim()}'")
            if (ok) {
                println("[selftest-${case.name}] PASS")
                0
            } else {
                println("[selftest-${case.name}] FAIL: unexpected response")
                1
            }
        } catch (e: Exception) {
            println("[selftest-${case.name}] FAIL: ${e.javaClass.simpleName}: ${e.message}")
            1
        } finally {
            runCatching { client?.stop() }
            runCatching { serverProcess?.destroy() }
            server.stop()
        }
    }

    /** Generates a self-signed certificate with the core's own `tls cert` tool. */
    private fun generateCertificate(core: File, base: File) {
        base.parentFile?.mkdirs()
        val process = ProcessBuilder(
            core.absolutePath, "tls", "cert", "-domain=$TLS_DOMAIN", "-file=${base.absolutePath}",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("owenclave-core tls cert timed out")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException("owenclave-core tls cert failed:\n$output")
        }
    }

    /** The `tlsSettings.certificates` entry of the generated certificate pair. */
    private fun tlsCertificates(base: File): String {
        val cert = File(base.parentFile, base.name + "_cert.pem")
        val key = File(base.parentFile, base.name + "_key.pem")
        val certificates = cert.readLines().filter { it.isNotBlank() }.joinToString(",") { "\"$it\"" }
        val keys = key.readLines().filter { it.isNotBlank() }.joinToString(",") { "\"$it\"" }
        return """{"certificate":[$certificates],"key":[$keys]}"""
    }

}
