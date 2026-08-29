package io.nekohasekai.sagernet.ui.compose

import androidx.compose.runtime.mutableStateOf

/**
 * A request, captured from an `owenclave://add-subscription` deep link, to
 * open the "Add subscription from URL" dialog on the Groups screen with the
 * URL (and optionally the "Send HWID" toggle) pre-filled.
 *
 * [id] exists only to make every instance distinct so a repeat tap on the
 * same link (same [url]/[sendHwid]) still re-triggers the dialog even if the
 * person had dismissed it.
 */
data class PendingSubscriptionAdd(
    val url: String,
    val sendHwid: Boolean?,
    val id: Long = System.nanoTime(),
)

/**
 * [ComposeMainActivity.handleViewIntent] runs as a plain Activity callback,
 * outside of the composition, so it can't reach into [MainScreen]'s local
 * `currentDestination` state or [io.nekohasekai.sagernet.ui.compose.screens.GroupScreen]'s
 * dialog state directly. It drops the request here instead:
 * - [MainScreen] observes [pendingDestination] and switches tabs when it's set.
 * - [io.nekohasekai.sagernet.ui.compose.screens.GroupScreen] observes
 *   [pendingSubscriptionAdd], pre-fills its dialog, and clears both so the
 *   dialog only opens once per link tap.
 */
object PendingDeepLink {
    val pendingDestination = mutableStateOf<NavDestination?>(null)
    val pendingSubscriptionAdd = mutableStateOf<PendingSubscriptionAdd?>(null)
}
