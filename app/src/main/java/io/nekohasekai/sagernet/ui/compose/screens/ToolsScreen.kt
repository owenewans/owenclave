package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onMenuClick: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Backup", "Network", "Debug")

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Tools",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }

                when (selectedTab) {
                    0 -> BackupTab()
                    1 -> NetworkTab()
                    2 -> DebugTab()
                }
            }
        }
    }
}

@Composable
private fun BackupTab() {
    var backupConfig by remember { mutableStateOf(true) }
    var backupRules by remember { mutableStateOf(true) }
    var backupSettings by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Select items to export:",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))

        BackupItem(label = "Configurations", checked = backupConfig, onCheckedChange = { backupConfig = it })
        BackupItem(label = "Rules", checked = backupRules, onCheckedChange = { backupRules = it })
        BackupItem(label = "Settings", checked = backupSettings, onCheckedChange = { backupSettings = it })

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            Button(onClick = { /* export */ }) { Text("Export") }
            OutlinedButton(onClick = { /* share */ }) { Text("Share") }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { /* import */ }) { Text("Import from file") }
    }
}

@Composable
private fun BackupItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.padding(start = 16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NetworkTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("STUN Test", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Test NAT type and behavior using STUN protocol",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { /* start STUN test */ }) { Text("Start") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Probe Certificate", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Inspect TLS/SSL certificate of a remote server",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { /* start cert probe */ }) { Text("Start") }
            }
        }
    }
}

@Composable
private fun DebugTab() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = { /* reset settings */ },
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
        ) {
            Text("Reset Settings")
        }
    }
}
