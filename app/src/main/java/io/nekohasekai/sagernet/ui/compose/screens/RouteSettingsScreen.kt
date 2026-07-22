package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.nekohasekai.sagernet.ui.compose.components.SwitchPreferenceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var domains by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var sourcePort by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("") }
    var outbound by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(true) }
    var packages by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var networkType by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Route Rule",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    Button(onClick = onSave) { Text("Save") }
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Enabled",
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Outbound",
                        subtitle = when (outbound) {
                            0 -> "Proxy"
                            -1 -> "Bypass"
                            -2 -> "Block"
                            else -> "Profile $outbound"
                        },
                        onClick = { /* select outbound */ },
                    )
                }

                PreferenceHeader("Match Conditions")
                SectionCard {
                    OutlinedTextField(
                        value = domains,
                        onValueChange = { domains = it },
                        label = { Text("Domains") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("IP / CIDR") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = sourcePort,
                        onValueChange = { sourcePort = it },
                        label = { Text("Source Port") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = network,
                        onValueChange = { network = it },
                        label = { Text("Network (tcp/udp)") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text("Source IP / CIDR") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = protocol,
                        onValueChange = { protocol = it },
                        label = { Text("Protocol") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                }

                PreferenceHeader("App & Network")
                SectionCard {
                    PreferenceItem(
                        title = "Apps",
                        subtitle = packages.ifEmpty { "All apps" },
                        onClick = { /* open app list */ },
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("SSID") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = networkType,
                        onValueChange = { networkType = it },
                        label = { Text("Network Type") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                }
            }
        }
    }
}
