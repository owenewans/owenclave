package io.nekohasekai.sagernet.fmt

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http3.Http3Bean
import io.nekohasekai.sagernet.fmt.masque.MASQUEBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.parseRayUUID
import io.nekohasekai.sagernet.ktx.parseUUID
import org.junit.Assert.*
import org.junit.Test

class ProtocolCompatibilityTest {
    @Test
    fun naiveTimeoutsSurviveSerialization() {
        val bean = NaiveBean().apply {
            initializeDefaultValues()
            tunnelTimeout = 30
            idleTimeout = 60
            extraHeaders = "X-Test: value"
        }
        val restored = bean.clone()
        assertEquals(bean.tunnelTimeout, restored.tunnelTimeout)
        assertEquals(bean.idleTimeout, restored.idleTimeout)
        assertEquals(bean.extraHeaders, restored.extraHeaders)
    }

    @Test
    fun existingNaiveProfilesGetDefaultTimeouts() {
        val bytes = ByteBufferOutput(256, -1).apply {
            writeInt(7)
            writeString("example.com")
            writeInt(443)
            writeString("https")
            writeString("user")
            writeString("pass")
            writeString("X-Test: value")
            writeInt(2)
            writeBoolean(true)
            writeString("example.com")
            writeString("certificate")
            writeBoolean(true)
        }.toBytes()
        val restored = NaiveBean().apply {
            deserialize(ByteBufferInput(bytes))
            initializeDefaultValues()
        }
        assertEquals("user", restored.username)
        assertEquals("certificate", restored.certificate)
        assertEquals(0, restored.tunnelTimeout.toInt())
        assertEquals(0, restored.idleTimeout.toInt())
        assertTrue(restored.singUoT)
    }

    @Test
    fun forkSocksChainFieldsRemainReadable() {
        val bean = SOCKSBean().apply {
            initializeDefaultValues()
            name = "chain profile"
            chainHost = "127.0.0.1"
            chainPort = 1081
            chainUsername = "user"
            chainPassword = "pass"
        }
        val restored = bean.clone()
        assertEquals(bean.name, restored.name)
        assertEquals(bean.chainHost, restored.chainHost)
        assertEquals(bean.chainPort, restored.chainPort)
        assertEquals(bean.chainUsername, restored.chainUsername)
        assertEquals(bean.chainPassword, restored.chainPassword)
    }

    @Test
    fun connectUdpSurvivesSerialization() {
        val template = "https://example.com/.well-known/masque/udp/{target_host}/{target_port}/"
        val http = HttpBean().apply {
            initializeDefaultValues()
            connectUDP = true
            uriTemplate = template
        }.clone()
        val http3 = Http3Bean().apply {
            initializeDefaultValues()
            connectUDP = true
            uriTemplate = template
        }.clone()
        assertTrue(http.connectUDP)
        assertTrue(http3.connectUDP)
        assertEquals(template, http.uriTemplate)
        assertEquals(template, http3.uriTemplate)
    }

    @Test
    fun masqueDoesNotReuseRemovedProtocolId() {
        assertEquals(32, ProxyEntity.TYPE_MASQUE)
        val bean = MASQUEBean().apply {
            initializeDefaultValues()
            username = "user"
            password = "pass"
            uriTemplate = "/udp/{target_host}/{target_port}/"
            http3 = false
            mtu = 1400
        }
        val restored = bean.clone()
        assertEquals(bean.username, restored.username)
        assertEquals(bean.password, restored.password)
        assertEquals(bean.uriTemplate, restored.uriTemplate)
        assertFalse(restored.http3)
        assertEquals(bean.mtu, restored.mtu)
    }

    @Test
    fun legacyShadowQuicDisableAlpnBecomesEmptyAlpn() {
        val bytes = ByteBufferOutput(256, -1).apply {
            writeString("example.com")
            writeInt(443)
            writeInt(3)
            writeString("user")
            writeString("pass")
            writeString("example.com")
            writeString("h3")
            writeString("new-reno")
            writeBoolean(false)
            writeBoolean(true)
            writeBoolean(true)
            writeBoolean(false)
            writeString("")
            writeLong(0)
        }.toBytes()
        val restored = ShadowQUICBean().apply {
            deserialize(ByteBufferInput(bytes))
            initializeDefaultValues()
        }
        assertEquals("", restored.alpn)
        assertEquals("new_reno", restored.congestionControl)
        assertTrue(restored.udpOverStream)
    }

    @Test
    fun malformedRayUuidDoesNotThrow() {
        for (value in listOf("", "0".repeat(31), "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz")) {
            assertNull(parseRayUUID(value))
        }
        assertNotNull(parseRayUUID("short-xray-id"))
    }

    @Test
    fun uuidParserAcceptsUpstreamForms() {
        val hex = "550e8400e29b41d4a716446655440000"
        val dashed = "550e8400-e29b-41d4-a716-446655440000"
        for (value in listOf(hex, dashed, "{$hex}", "{$dashed}", "urn:uuid:$hex", "urn:uuid:$dashed")) {
            assertEquals(dashed, parseUUID(value)?.toHexDashString())
        }
        assertNull(parseUUID("not-a-uuid"))
    }
}
