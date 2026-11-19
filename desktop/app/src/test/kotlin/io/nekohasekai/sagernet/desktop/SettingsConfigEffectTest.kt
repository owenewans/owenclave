package io.nekohasekai.sagernet.desktop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.RouteMode
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Proves that the Android global preferences the desktop Settings tab renders
 * actually reach the generated core config: every setting here is changed through
 * [DesktopSettings.withValue] (the same path the UI uses) and then observed in
 * the JSON produced by [DesktopConfigBuilder.build].
 *
 * This is the counterpart of `SettingsParityTest`: that one guarantees the menu is
 * complete, this one guarantees the values are not decorative.
 */
class SettingsConfigEffectTest {

    private val testUuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    private fun vmess(mux: Boolean = false): VMessBean = VMessBean().apply {
        serverAddress = "1.2.3.4"
        serverPort = 443
        uuid = testUuid
        alterId = 0
        encryption = "auto"
        type = "tcp"
        security = "none"
        this.mux = mux
        initializeDefaultValues()
    }

    private fun trojanTls(): TrojanBean = TrojanBean().apply {
        serverAddress = "1.2.3.4"
        serverPort = 443
        password = "secret"
        type = "tcp"
        security = "tls"
        sni = "example.com"
        allowInsecure = true
        initializeDefaultValues()
    }

    private fun build(bean: AbstractBean, settings: DesktopSettings = DesktopSettings()): JsonObject =
        JsonParser.parseString(
            DesktopConfigBuilder.build(Profile(bean = bean), settings, emptyList(), null).json
        ).asJsonObject

    private fun JsonObject.outbounds(): List<JsonObject> =
        getAsJsonArray("outbounds").map { it.asJsonObject }

    /** The outbound that carries the selected profile. */
    private fun JsonObject.proxyOutbound(): JsonObject = outbounds().first {
        val tag = it.get("tag")?.asString ?: ""
        tag == "proxy" || tag.startsWith("proxy-global-")
    }

    private fun JsonObject.inbound(protocol: String): JsonObject? =
        getAsJsonArray("inbounds").map { it.asJsonObject }.firstOrNull {
            it.get("protocol")?.asString == protocol
        }

    private fun JsonObject.inboundsByTag(tag: String): List<JsonObject> =
        getAsJsonArray("inbounds").map { it.asJsonObject }.filter {
            it.get("tag")?.asString == tag
        }

    private fun tlsFragmentOf(config: JsonObject): JsonObject? = config.proxyOutbound()
        .getAsJsonObject("streamSettings")
        ?.getAsJsonObject("sockopt")
        ?.getAsJsonObject("tlsFragmentation")

    // ------------------------------------------------------------------ routing

    @Test
    fun `remoteDNS and its query strategy reach the dns block`() {
        val base = build(vmess())
        assertEquals("tcp://1.1.1.1", base.getAsJsonObject("dns")
            .getAsJsonArray("servers")[0].asJsonObject.get("address").asString)

        val changed = build(
            vmess(),
            DesktopSettings()
                .withValue(Key.REMOTE_DNS, "tcp://9.9.9.9")
                .withValue(Key.REMOTE_DNS_QUERY_STRATEGY, "UseIPv4"),
        )
        val server = changed.getAsJsonObject("dns").getAsJsonArray("servers")[0].asJsonObject
        assertEquals("tcp://9.9.9.9", server.get("address").asString)
        assertEquals("UseIPv4", server.get("queryStrategy").asString)
        assertNotEquals(base, changed)
    }

    @Test
    fun `dns routing and fake dns switches reach the config`() {
        val withRouting = build(
            vmess(),
            DesktopSettings().withValue(Key.ENABLE_DNS_ROUTING, "false"),
        )
        assertNotEquals(build(vmess()), withRouting)

        val withFakeDns = build(
            vmess(),
            DesktopSettings().withValue(Key.ENABLE_FAKEDNS, "true"),
        )
        // Fake DNS adds a fakedns pool to the DNS block.
        assertTrue(withFakeDns.getAsJsonObject("dns").toString().contains("fakedns"))
    }

    @Test
    fun `fragment can be switched on and off`() {
        val off = build(trojanTls())
        assertTrue(tlsFragmentOf(off) == null, "fragmentation must be off by default")

        val on = build(trojanTls(), DesktopSettings().withValue(Key.ENABLE_FRAGMENT, "true"))
        val fragment = tlsFragmentOf(on)
        assertTrue(fragment != null, "fragmentation must be present when switched on")
        assertEquals(true, fragment!!.get("tlsRecordFragmentation").asBoolean)

        val segmented = build(
            trojanTls(),
            DesktopSettings()
                .withValue(Key.ENABLE_FRAGMENT, "true")
                .withValue(Key.FRAGMENT_METHOD, "1"),
        )
        assertEquals(true, tlsFragmentOf(segmented)!!.get("tcpSegmentation").asBoolean)
    }

    @Test
    fun `sniffing can be switched on and off`() {
        val on = build(vmess())
        assertEquals(true, on.inbound("socks")!!.getAsJsonObject("sniffing").get("enabled").asBoolean)

        val off = build(vmess(), DesktopSettings().withValue(Key.TRAFFIC_SNIFFING, "false"))
        assertFalse(off.inbound("socks")!!.has("sniffing"), "sniffing must be gone when disabled")
        assertNotEquals(on, off)
    }

    @Test
    fun `route mode direct drops the upstream profile`() {
        val global = build(vmess())
        assertTrue(global.proxyOutbound().get("protocol").asString == "vmess")

        val direct = build(vmess(), DesktopSettings().withValue(Key.ROUTE_MODE, RouteMode.DIRECT.toString()))
        assertEquals("freedom", direct.proxyOutbound().get("protocol").asString)
    }

    @Test
    fun `socks and http ports reach the inbounds`() {
        val base = build(vmess())
        assertEquals(10808, base.inbound("socks")!!.get("port").asInt)
        assertEquals(10809, base.inbound("http")!!.get("port").asInt)

        val changed = build(
            vmess(),
            DesktopSettings()
                .withValue(Key.SOCKS_PORT, "12345")
                .withValue(Key.HTTP_PORT, "12346"),
        )
        assertEquals(12345, changed.inbound("socks")!!.get("port").asInt)
        assertEquals(12346, changed.inbound("http")!!.get("port").asInt)
    }

    @Test
    fun `inbound switches and credentials reach the inbounds`() {
        val settings = DesktopSettings()
            .withValue(Key.REQUIRE_SOCKS, "false")
            .withValue(Key.REQUIRE_HTTP, "true")
            .withValue(Key.SOCKS_USERNAME, "user")
            .withValue(Key.SOCKS_PASSWORD, "pass")
            .withValue(Key.ALLOW_ACCESS, "true")
        val config = build(vmess(), settings)
        assertTrue(config.inbound("socks") == null, "the SOCKS inbound is switched off")
        val http = config.inbound("http")!!
        // allowAccess binds the inbounds to all interfaces.
        assertEquals("0.0.0.0", http.get("listen").asString)
    }

    @Test
    fun `mux on the profile changes the outbound`() {
        val off = build(vmess(mux = false))
        val on = build(vmess(mux = true))
        assertNotEquals(off, on)
        assertTrue(on.proxyOutbound().has("mux"), "mux must appear on the outbound")
        assertFalse(off.proxyOutbound().has("mux"))
    }

    @Test
    fun `the Android local DNS inbound is adapted for desktop`() {
        // The Android UDS (`ipc_dns.sock`) is always dropped, the port based
        // `requireDnsInbound` listener is kept and honours `portLocalDns`.
        val off = build(vmess())
        assertTrue(off.inboundsByTag("dns-in").isEmpty(), "no DNS inbound by default")

        val on = build(
            vmess(),
            DesktopSettings()
                .withValue(Key.REQUIRE_DNS_INBOUND, "true")
                .withValue(Key.LOCAL_DNS_PORT, "15353"),
        )
        val dns = on.inboundsByTag("dns-in").single()
        assertEquals("dokodemo-door", dns.get("protocol").asString)
        assertEquals(15353, dns.get("port").asInt)
        assertEquals("127.0.0.1", dns.get("listen").asString)
        assertFalse(dns.get("listen").asString.endsWith(".sock"))
        // the inboundTag dns-in -> dns-out routing rule survives postProcess
        val rules = on.getAsJsonObject("routing").getAsJsonArray("rules")
        assertTrue(
            rules.any { rule ->
                rule.asJsonObject.getAsJsonArray("inboundTag")
                    ?.any { it.asString == "dns-in" } == true
            },
            "the dns-in -> dns-out routing rule must be kept",
        )
    }

    // ---------------------------------------------------------------- mapping

    @Test
    fun `every DataStoreMember binding has a shim mapping`() {
        val members = SettingsCatalogParser.load().entries
            .mapNotNull { (it.binding as? DataStoreMember)?.member }
        assertTrue(members.isNotEmpty())
        members.forEach { member -> DesktopConfigBuilder.applyToDataStore(member, null) }
        assertFailsWith<IllegalStateException> {
            DesktopConfigBuilder.applyToDataStore("no-such-member", null)
        }
    }

    // ------------------------------------------------- VpnService -> TUN bridge

    @Test
    fun `service mode VPN is the desktop TUN device`() {
        val off = DesktopSettings()
        assertEquals("proxy", off.value(Key.SERVICE_MODE))
        assertFalse(off.tunEnabled, "the desktop TUN is off by default")
        assertFalse(off.systemProxyEnabled)

        val on = off.withValue(Key.SERVICE_MODE, "vpn")
        assertTrue(on.tunEnabled, "Service mode = VPN must enable the desktop TUN")
        assertEquals("vpn", on.value(Key.SERVICE_MODE))
        assertFalse(on.withValue(Key.SERVICE_MODE, "proxy").tunEnabled)

        // The desktop-only third choice points the OS proxy at the local inbound.
        val system = off.withValue(Key.SERVICE_MODE, "system")
        assertTrue(system.systemProxyEnabled)
        assertFalse(system.tunEnabled, "System proxy is not a TUN mode")

        // The generated config must still be told "proxy": the desktop TUN device is
        // built by tun2socks, never through the Android VpnService arguments.
        build(vmess(), on)
        assertEquals(
            io.nekohasekai.sagernet.Key.MODE_PROXY,
            io.nekohasekai.sagernet.database.DataStore.serviceMode,
        )
    }

    @Test
    fun `system proxy and per-app routing leave the core config unchanged`() {
        // The same Profile, so the generated outbound tag is identical.
        val profile = Profile(bean = vmess())
        val base = JsonParser.parseString(
            DesktopConfigBuilder.build(profile, DesktopSettings(), emptyList(), null).json
        ).asJsonObject
        val external = JsonParser.parseString(
            DesktopConfigBuilder.build(
                profile,
                DesktopSettings()
                    .withValue(Key.SERVICE_MODE, "system")
                    .withValue(Key.PROXY_APPS, "true")
                    .copy(perAppProcesses = "curl\n/usr/bin/firefox"),
                emptyList(),
                null,
            ).json
        ).asJsonObject
        // Both features act on the operating system, never on the core JSON.
        assertEquals(base, external)
    }

    @Test
    fun `the IPv6 TUN switch is persisted and read by the runner`() {
        assertEquals("false", DesktopSettings().value(Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS))
        // CoreRunner turns exactly this predicate into TunSession's ipv6 flag.
        val on = DesktopSettings().withValue(Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS, "true")
        assertTrue(on.value(Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS) == "true")
        assertTrue(TunSession.TUN_ADDRESS6.startsWith("fd"), "the tunnel uses a ULA address")
    }

    @Test
    fun `desktop preferences survive a store round trip`() {
        val file = kotlin.io.path.createTempFile("owenclave-settings", ".json").toFile()
        try {
            val store = ProfileStore(file)
            store.load()
            store.settings = DesktopSettings()
                .withValue(Key.REMOTE_DNS, "tcp://8.8.8.8")
                .withValue(Key.ENABLE_FRAGMENT, "true")
                .withValue(Key.SOCKS_PORT, "23456")
                .withValue(Key.SERVICE_MODE, "vpn")
                .withValue(Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS, "true")
                .copy(perAppProcesses = "curl\nfirefox")
            store.save()

            val reloaded = ProfileStore(file).apply { load() }
            assertEquals("tcp://8.8.8.8", reloaded.settings.value(Key.REMOTE_DNS))
            assertEquals("true", reloaded.settings.value(Key.ENABLE_FRAGMENT))
            assertEquals(23456, reloaded.settings.socksPort)
            assertEquals("vpn", reloaded.settings.value(Key.SERVICE_MODE))
            assertTrue(reloaded.settings.tunEnabled)
            assertEquals("true", reloaded.settings.value(Key.ENABLE_VPN_INTERFACE_IPV6_ADDRESS))
            assertEquals("curl\nfirefox", reloaded.settings.perAppProcesses)
        } finally {
            file.delete()
        }
    }
}
