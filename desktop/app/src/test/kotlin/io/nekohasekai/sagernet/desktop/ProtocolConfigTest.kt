package io.nekohasekai.sagernet.desktop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http3.Http3Bean
import io.nekohasekai.sagernet.fmt.hysteria2.Hysteria2Bean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.masque.MASQUEBean
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trusttunnel.TrustTunnelBean
import io.nekohasekai.sagernet.fmt.tuic5.Tuic5Bean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Builds a config for one synthetic profile of **every** protocol the desktop
 * client advertises and checks the resulting outbound.
 *
 * When the development core binary for the host is present
 * (`desktop/core/dist/<platformTag>/owenclave-core`) each generated config is
 * additionally fed to `owenclave-core test`, so a protocol the shared generator
 * emits but the core cannot parse is a hard failure. Without the binary the
 * validation is skipped with a clear message.
 */
class ProtocolConfigTest {

    private data class ProtocolCase(
        val name: String,
        /** Protocol of the outbound that dials the upstream. */
        val protocol: String,
        /** True when the protocol runs as an external plugin process. */
        val external: Boolean = false,
        /** True when `core test` requires an external SIP003 binary on `$PATH`. */
        val requiresHostSip003Plugin: Boolean = false,
        val bean: () -> AbstractBean,
    )

    private val testUuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    private val cases = listOf(
        ProtocolCase("shadowsocks", "shadowsocks") {
            ShadowsocksBean().apply {
                serverAddress = "127.0.0.1"
                serverPort = 8388
                method = "aes-256-gcm"
                password = "secret"
            }
        },
        ProtocolCase("shadowsocksr", "shadowsocks", requiresHostSip003Plugin = true) {
            ShadowsocksRBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 8388
                method = "aes-256-cfb"
                password = "secret"
                protocol = "origin"
                obfs = "plain"
            }
        },
        ProtocolCase("vmess", "vmess") {
            VMessBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                uuid = testUuid
                alterId = 0
                encryption = "auto"
                type = "tcp"
                security = "none"
            }
        },
        ProtocolCase("vless", "vless") {
            VLESSBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                uuid = testUuid
                encryption = "none"
                type = "tcp"
                security = "none"
            }
        },
        ProtocolCase("trojan", "trojan") {
            TrojanBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                password = "secret"
                type = "tcp"
                security = "none"
            }
        },
        ProtocolCase("hysteria2", "hysteria2") {
            Hysteria2Bean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                serverPorts = "443"
                auth = "secret"
                sni = "example.com"
                allowInsecure = true
            }
        },
        ProtocolCase("tuic", "tuic") {
            Tuic5Bean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                uuid = testUuid
                password = "secret"
                sni = "example.com"
                allowInsecure = true
            }
        },
        ProtocolCase("anytls", "anytls") {
            AnyTLSBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                password = "secret"
                security = "tls"
                sni = "example.com"
                allowInsecure = true
            }
        },
        ProtocolCase("ssh", "ssh") {
            SSHBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 22
                username = "user"
                password = "secret"
                authType = SSHBean.AUTH_TYPE_PASSWORD
            }
        },
        ProtocolCase("snell", "snell") {
            SnellBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                psk = "secret"
                version = SnellBean.VERSION_4
                obfsMode = SnellBean.OBFS_NONE
                mode = SnellBean.MODE_DEFAULT
            }
        },
        ProtocolCase("shadowquic", "shadowquic") {
            ShadowQUICBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                username = "user"
                password = "secret"
                sni = "example.com"
            }
        },
        ProtocolCase("mieru", "mieru") {
            MieruBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                protocol = MieruBean.PROTOCOL_TCP
                username = "user"
                password = "secret"
            }
        },
        ProtocolCase("juicity", "juicity") {
            JuicityBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                uuid = testUuid
                password = "secret"
                sni = "example.com"
                allowInsecure = true
            }
        },
        ProtocolCase("trusttunnel", "trusttunnel") {
            TrustTunnelBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                protocol = "https"
                username = "user"
                password = "secret"
                sni = "example.com"
                allowInsecure = true
            }
        },
        ProtocolCase("http3", "http3") {
            Http3Bean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                username = "user"
                password = "secret"
                sni = "example.com"
                allowInsecure = true
            }
        },
        ProtocolCase("wireguard", "wireguard") {
            WireGuardBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 51820
                localAddress = "10.0.0.2/32"
                privateKey = "6FeYivM5J5iXO8f+sfEYJp6X/GrsV3H9nS/Ugwk+P1s="
                peerPublicKey = "LkxbEv9NwSErAYtp+ssk8jPQ1gEPRc6FBoujgtMgtwY="
            }
        },
        ProtocolCase("socks", "socks") {
            SOCKSBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 1080
                protocol = SOCKSBean.PROTOCOL_SOCKS5
            }
        },
        ProtocolCase("http", "http") {
            HttpBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 8080
                username = "user"
                password = "secret"
            }
        },
        ProtocolCase("https", "http") {
            HttpBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                username = "user"
                password = "secret"
                security = "tls"
                sni = "proxy.example.com"
                alpn = "h2,http/1.1"
                allowInsecure = true
            }
        },
        ProtocolCase("masque", "masque") {
            MASQUEBean().apply {
                serverAddress = "1.2.3.4"
                serverPort = 443
                username = "user"
                password = "secret"
                http3 = true
                security = "tls"
                sni = "example.com"
                allowInsecure = true
            }
        },
        // External engines: the core only sees a local SOCKS outbound while the
        // plugin process is started separately.
        ProtocolCase("naive", "socks", external = true) {
            NaiveBean().apply {
                serverAddress = "naive.example.com"
                serverPort = 443
                proto = "https"
                username = "user"
                password = "secret"
                sni = "naive.example.com"
            }
        },
    )

    private fun build(bean: AbstractBean, bindInterface: String? = null): CoreConfig {
        bean.initializeDefaultValues()
        return DesktopConfigBuilder.build(
            Profile(name = "protocol-test", bean = bean),
            DesktopSettings(bypassPrivateNetworks = false),
            emptyList(),
            bindInterface,
        )
    }

    private fun proxyOutbound(config: String): JsonObject = JsonParser.parseString(config).asJsonObject
        .getAsJsonArray("outbounds")
        .map { it.asJsonObject }
        .first { (it.get("tag")?.asString ?: "").startsWith("proxy-global-") }

    private fun protocolNames(config: String): Set<String> = JsonParser.parseString(config).asJsonObject
        .getAsJsonArray("outbounds")
        .mapNotNull { it.asJsonObject.get("protocol")?.asString }
        .toSet()

    @Test
    fun `builds an outbound for every supported protocol`() {
        cases.forEach { case ->
            val result = build(case.bean())
            val proxy = proxyOutbound(result.json)
            assertEquals(case.protocol, proxy.get("protocol").asString, "protocol of ${case.name}")
            if (case.external) {
                assertEquals(1, result.plugins.size, "${case.name} should start exactly one plugin")
                assertEquals(proxy.getAsJsonObject("settings").getAsJsonArray("servers")[0]
                    .asJsonObject.get("port").asInt, result.plugins[0].port, "${case.name} plugin port")
            } else {
                assertTrue(result.plugins.isEmpty(), "${case.name} should not start a plugin")
            }
        }
    }

    @Test
    fun `generated configs are accepted by the core`() {
        val core = hostCoreBinary()
        if (core == null) {
            println("[protocol-config] owenclave-core for ${DesktopRuntime.platformTag} not found, " +
                "skipping the core validation (build it with ./run desktop core build)")
            return
        }
        println("[protocol-config] validating with ${core.absolutePath}")
        val failures = ArrayList<String>()
        // Validate both the plain config and the transparent-mode variant, which
        // adds streamSettings.sockopt.bindToDevice to every real outbound.
        listOf(null, "owenclave0").forEach { bindInterface ->
            cases.filterNot { it.requiresHostSip003Plugin }.forEach { case ->
                val result = build(case.bean(), bindInterface)
                val file = File.createTempFile("owenclave-${case.name}-", ".json")
                try {
                    file.writeText(result.json)
                    val process = ProcessBuilder(core.absolutePath, "test", "-c", file.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        failures += "${case.name} (bind=${bindInterface ?: "none"}): core test timed out"
                    } else if (process.exitValue() != 0 || !output.contains("Configuration OK")) {
                        failures += "${case.name} (bind=${bindInterface ?: "none"}): core rejected the config\n$output"
                    } else {
                        println("[protocol-config] ${case.name} (bind=${bindInterface ?: "none"}): OK")
                    }
                } finally {
                    file.delete()
                }
            }
        }
        assertTrue(failures.isEmpty(), "the core rejected generated configs:\n${failures.joinToString("\n\n")}")
    }

    @Test
    fun `transparent mode binds every real outbound to the interface`() {
        val result = build(cases.first { it.name == "vless" }.bean(), "owenclave0")
        val outbounds = JsonParser.parseString(result.json).asJsonObject.getAsJsonArray("outbounds")
            .map { it.asJsonObject }
        outbounds.forEach { outbound ->
            val protocol = outbound.get("protocol")?.asString
            val bindToDevice = outbound.getAsJsonObject("streamSettings")
                ?.getAsJsonObject("sockopt")
                ?.get("bindToDevice")?.asString
            if (protocol == "blackhole" || protocol == "dns") {
                assertTrue(bindToDevice == null, "$protocol must not be bound to the interface")
            } else {
                assertEquals("owenclave0", bindToDevice, "$protocol outbound has to be bound")
            }
        }
    }

    @Test
    fun `custom config profiles are passed through`() {
        val raw = """{"inbounds":[],"outbounds":[{"tag":"proxy","protocol":"socks","settings":{}}]}"""
        val result = DesktopConfigBuilder.build(
            Profile(name = "custom", customConfig = raw),
            DesktopSettings(),
            emptyList(),
            null,
        )
        assertEquals(raw, result.json)
        assertTrue(result.plugins.isEmpty())
    }

    /** The core binary of the host, exactly where `bin/desktop/core/build.sh` puts it. */
    private fun hostCoreBinary(): File? {
        val root = System.getProperty("owenclave.devRoot")?.takeIf { it.isNotBlank() } ?: return null
        val binaryName = if (DesktopRuntime.isWindows) "owenclave-core.exe" else "owenclave-core"
        return File(root, "desktop/core/dist/${DesktopRuntime.platformTag}/$binaryName").takeIf { it.isFile }
    }

}
