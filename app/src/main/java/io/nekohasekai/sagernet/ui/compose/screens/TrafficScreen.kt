package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ui.compose.components.EmptyState
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(
    stats: List<AppStats>,
    onClearStats: () -> Unit,
    serviceConnected: Boolean,
    trafficStatsEnabled: Boolean,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Active", "Stats")
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val activeStats = remember(stats) {
        val now = System.currentTimeMillis() / 1000
        stats.filter { it.deactivateAt == 0 || now - it.deactivateAt < 5 }
            .sortedWith(
                compareByDescending<AppStats> { it.uplink + it.downlink }
                    .thenByDescending { it.tcpConnections + it.udpConnections }
                    .thenByDescending { it.uid }
            )
    }

    val cumulativeStats by produceState<List<AppStats>>(emptyList(), stats) {
        value = withContext(Dispatchers.Default) {
            val data = stats.associate { it.uid to it.copy() }.toMutableMap()
            for (dbStats in SagerDatabase.statsDao.all()) {
                if (data.containsKey(dbStats.uid)) {
                    data[dbStats.uid]!! += dbStats
                } else {
                    data[dbStats.uid] = dbStats.toStats()
                }
            }
            for (s in data.values) {
                s.tcpConnectionsTotal += s.tcpConnections
                s.udpConnectionsTotal += s.udpConnections
                s.uplinkTotal += s.uplink
                s.downlinkTotal += s.downlink
            }
            data.values.sortedWith(
                compareByDescending<AppStats> { it.uplinkTotal + it.downlinkTotal }
                    .thenByDescending { it.tcpConnectionsTotal + it.udpConnectionsTotal }
                    .thenByDescending { it.uid }
            )
        }
    }

    val isVpn = DataStore.serviceMode == Key.MODE_VPN

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Traffic",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onClearStats) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            val displayList = if (selectedTab == 0) activeStats else cumulativeStats
            val showRates = selectedTab == 0

            if (displayList.isEmpty()) {
                val message = when {
                    !serviceConnected || !isVpn ->
                        "Turn on VPN to record traffic statistics"
                    !trafficStatsEnabled ->
                        "App traffic statistics disabled"
                    else ->
                        "No statistics yet"
                }
                EmptyState(message = message, icon = Icons.Filled.Transform)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(displayList, key = { it.uid }) { stat ->
                        TrafficItem(stat = stat, showRates = showRates)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

private data class AppInfo(val packageName: String?, val label: String)

@Composable
private fun TrafficItem(stat: AppStats, showRates: Boolean) {
    val appInfo by produceState<AppInfo?>(null, stat.uid) {
        value = withContext(Dispatchers.Default) {
            PackageCache.awaitLoadSync()
            val pkg = when (stat.uid) {
                1000 -> "android"
                else -> PackageCache.uidMap[stat.uid]?.firstOrNull()
            }
            val label = pkg?.let { PackageCache.loadLabel(it) } ?: "UID ${stat.uid}"
            AppInfo(pkg, label)
        }
    }

    val iconBitmap by produceState<ImageBitmap?>(null, appInfo?.packageName) {
        val pkg = appInfo?.packageName ?: return@produceState
        value = withContext(Dispatchers.Default) {
            try {
                val info = PackageCache.installedApps[pkg] ?: PackageCache.installedApps["android"]
                info?.loadIcon(app.packageManager)?.toBitmap(72, 72)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val useIEC = DataStore.useIECUnit
    val info = appInfo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {}
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info?.label ?: "UID ${stat.uid}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val pkg = info?.packageName
            Text(
                text = if (pkg != null) "$pkg (${stat.uid})" else "UID ${stat.uid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tcp = if (showRates) stat.tcpConnections else stat.tcpConnectionsTotal
            val udp = if (showRates) stat.udpConnections else stat.udpConnectionsTotal
            if (tcp > 0 || udp > 0) {
                val parts = mutableListOf<String>()
                if (tcp > 0) parts.add("$tcp TCP")
                if (udp > 0) parts.add("$udp UDP")
                Text(
                    text = parts.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (showRates) {
                Text(
                    text = "${FormatFileSizeCompat.formatFileSize(app, stat.uplinkTotal, useIEC)} | ${FormatFileSizeCompat.formatFileSize(app, stat.uplink, useIEC)}/s \u2191",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = "${FormatFileSizeCompat.formatFileSize(app, stat.downlinkTotal, useIEC)} | ${FormatFileSizeCompat.formatFileSize(app, stat.downlink, useIEC)}/s \u2193",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = "${FormatFileSizeCompat.formatFileSize(app, stat.uplinkTotal, useIEC)} \u2191",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = "${FormatFileSizeCompat.formatFileSize(app, stat.downlinkTotal, useIEC)} \u2193",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
    }
}
