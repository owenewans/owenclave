package io.nekohasekai.sagernet.desktop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxWindowSupportTest {

    @Test
    fun `detects niri xwayland-satellite and tiling WMs without false positives on GNOME or KDE`() {
        assertTrue(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = emptyMap(),
                netWmName = "xwayland-satellite",
            ),
        )
        assertTrue(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = mapOf("XDG_CURRENT_DESKTOP" to "niri"),
                netWmName = "",
            ),
        )
        assertTrue(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = mapOf("XDG_CURRENT_DESKTOP" to "Hyprland"),
                netWmName = "wlroots wm",
            ),
        )
        assertTrue(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = mapOf("DESKTOP_SESSION" to "none+i3"),
                netWmName = "i3",
            ),
        )
        assertFalse(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = mapOf("XDG_CURRENT_DESKTOP" to "GNOME"),
                netWmName = "GNOME Shell",
            ),
        )
        assertFalse(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = mapOf("XDG_CURRENT_DESKTOP" to "KDE"),
                netWmName = "KWin",
            ),
        )
        assertFalse(
            LinuxWindowSupport.isNonReparentingEnvironment(
                env = mapOf(
                    "XDG_CURRENT_DESKTOP" to "niri",
                    "_JAVA_AWT_WM_NONREPARENTING" to "0",
                ),
                netWmName = "xwayland-satellite",
            ),
        )
    }

    @Test
    fun `parses fractional Xft dpi and avoids double-scaling when AWT transform is already scaled`() {
        val dpi = LinuxWindowSupport.parseXftDpi("Xft.dpi:\t154\n")
        assertEquals(154f, dpi)

        val scale = LinuxWindowSupport.computeScaleOverride(
            awtScale = 1.0f,
            explicitScale = null,
            xftDpi = dpi,
            gnomeXftDpiScaled = null,
            env = emptyMap(),
        )
        assertNotNull(scale)
        assertTrue(abs(scale - (154f / 96f)) < 0.001f)

        // When AWT already applied 2x HiDPI transform, do not apply a second multiplier.
        assertNull(
            LinuxWindowSupport.computeScaleOverride(
                awtScale = 2.0f,
                explicitScale = null,
                xftDpi = 192f,
                gnomeXftDpiScaled = 192 * 1024,
                env = emptyMap(),
            ),
        )

        // Standard 96 DPI 1x display leaves density untouched.
        assertNull(
            LinuxWindowSupport.computeScaleOverride(
                awtScale = 1.0f,
                explicitScale = null,
                xftDpi = 96f,
                gnomeXftDpiScaled = null,
                env = emptyMap(),
            ),
        )

        // Explicit override always wins.
        assertEquals(
            1.5f,
            LinuxWindowSupport.computeScaleOverride(
                awtScale = 2.0f,
                explicitScale = 1.5f,
                xftDpi = 154f,
                gnomeXftDpiScaled = null,
                env = emptyMap(),
            ),
        )
    }
}

