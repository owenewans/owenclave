package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.PreferenceItem
import io.nekohasekai.sagernet.ui.compose.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSettingsScreen(
    routeName: String = "",
    routeDomain: String = "",
    routeIP: String = "",
    routePort: String = "",
    routeSourcePort: String = "",
    routeNetwork: String = "",
    routeSource: String = "",
    routeProtocol: String = "",
    routeOutbound: Int = 0,
    routePackages: String = "",
    onBack: () -> Unit,
    onSave: (
        name: String, domains: String, ip: String, port: String,
        sourcePort: String, network: String, source: String, protocol: String,
        outbound: Int, packages: String,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(routeName) }
    var domains by remember { mutableStateOf(routeDomain) }
    var ip by remember { mutableStateOf(routeIP) }
    var port by remember { mutableStateOf(routePort) }
    var sourcePort by remember { mutableStateOf(routeSourcePort) }
    var network by remember { mutableStateOf(routeNetwork) }
    var source by remember { mutableStateOf(routeSource) }
    var protocol by remember { mutableStateOf(routeProtocol) }
    var outbound by remember { mutableIntStateOf(routeOutbound) }
    var packages by remember { mutableStateOf(routePackages) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Route Rule",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    Button(onClick = {
                        onSave(name, domains, ip, port, sourcePort, network, source, protocol, outbound, packages)
                    }) { Text("Save") }
                },
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
                PreferenceHeader("General")
                SectionCard {
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                        modifier = Modifier.padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Outbound",
                        subtitle = when (outbound) {
                            0 -> "Proxy"
                            1 -> "Bypass"
                            2 -> "Block"
                            else -> "Profile"
                        },
                        onClick = { outbound = (outbound + 1) % 4 },
                    )
                }

                PreferenceHeader("Match Conditions")
                SectionCard {
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = domains,
                        onValueChange = { domains = it },
                        label = "Domains (one per line)",
                        modifier = Modifier.padding(16.dp),
                        singleLine = false,
                    )
                    DividerItem()
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = "IP / CIDR (one per line)",
                        modifier = Modifier.padding(16.dp),
                        singleLine = false,
                    )
                    DividerItem()
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = "Port (e.g. 53,443,1000-2000)",
                        modifier = Modifier.padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = sourcePort,
                        onValueChange = { sourcePort = it },
                        label = "Source Port",
                        modifier = Modifier.padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = network,
                        onValueChange = { network = it },
                        label = "Network (tcp/udp)",
                        modifier = Modifier.padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = "Source IP / CIDR",
                        modifier = Modifier.padding(16.dp),
                        singleLine = false,
                    )
                    DividerItem()
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = protocol,
                        onValueChange = { protocol = it },
                        label = "Protocol",
                        modifier = Modifier.padding(16.dp),
                        singleLine = true,
                    )
                }

                PreferenceHeader("Apps")
                SectionCard {
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = packages,
                        onValueChange = { packages = it },
                        label = "Package names (one per line)",
                        modifier = Modifier.padding(16.dp),
                        singleLine = false,
                    )
                }
            }
        }
    }
}
