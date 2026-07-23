package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks how many expressive overlays (dialogs/popups) are currently open so the
 * root content can blur itself behind them. Real background blur only works when
 * the blurred element and the overlay live in the SAME window — so overlays use
 * [androidx.compose.ui.window.Popup] (same window) instead of Dialog, and the
 * root content reads [blurRadiusDp] to blur itself.
 */
class ScrimController {
    var openCount by mutableIntStateOf(0)
        private set

    val isActive: Boolean get() = openCount > 0

    /** Blur radius applied to the root content while overlays are open. */
    val blurRadiusDp: Float get() = if (openCount > 0) 18f else 0f

    fun push() {
        openCount++
    }

    fun pop() {
        if (openCount > 0) openCount--
    }
}

val LocalScrimController = compositionLocalOf { ScrimController() }
