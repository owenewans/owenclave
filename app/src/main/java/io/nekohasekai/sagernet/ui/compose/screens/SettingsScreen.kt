package io.nekohasekai.sagernet.ui.compose.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.RouteMode
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.compose.ComposeAssetsActivity
import io.nekohasekai.sagernet.ui.compose.ComposeStunActivity
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.PreferenceItem
import io.nekohasekai.sagernet.ui.compose.components.SectionCard
import io.nekohasekai.sagernet.ui.compose.components.SwitchPreferenceItem
import io.nekohasekai.sagernet.utils.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    onThemeChanged: (Int) -> Unit = {},
    onNightThemeChanged: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    var showThemePicker by remember { mutableStateOf(false) }
    var showNightModePicker by remember { mutableStateOf(false) }
    var showServiceModePicker by remember { mutableStateOf(false) }
    var showTunPicker by remember { mutableStateOf(false) }
    var showMtuPicker by remember { mutableStateOf(false) }
    var showLogLevelPicker by remember { mutableStateOf(false) }
    var showRootCaPicker by remember { mutableStateOf(false) }
    var showDomainStrategyPicker by remember { mutableStateOf(false) }
    var showRouteModePicker by remember { mutableStateOf(false) }
    var showSpeedIntervalPicker by remember { mutableStateOf(false) }
    var showOutboundStrategyPicker by remember { mutableStateOf(false) }
    var showOutboundDirectStrategyPicker by remember { mutableStateOf(false) }
    var showOutboundServerStrategyPicker by remember { mutableStateOf(false) }
    var showRemoteDnsQueryPicker by remember { mutableStateOf(false) }
    var showDirectDnsQueryPicker by remember { mutableStateOf(false) }
    var showRulesProviderPicker by remember { mutableStateOf(false) }
    var showFabStylePicker by remember { mutableStateOf(false) }
    var showFragmentMethodPicker by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf<Pair<String, String>>(Pair("", "")) }
    var editingTextKey by remember { mutableStateOf("") }
    var editingTextValue by remember { mutableStateOf("") }
    var showTextEditDialog by remember { mutableStateOf(false) }

    var socksProxyChain by remember { mutableStateOf(DataStore.socksProxyChainEnabled) }
    var enableTwps2 by remember { mutableStateOf(DataStore.enableTwps2) }
    var enableFragment by remember { mutableStateOf(DataStore.enableFragment) }
    var interruptReusedConnections by remember { mutableStateOf(DataStore.interruptReusedConnections) }
    var profileSecurityAdvisory by remember { mutableStateOf(DataStore.profileSecurityAdvisory) }
    var enableUnlockRu by remember { mutableStateOf(DataStore.enableUnlockRu) }
    var directProxyMode by remember { mutableStateOf(DataStore.directProxyMode) }
    var realityDisableX25519Mlkem768 by remember { mutableStateOf(DataStore.realityDisableX25519Mlkem768) }
    var hysteria2OmitMaxDatagramFrameSize by remember { mutableStateOf(DataStore.hysteria2OmitMaxDatagramFrameSize) }
    var grpcServiceNameCompat by remember { mutableStateOf(DataStore.grpcServiceNameCompat) }
    var enableFragmentForDirect by remember { mutableStateOf(DataStore.enableFragmentForDirect) }
    var persistAcrossReboot by remember { mutableStateOf(DataStore.persistAcrossReboot) }

    var trafficSniffing by remember { mutableStateOf(DataStore.trafficSniffing) }
    var destinationOverride by remember { mutableStateOf(DataStore.destinationOverride) }
    var enableDnsRouting by remember { mutableStateOf(DataStore.enableDnsRouting) }
    var enableFakeDns by remember { mutableStateOf(DataStore.enableFakeDns) }
    var hijackDns by remember { mutableStateOf(DataStore.hijackDns) }
    var bypassLan by remember { mutableStateOf(DataStore.bypassLan) }
    var proxyApps by remember { mutableStateOf(DataStore.proxyApps) }
    var allowAppsBypassVpn by remember { mutableStateOf(DataStore.allowAppsBypassVpn) }
    var enableVPNIPv6 by remember { mutableStateOf(DataStore.enableVPNInterfaceIPv6Address) }
    var meteredNetwork by remember { mutableStateOf(DataStore.meteredNetwork) }
    var enablePcap by remember { mutableStateOf(DataStore.enablePcap) }
    var discardICMP by remember { mutableStateOf(DataStore.discardICMP) }
    var showDirectSpeed by remember { mutableStateOf(DataStore.showDirectSpeed) }
    var appTrafficStats by remember { mutableStateOf(DataStore.appTrafficStatistics) }
    var profileTrafficStats by remember { mutableStateOf(DataStore.profileTrafficStatistics) }

    var requireSocks by remember { mutableStateOf(DataStore.requireSocks) }
    var socksUDP by remember { mutableStateOf(DataStore.socksUDP) }
    var requireHttp by remember { mutableStateOf(DataStore.requireHttp) }
    var appendHttpProxy by remember { mutableStateOf(DataStore.appendHttpProxy) }
    var requireTransproxy by remember { mutableStateOf(DataStore.requireTransproxy) }
    var requireDnsInbound by remember { mutableStateOf(DataStore.requireDnsInbound) }
    var allowAccess by remember { mutableStateOf(DataStore.allowAccess) }
    var useLocalDnsAsDirectDns by remember { mutableStateOf(DataStore.useLocalDnsAsDirectDns) }
    var useLocalDnsAsBootstrapDns by remember { mutableStateOf(DataStore.useLocalDnsAsBootstrapDns) }

    var showGroupName by remember { mutableStateOf(DataStore.showGroupName) }
    var alwaysShowAddress by remember { mutableStateOf(DataStore.alwaysShowAddress) }
    var acquireWakeLock by remember { mutableStateOf(DataStore.acquireWakeLock) }
    var useIECUnit by remember { mutableStateOf(DataStore.useIECUnit) }
    var queryAllPackagesAlternativeMethod by remember { mutableStateOf(DataStore.queryAllPackagesAlternativeMethod) }

    // ── Dialogs ──
    if (showThemePicker) {
        val themes = listOf(
            "Pink" to Theme.PINK, "Red" to Theme.RED, "Purple" to Theme.PURPLE,
            "Deep Purple" to Theme.DEEP_PURPLE, "Indigo" to Theme.INDIGO, "Blue" to Theme.BLUE,
            "Light Blue" to Theme.LIGHT_BLUE, "Cyan" to Theme.CYAN, "Teal" to Theme.TEAL,
            "Green" to Theme.GREEN, "Light Green" to Theme.LIGHT_GREEN, "Lime" to Theme.LIME,
            "Yellow" to Theme.YELLOW, "Amber" to Theme.AMBER, "Orange" to Theme.ORANGE,
            "Deep Orange" to Theme.DEEP_ORANGE, "Brown" to Theme.BROWN, "Grey" to Theme.GREY,
            "Blue Grey" to Theme.BLUE_GREY, "Black" to Theme.BLACK, "Dynamic" to Theme.DYNAMIC,
            "Unrecovery" to Theme.UNRECOVERY,
        )
        SingleChoiceDialog(
            title = "Theme",
            items = themes,
            selected = DataStore.appTheme,
            onSelect = { DataStore.appTheme = it; onThemeChanged(it); showThemePicker = false },
            onDismiss = { showThemePicker = false },
        )
    }

    if (showNightModePicker) {
        SingleChoiceDialog(
            title = "Night Mode",
            items = listOf("Follow system" to 0, "Always dark" to 1, "Always light" to 2),
            selected = DataStore.nightTheme,
            onSelect = { DataStore.nightTheme = it; onNightThemeChanged(it); showNightModePicker = false },
            onDismiss = { showNightModePicker = false },
        )
    }

    if (showServiceModePicker) {
        SingleChoiceDialog(
            title = "Service Mode",
            items = listOf("VPN" to "vpn", "Proxy" to "proxy"),
            selected = DataStore.serviceMode,
            onSelect = { DataStore.serviceMode = it; showServiceModePicker = false },
            onDismiss = { showServiceModePicker = false },
        )
    }

    if (showTunPicker) {
        SingleChoiceDialog(
            title = "TUN Implementation",
            items = listOf("gVisor" to TunImplementation.GVISOR, "System" to TunImplementation.SYSTEM),
            selected = DataStore.tunImplementation,
            onSelect = { DataStore.tunImplementation = it; showTunPicker = false },
            onDismiss = { showTunPicker = false },
        )
    }

    if (showMtuPicker) {
        var mtuValue by remember { mutableStateOf(DataStore.mtu.toString()) }
        AlertDialog(
            onDismissRequest = { showMtuPicker = false },
            title = { Text("MTU") },
            text = {
                OutlinedTextField(
                    value = mtuValue,
                    onValueChange = { mtuValue = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DataStore.mtu = mtuValue.toIntOrNull() ?: 1500
                    showMtuPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showMtuPicker = false }) { Text("Cancel") } },
        )
    }

    if (showLogLevelPicker) {
        SingleChoiceDialog(
            title = "Log Level",
            items = listOf("None" to LogLevel.NONE, "Error" to LogLevel.ERROR, "Warning" to LogLevel.WARNING, "Info" to LogLevel.INFO, "Debug" to LogLevel.DEBUG),
            selected = DataStore.logLevel,
            onSelect = { DataStore.logLevel = it; showLogLevelPicker = false },
            onDismiss = { showLogLevelPicker = false },
        )
    }

    if (showRootCaPicker) {
        SingleChoiceDialog(
            title = "Root CA Provider",
            items = listOf("Mozilla" to 0, "System" to 1, "System & User" to 2, "Custom" to 3),
            selected = DataStore.providerRootCA,
            onSelect = { DataStore.providerRootCA = it; showRootCaPicker = false },
            onDismiss = { showRootCaPicker = false },
        )
    }

    if (showDomainStrategyPicker) {
        SingleChoiceDialog(
            title = "Domain Strategy",
            items = listOf("AsIs" to "AsIs", "IPIfNonMatch" to "IPIfNonMatch", "IPOnDemand" to "IPOnDemand"),
            selected = DataStore.domainStrategy,
            onSelect = { DataStore.domainStrategy = it; showDomainStrategyPicker = false },
            onDismiss = { showDomainStrategyPicker = false },
        )
    }

    if (showRouteModePicker) {
        SingleChoiceDialog(
            title = "Route Mode",
            items = listOf("Rule" to RouteMode.RULE, "Global" to RouteMode.GLOBAL, "Direct" to RouteMode.DIRECT),
            selected = DataStore.routeMode,
            onSelect = { DataStore.routeMode = it; showRouteModePicker = false },
            onDismiss = { showRouteModePicker = false },
        )
    }

    if (showSpeedIntervalPicker) {
        var speedValue by remember { mutableStateOf(DataStore.speedInterval.toString()) }
        AlertDialog(
            onDismissRequest = { showSpeedIntervalPicker = false },
            title = { Text("Speed Interval (seconds)") },
            text = {
                OutlinedTextField(
                    value = speedValue,
                    onValueChange = { speedValue = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DataStore.speedInterval = speedValue.toIntOrNull() ?: 3
                    showSpeedIntervalPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showSpeedIntervalPicker = false }) { Text("Cancel") } },
        )
    }

    if (showOutboundStrategyPicker) {
        SingleChoiceDialog(
            title = "Resolve Destination",
            items = listOf("Disable" to "AsIs", "IPv4 only" to "UseIPv4", "Prefer IPv4" to "PreferIPv4", "IPv4 and IPv6" to "UseIP", "Prefer IPv6" to "PreferIPv6", "IPv6 only" to "UseIPv6"),
            selected = DataStore.outboundDomainStrategy,
            onSelect = { DataStore.outboundDomainStrategy = it; showOutboundStrategyPicker = false },
            onDismiss = { showOutboundStrategyPicker = false },
        )
    }

    if (showOutboundDirectStrategyPicker) {
        SingleChoiceDialog(
            title = "Resolve Destination (direct)",
            items = listOf("Disable" to "AsIs", "IPv4 only" to "UseIPv4", "Prefer IPv4" to "PreferIPv4", "IPv4 and IPv6" to "UseIP", "Prefer IPv6" to "PreferIPv6", "IPv6 only" to "UseIPv6"),
            selected = DataStore.outboundDomainStrategyForDirect,
            onSelect = { DataStore.outboundDomainStrategyForDirect = it; showOutboundDirectStrategyPicker = false },
            onDismiss = { showOutboundDirectStrategyPicker = false },
        )
    }

    if (showOutboundServerStrategyPicker) {
        SingleChoiceDialog(
            title = "Resolve Destination (server)",
            items = listOf("Disable" to "AsIs", "IPv4 only" to "UseIPv4", "Prefer IPv4" to "PreferIPv4", "IPv4 and IPv6" to "UseIP", "Prefer IPv6" to "PreferIPv6", "IPv6 only" to "UseIPv6"),
            selected = DataStore.outboundDomainStrategyForServer,
            onSelect = { DataStore.outboundDomainStrategyForServer = it; showOutboundServerStrategyPicker = false },
            onDismiss = { showOutboundServerStrategyPicker = false },
        )
    }

    if (showRemoteDnsQueryPicker) {
        SingleChoiceDialog(
            title = "Remote DNS Query Strategy",
            items = listOf("IPv4 and IPv6" to "UseIP", "IPv4 only" to "UseIPv4", "IPv6 only" to "UseIPv6"),
            selected = DataStore.remoteDnsQueryStrategy,
            onSelect = { DataStore.remoteDnsQueryStrategy = it; showRemoteDnsQueryPicker = false },
            onDismiss = { showRemoteDnsQueryPicker = false },
        )
    }

    if (showDirectDnsQueryPicker) {
        SingleChoiceDialog(
            title = "Direct DNS Query Strategy",
            items = listOf("IPv4 and IPv6" to "UseIP", "IPv4 only" to "UseIPv4", "IPv6 only" to "UseIPv6"),
            selected = DataStore.directDnsQueryStrategy,
            onSelect = { DataStore.directDnsQueryStrategy = it; showDirectDnsQueryPicker = false },
            onDismiss = { showDirectDnsQueryPicker = false },
        )
    }

    if (showRulesProviderPicker) {
        SingleChoiceDialog(
            title = "Route Assets Provider",
            items = listOf("v2fly" to 0, "Loyalsoldier/v2ray-rules-dat" to 1, "Chocolate4U/Iran-v2ray-rules" to 2, "Custom" to 3),
            selected = DataStore.rulesProvider,
            onSelect = { DataStore.rulesProvider = it; showRulesProviderPicker = false },
            onDismiss = { showRulesProviderPicker = false },
        )
    }

    if (showFabStylePicker) {
        SingleChoiceDialog(
            title = "FAB Style",
            items = listOf("SagerNet" to 0, "Shadowsocks" to 1),
            selected = DataStore.fabStyle,
            onSelect = { DataStore.fabStyle = it; showFabStylePicker = false },
            onDismiss = { showFabStylePicker = false },
        )
    }

    if (showFragmentMethodPicker) {
        SingleChoiceDialog(
            title = "Fragmentation Method",
            items = listOf("TLS record" to 0, "TCP segmentation" to 1, "TLS + TCP" to 2),
            selected = DataStore.fragmentMethod,
            onSelect = { DataStore.fragmentMethod = it; showFragmentMethodPicker = false },
            onDismiss = { showFragmentMethodPicker = false },
        )
    }

    if (showTextEditDialog) {
        AlertDialog(
            onDismissRequest = { showTextEditDialog = false },
            title = { Text(editingText.first) },
            text = {
                OutlinedTextField(
                    value = editingTextValue,
                    onValueChange = { editingTextValue = it },
                    singleLine = editingTextKey != "hosts" && editingTextKey != "httpProxyException",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (editingTextKey) {
                        "connectionTestURL" -> DataStore.connectionTestURL = editingTextValue
                        "remoteDns" -> DataStore.remoteDns = editingTextValue
                        "directDns" -> DataStore.directDns = editingTextValue
                        "bootstrapDns" -> DataStore.bootstrapDns = editingTextValue
                        "ednsClientIp" -> DataStore.ednsClientIp = editingTextValue
                        "hosts" -> DataStore.hosts = editingTextValue
                        "socksUsername" -> DataStore.socksUsername = editingTextValue
                        "socksPassword" -> DataStore.socksPassword = editingTextValue
                        "httpUsername" -> DataStore.httpUsername = editingTextValue
                        "httpPassword" -> DataStore.httpPassword = editingTextValue
                        "httpProxyException" -> DataStore.httpProxyException = editingTextValue
                        "socksPort" -> DataStore.socksPort = editingTextValue.toIntOrNull() ?: 2080
                        "httpPort" -> DataStore.httpPort = editingTextValue.toIntOrNull() ?: 9080
                        "transproxyPort" -> DataStore.transproxyPort = editingTextValue.toIntOrNull() ?: 9200
                        "localDNSPort" -> DataStore.localDNSPort = editingTextValue.toIntOrNull() ?: 6450
                        "stunServers" -> DataStore.stunServers = editingTextValue
                        "pprofServer" -> DataStore.pprofServer = editingTextValue
                        "experimentalFlags" -> DataStore.experimentalFlags = editingTextValue
                        "rulesGeositeUrl" -> DataStore.rulesGeositeUrl = editingTextValue
                        "rulesGeoipUrl" -> DataStore.rulesGeoipUrl = editingTextValue
                        "socksProxyChainHost" -> DataStore.socksProxyChainHost = editingTextValue
                        "socksProxyChainPort" -> DataStore.socksProxyChainPort = editingTextValue.toIntOrNull() ?: 0
                        "socksProxyChainUsername" -> DataStore.socksProxyChainUsername = editingTextValue
                        "socksProxyChainPassword" -> DataStore.socksProxyChainPassword = editingTextValue
                        "subscriptionAutoUpdateDelay" -> {}
                    }
                    showTextEditDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTextEditDialog = false }) { Text("Cancel") } },
        )
    }

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
                        subtitle = "Restore connection after device boot",
                        checked = persistAcrossReboot,
                        onCheckedChange = { persistAcrossReboot = it; DataStore.persistAcrossReboot = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Theme",
                        subtitle = "Choose app color theme",
                        icon = Icons.Filled.Palette,
                        onClick = { showThemePicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Night Mode",
                        subtitle = when (DataStore.nightTheme) { 0 -> "Follow system"; 1 -> "Always dark"; 2 -> "Always light"; else -> "Auto" },
                        icon = Icons.Filled.WbSunny,
                        onClick = { showNightModePicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Language",
                        subtitle = "System default",
                        icon = Icons.Filled.Translate,
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:" + context.packageName))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Service Mode",
                        subtitle = DataStore.serviceMode,
                        icon = Icons.Filled.Dashboard,
                        onClick = { showServiceModePicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "TUN Implementation",
                        subtitle = if (DataStore.tunImplementation == TunImplementation.GVISOR) "gVisor" else "System",
                        icon = Icons.Filled.Lan,
                        onClick = { showTunPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "MTU",
                        subtitle = "${DataStore.mtu}",
                        icon = Icons.Filled.Public,
                        onClick = { showMtuPicker = true },
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
                        onClick = { showSpeedIntervalPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Log Level",
                        subtitle = when (DataStore.logLevel) { 0 -> "None"; 1 -> "Error"; 2 -> "Warning"; 3 -> "Info"; 4 -> "Debug"; else -> "Unknown" },
                        icon = Icons.Filled.Tune,
                        onClick = { showLogLevelPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Root CA Provider",
                        subtitle = when (DataStore.providerRootCA) { 0 -> "Mozilla"; 1 -> "System"; 2 -> "System & User"; 3 -> "Custom"; else -> "Unknown" },
                        icon = Icons.Filled.Security,
                        onClick = { showRootCaPicker = true },
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
                    PreferenceItem(
                        title = "Per-App Proxy",
                        subtitle = if (proxyApps) {
                            if (DataStore.bypass) "Bypass mode" else "Proxy mode"
                        } else {
                            "Disabled"
                        },
                        icon = Icons.Filled.Apps,
                        onClick = {
                            proxyApps = true; DataStore.proxyApps = true
                            context.startActivity(Intent(context, io.nekohasekai.sagernet.ui.compose.ComposeAppListActivity::class.java))
                        },
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
                        onClick = { showDomainStrategyPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Resolve Destination",
                        subtitle = DataStore.outboundDomainStrategy,
                        onClick = { showOutboundStrategyPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Resolve Destination (direct)",
                        subtitle = DataStore.outboundDomainStrategyForDirect,
                        onClick = { showOutboundDirectStrategyPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Resolve Destination (server)",
                        subtitle = DataStore.outboundDomainStrategyForServer,
                        onClick = { showOutboundServerStrategyPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Route Assets Provider",
                        subtitle = when (DataStore.rulesProvider) { 0 -> "v2fly"; 1 -> "Loyalsoldier"; 2 -> "Iran-v2ray-rules"; 3 -> "Custom"; else -> "Unknown" },
                        onClick = { showRulesProviderPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Route Mode",
                        subtitle = when (DataStore.routeMode) { 0 -> "Rule"; 1 -> "Global"; 2 -> "Direct"; else -> "Unknown" },
                        icon = Icons.Filled.Route,
                        onClick = { showRouteModePicker = true },
                    )
                }

                // ── Protocol ──
                PreferenceHeader("Protocol")
                SectionCard {
                    PreferenceItem(
                        title = "Connection Test URL",
                        subtitle = DataStore.connectionTestURL,
                        icon = Icons.Filled.Public,
                        onClick = {
                            editingTextKey = "connectionTestURL"
                            editingText = Pair("Connection Test URL", DataStore.connectionTestURL)
                            editingTextValue = DataStore.connectionTestURL
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "SOCKS Proxy Chain",
                        subtitle = "Route SOCKS through another proxy",
                        checked = socksProxyChain,
                        onCheckedChange = { socksProxyChain = it; DataStore.socksProxyChainEnabled = it },
                    )
                    if (socksProxyChain) {
                        DividerItem()
                        PreferenceItem(
                            title = "Chain Host",
                            subtitle = DataStore.socksProxyChainHost ?: "None",
                            onClick = {
                                editingTextKey = "socksProxyChainHost"
                                editingTextValue = DataStore.socksProxyChainHost ?: ""
                                editingText = Pair("Chain Host", editingTextValue)
                                showTextEditDialog = true
                            },
                        )
                        DividerItem()
                        PreferenceItem(
                            title = "Chain Port",
                            subtitle = "${DataStore.socksProxyChainPort}",
                            onClick = {
                                editingTextKey = "socksProxyChainPort"
                                editingTextValue = DataStore.socksProxyChainPort.toString()
                                editingText = Pair("Chain Port", editingTextValue)
                                showTextEditDialog = true
                            },
                        )
                        DividerItem()
                        PreferenceItem(
                            title = "Chain Username",
                            subtitle = DataStore.socksProxyChainUsername ?: "None",
                            onClick = {
                                editingTextKey = "socksProxyChainUsername"
                                editingTextValue = DataStore.socksProxyChainUsername ?: ""
                                editingText = Pair("Chain Username", editingTextValue)
                                showTextEditDialog = true
                            },
                        )
                        DividerItem()
                        PreferenceItem(
                            title = "Chain Password",
                            subtitle = if (DataStore.socksProxyChainPassword.isNullOrEmpty()) "None" else "****",
                            onClick = {
                                editingTextKey = "socksProxyChainPassword"
                                editingTextValue = DataStore.socksProxyChainPassword ?: ""
                                editingText = Pair("Chain Password", editingTextValue)
                                showTextEditDialog = true
                            },
                        )
                    }
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable TWPS2 (zapret2)",
                        subtitle = "Global DPI bypass using twps2",
                        checked = enableTwps2,
                        onCheckedChange = { enableTwps2 = it; DataStore.enableTwps2 = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Unlock AI and EN Services (Russia)",
                        checked = enableUnlockRu,
                        onCheckedChange = { enableUnlockRu = it; DataStore.enableUnlockRu = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Direct Proxy Mode",
                        subtitle = "Direct traffic through device using zapret2",
                        checked = directProxyMode,
                        onCheckedChange = { directProxyMode = it; DataStore.directProxyMode = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Disable REALITY X25519MLKEM768",
                        checked = realityDisableX25519Mlkem768,
                        onCheckedChange = { realityDisableX25519Mlkem768 = it; DataStore.realityDisableX25519Mlkem768 = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Hysteria 2 OmitMaxDatagramFrameSize",
                        checked = hysteria2OmitMaxDatagramFrameSize,
                        onCheckedChange = { hysteria2OmitMaxDatagramFrameSize = it; DataStore.hysteria2OmitMaxDatagramFrameSize = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "gRPC Service Name Compatibility",
                        checked = grpcServiceNameCompat,
                        onCheckedChange = { grpcServiceNameCompat = it; DataStore.grpcServiceNameCompat = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enable TLS Fragment",
                        subtitle = "May help circumvent SNI censorship",
                        checked = enableFragment,
                        onCheckedChange = { enableFragment = it; DataStore.enableFragment = it },
                    )
                    if (enableFragment) {
                        DividerItem()
                        PreferenceItem(
                            title = "Fragmentation Method",
                            subtitle = when (DataStore.fragmentMethod) { 0 -> "TLS record"; 1 -> "TCP segmentation"; 2 -> "TLS + TCP"; else -> "Unknown" },
                            onClick = { showFragmentMethodPicker = true },
                        )
                        DividerItem()
                        SwitchPreferenceItem(
                            title = "Fragment for Direct",
                            checked = enableFragmentForDirect,
                            onCheckedChange = { enableFragmentForDirect = it; DataStore.enableFragmentForDirect = it },
                        )
                    }
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
                        onClick = {
                            editingTextKey = "remoteDns"
                            editingTextValue = DataStore.remoteDns
                            editingText = Pair("Remote DNS", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Remote DNS Query Strategy",
                        subtitle = DataStore.remoteDnsQueryStrategy,
                        onClick = { showRemoteDnsQueryPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "EDNS Client IP",
                        subtitle = DataStore.ednsClientIp ?: "None",
                        onClick = {
                            editingTextKey = "ednsClientIp"
                            editingTextValue = DataStore.ednsClientIp ?: ""
                            editingText = Pair("EDNS Client IP", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Direct DNS",
                        subtitle = DataStore.directDns,
                        onClick = {
                            editingTextKey = "directDns"
                            editingTextValue = DataStore.directDns
                            editingText = Pair("Direct DNS", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Direct DNS Query Strategy",
                        subtitle = DataStore.directDnsQueryStrategy,
                        onClick = { showDirectDnsQueryPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Bootstrap DNS",
                        subtitle = DataStore.bootstrapDns ?: "Auto",
                        onClick = {
                            editingTextKey = "bootstrapDns"
                            editingTextValue = DataStore.bootstrapDns ?: ""
                            editingText = Pair("Bootstrap DNS", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Domain Rewriting",
                        subtitle = DataStore.hosts ?: "None",
                        onClick = {
                            editingTextKey = "hosts"
                            editingTextValue = DataStore.hosts ?: ""
                            editingText = Pair("Domain Rewriting", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Use Local DNS as Direct DNS",
                        checked = useLocalDnsAsDirectDns,
                        onCheckedChange = { useLocalDnsAsDirectDns = it; DataStore.useLocalDnsAsDirectDns = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Use Local DNS as Bootstrap DNS",
                        checked = useLocalDnsAsBootstrapDns,
                        onCheckedChange = { useLocalDnsAsBootstrapDns = it; DataStore.useLocalDnsAsBootstrapDns = it },
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
                        onClick = {
                            editingTextKey = "socksPort"
                            editingTextValue = DataStore.socksPort.toString()
                            editingText = Pair("SOCKS Port", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "SOCKS Username",
                        subtitle = DataStore.socksUsername ?: "None",
                        onClick = {
                            editingTextKey = "socksUsername"
                            editingTextValue = DataStore.socksUsername ?: ""
                            editingText = Pair("SOCKS Username", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "SOCKS Password",
                        subtitle = if (DataStore.socksPassword.isNullOrEmpty()) "None" else "****",
                        onClick = {
                            editingTextKey = "socksPassword"
                            editingTextValue = DataStore.socksPassword ?: ""
                            editingText = Pair("SOCKS Password", editingTextValue)
                            showTextEditDialog = true
                        },
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
                        onClick = {
                            editingTextKey = "httpPort"
                            editingTextValue = DataStore.httpPort.toString()
                            editingText = Pair("HTTP Port", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "HTTP Username",
                        subtitle = DataStore.httpUsername ?: "None",
                        onClick = {
                            editingTextKey = "httpUsername"
                            editingTextValue = DataStore.httpUsername ?: ""
                            editingText = Pair("HTTP Username", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "HTTP Password",
                        subtitle = if (DataStore.httpPassword.isNullOrEmpty()) "None" else "****",
                        onClick = {
                            editingTextKey = "httpPassword"
                            editingTextValue = DataStore.httpPassword ?: ""
                            editingText = Pair("HTTP Password", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    if (requireHttp) {
                        DividerItem()
                        PreferenceItem(
                            title = "HTTP Proxy Exception",
                            subtitle = DataStore.httpProxyException ?: "None",
                            onClick = {
                                editingTextKey = "httpProxyException"
                                editingTextValue = DataStore.httpProxyException ?: ""
                                editingText = Pair("HTTP Proxy Exception", editingTextValue)
                                showTextEditDialog = true
                            },
                        )
                    }
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Require Transproxy",
                        checked = requireTransproxy,
                        onCheckedChange = { requireTransproxy = it; DataStore.requireTransproxy = it },
                    )
                    if (requireTransproxy) {
                        DividerItem()
                        PreferenceItem(
                            title = "Transproxy Port",
                            subtitle = "${DataStore.transproxyPort}",
                            onClick = {
                                editingTextKey = "transproxyPort"
                                editingTextValue = DataStore.transproxyPort.toString()
                                editingText = Pair("Transproxy Port", editingTextValue)
                                showTextEditDialog = true
                            },
                        )
                    }
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
                        onClick = {
                            editingTextKey = "localDNSPort"
                            editingTextValue = DataStore.localDNSPort.toString()
                            editingText = Pair("Local DNS Port", editingTextValue)
                            showTextEditDialog = true
                        },
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
                    SwitchPreferenceItem(
                        title = "Query All Packages (Alternative Method)",
                        checked = queryAllPackagesAlternativeMethod,
                        onCheckedChange = { queryAllPackagesAlternativeMethod = it; DataStore.queryAllPackagesAlternativeMethod = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "STUN Servers",
                        subtitle = DataStore.stunServers ?: "None",
                        onClick = {
                            editingTextKey = "stunServers"
                            editingTextValue = DataStore.stunServers ?: ""
                            editingText = Pair("STUN Servers", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "pprof Server",
                        subtitle = DataStore.pprofServer ?: "None",
                        onClick = {
                            editingTextKey = "pprofServer"
                            editingTextValue = DataStore.pprofServer ?: ""
                            editingText = Pair("pprof Server", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "FAB Style",
                        subtitle = when (DataStore.fabStyle) { 0 -> "SagerNet"; 1 -> "Shadowsocks"; else -> "Unknown" },
                        onClick = { showFabStylePicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "geosite.dat URL",
                        subtitle = DataStore.rulesGeositeUrl,
                        onClick = {
                            editingTextKey = "rulesGeositeUrl"
                            editingTextValue = DataStore.rulesGeositeUrl
                            editingText = Pair("geosite.dat URL", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "geoip.dat URL",
                        subtitle = DataStore.rulesGeoipUrl,
                        onClick = {
                            editingTextKey = "rulesGeoipUrl"
                            editingTextValue = DataStore.rulesGeoipUrl
                            editingText = Pair("geoip.dat URL", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Experimental Flags",
                        subtitle = DataStore.experimentalFlags ?: "None",
                        onClick = {
                            editingTextKey = "experimentalFlags"
                            editingTextValue = DataStore.experimentalFlags ?: ""
                            editingText = Pair("Experimental Flags", editingTextValue)
                            showTextEditDialog = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun <T> SingleChoiceDialog(
    title: String,
    items: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                items.forEach { (label, value) ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
