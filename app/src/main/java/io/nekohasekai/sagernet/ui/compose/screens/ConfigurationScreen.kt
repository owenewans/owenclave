package io.nekohasekai.sagernet.ui.compose.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.parseShareLinks
import io.nekohasekai.sagernet.ui.compose.ComposeProfileSettingsActivity
import io.nekohasekai.sagernet.ui.compose.ComposeScannerActivity
import io.nekohasekai.sagernet.ui.compose.components.EmptyState
import io.nekohasekai.sagernet.ui.compose.components.LoadingState
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.ProfileCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    onMenuClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var profiles by remember { mutableStateOf<List<ProxyEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedProfileId by remember { mutableStateOf(DataStore.selectedProxy) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showProtocolPicker by remember { mutableStateOf(false) }
    var pingingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var batchTestProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun reloadProfiles() {
        scope.launch(Dispatchers.IO) {
            val groupId = DataStore.currentGroupId()
            profiles = SagerDatabase.proxyDao.getByGroup(groupId)
            loading = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadProfiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun batchUrlTest() {
        scope.launch(Dispatchers.IO) {
            val groupId = DataStore.currentGroupId()
            val toTest = profiles.filter {
                it.type != ProxyEntity.TYPE_CHAIN && it.type != ProxyEntity.TYPE_BALANCER && it.type != ProxyEntity.TYPE_CONFIG
            }
            if (toTest.isEmpty()) return@launch
            val total = toTest.size
            var done = 0
            val link = DataStore.connectionTestURL
            val timeout = 5000
            withContext(Dispatchers.Main) { batchTestProgress = Pair(0, total) }
            for (entity in toTest) {
                withContext(Dispatchers.Main) { pingingIds = pingingIds + entity.id }
                try {
                    val instance = io.nekohasekai.sagernet.bg.test.V2RayTestInstance(entity, link, timeout)
                    val result = instance.use { it.doTest() }
                    entity.ping = result
                    entity.status = 1
                    entity.error = null
                } catch (e: Exception) {
                    entity.ping = -1
                    entity.status = 3
                    entity.error = e.message
                }
                SagerDatabase.proxyDao.updateProxy(entity)
                done++
                withContext(Dispatchers.Main) {
                    pingingIds = pingingIds - entity.id
                    profiles = profiles.map { if (it.id == entity.id) entity else it }
                    batchTestProgress = Pair(done, total)
                }
            }
            withContext(Dispatchers.Main) { batchTestProgress = null }
        }
    }

    fun importFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val beans = parseShareLinks(text)
                if (beans.isNotEmpty()) {
                    val groupId = DataStore.selectedGroupForImport()
                    ProfileManager.createProfile(groupId, beans[0])
                    withContext(Dispatchers.Main) { reloadProfiles() }
                }
            } catch (_: Exception) {
            }
        }
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Add Profile") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showAddMenu = false
                            showProtocolPicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Manual Input")
                    }
                    TextButton(
                        onClick = {
                            showAddMenu = false
                            importFromClipboard()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Import from Clipboard")
                    }
                    TextButton(
                        onClick = {
                            showAddMenu = false
                            context.startActivity(Intent(context, ComposeScannerActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Scan QR Code")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMenu = false }) { Text("Cancel") }
            },
        )
    }

    if (showProtocolPicker) {
        ProtocolPickerDialog(
            onSelect = { type ->
                showProtocolPicker = false
                context.startActivity(
                    Intent(context, ComposeProfileSettingsActivity::class.java).apply {
                        putExtra(ComposeProfileSettingsActivity.EXTRA_PROFILE_TYPE, type)
                    }
                )
            },
            onDismiss = { showProtocolPicker = false }
        )
    }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Configuration",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                actions = {
                    IconButton(onClick = { batchUrlTest() }, enabled = batchTestProgress == null && profiles.isNotEmpty()) {
                        Icon(Icons.Filled.NetworkCheck, contentDescription = "Test all")
                    }
                    IconButton(onClick = { importFromClipboard() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Import from clipboard")
                    }
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                },
            )
        },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    loading -> LoadingState()
                    profiles.isEmpty() -> EmptyState(
                        message = "No profiles. Tap + to add one.",
                        icon = Icons.Filled.Add,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = if (batchTestProgress != null) 48.dp else 8.dp,
                            bottom = 8.dp,
                        ),
                    ) {
                        if (batchTestProgress != null) {
                            val (done, total) = batchTestProgress!!
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    Text(
                                        text = "Testing $done/$total",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LinearProgressIndicator(
                                        progress = { if (total > 0) done.toFloat() / total else 0f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        items(profiles, key = { it.id }) { entity ->
                        ProfileCard(
                            entity = entity,
                            selected = entity.id == selectedProfileId,
                            pinging = entity.id in pingingIds,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                selectedProfileId = entity.id
                                DataStore.selectedProxy = entity.id
                                SagerNet.reloadService()
                            },
                            onEdit = {
                                context.startActivity(
                                    Intent(context, ComposeProfileSettingsActivity::class.java).apply {
                                        putExtra(ComposeProfileSettingsActivity.EXTRA_PROFILE_ID, entity.id)
                                        putExtra(ComposeProfileSettingsActivity.EXTRA_PROFILE_TYPE, entity.type)
                                    }
                                )
                            },
                            onShare = if (entity.hasShareLink()) {
                                {
                                    val link = entity.toLink()
                                    if (link != null) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, link)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                    }
                                }
                            } else null,
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    val groupId = DataStore.currentGroupId()
                                    ProfileManager.deleteProfile(groupId, entity.id)
                                    withContext(Dispatchers.Main) {
                                        profiles = SagerDatabase.proxyDao.getByGroup(groupId)
                                    }
                                }
                            },
                            onPing = {
                                scope.launch(Dispatchers.IO) {
                                    pingingIds = pingingIds + entity.id
                                    try {
                                        val link = DataStore.connectionTestURL
                                        val timeout = 5000
                                        val instance = io.nekohasekai.sagernet.bg.test.V2RayTestInstance(
                                            entity, link, timeout
                                        )
                                        val result = instance.use { it.doTest() }
                                        entity.ping = result
                                        entity.status = 1
                                        entity.error = null
                                        SagerDatabase.proxyDao.updateProxy(entity)
                                        withContext(Dispatchers.Main) {
                                            profiles = profiles.map { if (it.id == entity.id) entity else it }
                                        }
                                    } catch (e: Exception) {
                                        entity.ping = -1
                                        entity.status = 3
                                        entity.error = e.message
                                        SagerDatabase.proxyDao.updateProxy(entity)
                                        withContext(Dispatchers.Main) {
                                            profiles = profiles.map { if (it.id == entity.id) entity else it }
                                        }
                                    } finally {
                                        pingingIds = pingingIds - entity.id
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolPickerDialog(
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val protocols = listOf(
        "SOCKS" to ProxyEntity.TYPE_SOCKS,
        "HTTP" to ProxyEntity.TYPE_HTTP,
        "Shadowsocks" to ProxyEntity.TYPE_SS,
        "ShadowsocksR" to ProxyEntity.TYPE_SSR,
        "VMess" to ProxyEntity.TYPE_VMESS,
        "VLESS" to ProxyEntity.TYPE_VLESS,
        "Trojan" to ProxyEntity.TYPE_TROJAN,
        "Naive" to ProxyEntity.TYPE_NAIVE,
        "Hysteria 2" to ProxyEntity.TYPE_HYSTERIA2,
        "SSH" to ProxyEntity.TYPE_SSH,
        "WireGuard" to ProxyEntity.TYPE_WG,
        "Mieru" to ProxyEntity.TYPE_MIERU,
        "TUIC" to ProxyEntity.TYPE_TUIC5,
        "Juicity" to ProxyEntity.TYPE_JUICITY,
        "HTTP/3" to ProxyEntity.TYPE_HTTP3,
        "AnyTLS" to ProxyEntity.TYPE_ANYTLS,
        "ShadowQUIC" to ProxyEntity.TYPE_SHADOWQUIC,
        "TrustTunnel" to ProxyEntity.TYPE_TRUSTTUNNEL,
        "Snell" to ProxyEntity.TYPE_SNELL,
        "OLCRTC" to ProxyEntity.TYPE_OLCRTC,
        "Chain" to ProxyEntity.TYPE_CHAIN,
        "Balancer" to ProxyEntity.TYPE_BALANCER,
        "Custom Config" to ProxyEntity.TYPE_CONFIG,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Protocol") },
        text = {
            LazyColumn {
                items(protocols) { (name, type) ->
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
