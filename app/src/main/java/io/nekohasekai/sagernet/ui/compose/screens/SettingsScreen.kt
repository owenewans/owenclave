package io.nekohasekai.sagernet.ui.compose.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lan
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.nekohasekai.sagernet.ui.compose.components.PreferenceGroup
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
    onServiceModeChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    var showThemePicker by remember { mutableStateOf(false) }
    var showLogoPicker by remember { mutableStateOf(false) }
    var currentLogo by remember { mutableStateOf(DataStore.appLogo) }
    var showNightModePicker by remember { mutableStateOf(false) }
    var showServiceModePicker by remember { mutableStateOf(false) }
    var showTunPicker by remember { mutableStateOf(false) }
    var showMtuPicker by remember { mutableStateOf(false) }
    var showLogLevelPicker by remember { mutableStateOf(false) }
    var showRootCaPicker by remember { mutableStateOf(false) }
    var showDomainStrategyPicker by remember { mutableStateOf(false) }
    var showRouteModePicker by remember { mutableStateOf(false) }
    var showSpeedIntervalPicker by remember { mutableStateOf(false) }
    var showConnectionTimeoutPicker by remember { mutableStateOf(false) }
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

    // Reactive state for preference subtitles — DataStore reads are synchronous
    // but don't trigger recomposition, so we mirror values into State here.
    var serviceMode by remember { mutableStateOf(DataStore.serviceMode) }
    var tunImplementation by remember { mutableStateOf(DataStore.tunImplementation) }
    var mtu by remember { mutableStateOf(DataStore.mtu) }
    var speedInterval by remember { mutableStateOf(DataStore.speedInterval) }
    var logLevel by remember { mutableStateOf(DataStore.logLevel) }
    var providerRootCA by remember { mutableStateOf(DataStore.providerRootCA) }
    var domainStrategy by remember { mutableStateOf(DataStore.domainStrategy) }
    var routeMode by remember { mutableStateOf(DataStore.routeMode) }
    var outboundDomainStrategy by remember { mutableStateOf(DataStore.outboundDomainStrategy) }
    var outboundDomainStrategyForDirect by remember { mutableStateOf(DataStore.outboundDomainStrategyForDirect) }
    var outboundDomainStrategyForServer by remember { mutableStateOf(DataStore.outboundDomainStrategyForServer) }
    var remoteDnsQueryStrategy by remember { mutableStateOf(DataStore.remoteDnsQueryStrategy) }
    var directDnsQueryStrategy by remember { mutableStateOf(DataStore.directDnsQueryStrategy) }
    var rulesProvider by remember { mutableStateOf(DataStore.rulesProvider) }
    var fabStyle by remember { mutableStateOf(DataStore.fabStyle) }
    var fragmentMethod by remember { mutableStateOf(DataStore.fragmentMethod) }
    var connectionTestURL by remember { mutableStateOf(DataStore.connectionTestURL) }
    var connectionTestTimeout by remember { mutableStateOf(DataStore.connectionTestTimeout) }
    var remoteDns by remember { mutableStateOf(DataStore.remoteDns) }
    var directDns by remember { mutableStateOf(DataStore.directDns) }
    var bootstrapDns by remember { mutableStateOf(DataStore.bootstrapDns ?: "") }
    var ednsClientIp by remember { mutableStateOf(DataStore.ednsClientIp ?: "") }
    var hosts by remember { mutableStateOf(DataStore.hosts ?: "") }
    var socksPort by remember { mutableStateOf(DataStore.socksPort) }
    var httpPort by remember { mutableStateOf(DataStore.httpPort) }
    var transproxyPort by remember { mutableStateOf(DataStore.transproxyPort) }
    var localDNSPort by remember { mutableStateOf(DataStore.localDNSPort) }
    var socksUsername by remember { mutableStateOf(DataStore.socksUsername ?: "") }
    var socksPassword by remember { mutableStateOf(DataStore.socksPassword ?: "") }
    var httpUsername by remember { mutableStateOf(DataStore.httpUsername ?: "") }
    var httpPassword by remember { mutableStateOf(DataStore.httpPassword ?: "") }
    var httpProxyException by remember { mutableStateOf(DataStore.httpProxyException ?: "") }
    var stunServers by remember { mutableStateOf(DataStore.stunServers ?: "") }
    var pprofServer by remember { mutableStateOf(DataStore.pprofServer ?: "") }
    var experimentalFlags by remember { mutableStateOf(DataStore.experimentalFlags ?: "") }
    var rulesGeositeUrl by remember { mutableStateOf(DataStore.rulesGeositeUrl) }
    var rulesGeoipUrl by remember { mutableStateOf(DataStore.rulesGeoipUrl) }
    var socksProxyChainHost by remember { mutableStateOf(DataStore.socksProxyChainHost ?: "") }
    var socksProxyChainPort by remember { mutableStateOf(DataStore.socksProxyChainPort) }
    var socksProxyChainUsername by remember { mutableStateOf(DataStore.socksProxyChainUsername ?: "") }
    var socksProxyChainPassword by remember { mutableStateOf(DataStore.socksProxyChainPassword ?: "") }

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

    if (showLogoPicker) {
        LogoPickerDialog(
            selected = currentLogo,
            onSelect = {
                DataStore.appLogo = it
                currentLogo = it
                showLogoPicker = false
            },
            onDismiss = { showLogoPicker = false },
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
            onSelect = { DataStore.serviceMode = it; serviceMode = it; showServiceModePicker = false; onServiceModeChanged() },
            onDismiss = { showServiceModePicker = false },
        )
    }

    if (showTunPicker) {
        SingleChoiceDialog(
            title = "TUN Implementation",
            items = listOf("gVisor" to TunImplementation.GVISOR, "System" to TunImplementation.SYSTEM),
            selected = DataStore.tunImplementation,
            onSelect = { DataStore.tunImplementation = it; tunImplementation = it; showTunPicker = false },
            onDismiss = { showTunPicker = false },
        )
    }

    if (showMtuPicker) {
        var mtuValue by remember { mutableStateOf(DataStore.mtu.toString()) }
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { showMtuPicker = false }) {
            Text(
                text = "MTU",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                value = mtuValue,
                onValueChange = { mtuValue = it.filter { c -> c.isDigit() } },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showMtuPicker = false }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    DataStore.mtu = mtuValue.toIntOrNull() ?: 1500
                    mtu = DataStore.mtu
                    showMtuPicker = false
                }) { Text("OK") }
            }
        }
    }

    if (showLogLevelPicker) {
        SingleChoiceDialog(
            title = "Log Level",
            items = listOf("None" to LogLevel.NONE, "Error" to LogLevel.ERROR, "Warning" to LogLevel.WARNING, "Info" to LogLevel.INFO, "Debug" to LogLevel.DEBUG),
            selected = DataStore.logLevel,
            onSelect = { DataStore.logLevel = it; logLevel = it; showLogLevelPicker = false },
            onDismiss = { showLogLevelPicker = false },
        )
    }

    if (showRootCaPicker) {
        SingleChoiceDialog(
            title = "Root CA Provider",
            items = listOf("Mozilla" to 0, "System" to 1, "System & User" to 2, "Custom" to 3),
            selected = DataStore.providerRootCA,
            onSelect = { DataStore.providerRootCA = it; providerRootCA = it; showRootCaPicker = false },
            onDismiss = { showRootCaPicker = false },
        )
    }

    if (showDomainStrategyPicker) {
        SingleChoiceDialog(
            title = "Domain Strategy",
            items = listOf("AsIs" to "AsIs", "IPIfNonMatch" to "IPIfNonMatch", "IPOnDemand" to "IPOnDemand"),
            selected = DataStore.domainStrategy,
            onSelect = { DataStore.domainStrategy = it; domainStrategy = it; showDomainStrategyPicker = false },
            onDismiss = { showDomainStrategyPicker = false },
        )
    }

    if (showRouteModePicker) {
        SingleChoiceDialog(
            title = "Route Mode",
            items = listOf("Rule" to RouteMode.RULE, "Global" to RouteMode.GLOBAL, "Direct" to RouteMode.DIRECT),
            selected = DataStore.routeMode,
            onSelect = { DataStore.routeMode = it; routeMode = it; showRouteModePicker = false },
            onDismiss = { showRouteModePicker = false },
        )
    }

    if (showSpeedIntervalPicker) {
        SingleChoiceDialog(
            title = "Speed Interval",
            items = listOf(
                "0.5s" to 500,
                "1s" to 1000,
                "3s" to 3000,
                "10s" to 10000,
                "60s" to 60000,
                "180s" to 180000,
            ),
            selected = DataStore.speedInterval,
            onSelect = { DataStore.speedInterval = it; speedInterval = it; showSpeedIntervalPicker = false },
            onDismiss = { showSpeedIntervalPicker = false },
        )
    }

    if (showConnectionTimeoutPicker) {
        SingleChoiceDialog(
            title = "Connection Test Timeout",
            items = listOf(
                "2s" to 2000,
                "5s" to 5000,
                "10s" to 10000,
                "15s" to 15000,
                "30s" to 30000,
            ),
            selected = connectionTestTimeout,
            onSelect = { DataStore.connectionTestTimeout = it; connectionTestTimeout = it; showConnectionTimeoutPicker = false },
            onDismiss = { showConnectionTimeoutPicker = false },
        )
    }

    if (showOutboundStrategyPicker) {
        SingleChoiceDialog(
            title = "Resolve Destination",
            items = listOf("Disable" to "AsIs", "IPv4 only" to "UseIPv4", "Prefer IPv4" to "PreferIPv4", "IPv4 and IPv6" to "UseIP", "Prefer IPv6" to "PreferIPv6", "IPv6 only" to "UseIPv6"),
            selected = DataStore.outboundDomainStrategy,
            onSelect = { DataStore.outboundDomainStrategy = it; outboundDomainStrategy = it; showOutboundStrategyPicker = false },
            onDismiss = { showOutboundStrategyPicker = false },
        )
    }

    if (showOutboundDirectStrategyPicker) {
        SingleChoiceDialog(
            title = "Resolve Destination (direct)",
            items = listOf("Disable" to "AsIs", "IPv4 only" to "UseIPv4", "Prefer IPv4" to "PreferIPv4", "IPv4 and IPv6" to "UseIP", "Prefer IPv6" to "PreferIPv6", "IPv6 only" to "UseIPv6"),
            selected = DataStore.outboundDomainStrategyForDirect,
            onSelect = { DataStore.outboundDomainStrategyForDirect = it; outboundDomainStrategyForDirect = it; showOutboundDirectStrategyPicker = false },
            onDismiss = { showOutboundDirectStrategyPicker = false },
        )
    }

    if (showOutboundServerStrategyPicker) {
        SingleChoiceDialog(
            title = "Resolve Destination (server)",
            items = listOf("Disable" to "AsIs", "IPv4 only" to "UseIPv4", "Prefer IPv4" to "PreferIPv4", "IPv4 and IPv6" to "UseIP", "Prefer IPv6" to "PreferIPv6", "IPv6 only" to "UseIPv6"),
            selected = DataStore.outboundDomainStrategyForServer,
            onSelect = { DataStore.outboundDomainStrategyForServer = it; outboundDomainStrategyForServer = it; showOutboundServerStrategyPicker = false },
            onDismiss = { showOutboundServerStrategyPicker = false },
        )
    }

    if (showRemoteDnsQueryPicker) {
        SingleChoiceDialog(
            title = "Remote DNS Query Strategy",
            items = listOf("IPv4 and IPv6" to "UseIP", "IPv4 only" to "UseIPv4", "IPv6 only" to "UseIPv6"),
            selected = DataStore.remoteDnsQueryStrategy,
            onSelect = { DataStore.remoteDnsQueryStrategy = it; remoteDnsQueryStrategy = it; showRemoteDnsQueryPicker = false },
            onDismiss = { showRemoteDnsQueryPicker = false },
        )
    }

    if (showDirectDnsQueryPicker) {
        SingleChoiceDialog(
            title = "Direct DNS Query Strategy",
            items = listOf("IPv4 and IPv6" to "UseIP", "IPv4 only" to "UseIPv4", "IPv6 only" to "UseIPv6"),
            selected = DataStore.directDnsQueryStrategy,
            onSelect = { DataStore.directDnsQueryStrategy = it; directDnsQueryStrategy = it; showDirectDnsQueryPicker = false },
            onDismiss = { showDirectDnsQueryPicker = false },
        )
    }

    if (showRulesProviderPicker) {
        SingleChoiceDialog(
            title = "Route Assets Provider",
            items = listOf("v2fly" to 0, "Loyalsoldier/v2ray-rules-dat" to 1, "Chocolate4U/Iran-v2ray-rules" to 2, "Custom" to 3),
            selected = DataStore.rulesProvider,
            onSelect = { DataStore.rulesProvider = it; rulesProvider = it; showRulesProviderPicker = false },
            onDismiss = { showRulesProviderPicker = false },
        )
    }

    if (showFabStylePicker) {
        SingleChoiceDialog(
            title = "FAB Style",
            items = listOf("SagerNet" to 0, "Shadowsocks" to 1),
            selected = DataStore.fabStyle,
            onSelect = { DataStore.fabStyle = it; fabStyle = it; showFabStylePicker = false },
            onDismiss = { showFabStylePicker = false },
        )
    }

    if (showFragmentMethodPicker) {
        SingleChoiceDialog(
            title = "Fragmentation Method",
            items = listOf("TLS record" to 0, "TCP segmentation" to 1, "TLS + TCP" to 2),
            selected = DataStore.fragmentMethod,
            onSelect = { DataStore.fragmentMethod = it; fragmentMethod = it; showFragmentMethodPicker = false },
            onDismiss = { showFragmentMethodPicker = false },
        )
    }

    if (showTextEditDialog) {
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { showTextEditDialog = false }) {
            Text(
                text = editingText.first,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                value = editingTextValue,
                onValueChange = { editingTextValue = it },
                singleLine = editingTextKey != "hosts" && editingTextKey != "httpProxyException",
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showTextEditDialog = false }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    when (editingTextKey) {
                        "connectionTestURL" -> { DataStore.connectionTestURL = editingTextValue; connectionTestURL = editingTextValue }
                        "remoteDns" -> { DataStore.remoteDns = editingTextValue; remoteDns = editingTextValue }
                        "directDns" -> { DataStore.directDns = editingTextValue; directDns = editingTextValue }
                        "bootstrapDns" -> { DataStore.bootstrapDns = editingTextValue; bootstrapDns = editingTextValue }
                        "ednsClientIp" -> { DataStore.ednsClientIp = editingTextValue; ednsClientIp = editingTextValue }
                        "hosts" -> { DataStore.hosts = editingTextValue; hosts = editingTextValue }
                        "socksUsername" -> { DataStore.socksUsername = editingTextValue; socksUsername = editingTextValue }
                        "socksPassword" -> { DataStore.socksPassword = editingTextValue; socksPassword = editingTextValue }
                        "httpUsername" -> { DataStore.httpUsername = editingTextValue; httpUsername = editingTextValue }
                        "httpPassword" -> { DataStore.httpPassword = editingTextValue; httpPassword = editingTextValue }
                        "httpProxyException" -> { DataStore.httpProxyException = editingTextValue; httpProxyException = editingTextValue }
                        "socksPort" -> { DataStore.socksPort = editingTextValue.toIntOrNull() ?: 2080; socksPort = DataStore.socksPort }
                        "httpPort" -> { DataStore.httpPort = editingTextValue.toIntOrNull() ?: 9080; httpPort = DataStore.httpPort }
                        "transproxyPort" -> { DataStore.transproxyPort = editingTextValue.toIntOrNull() ?: 9200; transproxyPort = DataStore.transproxyPort }
                        "localDNSPort" -> { DataStore.localDNSPort = editingTextValue.toIntOrNull() ?: 6450; localDNSPort = DataStore.localDNSPort }
                        "stunServers" -> { DataStore.stunServers = editingTextValue; stunServers = editingTextValue }
                        "pprofServer" -> { DataStore.pprofServer = editingTextValue; pprofServer = editingTextValue }
                        "experimentalFlags" -> { DataStore.experimentalFlags = editingTextValue; experimentalFlags = editingTextValue }
                        "rulesGeositeUrl" -> { DataStore.rulesGeositeUrl = editingTextValue; rulesGeositeUrl = editingTextValue }
                        "rulesGeoipUrl" -> { DataStore.rulesGeoipUrl = editingTextValue; rulesGeoipUrl = editingTextValue }
                        "socksProxyChainHost" -> { DataStore.socksProxyChainHost = editingTextValue; socksProxyChainHost = editingTextValue }
                        "socksProxyChainPort" -> { DataStore.socksProxyChainPort = editingTextValue.toIntOrNull() ?: 0; socksProxyChainPort = DataStore.socksProxyChainPort }
                        "socksProxyChainUsername" -> { DataStore.socksProxyChainUsername = editingTextValue; socksProxyChainUsername = editingTextValue }
                        "socksProxyChainPassword" -> { DataStore.socksProxyChainPassword = editingTextValue; socksProxyChainPassword = editingTextValue }
                        "subscriptionAutoUpdateDelay" -> {}
                    }
                    showTextEditDialog = false
                }) { Text("OK") }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Settings",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(bottom = io.nekohasekai.sagernet.ui.compose.components.StatsBarBottomInset)) {
                // ── General ──
                PreferenceHeader("General")
                PreferenceGroup {
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Auto Connect",
                            subtitle = "Restore connection after device boot",
                            checked = persistAcrossReboot,
                            onCheckedChange = { persistAcrossReboot = it; DataStore.persistAcrossReboot = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Theme",
                            subtitle = "Choose app color theme",
                            icon = Icons.Filled.Palette,
                            onClick = { showThemePicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "App Logo",
                            subtitle = io.nekohasekai.sagernet.ui.compose.components.AppLogoStyle.fromId(currentLogo).label,
                            icon = Icons.Filled.Face,
                            onClick = { showLogoPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Night Mode",
                            subtitle = when (DataStore.nightTheme) { 0 -> "Follow system"; 1 -> "Always dark"; 2 -> "Always light"; else -> "Auto" },
                            icon = Icons.Filled.WbSunny,
                            onClick = { showNightModePicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
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
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Service Mode",
                            subtitle = serviceMode,
                            icon = Icons.Filled.Dashboard,
                            onClick = { showServiceModePicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "TUN Implementation",
                            subtitle = if (tunImplementation == TunImplementation.GVISOR) "gVisor" else "System",
                            icon = Icons.Filled.Lan,
                            onClick = { showTunPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "MTU",
                            subtitle = "$mtu",
                            icon = Icons.Filled.Public,
                            onClick = { showMtuPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Metered Network",
                            subtitle = "Treat VPN as metered",
                            icon = Icons.Filled.Wifi,
                            checked = meteredNetwork,
                            onCheckedChange = { meteredNetwork = it; DataStore.meteredNetwork = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Enable PCAP",
                            subtitle = "Capture packets to file",
                            icon = Icons.Filled.Construction,
                            checked = enablePcap,
                            onCheckedChange = { enablePcap = it; DataStore.enablePcap = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Discard ICMP",
                            checked = discardICMP,
                            onCheckedChange = { discardICMP = it; DataStore.discardICMP = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "App Traffic Statistics",
                            icon = Icons.Filled.Speed,
                            checked = appTrafficStats,
                            onCheckedChange = { appTrafficStats = it; DataStore.appTrafficStatistics = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Profile Traffic Statistics",
                            checked = profileTrafficStats,
                            onCheckedChange = { profileTrafficStats = it; DataStore.profileTrafficStatistics = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Show Direct Speed",
                            checked = showDirectSpeed,
                            onCheckedChange = { showDirectSpeed = it; DataStore.showDirectSpeed = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Speed Interval",
                            subtitle = when (speedInterval) {
                                500 -> "0.5s"
                                1000 -> "1s"
                                3000 -> "3s"
                                10000 -> "10s"
                                60000 -> "60s"
                                180000 -> "180s"
                                else -> "${speedInterval}ms"
                            },
                            icon = Icons.Filled.Speed,
                            onClick = { showSpeedIntervalPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Log Level",
                            subtitle = when (logLevel) { 0 -> "None"; 1 -> "Error"; 2 -> "Warning"; 3 -> "Info"; 4 -> "Debug"; else -> "Unknown" },
                            icon = Icons.Filled.Tune,
                            onClick = { showLogLevelPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Root CA Provider",
                            subtitle = when (providerRootCA) { 0 -> "Mozilla"; 1 -> "System"; 2 -> "System & User"; 3 -> "Custom"; else -> "Unknown" },
                            icon = Icons.Filled.Security,
                            onClick = { showRootCaPicker = true },
                            shape = shape,
                        )
                    }
                }

                // ── Route ──
                PreferenceHeader("Route")
                PreferenceGroup {
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "IPv6 Address on VPN Interface",
                            checked = enableVPNIPv6,
                            onCheckedChange = { enableVPNIPv6 = it; DataStore.enableVPNInterfaceIPv6Address = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
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
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Allow Apps to Bypass VPN",
                            checked = allowAppsBypassVpn,
                            onCheckedChange = { allowAppsBypassVpn = it; DataStore.allowAppsBypassVpn = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Bypass LAN",
                            checked = bypassLan,
                            onCheckedChange = { bypassLan = it; DataStore.bypassLan = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Traffic Sniffing",
                            checked = trafficSniffing,
                            onCheckedChange = { trafficSniffing = it; DataStore.trafficSniffing = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Destination Override",
                            checked = destinationOverride,
                            onCheckedChange = { destinationOverride = it; DataStore.destinationOverride = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Hijack DNS",
                            checked = hijackDns,
                            onCheckedChange = { hijackDns = it; DataStore.hijackDns = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Domain Strategy",
                            subtitle = domainStrategy,
                            onClick = { showDomainStrategyPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Resolve Destination",
                            subtitle = outboundDomainStrategy,
                            onClick = { showOutboundStrategyPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Resolve Destination (direct)",
                            subtitle = outboundDomainStrategyForDirect,
                            onClick = { showOutboundDirectStrategyPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Resolve Destination (server)",
                            subtitle = outboundDomainStrategyForServer,
                            onClick = { showOutboundServerStrategyPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Route Assets Provider",
                            subtitle = when (rulesProvider) { 0 -> "v2fly"; 1 -> "Loyalsoldier"; 2 -> "Iran-v2ray-rules"; 3 -> "Custom"; else -> "Unknown" },
                            onClick = { showRulesProviderPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Route Mode",
                            subtitle = when (routeMode) { 0 -> "Rule"; 1 -> "Global"; 2 -> "Direct"; else -> "Unknown" },
                            icon = Icons.Filled.Route,
                            onClick = { showRouteModePicker = true },
                            shape = shape,
                        )
                    }
                }

                // ── Protocol ──
                PreferenceHeader("Protocol")
                PreferenceGroup {
                    item { shape ->
                        PreferenceItem(
                            title = "Connection Test URL",
                            subtitle = connectionTestURL,
                            icon = Icons.Filled.Public,
                            onClick = {
                                editingTextKey = "connectionTestURL"
                                editingText = Pair("Connection Test URL", DataStore.connectionTestURL)
                                editingTextValue = connectionTestURL
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Connection Test Timeout",
                            subtitle = when (connectionTestTimeout) {
                                2000 -> "2s"
                                5000 -> "5s"
                                10000 -> "10s"
                                15000 -> "15s"
                                30000 -> "30s"
                                else -> "${connectionTestTimeout}ms"
                            },
                            icon = Icons.Filled.Speed,
                            onClick = { showConnectionTimeoutPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "SOCKS Proxy Chain",
                            subtitle = "Route SOCKS through another proxy",
                            checked = socksProxyChain,
                            onCheckedChange = { socksProxyChain = it; DataStore.socksProxyChainEnabled = it },
                            shape = shape,
                        )
                    }
                    if (socksProxyChain) {
                        item { shape ->
                            PreferenceItem(
                                title = "Chain Host",
                                subtitle = socksProxyChainHost.ifEmpty { "None" },
                                onClick = {
                                    editingTextKey = "socksProxyChainHost"
                                    editingTextValue = socksProxyChainHost
                                    editingText = Pair("Chain Host", editingTextValue)
                                    showTextEditDialog = true
                                },
                                shape = shape,
                            )
                        }
                        item { shape ->
                            PreferenceItem(
                                title = "Chain Port",
                                subtitle = "$socksProxyChainPort",
                                onClick = {
                                    editingTextKey = "socksProxyChainPort"
                                    editingTextValue = socksProxyChainPort.toString()
                                    editingText = Pair("Chain Port", editingTextValue)
                                    showTextEditDialog = true
                                },
                                shape = shape,
                            )
                        }
                        item { shape ->
                            PreferenceItem(
                                title = "Chain Username",
                                subtitle = socksProxyChainUsername.ifEmpty { "None" },
                                onClick = {
                                    editingTextKey = "socksProxyChainUsername"
                                    editingTextValue = socksProxyChainUsername
                                    editingText = Pair("Chain Username", editingTextValue)
                                    showTextEditDialog = true
                                },
                                shape = shape,
                            )
                        }
                        item { shape ->
                            PreferenceItem(
                                title = "Chain Password",
                                subtitle = if (socksProxyChainPassword.isEmpty()) "None" else "****",
                                onClick = {
                                    editingTextKey = "socksProxyChainPassword"
                                    editingTextValue = socksProxyChainPassword
                                    editingText = Pair("Chain Password", editingTextValue)
                                    showTextEditDialog = true
                                },
                                shape = shape,
                            )
                        }
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Enable TWPS2 (zapret2)",
                            subtitle = "Global DPI bypass using twps2",
                            checked = enableTwps2,
                            onCheckedChange = { enableTwps2 = it; DataStore.enableTwps2 = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Unlock AI and EN Services (Russia)",
                            checked = enableUnlockRu,
                            onCheckedChange = { enableUnlockRu = it; DataStore.enableUnlockRu = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Direct Proxy Mode",
                            subtitle = "Direct traffic through device using zapret2",
                            checked = directProxyMode,
                            onCheckedChange = { directProxyMode = it; DataStore.directProxyMode = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Disable REALITY X25519MLKEM768",
                            checked = realityDisableX25519Mlkem768,
                            onCheckedChange = { realityDisableX25519Mlkem768 = it; DataStore.realityDisableX25519Mlkem768 = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Hysteria 2 OmitMaxDatagramFrameSize",
                            checked = hysteria2OmitMaxDatagramFrameSize,
                            onCheckedChange = { hysteria2OmitMaxDatagramFrameSize = it; DataStore.hysteria2OmitMaxDatagramFrameSize = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "gRPC Service Name Compatibility",
                            checked = grpcServiceNameCompat,
                            onCheckedChange = { grpcServiceNameCompat = it; DataStore.grpcServiceNameCompat = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Enable TLS Fragment",
                            subtitle = "May help circumvent SNI censorship",
                            checked = enableFragment,
                            onCheckedChange = { enableFragment = it; DataStore.enableFragment = it },
                            shape = shape,
                        )
                    }
                    if (enableFragment) {
                        item { shape ->
                            PreferenceItem(
                                title = "Fragmentation Method",
                                subtitle = when (fragmentMethod) { 0 -> "TLS record"; 1 -> "TCP segmentation"; 2 -> "TLS + TCP"; else -> "Unknown" },
                                onClick = { showFragmentMethodPicker = true },
                                shape = shape,
                            )
                        }
                        item { shape ->
                            SwitchPreferenceItem(
                                title = "Fragment for Direct",
                                checked = enableFragmentForDirect,
                                onCheckedChange = { enableFragmentForDirect = it; DataStore.enableFragmentForDirect = it },
                                shape = shape,
                            )
                        }
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Interrupt Reused Connections",
                            checked = interruptReusedConnections,
                            onCheckedChange = { interruptReusedConnections = it; DataStore.interruptReusedConnections = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Profile Security Advisory",
                            checked = profileSecurityAdvisory,
                            onCheckedChange = { profileSecurityAdvisory = it; DataStore.profileSecurityAdvisory = it },
                            shape = shape,
                        )
                    }
                }

                // ── DNS ──
                PreferenceHeader("DNS")
                PreferenceGroup {
                    item { shape ->
                        PreferenceItem(
                            title = "Remote DNS",
                            subtitle = remoteDns,
                            icon = Icons.Filled.Dns,
                            onClick = {
                                editingTextKey = "remoteDns"
                                editingTextValue = remoteDns
                                editingText = Pair("Remote DNS", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Remote DNS Query Strategy",
                            subtitle = remoteDnsQueryStrategy,
                            onClick = { showRemoteDnsQueryPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "EDNS Client IP",
                            subtitle = ednsClientIp.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "ednsClientIp"
                                editingTextValue = ednsClientIp
                                editingText = Pair("EDNS Client IP", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Direct DNS",
                            subtitle = directDns,
                            onClick = {
                                editingTextKey = "directDns"
                                editingTextValue = directDns
                                editingText = Pair("Direct DNS", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Direct DNS Query Strategy",
                            subtitle = directDnsQueryStrategy,
                            onClick = { showDirectDnsQueryPicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Bootstrap DNS",
                            subtitle = bootstrapDns.ifEmpty { "Auto" },
                            onClick = {
                                editingTextKey = "bootstrapDns"
                                editingTextValue = bootstrapDns
                                editingText = Pair("Bootstrap DNS", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Domain Rewriting",
                            subtitle = hosts.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "hosts"
                                editingTextValue = hosts
                                editingText = Pair("Domain Rewriting", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Use Local DNS as Direct DNS",
                            checked = useLocalDnsAsDirectDns,
                            onCheckedChange = { useLocalDnsAsDirectDns = it; DataStore.useLocalDnsAsDirectDns = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Use Local DNS as Bootstrap DNS",
                            checked = useLocalDnsAsBootstrapDns,
                            onCheckedChange = { useLocalDnsAsBootstrapDns = it; DataStore.useLocalDnsAsBootstrapDns = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Enable DNS Routing",
                            checked = enableDnsRouting,
                            onCheckedChange = { enableDnsRouting = it; DataStore.enableDnsRouting = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Enable FakeDNS",
                            checked = enableFakeDns,
                            onCheckedChange = { enableFakeDns = it; DataStore.enableFakeDns = it },
                            shape = shape,
                        )
                    }
                }

                // ── Inbound ──
                PreferenceHeader("Inbound")
                PreferenceGroup {
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Require SOCKS",
                            checked = requireSocks,
                            onCheckedChange = { requireSocks = it; DataStore.requireSocks = it },
                            shape = shape,
                        )
                    }
                    if (requireSocks) {
                        item { shape ->
                            SwitchPreferenceItem(
                                title = "SOCKS UDP",
                                checked = socksUDP,
                                onCheckedChange = { socksUDP = it; DataStore.socksUDP = it },
                                shape = shape,
                            )
                        }
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "SOCKS Port",
                            subtitle = "$socksPort",
                            icon = Icons.Filled.Cable,
                            onClick = {
                                editingTextKey = "socksPort"
                                editingTextValue = socksPort.toString()
                                editingText = Pair("SOCKS Port", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "SOCKS Username",
                            subtitle = socksUsername.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "socksUsername"
                                editingTextValue = socksUsername
                                editingText = Pair("SOCKS Username", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "SOCKS Password",
                            subtitle = if (socksPassword.isEmpty()) "None" else "****",
                            onClick = {
                                editingTextKey = "socksPassword"
                                editingTextValue = socksPassword
                                editingText = Pair("SOCKS Password", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Require HTTP",
                            checked = requireHttp,
                            onCheckedChange = { requireHttp = it; DataStore.requireHttp = it },
                            shape = shape,
                        )
                    }
                    if (requireHttp) {
                        item { shape ->
                            SwitchPreferenceItem(
                                title = "Append HTTP Proxy",
                                checked = appendHttpProxy,
                                onCheckedChange = { appendHttpProxy = it; DataStore.appendHttpProxy = it },
                                shape = shape,
                            )
                        }
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "HTTP Port",
                            subtitle = "$httpPort",
                            onClick = {
                                editingTextKey = "httpPort"
                                editingTextValue = httpPort.toString()
                                editingText = Pair("HTTP Port", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "HTTP Username",
                            subtitle = httpUsername.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "httpUsername"
                                editingTextValue = httpUsername
                                editingText = Pair("HTTP Username", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "HTTP Password",
                            subtitle = if (httpPassword.isEmpty()) "None" else "****",
                            onClick = {
                                editingTextKey = "httpPassword"
                                editingTextValue = httpPassword
                                editingText = Pair("HTTP Password", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    if (requireHttp) {
                        item { shape ->
                            PreferenceItem(
                            title = "HTTP Proxy Exception",
                            subtitle = httpProxyException.ifEmpty { "None" },
                                onClick = {
                                    editingTextKey = "httpProxyException"
                                    editingTextValue = httpProxyException
                                    editingText = Pair("HTTP Proxy Exception", editingTextValue)
                                    showTextEditDialog = true
                                },
                                shape = shape,
                            )
                        }
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Require Transproxy",
                            checked = requireTransproxy,
                            onCheckedChange = { requireTransproxy = it; DataStore.requireTransproxy = it },
                            shape = shape,
                        )
                    }
                    if (requireTransproxy) {
                        item { shape ->
                            PreferenceItem(
                                title = "Transproxy Port",
                                subtitle = "$transproxyPort",
                                onClick = {
                                    editingTextKey = "transproxyPort"
                                    editingTextValue = transproxyPort.toString()
                                    editingText = Pair("Transproxy Port", editingTextValue)
                                    showTextEditDialog = true
                                },
                                shape = shape,
                            )
                        }
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Require DNS Inbound",
                            checked = requireDnsInbound,
                            onCheckedChange = { requireDnsInbound = it; DataStore.requireDnsInbound = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Local DNS Port",
                            subtitle = "$localDNSPort",
                            onClick = {
                                editingTextKey = "localDNSPort"
                                editingTextValue = localDNSPort.toString()
                                editingText = Pair("Local DNS Port", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Allow Access from Other Devices",
                            checked = allowAccess,
                            onCheckedChange = { allowAccess = it; DataStore.allowAccess = it },
                            shape = shape,
                        )
                    }
                }

                // ── Misc ──
                PreferenceHeader("Misc")
                PreferenceGroup {
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Show Group Name",
                            checked = showGroupName,
                            onCheckedChange = { showGroupName = it; DataStore.showGroupName = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Always Show Address",
                            checked = alwaysShowAddress,
                            onCheckedChange = { alwaysShowAddress = it; DataStore.alwaysShowAddress = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Acquire Wake Lock",
                            checked = acquireWakeLock,
                            onCheckedChange = { acquireWakeLock = it; DataStore.acquireWakeLock = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Use IEC Unit",
                            checked = useIECUnit,
                            onCheckedChange = { useIECUnit = it; DataStore.useIECUnit = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        SwitchPreferenceItem(
                            title = "Query All Packages (Alternative Method)",
                            checked = queryAllPackagesAlternativeMethod,
                            onCheckedChange = { queryAllPackagesAlternativeMethod = it; DataStore.queryAllPackagesAlternativeMethod = it },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "STUN Servers",
                            subtitle = stunServers.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "stunServers"
                                editingTextValue = stunServers
                                editingText = Pair("STUN Servers", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "pprof Server",
                            subtitle = pprofServer.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "pprofServer"
                                editingTextValue = pprofServer
                                editingText = Pair("pprof Server", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "FAB Style",
                            subtitle = when (fabStyle) { 0 -> "SagerNet"; 1 -> "Shadowsocks"; else -> "Unknown" },
                            onClick = { showFabStylePicker = true },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "geosite.dat URL",
                            subtitle = rulesGeositeUrl,
                            onClick = {
                                editingTextKey = "rulesGeositeUrl"
                                editingTextValue = rulesGeositeUrl
                                editingText = Pair("geosite.dat URL", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "geoip.dat URL",
                            subtitle = rulesGeoipUrl,
                            onClick = {
                                editingTextKey = "rulesGeoipUrl"
                                editingTextValue = rulesGeoipUrl
                                editingText = Pair("geoip.dat URL", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        PreferenceItem(
                            title = "Experimental Flags",
                            subtitle = experimentalFlags.ifEmpty { "None" },
                            onClick = {
                                editingTextKey = "experimentalFlags"
                                editingTextValue = experimentalFlags
                                editingText = Pair("Experimental Flags", editingTextValue)
                                showTextEditDialog = true
                            },
                            shape = shape,
                        )
                    }
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
    io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .clip(io.nekohasekai.sagernet.ui.compose.components.GroupContainerShape)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { index, (label, value) ->
                val isSelected = value == selected
                androidx.compose.material3.Surface(
                    onClick = { onSelect(value) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun LogoPickerDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = "App Logo",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Pick a logo shape",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        val styles = io.nekohasekai.sagernet.ui.compose.components.AppLogoStyle.entries
        Column(
            modifier = Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            styles.chunked(3).forEach { rowStyles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    rowStyles.forEach { style ->
                        val isSelected = style.id == selected
                        androidx.compose.material3.Surface(
                            onClick = { onSelect(style.id) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected)
                                androidx.compose.foundation.BorderStroke(
                                    2.dp, MaterialTheme.colorScheme.primary,
                                )
                            else null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            ) {
                                io.nekohasekai.sagernet.ui.compose.components.AppLogo(
                                    style = style,
                                    size = 56.dp,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    // pad incomplete rows so cells keep equal width
                    repeat(3 - rowStyles.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
