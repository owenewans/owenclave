package io.nekohasekai.sagernet.desktop

import com.google.gson.JsonParser
import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the desktop specific glue: the shared importer wired to the desktop
 * config builder, and the desktop `libexclavecore` shim that builds proxy URIs.
 */
class DesktopConfigBuilderTest {

    /** The outbound that carries the selected profile (the core names it `proxy-global-<id>`). */
    private fun proxyOutboundOf(config: String) = JsonParser.parseString(config).asJsonObject
        .getAsJsonArray("outbounds")
        .map { it.asJsonObject }
        .first {
            val tag = it.get("tag")?.asString ?: ""
            tag == "proxy" || tag.startsWith("proxy-global-")
        }

    private fun build(
        bean: io.nekohasekai.sagernet.fmt.AbstractBean,
        settings: DesktopSettings = DesktopSettings(),
        rules: List<RoutingRule> = emptyList(),
    ): String {
        bean.initializeDefaultValues()
        return DesktopConfigBuilder.build(Profile(bean = bean), settings, rules, null).json
    }

    @Test
    fun `parses a shadowsocks share link and proxies through it`() {
        val beans = SubscriptionImporter.parse("ss://YWVzLTI1Ni1nY206d2ludGVzdHBhc3M=@127.0.0.1:18388#selftest")
        assertEquals(1, beans.size)
        val bean = beans.first()
        assertTrue(bean is ShadowsocksBean)
        assertEquals("127.0.0.1", bean.serverAddress)
        assertEquals(18388, bean.serverPort)
        assertEquals("aes-256-gcm", (bean as ShadowsocksBean).method)
        assertEquals("wintestpass", bean.password)

        val config = build(bean)
        val proxy = proxyOutboundOf(config)
        assertEquals("shadowsocks", proxy.get("protocol").asString)
        val server = proxy.getAsJsonObject("settings").getAsJsonArray("servers")[0].asJsonObject
        assertEquals("127.0.0.1", server.get("address").asString)
        assertEquals(18388, server.get("port").asInt)
    }

    @Test
    fun `parses a clash yaml subscription including naive`() {
        val yaml = """
            proxies:
              - name: My Naive
                type: naive
                server: example.com
                port: 443
                proto: https
                username: user
                password: pass
                sni: example.com
              - name: My SS
                type: ss
                server: ss.example.com
                port: 8388
                cipher: aes-256-gcm
                password: secret
        """.trimIndent()

        val beans = SubscriptionImporter.parse(yaml)
        assertEquals(2, beans.size)
        assertTrue(beans.any { it is NaiveBean }, "naive should be parsed from clash yaml")
        assertTrue(beans.any { it is ShadowsocksBean }, "ss should be parsed from clash yaml")
        assertEquals("My Naive", beans.first().name)
    }

    @Test
    fun `parses a v2ray vmess outbound document`() {
        val document = """
            {
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vmess",
                  "settings": {
                    "vnext": [
                      { "address": "v.example.com", "port": 443,
                        "users": [ { "id": "b831381d-6324-4d53-ad4f-8cda48b30811", "alterId": 0 } ] }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val beans = SubscriptionImporter.parse(document)
        assertEquals(1, beans.size)
        val bean = beans.first()
        assertTrue(bean is VMessBean)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", (bean as VMessBean).uuid)
    }

    @Test
    fun `naive profile is routed through the plugin socks listener`() {
        val bean = NaiveBean().apply {
            serverAddress = "naive.example.com"
            serverPort = 443
            proto = "https"
            username = "user"
            password = "pass"
            sni = "naive.example.com"
        }
        bean.initializeDefaultValues()

        val result = DesktopConfigBuilder.build(Profile(bean = bean), DesktopSettings(), emptyList(), null)

        // NaiveProxy is external: the core gets a socks outbound pointing at the
        // plugin port, and the plugin binding is reported back to the runner.
        assertEquals(1, result.plugins.size)
        val plugin = result.plugins.single()
        assertTrue(plugin.bean is NaiveBean)
        assertTrue(plugin.port > 0)
        assertTrue(plugin.username.isNotEmpty())
        assertTrue(plugin.password.isNotEmpty())

        val proxy = proxyOutboundOf(result.json)
        assertEquals("socks", proxy.get("protocol").asString)
        val server = proxy.getAsJsonObject("settings").getAsJsonArray("servers")[0].asJsonObject
        assertEquals("127.0.0.1", server.get("address").asString)
        assertEquals(plugin.port, server.get("port").asInt)
        val user = server.getAsJsonArray("users")[0].asJsonObject
        assertEquals(plugin.username, user.get("user").asString)
        assertEquals(plugin.password, user.get("pass").asString)
    }

    @Test
    fun `naive plugin config is generated from the shared formatter`() {
        val bean = NaiveBean().apply {
            serverAddress = "naive.example.com"
            serverPort = 443
            proto = "https"
            username = "user"
            password = "pass"
            sni = "naive.example.com"
            extraHeaders = "X-Test: 1"
        }
        bean.initializeDefaultValues()

        val config = JsonParser.parseString(
            bean.buildNaiveConfig(41234, "u", "p")
        ).asJsonObject

        assertEquals("socks://u:p@127.0.0.1:41234", config.get("listen").asString)
        assertTrue(config.get("proxy").asString.startsWith("https://user:pass@naive.example.com:443"))
        assertEquals("X-Test: 1", config.get("extra-headers").asString)
        assertEquals("MAP naive.example.com naive.example.com", config.get("host-resolver-rules").asString)
    }

    @Test
    fun `raw config profiles are passed through unchanged`() {
        val profile = Profile(name = "custom", customConfig = """{ "inbounds": [], "outbounds": [] }""")
        assertNotNull(profile.customConfig)
        assertEquals("Custom", profile.protocolName)
        assertEquals("raw config", profile.address)

        val result = DesktopConfigBuilder.build(profile, DesktopSettings(), emptyList(), null)
        assertEquals(profile.customConfig, result.json)
        assertTrue(result.plugins.isEmpty())
    }

    @Test
    fun `private networks are bypassed only when enabled`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "secret"
        }

        val withBypass = JsonParser.parseString(
            build(bean, DesktopSettings(bypassPrivateNetworks = true))
        ).asJsonObject
        assertTrue(withBypass.has("routing"))

        val withoutBypass = JsonParser.parseString(
            build(bean, DesktopSettings(bypassPrivateNetworks = false))
        ).asJsonObject
        assertTrue(!withoutBypass.has("routing"))
    }

    @Test
    fun `direct mode does not use the upstream profile`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "secret"
        }

        val config = build(
            bean,
            DesktopSettings(routeMode = DesktopSettings.ROUTE_DIRECT, logLevel = LogLevel.DEBUG),
        )
        assertEquals("freedom", proxyOutboundOf(config).get("protocol").asString)
        assertEquals("debug", JsonParser.parseString(config).asJsonObject
            .getAsJsonObject("log").get("loglevel").asString)
    }

    @Test
    fun `routing rules are emitted before the lan bypass`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "secret"
        }

        val config = build(
            bean,
            DesktopSettings(),
            listOf(
                RoutingRule(name = "ads", domains = "keyword:ads, full:api.example.com", target = RuleTarget.BLOCK.tag),
                RoutingRule(enabled = false, domains = "disabled.example.com"),
                RoutingRule(ip = "10.0.0.0/8", target = RuleTarget.DIRECT.tag),
                RoutingRule(name = "a rule without matchers"),
            ),
        )
        val rules = JsonParser.parseString(config).asJsonObject
            .getAsJsonObject("routing").getAsJsonArray("rules")

        assertEquals(3, rules.size(), "two enabled user rules plus the LAN bypass")
        val first = rules[0].asJsonObject
        assertEquals("block", first.get("outboundTag").asString)
        assertEquals(
            listOf("keyword:ads", "full:api.example.com"),
            first.getAsJsonArray("domains").map { it.asString },
        )
        val second = rules[1].asJsonObject
        assertEquals("bypass", second.get("outboundTag").asString)
        assertEquals(listOf("10.0.0.0/8"), second.getAsJsonArray("ip").map { it.asString })
        assertEquals("bypass", rules[2].asJsonObject.get("outboundTag").asString)
    }

    @Test
    fun `android only inbounds and their routing rules are dropped`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "secret"
        }
        val root = JsonParser.parseString(build(bean)).asJsonObject
        val inboundTags = root.getAsJsonArray("inbounds").map {
            it.asJsonObject.get("tag")?.asString
        }
        assertTrue("ipc-in" !in inboundTags, "the Android ipc UDS inbound must be dropped")
        assertTrue("dns-in" !in inboundTags, "the Android DNS UDS inbound must be dropped")
        assertTrue("socks" in inboundTags)
        assertTrue("http" in inboundTags)
    }

    @Test
    fun `subscription and rule survive a json round trip`() {
        val subscription = Subscription(name = "provider", url = "https://example.com/sub", sendHwid = true)
        subscription.lastUpdated = 1234567890L
        subscription.profileCount = 7
        val parsedSubscription = Subscription.fromJson(subscription.toJson())
        assertEquals("provider", parsedSubscription.name)
        assertEquals("https://example.com/sub", parsedSubscription.url)
        assertTrue(parsedSubscription.sendHwid)
        assertEquals(1234567890L, parsedSubscription.lastUpdated)
        assertEquals(7, parsedSubscription.profileCount)

        val rule = RoutingRule(name = "ads", domains = "keyword:ads", port = "80,443", target = RuleTarget.BLOCK.tag)
        val parsedRule = RoutingRule.fromJson(rule.toJson())
        assertEquals("ads", parsedRule.name)
        assertEquals("keyword:ads", parsedRule.domains)
        assertEquals("80,443", parsedRule.port)
        assertEquals(RuleTarget.BLOCK.tag, parsedRule.target)
        assertTrue(parsedRule.enabled)
        assertEquals("domain=keyword:ads, port=80,443", parsedRule.summary)
    }

    @Test
    fun `hwid headers follow the global and per subscription switches`() {
        val settings = DesktopSettings()
        assertTrue(settings.hwidHeaders(subscriptionOverride = false).isEmpty())
        val headers = settings.hwidHeaders(subscriptionOverride = true)
        assertTrue(headers.containsKey("x-hwid"))
        assertTrue(headers.containsKey("x-device-os"))
        assertEquals(32, headers.getValue("x-hwid").length)

        val global = settings.copy(sendHwid = true)
        assertTrue(global.hwidHeaders(subscriptionOverride = false).containsKey("x-hwid"))
    }
}
