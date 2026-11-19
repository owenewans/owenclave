package io.nekohasekai.sagernet.desktop

import androidx.activity.DesktopActivityStack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.awt.ComposeWindow
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ui.compose.ComposeMainActivity
import io.nekohasekai.sagernet.ui.compose.NavDestination
import io.nekohasekai.sagernet.ui.compose.OwenclaveTheme
import io.nekohasekai.sagernet.ui.compose.PendingDeepLink
import io.nekohasekai.sagernet.ui.compose.screens.ProfileFieldState
import io.nekohasekai.sagernet.ui.compose.screens.UniversalProfileSettingsScreen
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class DesktopComposeUiSmokeTest {

    private fun teardownStack() {
        while (DesktopActivityStack.stack.isNotEmpty()) {
            val activity = DesktopActivityStack.stack.removeLast()
            runCatching { activity.onPause() }
            runCatching { activity.onStop() }
            runCatching { activity.onDestroy() }
        }
    }

    @Test
    fun `renders ComposeMainActivity across all navigation destinations and UniversalProfileSettingsScreen`() {
        SagerNet.application
        DataStore.appAutoUpdate = false

        runBlocking(Dispatchers.Main) {
            teardownStack()

            val mainScene = ImageComposeScene(width = 1180, height = 780, coroutineContext = Dispatchers.Main)
            try {
                val mainActivity = ComposeMainActivity()
                mainScene.setContent {
                    DesktopActivityStack.Host(mainActivity)
                }
                mainScene.render(0L).use { initialImg ->
                    assertTrue(initialImg.width == 1180 && initialImg.height == 780)
                }
                yield()

                var frameNano = 250_000_000L
                for (dest in NavDestination.entries) {
                    PendingDeepLink.pendingDestination.value = dest
                    repeat(3) {
                        mainScene.render(frameNano).close()
                        frameNano += 250_000_000L
                        yield()
                    }
                }

                // Exercise deep link routing to GroupScreen + quick-add dialog + Toast overlay
                val subIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("owenclave://add-subscription?url=https%3A%2F%2Fexample.com%2Fapi%2Fsub&hwid=1"),
                )
                mainActivity.onNewIntent(subIntent)
                android.widget.Toast.makeText(mainActivity, "Deep link received", android.widget.Toast.LENGTH_SHORT).show()
                repeat(4) {
                    mainScene.render(frameNano).close()
                    frameNano += 250_000_000L
                    yield()
                }
                PendingDeepLink.pendingSubscriptionAdd.value = null
                PendingDeepLink.pendingDestination.value = null
            } finally {
                teardownStack()
                yield()
                mainScene.close()
            }

            // Also verify compact phone width (3-tab pill mode) renders cleanly
            val compactScene = ImageComposeScene(width = 412, height = 892, coroutineContext = Dispatchers.Main)
            try {
                val mainActivity = ComposeMainActivity()
                compactScene.setContent {
                    DesktopActivityStack.Host(mainActivity)
                }
                compactScene.render(0L).use { compactImg ->
                    assertTrue(compactImg.width == 412 && compactImg.height == 892)
                }
                yield()
            } finally {
                teardownStack()
                yield()
                compactScene.close()
            }

            val allTypes = listOf(
                ProxyEntity.TYPE_SOCKS to ProfileFieldState(),
                ProxyEntity.TYPE_HTTP to ProfileFieldState(),
                ProxyEntity.TYPE_HTTP to ProfileFieldState(security = "tls"),
                ProxyEntity.TYPE_SS to ProfileFieldState(),
                ProxyEntity.TYPE_SSR to ProfileFieldState(),
                ProxyEntity.TYPE_VMESS to ProfileFieldState(),
                ProxyEntity.TYPE_VLESS to ProfileFieldState(security = "reality"),
                ProxyEntity.TYPE_TROJAN to ProfileFieldState(security = "tls"),
                ProxyEntity.TYPE_NAIVE to ProfileFieldState(),
                ProxyEntity.TYPE_HYSTERIA2 to ProfileFieldState(),
                ProxyEntity.TYPE_SSH to ProfileFieldState(),
                ProxyEntity.TYPE_WG to ProfileFieldState(),
                ProxyEntity.TYPE_MIERU to ProfileFieldState(),
                ProxyEntity.TYPE_TUIC5 to ProfileFieldState(),
                ProxyEntity.TYPE_JUICITY to ProfileFieldState(),
                ProxyEntity.TYPE_HTTP3 to ProfileFieldState(),
                ProxyEntity.TYPE_ANYTLS to ProfileFieldState(),
                ProxyEntity.TYPE_SHADOWQUIC to ProfileFieldState(),
                ProxyEntity.TYPE_TRUSTTUNNEL to ProfileFieldState(),
                ProxyEntity.TYPE_SNELL to ProfileFieldState(),
                ProxyEntity.TYPE_MASQUE to ProfileFieldState(),
                ProxyEntity.TYPE_CHAIN to ProfileFieldState(),
                ProxyEntity.TYPE_CONFIG to ProfileFieldState(),
                ProxyEntity.TYPE_BALANCER to ProfileFieldState(),
            )

            val editorScene = ImageComposeScene(width = 1000, height = 1600, coroutineContext = Dispatchers.Main)
            try {
                var activeIndex by mutableIntStateOf(0)
                editorScene.setContent {
                    val (type, initial) = allTypes[activeIndex]
                    OwenclaveTheme(themeId = 0, nightTheme = 1) {
                        key(activeIndex) {
                            UniversalProfileSettingsScreen(
                                profileType = type,
                                initialState = initial,
                                onBack = {},
                                onSave = {},
                            )
                        }
                    }
                }
                var editorFrameNano = 0L
                for (index in allTypes.indices) {
                    activeIndex = index
                    editorScene.render(editorFrameNano).use { img ->
                        assertTrue(img.width == 1000)
                    }
                    editorFrameNano += 250_000_000L
                    yield()
                }
            } finally {
                yield()
                editorScene.close()
            }
        }

        val canOpenNativeWindow = !GraphicsEnvironment.isHeadless() &&
            System.getenv("CI").isNullOrBlank() &&
            System.getProperty("owenclave.ci").isNullOrBlank()
        if (canOpenNativeWindow) {
            var windowError: Throwable? = null
            SwingUtilities.invokeAndWait {
                var window: ComposeWindow? = null
                try {
                    teardownStack()
                    window = ComposeWindow()
                    window.setSize(1180, 780)
                    val mainActivity = ComposeMainActivity()
                    window.setContent {
                        DesktopActivityStack.Host(mainActivity)
                    }
                } catch (t: Throwable) {
                    windowError = t
                } finally {
                    teardownStack()
                    runCatching { window?.dispose() }
                }
            }
            windowError?.let { throw it }
        }
    }
}
