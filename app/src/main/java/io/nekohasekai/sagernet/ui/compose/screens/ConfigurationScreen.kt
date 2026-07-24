package io.nekohasekai.sagernet.ui.compose.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.draw.clip
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
import io.nekohasekai.sagernet.ui.compose.components.EmptyState
import io.nekohasekai.sagernet.ui.compose.components.LoadingState
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.ProfileCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfigurationScreen(
    onMenuClick: () -> Unit,
    serviceRunning: Boolean = false,
    serviceConnected: Boolean = false,
    batchTestProgress: Pair<Int, Int>? = null,
    onBatchTestProgress: (Pair<Int, Int>?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var profiles by remember { mutableStateOf<List<ProxyEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedProfileId by remember { mutableStateOf(0L) }
    var showProtocolPicker by remember { mutableStateOf(false) }
    var pingingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDinoGame by remember { mutableStateOf(false) }
    val reloadAccess = remember { kotlinx.coroutines.sync.Mutex() }

    LaunchedEffect(Unit) {
        selectedProfileId = withContext(Dispatchers.IO) { DataStore.selectedProxy }
    }

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
            var anyWorking = false
            val link = DataStore.connectionTestURL
            val timeout = DataStore.connectionTestTimeout
            withContext(Dispatchers.Main) { onBatchTestProgress(Pair(0, total)) }
            for (entity in toTest) {
                withContext(Dispatchers.Main) { pingingIds = pingingIds + entity.id }
                try {
                    val instance = io.nekohasekai.sagernet.bg.test.V2RayTestInstance(entity, link, timeout)
                    val result = instance.use { it.doTest() }
                    entity.ping = result
                    entity.status = 1
                    entity.error = null
                    if (result > 0) anyWorking = true
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
                    onBatchTestProgress(Pair(done, total))
                }
            }
            withContext(Dispatchers.Main) {
                onBatchTestProgress(null)
                // If not a single server responded, cheer the user up with a game.
                if (!anyWorking) showDinoGame = true
            }
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

    if (showDinoGame) {
        io.nekohasekai.sagernet.ui.compose.components.DinoGameDialog(
            onDismiss = { showDinoGame = false }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Configuration",
                scrollBehavior = scrollBehavior,
                actions = {
                    // Only offer "test all" when there is at least one profile to test.
                    if (profiles.isNotEmpty()) {
                        IconButton(
                            onClick = { batchUrlTest() },
                            enabled = batchTestProgress == null,
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = "Test all")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = { importFromClipboard() },
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Import subscription from clipboard")
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { showProtocolPicker = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add profile")
                    }
                    Spacer(Modifier.width(8.dp))
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
                    // Keep the LazyColumn always composed (even when empty) so that
                    // adding/removing the only profile animates the card in/out via
                    // animateItem() instead of hard-swapping the whole screen.
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 8.dp,
                            bottom = io.nekohasekai.sagernet.ui.compose.components.StatsBarBottomInset,
                        ),
                    ) {
                        if (profiles.isEmpty()) {
                            item(key = "__empty__") {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .animateItem(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    EmptyState(
                                        message = "No profiles. Tap + to add one.",
                                        icon = Icons.Filled.Add,
                                    )
                                }
                            }
                        }
                        items(profiles, key = { it.id }) { entity ->
                        ProfileCard(
                            entity = entity,
                            selected = entity.id == selectedProfileId,
                            connected = entity.id == selectedProfileId && serviceConnected,
                            pinging = entity.id in pingingIds,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                val entity = entity
                                io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher {
                                    val update = selectedProfileId != entity.id
                                    if (!update) return@runOnDefaultDispatcher
                                    DataStore.selectedProxy = entity.id
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        selectedProfileId = entity.id
                                    }
                                    if (serviceRunning && reloadAccess.tryLock()) {
                                        try {
                                            SagerNet.reloadService()
                                        } finally {
                                            reloadAccess.unlock()
                                        }
                                    }
                                }
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
                                    scope.launch(Dispatchers.IO) {
                                        val link = entity.toLink()
                                        if (link != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, link)
                                            }
                                            withContext(Dispatchers.Main) {
                                                context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                            }
                                        }
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
                                        val timeout = DataStore.connectionTestTimeout
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

    io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = "New profile",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Choose a protocol",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(protocols.size) { index ->
                val (name, type) = protocols[index]
                // Uniform small radius: the outer LazyColumn clip (24dp) defines the
                // overall rounded group, so the first/last items no longer get their
                // corners harshly squared off by the scroll clip.
                androidx.compose.material3.Surface(
                    onClick = { onSelect(type) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        io.nekohasekai.sagernet.ui.compose.components.ShapedIconStatic(
                            icon = Icons.Filled.Add,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 40.dp,
                            shape = io.nekohasekai.sagernet.ui.compose.components.shapeForSeed(name),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}
