package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http.parseHttp
import io.nekohasekai.sagernet.fmt.http.toUri
import io.nekohasekai.sagernet.fmt.masque.MASQUEBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.socks.toUri
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.HappLink
import io.nekohasekai.sagernet.ui.compose.ComposeProfileSettingsActivity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpProxyAndProfileStateTest {

    @BeforeTest
    fun initRuntime() {
        SagerNet.application
    }

    @Test
    fun `http and https share links parse and round trip with tls sni and ipv6`() {
        val httpLink = "http://alice:s3cr3t@198.51.100.10:8080#OfficeHTTP"
        val httpBean = parseHttp(httpLink)
        httpBean.initializeDefaultValues()
        assertEquals("HTTP", httpBean.protocolName())
        assertEquals("198.51.100.10", httpBean.serverAddress)
        assertEquals(8080, httpBean.serverPort)
        assertEquals("alice", httpBean.username)
        assertEquals("s3cr3t", httpBean.password)
        assertEquals("none", httpBean.security)
        assertEquals("OfficeHTTP", httpBean.name)

        val exportedHttp = assertNotNull(httpBean.toUri())
        val roundTrippedHttp = parseHttp(exportedHttp).apply { initializeDefaultValues() }
        assertEquals("HTTP", roundTrippedHttp.protocolName())
        assertEquals("198.51.100.10", roundTrippedHttp.serverAddress)
        assertEquals(8080, roundTrippedHttp.serverPort)
        assertEquals("alice", roundTrippedHttp.username)
        assertEquals("s3cr3t", roundTrippedHttp.password)

        val httpsLink = "HTTPS://bob:p%40ss@[2001:db8::42]:8443?sni=edge.example.com&allowInsecure=1#EdgeHTTPS"
        val httpsBean = parseHttp(httpsLink).apply { initializeDefaultValues() }
        assertEquals("HTTPS", httpsBean.protocolName())
        assertEquals("2001:db8::42", httpsBean.serverAddress)
        assertEquals(8443, httpsBean.serverPort)
        assertEquals("bob", httpsBean.username)
        assertEquals("p@ss", httpsBean.password)
        assertEquals("tls", httpsBean.security)
        assertEquals("edge.example.com", httpsBean.sni)
        assertTrue(httpsBean.allowInsecure)
        assertEquals("EdgeHTTPS", httpsBean.name)

        val exportedHttps = assertNotNull(httpsBean.toUri())
        assertTrue(exportedHttps.startsWith("https://"))
        val roundTrippedHttps = parseHttp(exportedHttps).apply { initializeDefaultValues() }
        assertEquals("HTTPS", roundTrippedHttps.protocolName())
        assertEquals("2001:db8::42", roundTrippedHttps.serverAddress)
        assertEquals(8443, roundTrippedHttps.serverPort)
        assertEquals("edge.example.com", roundTrippedHttps.sni)
        assertTrue(roundTrippedHttps.allowInsecure)
    }

    @Test
    fun `clash yaml imports both http and https proxies`() {
        val yaml = """
            proxies:
              - name: Plain HTTP
                type: http
                server: http.example.com
                port: 3128
                username: u1
                password: p1
              - name: Secure HTTPS
                type: http
                server: https.example.com
                port: 443
                username: u2
                password: p2
                tls: true
                sni: sni.example.com
                skip-cert-verify: true
        """.trimIndent()

        val beans = SubscriptionImporter.parse(yaml)
        assertEquals(2, beans.size)
        val plain = beans[0] as HttpBean
        val secure = beans[1] as HttpBean
        assertEquals("HTTP", plain.protocolName())
        assertEquals("none", plain.security)
        assertEquals(3128, plain.serverPort)

        assertEquals("HTTPS", secure.protocolName())
        assertEquals("tls", secure.security)
        assertEquals("sni.example.com", secure.sni)
        assertTrue(secure.allowInsecure)

        val p1 = Profile(name = plain.name, bean = plain)
        val p2 = Profile(name = secure.name, bean = secure)
        assertEquals("HTTP", p1.protocolName)
        assertEquals("HTTPS", p2.protocolName)
    }

    @Test
    fun `socks plus tls share link parses and round trips`() {
        val link = "socks+tls://user1:pass1@socks.example.com:1080#SocksTLS"
        val bean = parseSOCKS(link).apply { initializeDefaultValues() }
        assertEquals(SOCKSBean.PROTOCOL_SOCKS5, bean.protocol)
        assertEquals("tls", bean.security)
        assertEquals("socks.example.com", bean.serverAddress)
        assertEquals(1080, bean.serverPort)
        assertEquals("user1", bean.username)
        assertEquals("pass1", bean.password)

        val exported = assertNotNull(bean.toUri())
        assertTrue(exported.startsWith("socks+tls://"))
        val reparsed = parseSOCKS(exported).apply { initializeDefaultValues() }
        assertEquals("tls", reparsed.security)
        assertEquals("socks.example.com", reparsed.serverAddress)
    }

    @Test
    fun `compose profile editor state round trips http https vmess alterId and vless reality`() {
        // 1. Default HTTP vs HTTPS presets
        val httpDefault = ComposeProfileSettingsActivity.defaultInitialState(ProxyEntity.TYPE_HTTP, httpTls = false)
        assertEquals("8080", httpDefault.serverPort)
        assertEquals("none", httpDefault.security)

        val httpsDefault = ComposeProfileSettingsActivity.defaultInitialState(ProxyEntity.TYPE_HTTP, httpTls = true)
        assertEquals("443", httpsDefault.serverPort)
        assertEquals("tls", httpsDefault.security)

        val httpsEntity = ProxyEntity(type = ProxyEntity.TYPE_HTTP)
        ComposeProfileSettingsActivity.applyStateToProfileEntity(
            httpsEntity,
            ProxyEntity.TYPE_HTTP,
            httpsDefault.copy(
                name = "Corp HTTPS",
                serverAddress = "corp.example.com",
                serverPort = "8443",
                username = "admin",
                password = "pw",
                sni = "corp.example.com",
                alpn = "h2",
                allowInsecure = true,
                connectUDP = true,
                mux = true,
                muxConcurrency = "4",
            ),
        )
        val savedHttps = httpsEntity.httpBean!!
        assertEquals("HTTPS", savedHttps.protocolName())
        assertEquals(8443, savedHttps.serverPort)
        assertEquals("tls", savedHttps.security)
        assertEquals("corp.example.com", savedHttps.sni)
        assertTrue(savedHttps.allowInsecure)
        assertTrue(savedHttps.connectUDP)
        assertTrue(savedHttps.mux)
        assertEquals(4, savedHttps.muxConcurrency)

        val reloadedHttpsState = ComposeProfileSettingsActivity.profileEntityToState(httpsEntity)
        assertEquals("tls", reloadedHttpsState.security)
        assertEquals("8443", reloadedHttpsState.serverPort)
        assertEquals("corp.example.com", reloadedHttpsState.sni)
        assertTrue(reloadedHttpsState.mux)

        // 2. VMess alterId does NOT overwrite serverPort
        val vmessEntity = ProxyEntity(type = ProxyEntity.TYPE_VMESS)
        val vmessInit = ComposeProfileSettingsActivity.defaultInitialState(ProxyEntity.TYPE_VMESS)
        ComposeProfileSettingsActivity.applyStateToProfileEntity(
            vmessEntity,
            ProxyEntity.TYPE_VMESS,
            vmessInit.copy(
                serverAddress = "vmess.example.com",
                serverPort = "2053",
                uuid = "b831381d-6324-4d53-ad4f-8cda48b30811",
                alterId = "16",
            ),
        )
        val savedVmess: VMessBean = vmessEntity.vmessBean!!
        assertEquals(2053, savedVmess.serverPort)
        assertEquals(16, savedVmess.alterId)
        val vmessStateBack = ComposeProfileSettingsActivity.profileEntityToState(vmessEntity)
        assertEquals("2053", vmessStateBack.serverPort)
        assertEquals("16", vmessStateBack.alterId)

        // 3. VLESS + REALITY + gRPC preserves realityPublicKey, realityShortId, grpcServiceName
        val vlessEntity = ProxyEntity(type = ProxyEntity.TYPE_VLESS)
        val vlessInit = ComposeProfileSettingsActivity.defaultInitialState(ProxyEntity.TYPE_VLESS)
        ComposeProfileSettingsActivity.applyStateToProfileEntity(
            vlessEntity,
            ProxyEntity.TYPE_VLESS,
            vlessInit.copy(
                serverAddress = "vless.example.com",
                serverPort = "443",
                uuid = "b831381d-6324-4d53-ad4f-8cda48b30811",
                network = "grpc",
                path = "gun-svc",
                security = "reality",
                sni = "www.microsoft.com",
                realityPublicKey = "pubkey123",
                realityShortId = "abcd0123",
                utlsFingerprint = "chrome",
            ),
        )
        val savedVless: VLESSBean = vlessEntity.vlessBean!!
        assertEquals("reality", savedVless.security)
        assertEquals("pubkey123", savedVless.realityPublicKey)
        assertEquals("abcd0123", savedVless.realityShortId)
        assertEquals("gun-svc", savedVless.grpcServiceName)
        val vlessStateBack = ComposeProfileSettingsActivity.profileEntityToState(vlessEntity)
        assertEquals("pubkey123", vlessStateBack.realityPublicKey)
        assertEquals("abcd0123", vlessStateBack.realityShortId)
        assertEquals("gun-svc", vlessStateBack.path)
    }

    @Test
    fun `profile protocolName and ipv6 displayAddress match upstream conventions`() {
        val ipv6Http = HttpBean().apply {
            serverAddress = "2001:db8::1"
            serverPort = 443
            security = "tls"
            initializeDefaultValues()
        }
        val profile = Profile(name = "v6", bean = ipv6Http)
        assertEquals("HTTPS", profile.protocolName)
        assertEquals("[2001:db8::1]:443", profile.address)

        assertEquals("ShadowsocksR", Profile(bean = ShadowsocksRBean().apply { initializeDefaultValues() }).protocolName)
        assertEquals("MASQUE", Profile(bean = MASQUEBean().apply { initializeDefaultValues() }).protocolName)
    }

    @Test
    fun `happ keys resource is bundled on desktop classpath`() {
        assertNotNull(
            HappLink::class.java.getResourceAsStream("/happ/happ-keys.json"),
            "/happ/happ-keys.json must be accessible from :desktop:shared resources",
        )
    }

}
