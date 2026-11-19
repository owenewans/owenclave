package io.nekohasekai.sagernet.database

import android.os.Parcel
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.aidl.AppStatsList
import io.nekohasekai.sagernet.aidl.TrafficStats

object ParcelizeBridge {
    fun createRule(parcel: Parcel): RuleEntity = RuleEntity(
        id = parcel.readLong(),
        name = parcel.readString() ?: "",
        userOrder = parcel.readLong(),
        enabled = parcel.readInt() != 0,
        domains = parcel.readString() ?: "",
        ip = parcel.readString() ?: "",
        port = parcel.readString() ?: "",
        sourcePort = parcel.readString() ?: "",
        network = parcel.readString() ?: "",
        source = parcel.readString() ?: "",
        protocol = parcel.readString() ?: "",
        attrs = parcel.readString() ?: "",
        outbound = parcel.readLong(),
        packages = parcel.createStringArrayList() ?: emptyList(),
        ssid = parcel.readString() ?: "",
        networkType = (parcel.createStringArrayList() ?: emptyList()).toSet(),
        customPackageNames = parcel.createStringArrayList() ?: emptyList(),
    )

    fun writeRule(rule: RuleEntity, parcel: Parcel) {
        parcel.writeLong(rule.id)
        parcel.writeString(rule.name)
        parcel.writeLong(rule.userOrder)
        parcel.writeInt(if (rule.enabled) 1 else 0)
        parcel.writeString(rule.domains)
        parcel.writeString(rule.ip)
        parcel.writeString(rule.port)
        parcel.writeString(rule.sourcePort)
        parcel.writeString(rule.network)
        parcel.writeString(rule.source)
        parcel.writeString(rule.protocol)
        parcel.writeString(rule.attrs)
        parcel.writeLong(rule.outbound)
        parcel.writeStringList(rule.packages)
        parcel.writeString(rule.ssid)
        parcel.writeStringList(rule.networkType.toList())
        parcel.writeStringList(rule.customPackageNames)
    }

    fun createAsset(parcel: Parcel): AssetEntity = AssetEntity(
        id = parcel.readLong(),
        url = parcel.readString() ?: "",
        name = parcel.readString() ?: ""
    )

    fun writeAsset(asset: AssetEntity, parcel: Parcel) {
        parcel.writeLong(asset.id)
        parcel.writeString(asset.url)
        parcel.writeString(asset.name)
    }

    fun createStats(parcel: Parcel): StatsEntity = StatsEntity(
        id = parcel.readInt(),
        uid = parcel.readInt(),
        tcpConnections = parcel.readInt(),
        udpConnections = parcel.readInt(),
        uplink = parcel.readLong(),
        downlink = parcel.readLong()
    )

    fun writeStats(stats: StatsEntity, parcel: Parcel) {
        parcel.writeInt(stats.id)
        parcel.writeInt(stats.uid)
        parcel.writeInt(stats.tcpConnections)
        parcel.writeInt(stats.udpConnections)
        parcel.writeLong(stats.uplink)
        parcel.writeLong(stats.downlink)
    }

    fun createAppStats(parcel: Parcel): AppStats = AppStats(
        uid = parcel.readInt(),
        tcpConnections = parcel.readInt(),
        udpConnections = parcel.readInt(),
        tcpConnectionsTotal = parcel.readInt(),
        udpConnectionsTotal = parcel.readInt(),
        uplink = parcel.readLong(),
        downlink = parcel.readLong(),
        uplinkTotal = parcel.readLong(),
        downlinkTotal = parcel.readLong(),
        deactivateAt = parcel.readInt()
    )

    fun writeAppStats(stats: AppStats, parcel: Parcel) {
        parcel.writeInt(stats.uid)
        parcel.writeInt(stats.tcpConnections)
        parcel.writeInt(stats.udpConnections)
        parcel.writeInt(stats.tcpConnectionsTotal)
        parcel.writeInt(stats.udpConnectionsTotal)
        parcel.writeLong(stats.uplink)
        parcel.writeLong(stats.downlink)
        parcel.writeLong(stats.uplinkTotal)
        parcel.writeLong(stats.downlinkTotal)
        parcel.writeInt(stats.deactivateAt)
    }

    fun createTrafficStats(parcel: Parcel): TrafficStats = TrafficStats(
        txRateProxy = parcel.readLong(),
        rxRateProxy = parcel.readLong(),
        txRateDirect = parcel.readLong(),
        rxRateDirect = parcel.readLong(),
        txTotal = parcel.readLong(),
        rxTotal = parcel.readLong()
    )

    fun writeTrafficStats(stats: TrafficStats, parcel: Parcel) {
        parcel.writeLong(stats.txRateProxy)
        parcel.writeLong(stats.rxRateProxy)
        parcel.writeLong(stats.txRateDirect)
        parcel.writeLong(stats.rxRateDirect)
        parcel.writeLong(stats.txTotal)
        parcel.writeLong(stats.rxTotal)
    }

    fun createAppStatsList(parcel: Parcel): AppStatsList {
        val size = parcel.readInt()
        val list = ArrayList<AppStats>(if (size > 0) size else 0)
        repeat(size.coerceAtLeast(0)) {
            list.add(createAppStats(parcel))
        }
        return AppStatsList(list)
    }

    fun writeAppStatsList(list: AppStatsList, parcel: Parcel) {
        parcel.writeInt(list.data.size)
        for (item in list.data) {
            writeAppStats(item, parcel)
        }
    }
}
