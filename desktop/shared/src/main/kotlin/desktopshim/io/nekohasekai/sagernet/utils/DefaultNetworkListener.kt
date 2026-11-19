package io.nekohasekai.sagernet.utils

import android.net.Network

object DefaultNetworkListener {
    var ssid: String? = null
    private val defaultNetwork = Network()

    suspend fun start(key: Any, listener: (Network?) -> Unit) {
        listener(defaultNetwork)
    }

    suspend fun get(): Network = defaultNetwork

    suspend fun stop(key: Any) {}
}
