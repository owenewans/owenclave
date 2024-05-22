/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.bg

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.IpPrefix
import android.net.Network
import android.net.ProxyInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.StatsEntity
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Subnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libcore.*
import java.io.FileDescriptor
import java.net.InetAddress
import android.net.VpnService as BaseVpnService

class VpnService : BaseVpnService(),
    BaseService.Interface,
    TrafficListener,
    Protector,
    LocalResolver {

    companion object {
        var instance: VpnService? = null

        const val DEFAULT_MTU = 1500
        const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        const val PRIVATE_VLAN4_GATEWAY = "172.19.0.2"
        const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
        const val PRIVATE_VLAN6_GATEWAY = "fdfe:dcba:9876::2"
        const val FAKEDNS_VLAN4_CLIENT = "198.18.0.0"
        const val FAKEDNS_VLAN6_CLIENT = "fc00::"

        private fun <T> FileDescriptor.use(block: (FileDescriptor) -> T) = try {
            block(this)
        } finally {
            try {
                Os.close(this)
            } catch (_: ErrnoException) {
            }
        }
    }

    lateinit var conn: ParcelFileDescriptor
    var tun: Tun2ray? = null

    private var active = false
    private var metered = false

    @Volatile
    override var underlyingNetwork: Network? = null
        set(value) {
            field = value
            if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(underlyingNetworks)
            }
        }
    private val underlyingNetworks
        get() = // clearing underlyingNetworks makes Android 9 consider the network to be metered
            if (Build.VERSION.SDK_INT == 28 && metered) null else underlyingNetwork?.let {
                arrayOf(it)
            }

    override suspend fun startProcesses() {
        super.startProcesses()
        startVpn()
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun killProcesses() {
        tun?.apply {
            close()
        }
        if (::conn.isInitialized) conn.close()
        super.killProcesses()
        persistAppStats()
        active = false
        tun?.apply {
            tun = null
        }
        GlobalScope.launch(Dispatchers.Default) { DefaultNetworkListener.stop(this) }
    }

    override fun onBind(intent: Intent) = when (intent.action) {
        SERVICE_INTERFACE -> super<BaseVpnService>.onBind(intent)
        else -> super<BaseService.Interface>.onBind(intent)
    }

    override val data = BaseService.Data(this)
    override val tag = "SagerNetVpnService"
    override fun createNotification(profileName: String) =
        ServiceNotification(this, profileName, "service-vpn")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DataStore.serviceMode == Key.MODE_VPN) {
            if (prepare(this) != null) {
                startActivity(
                    Intent(
                        this, VpnRequestActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else return super<BaseService.Interface>.onStartCommand(intent, flags, startId)
        }
        stopRunner()
        return Service.START_NOT_STICKY
    }

    override suspend fun preInit() {
        DefaultNetworkListener.start(this) {
            underlyingNetwork = it
            SagerNet.reloadNetwork(it)
        }
    }

    inner class NullConnectionException : NullPointerException(),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() = getString(R.string.reboot_required)
    }

    private fun startVpn() {
        instance = this

        val builder = Builder().setConfigureIntent(SagerNet.configureIntent(this))
            .setSession(getString(R.string.app_name))
            .setMtu(DataStore.mtu)

        val useFakeDns = DataStore.enableFakeDns
        val ipv6Mode = DataStore.ipv6Mode

        builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)
        if (useFakeDns) {
            builder.addAddress(FAKEDNS_VLAN4_CLIENT, 15)
        }

        if (ipv6Mode != IPv6Mode.DISABLE) {
            builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)

            if (useFakeDns) {
                builder.addAddress(FAKEDNS_VLAN6_CLIENT, 18)
            }
        }

        if (DataStore.bypassLan && !DataStore.bypassLanInCoreOnly) {
            resources.getStringArray(R.array.bypass_private_route).forEach {
                val subnet = Subnet.fromString(it)!!
                builder.addRoute(subnet.address.hostAddress!!, subnet.prefixSize)
            }
            builder.addRoute(PRIVATE_VLAN4_GATEWAY, 32)
            // https://issuetracker.google.com/issues/149636790
            if (ipv6Mode != IPv6Mode.DISABLE) {
                builder.addRoute("2000::", 3)
                builder.addRoute(PRIVATE_VLAN6_GATEWAY, 128)
            }
        } else {
            if (DataStore.allowAccess) {
                // it seems that v2ray can't handle broadcast address properly
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    builder.addRoute("0.0.0.0", 0)
                    builder.excludeRoute(IpPrefix(InetAddress.getByName("255.255.255.255"), 32))
                } else {
                    builder.addRoute("0.0.0.0", 1)
                    builder.addRoute("128.0.0.0", 2)
                    builder.addRoute("192.0.0.0", 3)
                    builder.addRoute("224.0.0.0", 4)
                    builder.addRoute("240.0.0.0", 5)
                    builder.addRoute("248.0.0.0", 6)
                    builder.addRoute("252.0.0.0", 7)
                    builder.addRoute("254.0.0.0", 8)
                    builder.addRoute("255.0.0.0", 9)
                    builder.addRoute("255.128.0.0", 10)
                    builder.addRoute("255.192.0.0", 11)
                    builder.addRoute("255.224.0.0", 12)
                    builder.addRoute("255.240.0.0", 13)
                    builder.addRoute("255.248.0.0", 14)
                    builder.addRoute("255.252.0.0", 15)
                    builder.addRoute("255.254.0.0", 16)
                    builder.addRoute("255.255.0.0", 17)
                    builder.addRoute("255.255.128.0", 18)
                    builder.addRoute("255.255.192.0", 19)
                    builder.addRoute("255.255.224.0", 20)
                    builder.addRoute("255.255.240.0", 21)
                    builder.addRoute("255.255.248.0", 22)
                    builder.addRoute("255.255.252.0", 23)
                    builder.addRoute("255.255.254.0", 24)
                    builder.addRoute("255.255.255.0", 25)
                    builder.addRoute("255.255.255.128", 26)
                    builder.addRoute("255.255.255.192", 27)
                    builder.addRoute("255.255.255.224", 28)
                    builder.addRoute("255.255.255.240", 29)
                    builder.addRoute("255.255.255.248", 30)
                    builder.addRoute("255.255.255.252", 31)
                    builder.addRoute("255.255.255.254", 32)
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }
            if (ipv6Mode != IPv6Mode.DISABLE) {
                builder.addRoute("::", 0)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder.setUnderlyingNetworks(underlyingNetworks)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(metered)

        val packageName = packageName
        val proxyApps = DataStore.proxyApps
        val tunImplementation = DataStore.tunImplementation
        val needIncludeSelf = tunImplementation == TunImplementation.SYSTEM /*data.proxy!!.config.index.any { !it.isBalancer && it.chain.size > 1 }*/
        val needBypassRootUid = data.proxy!!.config.outboundTagsAll.values.any {
            it.ptBean != null || it.hysteriaBean?.protocol == HysteriaBean.PROTOCOL_FAKETCP
        }
        if (proxyApps || needBypassRootUid) {
            var bypass = DataStore.bypass
            val individual = mutableSetOf<String>()
            val allApps by lazy {
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter {
                    when (it.packageName) {
                        packageName -> false
                        "android" -> true
                        else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    }
                }.map {
                    it.packageName
                }
            }
            if (proxyApps) {
                individual.addAll(DataStore.individual.split('\n').filter { it.isNotBlank() })
                if (bypass && needBypassRootUid) {
                    val individualNew = allApps.toMutableList()
                    individualNew.removeAll(individual)
                    individual.clear()
                    individual.addAll(individualNew)
                    bypass = false
                }
            } else {
                individual.addAll(allApps)
                bypass = false
            }

            individual.apply {
                if (bypass xor needIncludeSelf) add(packageName) else remove(packageName)
            }.forEach {
                try {
                    if (bypass) {
                        builder.addDisallowedApplication(it)
                        Logs.d("Add bypass: $it")
                    } else {
                        builder.addAllowedApplication(it)
                        Logs.d("Add allow: $it")
                    }
                } catch (ex: PackageManager.NameNotFoundException) {
                    Logs.w(ex)
                }
            }
        } else if (!needIncludeSelf) {
            builder.addDisallowedApplication(packageName)
        }

        builder.addDnsServer(PRIVATE_VLAN4_GATEWAY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DataStore.appendHttpProxy && DataStore.requireHttp) {
            builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOCALHOST, DataStore.httpPort))
        }

        metered = DataStore.meteredNetwork
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(metered)

        conn = builder.establish() ?: throw NullConnectionException()
        active = true   // possible race condition here?

        val config = TunConfig().apply {
            fileDescriptor = conn.fd
            protect = needIncludeSelf
            mtu = DataStore.mtu
            v2Ray = data.proxy!!.v2rayPoint
            gateway4 = PRIVATE_VLAN4_GATEWAY
            gateway6 = PRIVATE_VLAN6_GATEWAY
            iPv6Mode = ipv6Mode
            implementation = tunImplementation
            sniffing = DataStore.trafficSniffing
            overrideDestination = DataStore.destinationOverride
            fakeDNS = DataStore.enableFakeDns
            hijackDNS = DataStore.hijackDns
            debug = DataStore.enableLog
            dumpUID = data.proxy!!.config.dumpUid
            trafficStats = DataStore.appTrafficStatistics
            pCap = DataStore.enablePcap
            errorHandler = ErrorHandler {
                stopRunner(false, it)
            }
            protector = this@VpnService
            localResolver = this@VpnService
        }

        tun = Libcore.newTun2ray(config)
    }

    val appStats = mutableListOf<AppStats>()

    override fun updateStats(stats: AppStats) {
        appStats.add(stats)
    }

    fun persistAppStats() {
        if (!DataStore.appTrafficStatistics) return
        val tun = tun ?: return
        appStats.clear()
        tun.readAppTraffics(this)
        val toUpdate = mutableListOf<StatsEntity>()
        val all = SagerDatabase.statsDao.all().associateBy { it.packageName }
        for (stats in appStats) {
            val packageName = if (stats.uid >= 10000) {
                PackageCache.uidMap[stats.uid]?.iterator()?.next() ?: "android"
            } else {
                "android"
            }
            if (!all.containsKey(packageName)) {
                SagerDatabase.statsDao.create(
                    StatsEntity(
                        packageName = packageName,
                        tcpConnections = stats.tcpConnTotal,
                        udpConnections = stats.udpConnTotal,
                        uplink = stats.uplinkTotal,
                        downlink = stats.downlinkTotal
                    )
                )
            } else {
                val entity = all[packageName]!!
                entity.tcpConnections += stats.tcpConnTotal
                entity.udpConnections += stats.udpConnTotal
                entity.uplink += stats.uplinkTotal
                entity.downlink += stats.downlinkTotal
                toUpdate.add(entity)
            }
        }
        if (toUpdate.isNotEmpty()) {
            SagerDatabase.statsDao.update(toUpdate)
        }
    }

    override fun onRevoke() = stopRunner()

    override fun onDestroy() {
        super.onDestroy()
        data.binder.close()
    }


}