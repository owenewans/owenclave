package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop subscription download path: the shared share link parser fed with a
 * plain and a base64 encoded document, plus a real download through the JDK
 * [java.net.http.HttpClient] against a loopback server (the same pair
 * [SubscriptionSelfTest] and `AppState.refreshSubscription` run).
 */
class SubscriptionImporterTest {

    private val document: String get() = SubscriptionSelfTest.sampleDocument

    private fun assertTwoProfiles(beans: List<io.nekohasekai.sagernet.fmt.AbstractBean>) {
        assertEquals(2, beans.size)
        val protocols = beans.groupingBy { it.javaClass.simpleName }.eachCount()
        assertEquals(1, protocols["ShadowsocksBean"], "one shadowsocks profile expected: $protocols")
        assertEquals(1, protocols["VLESSBean"], "one vless profile expected: $protocols")
        beans.forEach { bean ->
            assertTrue(bean.serverAddress.isNotBlank(), "${bean.javaClass.simpleName}: empty server address")
            assertTrue(bean.serverPort in 1..65535, "${bean.javaClass.simpleName}: invalid port")
        }
        val ss = beans.filterIsInstance<ShadowsocksBean>().single()
        assertEquals("aes-256-gcm", ss.method)
        assertEquals("owenclave-sub-selftest", ss.password)
        assertEquals("198.51.100.10", ss.serverAddress)
        assertEquals(8388, ss.serverPort)
        val vless = beans.filterIsInstance<VLESSBean>().single()
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", vless.uuid)
        assertEquals("198.51.100.11", vless.serverAddress)
        assertEquals(443, vless.serverPort)
    }

    @Test
    fun `parses a plain share link list`() {
        assertTwoProfiles(SubscriptionImporter.parse(document))
    }

    @Test
    fun `parses a base64 encoded share link list`() {
        val encoded = Base64.getEncoder().encodeToString(document.toByteArray(Charsets.UTF_8))
        assertTwoProfiles(SubscriptionImporter.parse(encoded))
    }

    @Test
    fun `downloads a subscription through the jdk http client`() {
        // SelfTest.LocalHttpServer is jdk.httpserver free, so this also works inside
        // the packaged jlink image.
        val server = SelfTest.LocalHttpServer(document)
        try {
            val url = "http://127.0.0.1:${server.port}/"
            val body = SubscriptionImporter.download(url)
            assertEquals(document.trim(), body.trim())
            assertTwoProfiles(SubscriptionImporter.parse(body))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `mask url keeps only the scheme host and two path characters`() {
        assertEquals(
            "https://sub.example.test/-",
            SubscriptionSelfTest.maskUrl("https://sub.example.test/-fakePath0123456789?token=secret#frag"),
        )
        assertEquals(
            "https://sub.example.test/a",
            SubscriptionSelfTest.maskUrl("https://user:password@sub.example.test/abcdef"),
        )
        assertEquals("https://sub.example.test", SubscriptionSelfTest.maskUrl("https://sub.example.test"))
    }

}
