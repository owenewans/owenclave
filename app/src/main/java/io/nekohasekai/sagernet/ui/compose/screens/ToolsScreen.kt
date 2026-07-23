package io.nekohasekai.sagernet.ui.compose.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.ui.compose.ComposeProbeCertActivity
import io.nekohasekai.sagernet.ui.compose.ComposeStunActivity
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onMenuClick: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    val tabs = listOf("Backup", "Network", "Debug")

    if (showResetDialog) {
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { showResetDialog = false }) {
            Text(
                text = "Reset Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text("Are you sure you want to reset all settings to defaults? The app will restart.")
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    showResetDialog = false
                    io.nekohasekai.sagernet.SagerNet.stopService()
                    io.nekohasekai.sagernet.ui.compose.BackupUtil.resetSettings()
                    com.jakewharton.processphoenix.ProcessPhoenix.triggerRebirth(
                        context,
                        Intent(context, io.nekohasekai.sagernet.ui.compose.ComposeMainActivity::class.java),
                    )
                }) { Text("Reset") }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Tools",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                scrollBehavior = scrollBehavior,
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
                    1 -> NetworkTab(
                        onStunTest = { context.startActivity(Intent(context, ComposeStunActivity::class.java)) },
                        onProbeCert = { context.startActivity(Intent(context, ComposeProbeCertActivity::class.java)) },
                    )
                    2 -> DebugTab(onReset = { showResetDialog = true })
                }
            }
        }
    }
}

@Composable
private fun BackupTab() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var backupConfig by remember { mutableStateOf(true) }
    var backupRules by remember { mutableStateOf(true) }
    var backupSettings by remember { mutableStateOf(true) }

    // Content produced on demand and consumed by the export/save launcher.
    var pendingContent by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<com.google.gson.JsonObject?>(null) }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use {
                        it.write(pendingContent)
                    }
                    withContext(kotlinx.coroutines.Dispatchers.Main) { toast("Exported") }
                } catch (e: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        toast(e.message ?: "Export failed")
                    }
                }
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val text = try {
                    (context.contentResolver.openInputStream(uri) ?: return@launch).use {
                        it.bufferedReader().readText()
                    }
                } catch (_: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) { toast("Cannot read file") }
                    return@launch
                }
                val parsed = io.nekohasekai.sagernet.ui.compose.BackupUtil.parseBackup(text)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (parsed == null) toast("Invalid backup file")
                    else pendingImport = parsed
                }
            }
        }
    }

    // Import confirmation dialog.
    pendingImport?.let { content ->
        val hasProfiles = content.has("profiles")
        val hasRules = content.has("rules")
        val hasSettings = content.has("settings")
        var impProfiles by remember { mutableStateOf(true) }
        var impRules by remember { mutableStateOf(true) }
        var impSettings by remember { mutableStateOf(true) }
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { pendingImport = null }) {
            Text(
                text = "Import Backup",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "Existing data for the selected sections will be replaced. The app will restart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (hasProfiles) BackupItem(label = "Configurations", checked = impProfiles, onCheckedChange = { impProfiles = it })
            if (hasRules) BackupItem(label = "Rules", checked = impRules, onCheckedChange = { impRules = it })
            if (hasSettings) BackupItem(label = "Settings", checked = impSettings, onCheckedChange = { impSettings = it })
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    pendingImport = null
                    io.nekohasekai.sagernet.SagerNet.stopService()
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            io.nekohasekai.sagernet.ui.compose.BackupUtil.finishImport(
                                content, impProfiles, impRules, impSettings,
                            )
                        }
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            com.jakewharton.processphoenix.ProcessPhoenix.triggerRebirth(
                                context,
                                Intent(context, io.nekohasekai.sagernet.ui.compose.ComposeMainActivity::class.java),
                            )
                        }
                    }
                }) { Text("Import") }
            }
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    pendingContent = io.nekohasekai.sagernet.ui.compose.BackupUtil.doBackup(
                        backupConfig, backupRules, backupSettings,
                    )
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        exportLauncher.launch("owenclave_backup_${System.currentTimeMillis()}.json")
                    }
                }
            }) { Text("Export") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val content = io.nekohasekai.sagernet.ui.compose.BackupUtil.doBackup(
                        backupConfig, backupRules, backupSettings,
                    )
                    val app = io.nekohasekai.sagernet.SagerNet.application
                    app.cacheDir.mkdirs()
                    val cacheFile = java.io.File(app.cacheDir, "owenclave_backup_${System.currentTimeMillis()}.json")
                    cacheFile.writeText(content)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            app, io.nekohasekai.sagernet.BuildConfig.APPLICATION_ID + ".cache", cacheFile,
                        )
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("application/json")
                                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(Intent.EXTRA_STREAM, uri),
                                "Share",
                            )
                        )
                    }
                }
            }) { Text("Share") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { importLauncher.launch("*/*") }) { Text("Import") }
        }
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
private fun NetworkTab(
    onStunTest: () -> Unit,
    onProbeCert: () -> Unit,
) {
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
                Button(onClick = onStunTest) { Text("Start") }
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
                Button(onClick = onProbeCert) { Text("Start") }
            }
        }
    }
}

@Composable
private fun DebugTab(onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = onReset,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
        ) {
            Text("Reset Settings")
        }
    }
}
