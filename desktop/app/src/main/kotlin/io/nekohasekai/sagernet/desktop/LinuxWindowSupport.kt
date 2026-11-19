package io.nekohasekai.sagernet.desktop

import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * Fixes X11 / XWayland window management, WM_CLASS desktop integration, and fractional
 * HiDPI scaling on Linux tiling and Wayland compositors (Niri / xwayland-satellite,
 * Hyprland, Sway, River, Cosmic, Labwc, i3, bspwm, dwm, awesome, etc.).
 *
 * Background:
 * 1. OpenJDK's `sun.awt.X11.XWM` classifies modern non-reparenting Wayland/XWayland
 *    compositors (such as `xwayland-satellite` on Niri or `wlroots wm` on Sway/Hyprland)
 *    as `OTHER_WM = 3` (reparenting) unless `_JAVA_AWT_WM_NONREPARENTING=1` is set.
 *    `XDecoratedPeer.handleConfigureNotifyEvent` then drops every tiling resize event
 *    ("visible but not reparented, skipping"), leaving the Compose canvas frozen at its
 *    initial dimensions with an unpainted black void filling the rest of the tile and
 *    5px Motif fallback insets (`defaultInsets`) framing the frozen area.
 * 2. OpenJDK 21's `X11GraphicsDevice` ignores fractional `Xft.dpi` (e.g. `154` for `1.6x`
 *    on a 2560x1600 Wayland output) and truncates scale factors to integers, leaving UI
 *    elements unscaled (`1.0x`) on fractional HiDPI Wayland setups.
 * 3. `XToolkit` defaults `WM_CLASS` to `io-nekohasekai-sagernet-desktop-MainKt`, which
 *    does not match `StartupWMClass=Owenclave` in `owenclave.desktop`.
 */
object LinuxWindowSupport {

    private const val APP_WM_CLASS = "Owenclave"
    private const val XWM_OTHER_WM = 3
    private const val XWM_LG3D_WM = 13

    private val NON_REPARENTING_WM_TOKENS = setOf(
        "xwayland-satellite",
        "wlroots",
        "niri",
        "hyprland",
        "sway",
        "river",
        "wayfire",
        "labwc",
        "cosmic",
        "miracle",
        "mangowc",
        "weston",
        "cage",
        "gamescope",
        "dwl",
        "hikari",
        "jay",
        "scroll",
        "lg3d",
        "bspwm",
        "dwm",
        "awesome",
        "xmonad",
        "qtile",
        "leftwm",
        "herbstluftwm",
        "spectrwm",
        "i3",
        "i3wm",
        "ratpoison",
        "stumpwm",
        "worm",
        "berry",
        "echinus",
    )

    private val TOKEN_SPLIT_REGEX = Regex("[^a-z0-9-]+")
    private val XFT_DPI_REGEX = Regex("""(?m)^\s*Xft\.dpi\s*:\s*([0-9]+(?:\.[0-9]+)?)""")

    @Volatile
    private var configured = false

    /**
     * Must be invoked before any `Window` / `JFrame` is created in GUI mode.
     */
    fun configureBeforeWindow() {
        if (configured || !DesktopRuntime.isLinux || GraphicsEnvironment.isHeadless()) return
        configured = true

        runCatching {
            val toolkit = Toolkit.getDefaultToolkit()
            if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return

            // 1. Align X11 WM_CLASS / Wayland app_id with `StartupWMClass=Owenclave`.
            runCatching {
                val appClassField = toolkit.javaClass.getDeclaredField("awtAppClassName")
                appClassField.isAccessible = true
                appClassField.set(null, APP_WM_CLASS)
            }

            // 2. Enable non-reparenting WM handling and zero frame insets when running
            //    under Niri (xwayland-satellite), Hyprland, Sway, wlroots, or tiling WMs.
            val xwmClass = Class.forName("sun.awt.X11.XWM")
            val wmInstance = xwmClass.getDeclaredMethod("getWM").apply { isAccessible = true }.invoke(null)
            val netProtocol = xwmClass.getDeclaredMethod("getNETProtocol").apply { isAccessible = true }.invoke(wmInstance)
            val netWmName = netProtocol?.let { proto ->
                runCatching {
                    proto.javaClass.getDeclaredMethod("getWMName").apply { isAccessible = true }.invoke(proto) as? String
                }.getOrNull()
            }?.trim().orEmpty()

            if (isNonReparentingEnvironment(System.getenv(), netWmName)) {
                xwmClass.getDeclaredField("awtWMNonReparenting").apply {
                    isAccessible = true
                    setInt(null, 1)
                }
                val awtWmgrField = xwmClass.getDeclaredField("awt_wmgr").apply { isAccessible = true }
                if (awtWmgrField.getInt(null) == XWM_OTHER_WM) {
                    awtWmgrField.setInt(null, XWM_LG3D_WM)
                    if (wmInstance != null) {
                        runCatching {
                            xwmClass.getDeclaredField("WMID").apply {
                                isAccessible = true
                                setInt(wmInstance, XWM_LG3D_WM)
                            }
                        }
                        runCatching {
                            val stored = xwmClass.getDeclaredField("storedInsets").apply {
                                isAccessible = true
                            }.get(wmInstance) as? MutableMap<*, *>
                            stored?.clear()
                        }
                    }
                }
                android.util.Log.i(
                    "LinuxWindow",
                    "Configured non-reparenting X11/XWayland mode (wm='${netWmName.ifEmpty { System.getenv("XDG_CURRENT_DESKTOP").orEmpty() }}')",
                )
            }
        }.onFailure { err ->
            android.util.Log.w("LinuxWindow", "Could not apply X11 window hints: ${err.message}")
        }
    }

    /**
     * Installs a dark frame background (preventing white/grey border flashes during
     * compositor tiling resizes) and revalidates the root pane on window open/resize.
     */
    fun attachWindowFixes(window: Window) {
        val darkSurface = java.awt.Color(0x14, 0x12, 0x18)
        runCatching {
            window.background = darkSurface
            if (window is JFrame) {
                window.rootPane?.background = darkSurface
                window.contentPane?.background = darkSurface
            }
        }
        if (!DesktopRuntime.isLinux) return

        val syncLayout = {
            SwingUtilities.invokeLater {
                if (window.isDisplayable) {
                    if (window is JFrame) {
                        window.rootPane?.revalidate()
                    }
                    window.validate()
                    window.repaint()
                }
            }
        }
        window.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent?) = syncLayout()
        })
        window.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = syncLayout()
        })
    }

    /**
     * Computes initial window pixel/dp size on desktop, scaling up when AWT left XWayland
     * at `1.0` physical-pixel transform while Compose renders at fractional `Xft.dpi` density.
     */
    fun initialWindowDpSize(baseWidthDp: Int = 1180, baseHeightDp: Int = 780): Pair<Int, Int> {
        if (!DesktopRuntime.isLinux || GraphicsEnvironment.isHeadless()) {
            return baseWidthDp to baseHeightDp
        }
        val scale = detectLinuxScaleOverride(null) ?: return baseWidthDp to baseHeightDp
        val bounds = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        }.getOrNull()
        val rawW = (baseWidthDp * scale).roundToInt()
        val rawH = (baseHeightDp * scale).roundToInt()
        return if (bounds != null && bounds.width > 0 && bounds.height > 0) {
            val maxW = (bounds.width * 0.88f).roundToInt().coerceAtLeast(480)
            val maxH = (bounds.height * 0.88f).roundToInt().coerceAtLeast(560)
            rawW.coerceAtMost(maxW) to rawH.coerceAtMost(maxH)
        } else {
            rawW to rawH
        }
    }

    fun minimumWindowDimension(): Dimension = Dimension(380, 480)

    /**
     * Returns a fractional UI density override (e.g. `1.604f` for `Xft.dpi: 154`) on Linux
     * when OpenJDK AWT left the GraphicsConfiguration transform at `1.0x`. Returns `null`
     * when AWT already applied HiDPI scaling (`scaleX != 1.0`) or on standard 96 DPI screens.
     */
    fun detectLinuxScaleOverride(component: Component?): Float? {
        if (!DesktopRuntime.isLinux || GraphicsEnvironment.isHeadless()) return null
        val awtScale = runCatching {
            val gc = component?.graphicsConfiguration
                ?: GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice
                    ?.defaultConfiguration
            gc?.defaultTransform?.scaleX?.toFloat() ?: 1f
        }.getOrDefault(1f)

        val explicitProp = System.getProperty("owenclave.uiScale")?.trim()?.toFloatOrNull()
        val xftDpi = readX11ResourceManager()?.let(::parseXftDpi)
        val gnomeDpi = runCatching {
            (Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI") as? Number)?.toInt()
        }.getOrNull()

        return computeScaleOverride(
            awtScale = awtScale,
            explicitScale = explicitProp,
            xftDpi = xftDpi,
            gnomeXftDpiScaled = gnomeDpi,
            env = System.getenv(),
        )
    }

    private fun readX11ResourceManager(): String? {
        return runCatching {
            val toolkit = Toolkit.getDefaultToolkit()
            if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return null
            val xToolkitClass = toolkit.javaClass
            val rootWindow = xToolkitClass.getDeclaredMethod("getDefaultRootWindow")
                .apply { isAccessible = true }
                .invoke(null) as? Long ?: return null
            if (rootWindow == 0L) return null
            val xAtomClass = Class.forName("sun.awt.X11.XAtom")
            val atom = xAtomClass.getDeclaredMethod("get", String::class.java)
                .apply { isAccessible = true }
                .invoke(null, "RESOURCE_MANAGER") ?: return null
            xAtomClass.getDeclaredMethod("getProperty", Long::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(atom, rootWindow) as? String
        }.getOrNull()
    }

    internal fun isNonReparentingEnvironment(
        env: Map<String, String>,
        netWmName: String?,
    ): Boolean {
        when (env["_JAVA_AWT_WM_NONREPARENTING"]?.trim()?.lowercase()) {
            "1", "true", "yes" -> return true
            "0", "false", "no" -> return false
        }
        if (containsNonReparentingToken(netWmName)) return true
        for (key in listOf("XDG_CURRENT_DESKTOP", "XDG_SESSION_DESKTOP", "DESKTOP_SESSION")) {
            if (containsNonReparentingToken(env[key])) return true
        }
        return false
    }

    private fun containsNonReparentingToken(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return raw.lowercase()
            .split(TOKEN_SPLIT_REGEX)
            .any { token -> token.isNotEmpty() && token in NON_REPARENTING_WM_TOKENS }
    }

    internal fun parseXftDpi(resourceManager: String?): Float? {
        if (resourceManager.isNullOrBlank()) return null
        val match = XFT_DPI_REGEX.find(resourceManager) ?: return null
        val dpi = match.groupValues[1].toFloatOrNull() ?: return null
        return dpi.takeIf { it in 48f..576f }
    }

    internal fun computeScaleOverride(
        awtScale: Float,
        explicitScale: Float?,
        xftDpi: Float?,
        gnomeXftDpiScaled: Int?,
        env: Map<String, String>,
    ): Float? {
        if (explicitScale != null && explicitScale in 0.5f..4.0f) {
            return explicitScale
        }
        val envExplicit = env["OWENCLAVE_UI_SCALE"]?.trim()?.toFloatOrNull()
        if (envExplicit != null && envExplicit in 0.5f..4.0f) {
            return envExplicit
        }
        // If AWT already scaled the GraphicsConfiguration (e.g. GDK_SCALE=2 or sun.java2d.uiScale=2),
        // let Compose use AWT's density directly so we never double-scale.
        if (awtScale !in 0.95f..1.05f) {
            return null
        }

        val candidate = xftDpi?.let { it / 96f }
            ?: gnomeXftDpiScaled?.takeIf { it > 0 }?.let { (it / 1024f) / 96f }
            ?: env["QT_SCALE_FACTOR"]?.trim()?.toFloatOrNull()
            ?: env["GDK_DPI_SCALE"]?.trim()?.toFloatOrNull()

        return candidate?.takeIf { it in 1.05f..4.0f }
    }
}

