package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.database.DataStore
import libexclavecore.Tun2ray

class VpnService : ProxyService() {
    var tun: Tun2ray? = null

    fun protect(fd: Int): Boolean = true

    fun persistAppStats() {}

    companion object {
        var instance: VpnService? = null

        const val DEFAULT_MTU = 1500
        val PRIVATE_VLAN4_CLIENT =
            DataStore.experimentalFlagsProperties.getProperty("tunIPv4Address")?.substringBefore("/") ?: "172.19.0.1"
        val PRIVATE_VLAN4_CLIENT_PREFIX =
            DataStore.experimentalFlagsProperties.getProperty("tunIPv4Address")?.substringAfter("/")?.toInt() ?: 30
        const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        val PRIVATE_VLAN4_DNS =
            DataStore.experimentalFlagsProperties.getProperty("tunIPv4DNSAddress") ?: "172.19.0.2"
        val PRIVATE_VLAN6_CLIENT =
            DataStore.experimentalFlagsProperties.getProperty("tunIPv6Address")?.substringBefore("/") ?: "fdfe:dcba:9876::1"
        val PRIVATE_VLAN6_CLIENT_PREFIX =
            DataStore.experimentalFlagsProperties.getProperty("tunIPv6Address")?.substringAfter("/")?.toInt() ?: 126
        const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
        val PRIVATE_VLAN6_DNS =
            DataStore.experimentalFlagsProperties.getProperty("tunIPv6DNSAddress")
        val FAKEDNS_VLAN4_CLIENT =
            DataStore.experimentalFlagsProperties.getProperty("fakeDNSIPv4Pool")?.substringBefore("/") ?: "198.18.0.0"
        val FAKEDNS_VLAN4_CLIENT_PREFIX =
            DataStore.experimentalFlagsProperties.getProperty("fakeDNSIPv4Pool")?.substringAfter("/")?.toInt() ?: 15
        val FAKEDNS_VLAN4_CLIENT_POOL_SIZE =
            DataStore.experimentalFlagsProperties.getProperty("fakeDNSIPv4PoolSize")?.toInt() ?: 65535
        val FAKEDNS_VLAN6_CLIENT =
            DataStore.experimentalFlagsProperties.getProperty("fakeDNSIPv6Pool")?.substringBefore("/") ?: "fc00::"
        val FAKEDNS_VLAN6_CLIENT_PREFIX =
            DataStore.experimentalFlagsProperties.getProperty("fakeDNSIPv6Pool")?.substringAfter("/")?.toInt() ?: 18
        val FAKEDNS_VLAN6_CLIENT_POOL_SIZE =
            DataStore.experimentalFlagsProperties.getProperty("fakeDNSIPv6PoolSize")?.toInt() ?: 65535
    }
}
