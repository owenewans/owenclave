package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.PreferenceItem
import io.nekohasekai.sagernet.ui.compose.components.SectionCard
import io.nekohasekai.sagernet.ui.compose.components.SwitchPreferenceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
) {
    var autoConnect by remember { mutableStateOf(DataStore.persistAcrossReboot) }
    var serviceMode by remember { mutableStateOf(DataStore.serviceMode) }
    var tunImpl by remember { mutableStateOf(DataStore.tunImplementation) }
    var meteredNetwork by remember { mutableStateOf(DataStore.meteredNetwork) }
    var enablePcap by remember { mutableStateOf(DataStore.enablePcap) }
    var discardICMP by remember { mutableStateOf(DataStore.discardICMP) }
    var trafficSniffing by remember { mutableStateOf(DataStore.trafficSniffing) }
    var destinationOverride by remember { mutableStateOf(DataStore.destinationOverride) }
    var enableDnsRouting by remember { mutableStateOf(DataStore.enableDnsRouting) }
    var enableFakeDns by remember { mutableStateOf(DataStore.enableFakeDns) }
    var hijackDns by remember { mutableStateOf(DataStore.hijackDns) }
    var bypassLan by remember { mutableStateOf(DataStore.bypassLan) }
    var proxyApps by remember { mutableStateOf(DataStore.proxyApps) }
    var allowAppsBypassVpn by remember { mutableStateOf(DataStore.allowAppsBypassVpn) }
    var enableVPNIPv6 by remember { mutableStateOf(DataStore.enableVPNInterfaceIPv6Address) }
    var interruptReusedConnections by remember { mutableStateOf(DataStore.interruptReusedConnections) }
    var enableFragment by remember { mutableStateOf(DataStore.enableFragment) }
    var enableTwps2 by remember { mutableStateOf(DataStore.enableTwps2) }
    var enableUnlockRu by remember { mutableStateOf(DataStore.directProxyMode) }
    var profileSecurityAdvisory by remember { mutableStateOf(DataStore.profileSecurityAdvisory) }
    var requireSocks by remember { mutableStateOf(DataStore.requireSocks) }
    var socksUDP by remember { mutableStateOf(DataStore.socksUDP) }
    var requireHttp by remember { mutableStateOf(DataStore.requireHttp) }
    var appendHttpProxy by remember { mutableStateOf(DataStore.appendHttpProxy) }
    var requireTransproxy by remember { mutableStateOf(DataStore.requireTransproxy) }
    var requireDnsInbound by remember { mutableStateOf(DataStore.requireDnsInbound) }
    var allowAccess by remember { mutableStateOf(DataStore.allowAccess) }
    var showDirectSpeed by remember { mutableStateOf(DataStore.showDirectSpeed) }
    var appTrafficStats by remember { mutableStateOf(DataStore.appTrafficStatistics) }
    var profileTrafficStats by remember { mutableStateOf(DataStore.profileTrafficStatistics) }
    var acquireWakeLock by remember { mutableStateOf(DataStore.acquireWakeLock) }
    var showGroupName by remember { mutableStateOf(DataStore.showGroupName) }
    var alwaysShowAddress by remember { mutableStateOf(DataStore.alwaysShowAddress) }
    var useIECUnit by remember { mutableStateOf(DataStore.useIECUnit) }
    var logLevel by remember { mutableStateOf(DataStore.logLevel) }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Settings",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                // ── General ──
                PreferenceHeader("General")
                SectionCard {
                    SwitchPreferenceItem(
                        title = "Auto Connect",
                        subtitle = "Connect automatically on boot/reboot",
                        icon = Icons.Filled.RocketLaunch,
                        checked = autoConnect,
                        onCheckedChange = { },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Theme",
                        subtitle = "Choose app color theme",
                        icon = Icons.Filled.Palette,
                        onClick = { /* open color picker */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Night Mode",
                        subtitle = when (DataStore.nightTheme) {
                            0 -> "Follow system"
                            1 -> "Always dark"
                            2 -> "Always light"
                            else -> "Auto battery"
                        },
                        icon = Icons.Filled.WbSunny,
                        onClick = { /* toggle night mode */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Language",
                        subtitle = "System default",
                        icon = Icons.Filled.Translate,
                        onClick = { /* open language picker */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Service Mode",
                        subtitle = serviceMode,
                        icon = Icons.Filled.Dashboard,
                        onClick = { /* toggle vpn/proxy */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "TUN Implementation",
                        subtitle = if (tunImpl == 0) "gVisor" else "System",
                        icon = Icons.Filled.Lan,
                        onClick = { /* toggle */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "MTU",
                        subtitle = "${DataStore.mtu}",
                        icon = Icons.Filled.Public,
                        onClick = { /* edit MTU */ },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Metered Network",
                        subtitle = "Treat VPN as metered",
                        icon = Icons.Filled.Wifi,
                        checked = meteredNetwork,
                        onCheckedChange = { meteredNetwork = it; DataStore.meteredNetwork = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable PCAP",
                        subtitle = "Capture packets to file",
                        icon = Icons.Filled.Construction,
                        checked = enablePcap,
                        onCheckedChange = { enablePcap = it; DataStore.enablePcap = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Discard ICMP",
                        checked = discardICMP,
                        onCheckedChange = { discardICMP = it; DataStore.discardICMP = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "App Traffic Statistics",
                        icon = Icons.Filled.Speed,
                        checked = appTrafficStats,
                        onCheckedChange = { appTrafficStats = it; DataStore.appTrafficStatistics = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Profile Traffic Statistics",
                        checked = profileTrafficStats,
                        onCheckedChange = { profileTrafficStats = it; DataStore.profileTrafficStatistics = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Show Direct Speed",
                        checked = showDirectSpeed,
                        onCheckedChange = { showDirectSpeed = it; DataStore.showDirectSpeed = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Speed Interval",
                        subtitle = "${DataStore.speedInterval}s",
                        icon = Icons.Filled.Speed,
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Log Level",
                        subtitle = when (logLevel) {
                            0 -> "None"
                            1 -> "Error"
                            2 -> "Warning"
                            3 -> "Info"
                            4 -> "Debug"
                            else -> "Unknown"
                        },
                        icon = Icons.Filled.Tune,
                        onClick = { /* change log level */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Root CA Provider",
                        subtitle = when (DataStore.providerRootCA) {
                            0 -> "Mozilla"
                            1 -> "System"
                            2 -> "System & User"
                            3 -> "Custom"
                            else -> "Unknown"
                        },
                        icon = Icons.Filled.Security,
                        onClick = { /* change */ },
                    )
                }

                // ── Route ──
                PreferenceHeader("Route")
                SectionCard {
                    SwitchPreferenceItem(
                        title = "IPv6 Address on VPN Interface",
                        checked = enableVPNIPv6,
                        onCheckedChange = { enableVPNIPv6 = it; DataStore.enableVPNInterfaceIPv6Address = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Per-App Proxy",
                        checked = proxyApps,
                        onCheckedChange = { proxyApps = it; DataStore.proxyApps = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Allow Apps to Bypass VPN",
                        checked = allowAppsBypassVpn,
                        onCheckedChange = { allowAppsBypassVpn = it; DataStore.allowAppsBypassVpn = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Bypass LAN",
                        checked = bypassLan,
                        onCheckedChange = { bypassLan = it; DataStore.bypassLan = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Traffic Sniffing",
                        checked = trafficSniffing,
                        onCheckedChange = { trafficSniffing = it; DataStore.trafficSniffing = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Destination Override",
                        checked = destinationOverride,
                        onCheckedChange = { destinationOverride = it; DataStore.destinationOverride = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Hijack DNS",
                        checked = hijackDns,
                        onCheckedChange = { hijackDns = it; DataStore.hijackDns = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Domain Strategy",
                        subtitle = DataStore.domainStrategy,
                        onClick = { /* change */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Outbound Domain Strategy",
                        subtitle = DataStore.outboundDomainStrategy,
                        onClick = { /* change */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Route Mode",
                        subtitle = when (DataStore.routeMode) {
                            0 -> "Rule"
                            1 -> "Global"
                            2 -> "Direct"
                            else -> "Unknown"
                        },
                        icon = Icons.Filled.Route,
                        onClick = { /* change */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Rules Provider",
                        subtitle = when (DataStore.rulesProvider) {
                            0 -> "Built-in"
                            1 -> "External"
                            else -> "Unknown"
                        },
                        onClick = { /* change */ },
                    )
                }

                // ── Protocol ──
                PreferenceHeader("Protocol")
                SectionCard {
                    PreferenceItem(
                        title = "Connection Test URL",
                        subtitle = DataStore.connectionTestURL,
                        icon = Icons.Filled.Public,
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "SOCKS Proxy Chain",
                        checked = DataStore.socksProxyChainEnabled,
                        onCheckedChange = { DataStore.socksProxyChainEnabled = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable TWPS2",
                        checked = enableTwps2,
                        onCheckedChange = { enableTwps2 = it; DataStore.enableTwps2 = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable Unlock RU",
                        checked = enableUnlockRu,
                        onCheckedChange = { enableUnlockRu = it; DataStore.directProxyMode = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable Fragment",
                        checked = enableFragment,
                        onCheckedChange = { enableFragment = it; DataStore.enableFragment = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Interrupt Reused Connections",
                        checked = interruptReusedConnections,
                        onCheckedChange = { interruptReusedConnections = it; DataStore.interruptReusedConnections = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Profile Security Advisory",
                        checked = profileSecurityAdvisory,
                        onCheckedChange = { profileSecurityAdvisory = it; DataStore.profileSecurityAdvisory = it },
                    )
                }

                // ── DNS ──
                PreferenceHeader("DNS")
                SectionCard {
                    PreferenceItem(
                        title = "Remote DNS",
                        subtitle = DataStore.remoteDns,
                        icon = Icons.Filled.Dns,
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Direct DNS",
                        subtitle = DataStore.directDns,
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Bootstrap DNS",
                        subtitle = DataStore.bootstrapDns ?: "Auto",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Use Local DNS as Direct DNS",
                        checked = DataStore.useLocalDnsAsDirectDns,
                        onCheckedChange = { DataStore.useLocalDnsAsDirectDns = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Use Local DNS as Bootstrap DNS",
                        checked = DataStore.useLocalDnsAsBootstrapDns,
                        onCheckedChange = { DataStore.useLocalDnsAsBootstrapDns = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable DNS Routing",
                        checked = enableDnsRouting,
                        onCheckedChange = { enableDnsRouting = it; DataStore.enableDnsRouting = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable FakeDNS",
                        checked = enableFakeDns,
                        onCheckedChange = { enableFakeDns = it; DataStore.enableFakeDns = it },
                    )
                }

                // ── Inbound ──
                PreferenceHeader("Inbound")
                SectionCard {
                    SwitchPreferenceItem(
                        title = "Require SOCKS",
                        checked = requireSocks,
                        onCheckedChange = { requireSocks = it; DataStore.requireSocks = it },
                    )
                    if (requireSocks) {
                        DividerItem()
                        SwitchPreferenceItem(
                            title = "SOCKS UDP",
                            checked = socksUDP,
                            onCheckedChange = { socksUDP = it; DataStore.socksUDP = it },
                        )
                    }
                    DividerItem()
                    PreferenceItem(
                        title = "SOCKS Port",
                        subtitle = "${DataStore.socksPort}",
                        icon = Icons.Filled.Cable,
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "SOCKS Username",
                        subtitle = DataStore.socksUsername ?: "None",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "SOCKS Password",
                        subtitle = if (DataStore.socksPassword.isNullOrEmpty()) "None" else "****",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Require HTTP",
                        checked = requireHttp,
                        onCheckedChange = { requireHttp = it; DataStore.requireHttp = it },
                    )
                    if (requireHttp) {
                        DividerItem()
                        SwitchPreferenceItem(
                            title = "Append HTTP Proxy",
                            checked = appendHttpProxy,
                            onCheckedChange = { appendHttpProxy = it; DataStore.appendHttpProxy = it },
                        )
                    }
                    DividerItem()
                    PreferenceItem(
                        title = "HTTP Port",
                        subtitle = "${DataStore.httpPort}",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Require Transproxy",
                        checked = requireTransproxy,
                        onCheckedChange = { requireTransproxy = it; DataStore.requireTransproxy = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Require DNS Inbound",
                        checked = requireDnsInbound,
                        onCheckedChange = { requireDnsInbound = it; DataStore.requireDnsInbound = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Local DNS Port",
                        subtitle = "${DataStore.localDNSPort}",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Allow Access from Other Devices",
                        checked = allowAccess,
                        onCheckedChange = { allowAccess = it; DataStore.allowAccess = it },
                    )
                }

                // ── Misc ──
                PreferenceHeader("Misc")
                SectionCard {
                    SwitchPreferenceItem(
                        title = "Show Group Name",
                        checked = showGroupName,
                        onCheckedChange = { showGroupName = it; DataStore.showGroupName = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Always Show Address",
                        checked = alwaysShowAddress,
                        onCheckedChange = { alwaysShowAddress = it; DataStore.alwaysShowAddress = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Acquire Wake Lock",
                        checked = acquireWakeLock,
                        onCheckedChange = { acquireWakeLock = it; DataStore.acquireWakeLock = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Use IEC Unit",
                        checked = useIECUnit,
                        onCheckedChange = { useIECUnit = it; DataStore.useIECUnit = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "STUN Servers",
                        subtitle = DataStore.stunServers ?: "None",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "pprof Server",
                        subtitle = DataStore.pprofServer ?: "None",
                        onClick = { /* edit */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "FAB Style",
                        subtitle = when (DataStore.fabStyle) {
                            0 -> "SagerNet"
                            1 -> "Shadowsocks"
                            else -> "Unknown"
                        },
                        onClick = { /* change */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Experimental Flags",
                        subtitle = DataStore.experimentalFlags ?: "None",
                        onClick = { /* edit */ },
                    )
                }
            }
        }
    }
}
