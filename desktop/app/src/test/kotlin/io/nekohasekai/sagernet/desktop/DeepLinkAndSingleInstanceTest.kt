package io.nekohasekai.sagernet.desktop

import android.content.Intent
import android.net.Uri
import androidx.activity.DesktopActivityStack
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ui.compose.ComposeMainActivity
import io.nekohasekai.sagernet.ui.compose.NavDestination
import io.nekohasekai.sagernet.ui.compose.PendingDeepLink
import io.nekohasekai.sagernet.ui.compose.parseSubscriptionDeepLink
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinkAndSingleInstanceTest {

    @Test
    fun `extractDeepLinks recognizes owenclave, owenkey, happ and all proxy schemes while ignoring flags`() {
        val args = arrayOf(
            "--legacy-ui",
            "owenclave://add-subscription?url=https%3A%2F%2Fsub.example.com%2F1&hwid=1",
            "owenkey://eyJ0ZXN0IjoxfQ==",
            "happ://crypt5/abcdef",
            "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls#Test",
            "naive+https://user:pass@example.com:443#Naive",
            "--unknown-flag",
            "/some/local/file.json",
            "https://plain-web-page.example.com",
        )
        val extracted = DeepLinks.extractDeepLinks(args)
        assertEquals(
            listOf(
                "owenclave://add-subscription?url=https%3A%2F%2Fsub.example.com%2F1&hwid=1",
                "owenkey://eyJ0ZXN0IjoxfQ==",
                "happ://crypt5/abcdef",
                "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls#Test",
                "naive+https://user:pass@example.com:443#Naive",
            ),
            extracted,
        )
    }

    @Test
    fun `parseSubscriptionDeepLink supports query and path variants across owenclave, clash, sn, exclave and happ`() {
        val standard = parseSubscriptionDeepLink(
            Uri.parse("owenclave://add-subscription?url=https%3A%2F%2Fpanel.example.com%2Fsub%3Ftoken%3Dxyz&hwid=1"),
        )
        assertNotNull(standard)
        assertEquals("https://panel.example.com/sub?token=xyz", standard.url)
        assertEquals(true, standard.sendHwid)

        val hwidOff = parseSubscriptionDeepLink(
            Uri.parse("owenclave://add-subscription?url=https%3A%2F%2Fpanel.example.com%2Fsub&hwid=0"),
        )
        assertNotNull(hwidOff)
        assertEquals("https://panel.example.com/sub", hwidOff.url)
        assertEquals(false, hwidOff.sendHwid)

        val importPath = parseSubscriptionDeepLink(
            Uri.parse("owenclave://import/https://panel.example.com/api/v1/sub?token=abc"),
        )
        assertNotNull(importPath)
        assertEquals("https://panel.example.com/api/v1/sub?token=abc", importPath.url)
        assertNull(importPath.sendHwid)

        val rawUrlAfterScheme = parseSubscriptionDeepLink(
            Uri.parse("owenclave://https://panel.example.com/raw-sub"),
        )
        assertNotNull(rawUrlAfterScheme)
        assertEquals("https://panel.example.com/raw-sub", rawUrlAfterScheme.url)

        val clashConfig = parseSubscriptionDeepLink(
            Uri.parse("clash://install-config?url=https%3A%2F%2Fpanel.example.com%2Fclash.yaml"),
        )
        assertNotNull(clashConfig)
        assertEquals("https://panel.example.com/clash.yaml", clashConfig.url)

        val snSub = parseSubscriptionDeepLink(
            Uri.parse("sn://subscription?url=https%3A%2F%2Fpanel.example.com%2Fsagernet"),
        )
        assertNotNull(snSub)
        assertEquals("https://panel.example.com/sagernet", snSub.url)

        val happAdd = parseSubscriptionDeepLink(
            Uri.parse("happ://add/https://panel.example.com/happ-sub"),
        )
        assertNotNull(happAdd)
        assertEquals("https://panel.example.com/happ-sub", happAdd.url)

        // Share links must not be mistaken for subscription URLs
        assertNull(parseSubscriptionDeepLink(Uri.parse("vless://uuid@example.com:443#Node")))
        assertNull(parseSubscriptionDeepLink(Uri.parse("owenkey://payload")))
    }

    @Test
    fun `SingleInstanceManager forwards deep links from secondary invocation to running primary instance`() {
        val tempDir = Files.createTempDirectory("owenclave-ipc-test").toFile()
        try {
            val receivedBatches = CopyOnWriteArrayList<List<String>>()
            val latch = CountDownLatch(2)

            val first = SingleInstanceManager.acquireOrForward(
                dataDir = tempDir,
                uris = emptyList(),
                onActivate = { uris ->
                    receivedBatches.add(uris)
                    latch.countDown()
                },
            )
            assertTrue(first is SingleInstanceManager.AcquireResult.Primary)

            first.handle.use {
                val targetUri = "owenclave://add-subscription?url=https%3A%2F%2Fsub.example.com%2Ftest&hwid=1"
                val second = SingleInstanceManager.acquireOrForward(
                    dataDir = tempDir,
                    uris = listOf(targetUri),
                    onActivate = {},
                )
                assertEquals(SingleInstanceManager.AcquireResult.ForwardedToPrimary, second)

                // Empty activation (e.g. clicking launcher icon while already open) also wakes primary
                val third = SingleInstanceManager.acquireOrForward(
                    dataDir = tempDir,
                    uris = emptyList(),
                    onActivate = {},
                )
                assertEquals(SingleInstanceManager.AcquireResult.ForwardedToPrimary, third)

                assertTrue(latch.await(5, TimeUnit.SECONDS))
                assertEquals(listOf(targetUri), receivedBatches[0])
                assertEquals(emptyList(), receivedBatches[1])
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `ComposeMainActivity routes owenclave deep link to GROUP destination and PendingDeepLink`() {
        SagerNet.application
        PendingDeepLink.pendingSubscriptionAdd.value = null
        PendingDeepLink.pendingDestination.value = null
        DesktopActivityStack.stack.clear()

        val activity = ComposeMainActivity()
        activity.onNewIntent(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("owenclave://add-subscription?url=https%3A%2F%2Fmy.provider.test%2Fsub&hwid=true"),
            ),
        )

        assertEquals(NavDestination.GROUP, PendingDeepLink.pendingDestination.value)
        val pending = PendingDeepLink.pendingSubscriptionAdd.value
        assertNotNull(pending)
        assertEquals("https://my.provider.test/sub", pending.url)
        assertEquals(true, pending.sendHwid)

        PendingDeepLink.pendingSubscriptionAdd.value = null
        PendingDeepLink.pendingDestination.value = null
    }

    @Test
    fun `buildLinuxDesktopEntry quotes paths with spaces and registers owenclave scheme`() {
        val entry = UrlSchemeRegistrar.buildLinuxDesktopEntry("/opt/My Apps/Owenclave/bin/Owenclave")
        assertTrue(entry.contains("Exec=\"/opt/My Apps/Owenclave/bin/Owenclave\" %u"))
        assertTrue(entry.contains("x-scheme-handler/owenclave;"))
        assertTrue(entry.contains("x-scheme-handler/owenkey;"))
        assertFalse(entry.contains("Terminal=true"))
    }
}

