package io.nekohasekai.sagernet.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceNotification(
    private val service: BaseService.Interface,
    private val profileName: String,
    channel: String,
    visible: Boolean = false,
) : BroadcastReceiver() {
    fun updateActions() {}
    fun setState(state: BaseService.State) {}
    override fun onReceive(context: Context, intent: Intent) {}
    fun destroy() {}
}

