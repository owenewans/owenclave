package io.nekohasekai.sagernet.desktop

import com.google.gson.JsonParser
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * System proxy set/restore symmetry, idempotence and non-clobbering, with a
 * [FakeHost] command runner for every platform the layer implements. No test
 * touches the developer machine.
 */
class SystemProxyTest {

    private fun dataDir(): File = createTempDirectory("owenclave-proxy").toFile()

    private fun proxy(host: FakeHost, os: String, dir: File) =
        SystemProxy(runner = host, os = os, dataDir = dir)

    // ------------------------------------------------------------------- GNOME

    @Test
    fun `gnome apply and restore round trip byte for byte`() {
        val host = FakeHost()
        val dir = dataDir()
        val proxy = proxy(host, "linux", dir)

        host.gnome["org.gnome.system.proxy/mode"] = "'manual'"
        host.gnome["org.gnome.system.proxy.http/host"] = "'old.proxy'"
        host.gnome["org.gnome.system.proxy.http/port"] = "3128"
        host.gnome["org.gnome.system.proxy.socks/host"] = "'old.proxy'"
        host.gnome["org.gnome.system.proxy.socks/port"] = "1080"
        val before = LinkedHashMap(host.gnome)

        val message = proxy.apply("127.0.0.1", 10809, 10808)
        assertTrue(message.contains("gnome"), message)
        assertTrue(proxy.hasBackup(), "the previous state must be backed up before touching the OS")
        assertEquals("'manual'", host.gnome["org.gnome.system.proxy/mode"])
        assertEquals("'127.0.0.1'", host.gnome["org.gnome.system.proxy.http/host"])
        assertEquals("10809", host.gnome["org.gnome.system.proxy.http/port"])
        assertEquals("10808", host.gnome["org.gnome.system.proxy.socks/port"])

        val result = proxy.restore()
        assertTrue(result.restored)
        assertEquals(before, host.gnome, "restore must put every field back")
        assertFalse(proxy.hasBackup(), "the backup is consumed by the restore")
    }

    @Test
    fun `applying twice keeps the original backup and is idempotent`() {
        val host = FakeHost()
        val dir = dataDir()
        val proxy = proxy(host, "linux", dir)

        proxy.apply("127.0.0.1", 10809, 10808)
        val firstBackup = File(dir, SystemProxy.BACKUP_FILE).readText()
        val second = proxy.apply("127.0.0.1", 10809, 10808)
        assertTrue(second.contains("already points"), second)
        assertEquals(
            firstBackup,
            File(dir, SystemProxy.BACKUP_FILE).readText(),
            "a second apply must not overwrite the remembered original values",
        )

        proxy.restore()
        assertEquals("'none'", host.gnome["org.gnome.system.proxy/mode"], "restored to the original")
        assertEquals("''", host.gnome["org.gnome.system.proxy.http/host"])

        // Restore is idempotent: a second call does nothing.
        val again = proxy.restore()
        assertFalse(again.restored)
        assertEquals("no system proxy backup", again.message)
    }

    @Test
    fun `restore keeps a value the user changed while connected`() {
        val host = FakeHost()
        val dir = dataDir()
        val proxy = proxy(host, "linux", dir)

        proxy.apply("127.0.0.1", 10809, 10808)
        // The user switched the desktop proxy mode by hand while connected.
        host.gnome["org.gnome.system.proxy/mode"] = "'auto'"

        val result = proxy.restore()
        assertTrue(result.restored)
        assertEquals(listOf("gnome:mode"), result.kept)
        assertEquals("'auto'", host.gnome["org.gnome.system.proxy/mode"], "hand edit is not clobbered")
        assertEquals("''", host.gnome["org.gnome.system.proxy.http/host"], "other fields are restored")
    }

    @Test
    fun `a stale backup is restored on the next start`() {
        val host = FakeHost()
        val dir = dataDir()
        host.gnome["org.gnome.system.proxy/mode"] = "'manual'"
        host.gnome["org.gnome.system.proxy.http/host"] = "'old.proxy'"

        // First run: apply and "crash" without restoring.
        proxy(host, "linux", dir).apply("127.0.0.1", 10809, 10808)
        assertTrue(proxy(host, "linux", dir).hasBackup())

        // Next start finds the stale backup and puts the OS state back.
        val restored = proxy(host, "linux", dir).restoreStale()
        assertNotNull(restored)
        assertTrue(restored.restored)
        assertEquals("'manual'", host.gnome["org.gnome.system.proxy/mode"])
        assertEquals("'old.proxy'", host.gnome["org.gnome.system.proxy.http/host"])
        assertFalse(proxy(host, "linux", dir).hasBackup())
    }

    @Test
    fun `no gnome and no kde is reported instead of pretended`() {
        val host = FakeHost().apply {
            gsettingsAvailable = false
            kwriteconfigAvailable = false
            kreadconfigAvailable = false
        }
        val proxy = proxy(host, "linux", dataDir())
        val error = assertFailsWith<SystemProxyException> { proxy.apply("127.0.0.1", 10809, 10808) }
        assertTrue(error.message!!.contains("GNOME"), error.message)
        assertFalse(proxy.hasBackup(), "nothing was touched, so there is no backup")
    }

    // --------------------------------------------------------------------- KDE

    @Test
    fun `kde apply and restore round trip`() {
        val host = FakeHost().apply {
            gsettingsAvailable = false
            kwriteconfigAvailable = true
            kreadconfigAvailable = true
        }
        val dir = dataDir()
        val proxy = proxy(host, "linux", dir)

        proxy.apply("127.0.0.1", 10809, 10808)
        assertEquals("1", host.kde["ProxyType"])
        assertEquals("http://127.0.0.1:10809", host.kde["httpProxy"])
        assertEquals("http://127.0.0.1:10809", host.kde["httpsProxy"])
        assertEquals("socks://127.0.0.1:10808", host.kde["socksProxy"])

        proxy.restore()
        assertEquals("0", host.kde["ProxyType"], "the previous KDE proxy type is back")
        assertEquals("", host.kde["httpProxy"])
    }

    // ----------------------------------------------------------------- macOS

    @Test
    fun `macos sets and restores the active service proxies`() {
        val host = FakeHost().apply {
            mac["web.Enabled"] = "Yes"
            mac["web.Server"] = "old.proxy"
            mac["web.Port"] = "3128"
            mac["socks.Enabled"] = "No"
            mac["socks.Server"] = ""
            mac["socks.Port"] = "0"
        }
        val dir = dataDir()
        val proxy = proxy(host, "darwin", dir)

        proxy.apply("127.0.0.1", 10809, 10808)
        assertEquals("Yes", host.mac["web.Enabled"])
        assertEquals("127.0.0.1", host.mac["web.Server"])
        assertEquals("10809", host.mac["web.Port"])
        assertEquals("10809", host.mac["secureweb.Port"])
        assertEquals("10808", host.mac["socks.Port"])
        assertEquals("Yes", host.mac["socks.Enabled"])
        assertTrue(
            host.commands.any { it.first() == "networksetup" && it.contains("-setwebproxystate") },
            "the active service is resolved from networksetup and switched on",
        )

        proxy.restore()
        assertEquals("old.proxy", host.mac["web.Server"])
        assertEquals("3128", host.mac["web.Port"])
        assertEquals("No", host.mac["socks.Enabled"], "a disabled proxy is reverted to disabled")
        assertEquals("", host.mac["socks.Server"])
    }

    @Test
    fun `macos restores empty bypass domains using Empty keyword without networksetup error`() {
        val host = FakeHost().apply {
            mac["bypass"] = ""
        }
        val dir = dataDir()
        val proxy = proxy(host, "darwin", dir)

        proxy.apply("127.0.0.1", 9080, 2080, bypass = listOf("localhost", "127.0.0.1", "192.168.0.0/16"))
        assertEquals("localhost 127.0.0.1 192.168.0.0/16", host.mac["bypass"])
        assertTrue(proxy.hasBackup())

        val restoreRes = proxy.restore()
        assertTrue(restoreRes.restored, restoreRes.message)
        assertFalse(proxy.hasBackup())
        assertEquals("", host.mac["bypass"])
        assertTrue(
            host.commands.any { it.contains("-setproxybypassdomains") && it.last() == "Empty" },
            "Clearing bypass domains on macOS requires passing 'Empty' to networksetup",
        )
    }

    // --------------------------------------------------------------- Windows

    @Test
    fun `windows writes the registry and refreshes wininet`() {
        val host = FakeHost().apply {
            registry["ProxyEnable"] = "0x0"
            registry["ProxyServer"] = "old.proxy:8080"
            registry["ProxyOverride"] = "<local>"
        }
        val dir = dataDir()
        val proxy = proxy(host, "windows", dir)

        proxy.apply("127.0.0.1", 10809, 10808, bypass = listOf("<local>", "*.example.com"))
        assertEquals("0x1", host.registry["ProxyEnable"])
        assertEquals(
            "http=127.0.0.1:10809;https=127.0.0.1:10809;socks=127.0.0.1:10808",
            host.registry["ProxyServer"],
        )
        assertEquals("<local>;*.example.com", host.registry["ProxyOverride"])
        assertTrue(
            host.commands.any { it.first() == "powershell" && it.any { part -> part.contains("InternetSetOption") } },
            "WinINet must be told that the settings changed",
        )

        proxy.restore()
        assertEquals("0x0", host.registry["ProxyEnable"])
        assertEquals("old.proxy:8080", host.registry["ProxyServer"])
        assertEquals("<local>", host.registry["ProxyOverride"])
    }

    @Test
    fun `windows removes a value that did not exist before`() {
        val host = FakeHost().apply {
            registry["ProxyEnable"] = "0x0"
            registry["ProxyServer"] = null
            registry["ProxyOverride"] = null
        }
        val proxy = proxy(host, "windows", dataDir())
        proxy.apply("127.0.0.1", 10809, 10808)
        assertEquals("http=127.0.0.1:10809;https=127.0.0.1:10809;socks=127.0.0.1:10808", host.registry["ProxyServer"])

        proxy.restore()
        // ProxyServer did not exist before, so the value is deleted again.
        assertNull(host.registry["ProxyServer"])
        assertEquals("0x0", host.registry["ProxyEnable"])
    }

    // -------------------------------------------------------------- backup file

    @Test
    fun `the backup file records the platform backend and both states`() {
        val host = FakeHost()
        val dir = dataDir()
        proxy(host, "linux", dir).apply("127.0.0.1", 10809, 10808)

        val root = JsonParser.parseString(File(dir, SystemProxy.BACKUP_FILE).readText()).asJsonObject
        assertEquals(SystemProxy.BACKUP_VERSION, root.get("version").asInt)
        assertEquals("linux", root.get("os").asString)
        assertEquals("gnome", root.get("backend").asString)
        assertEquals("'manual'", root.getAsJsonObject("applied").get("gnome:mode").asString)
        assertEquals("'none'", root.getAsJsonObject("previous").get("gnome:mode").asString)
    }

    @Test
    fun `a backup from another os is discarded, not applied`() {
        val host = FakeHost()
        val dir = dataDir()
        // A linux backup cannot be restored on this instance pretending to be macOS.
        proxy(host, "windows", dir).apply("127.0.0.1", 10809, 10808)
        val result = proxy(host, "darwin", dir).restore()
        assertFalse(result.restored)
        assertTrue(result.message.contains("cannot be restored"), result.message)
        assertFalse(proxy(host, "darwin", dir).hasBackup())
    }
}
