package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.fmt.naive.parseNaive
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.compose.ComposeMainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import libexclavecore.Libexclavecore
import libexclavecore.V2RayInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedAndroidIntegrationTest {

    @Test
    fun libexclavecoreJniWorks() {
        SagerNet.application
        assertTrue(Libexclavecore.isIP("1.1.1.1"))
        assertTrue(Libexclavecore.isIP("2606:4700:4700::1111"))
        assertFalse(Libexclavecore.isIP("example.com"))

        val url = Libexclavecore.parseURL("vless://uuid@example.com:8443/ws?security=tls#TestNode")
        assertEquals("vless", url.scheme)
        assertEquals("example.com", url.host)
        assertEquals(8443, url.port)
        assertEquals("/ws", url.path)
        assertEquals("TestNode", url.fragment)
    }

    @Test
    fun v2rayInstanceLoadsAndroidConfigBuilderOutputViaJni() {
        SagerNet.application
        val bean = ShadowsocksBean().apply {
            name = "JNI Test"
            serverAddress = "127.0.0.1"
            serverPort = 18388
            method = "aes-256-gcm"
            password = "secret"
            initializeDefaultValues()
        }
        val entity = ProxyEntity(groupId = 1L).apply {
            putBean(bean)
        }
        val built = buildV2RayConfig(entity)
        assertTrue(built.config.isNotBlank())
        val instance = V2RayInstance()
        try {
            instance.loadConfig(built.config)
        } finally {
            runCatching { instance.close() }
        }
    }

    @Test
    fun sharedDatabaseAndAndroidComposeClassesInitialize() = runBlocking {
        SagerNet.application
        val defaultGroup = SagerDatabase.groupDao.allGroups().firstOrNull()
        assertNotNull(defaultGroup, "Default group must exist in SagerDatabase")

        val bean = ShadowsocksBean().apply {
            name = "DB Test Profile"
            serverAddress = "127.0.0.1"
            serverPort = 8388
            method = "2022-blake3-aes-128-gcm"
            password = "secret"
        }
        val entity = ProfileManager.createProfile(defaultGroup.id, bean)
        assertTrue(entity.id > 0)
        val loaded = SagerDatabase.proxyDao.getById(entity.id)
        assertNotNull(loaded)
        assertEquals("DB Test Profile", loaded.displayName())
        ProfileManager.deleteProfile(defaultGroup.id, entity.id)

        val activity = ComposeMainActivity()
        assertNotNull(activity)
    }

    @Test
    fun naivePluginAndAndroidConfigWork() {
        SagerNet.application
        val plugin = PluginManager.init("naive-plugin")
        assertNotNull(plugin)
        assertTrue(File(plugin.path).isFile, "Naive plugin binary must exist at ${plugin.path}")

        val bean: NaiveBean = parseNaive("naive+https://user:pass@example.com:443?sni=cdn.example.com#MyNaive").apply {
            initializeDefaultValues()
        }
        val entity = ProxyEntity(groupId = 1L).apply {
            putBean(bean)
        }
        val built = buildV2RayConfig(entity)
        assertTrue(built.index.isNotEmpty(), "Naive profile must register an external plugin chain in buildV2RayConfig")
        val chainEntry = built.index.first().chain.entries.first()
        val (port, username, password) = chainEntry.key
        val naiveJson = bean.buildNaiveConfig(port, username, password)
        assertTrue(naiveJson.contains("socks://$username:$password@127.0.0.1:$port"))
        assertTrue(naiveJson.contains("https://user:pass@cdn.example.com:${bean.finalPort}"))

        val instance = V2RayInstance()
        try {
            instance.loadConfig(built.config)
        } finally {
            runCatching { instance.close() }
        }
    }
}
