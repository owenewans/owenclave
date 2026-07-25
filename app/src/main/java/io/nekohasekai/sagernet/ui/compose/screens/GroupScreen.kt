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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ui.compose.ComposeGroupSettingsActivity
import io.nekohasekai.sagernet.ui.compose.components.EmptyState
import io.nekohasekai.sagernet.ui.compose.components.GroupCard
import io.nekohasekai.sagernet.ui.compose.components.LoadingState
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.shapeForSeed
import io.nekohasekai.sagernet.ui.compose.components.ShapedIconStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupScreen(
    onMenuClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var groups by remember { mutableStateOf<List<ProxyGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedGroupId by remember { mutableStateOf(DataStore.selectedGroup) }
    var deleteGroup by remember { mutableStateOf<ProxyGroup?>(null) }
    var clearGroup by remember { mutableStateOf<ProxyGroup?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddUrl by remember { mutableStateOf("") }

    fun reloadGroups() {
        scope.launch(Dispatchers.IO) {
            groups = SagerDatabase.groupDao.allGroups()
            loading = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadGroups()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (deleteGroup != null) {
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { deleteGroup = null }) {
            Text(
                text = "Delete group",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text("Are you sure you want to delete \"${deleteGroup!!.displayName()}\" and all its profiles?")
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { deleteGroup = null }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val g = deleteGroup!!
                    deleteGroup = null
                    scope.launch(Dispatchers.IO) {
                        GroupManager.deleteGroup(g.id)
                        withContext(Dispatchers.Main) { reloadGroups() }
                    }
                }) { Text("Delete") }
            }
        }
    }

    if (clearGroup != null) {
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { clearGroup = null }) {
            Text(
                text = "Clear profiles",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text("Remove all profiles from \"${clearGroup!!.displayName()}\"?")
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { clearGroup = null }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val g = clearGroup!!
                    clearGroup = null
                    scope.launch(Dispatchers.IO) {
                        GroupManager.clearGroup(g.id)
                        withContext(Dispatchers.Main) { reloadGroups() }
                    }
                }) { Text("Clear") }
            }
        }
    }

    if (showAddPicker) {
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { showAddPicker = false }) {
            Text(
                text = "Add",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Create a new group or add a subscription",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val addOptions = listOf(
                "New group" to { showAddPicker = false; DataStore.groupName = ""; DataStore.groupType = GroupType.BASIC; context.startActivity(Intent(context, ComposeGroupSettingsActivity::class.java)) },
                "Add subscription from URL" to { showAddPicker = false; showQuickAdd = true },
                "Import from clipboard" to {
                    showAddPicker = false
                    val clip = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(context).toString()
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (text.startsWith("owenkey://", ignoreCase = true)) {
                                    val import = io.nekohasekai.sagernet.ktx.parseOwenkeyLink(text.trim())
                                    if (import != null) {
                                GroupManager.createGroup(import.group)
                                import.profiles.forEach { profile ->
                                    profile.id = 0
                                    profile.groupId = import.group.id
                                    profile.userOrder = SagerDatabase.proxyDao.nextOrder(import.group.id) ?: 1
                                    profile.id = SagerDatabase.proxyDao.addProxy(profile)
                                }
                                        if (import.group.type == GroupType.SUBSCRIPTION && import.group.subscription?.link?.isNotEmpty() == true) {
                                            val created = SagerDatabase.groupDao.getById(import.group.id)
                                            if (created != null) {
                                                GroupUpdater.executeUpdate(created, true)
                                            }
                                        }
                                    }
                                } else {
                                    val beans = io.nekohasekai.sagernet.ktx.parseShareLinks(text)
                                    if (beans.isNotEmpty()) {
                                        val groupId = DataStore.selectedGroupForImport()
                                        beans.forEach { bean ->
                                            io.nekohasekai.sagernet.database.ProfileManager.createProfile(groupId, bean)
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "No valid share links found in clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, e.message ?: "Import failed", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                            withContext(Dispatchers.Main) { reloadGroups() }
                        }
                    }
                },
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .clip(RoundedCornerShape(24.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                addOptions.forEachIndexed { index, (name, action) ->
                    Surface(
                        onClick = action,
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ShapedIconStatic(
                                icon = if (index == 0) Icons.Filled.Folder else if (index == 1) Icons.Filled.Link else Icons.Filled.ContentPaste,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 40.dp,
                                shape = shapeForSeed(name),
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
                TextButton(onClick = { showAddPicker = false }) { Text("Cancel") }
            }
        }
    }

    if (showQuickAdd) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        androidx.compose.runtime.LaunchedEffect(showQuickAdd) {
            if (showQuickAdd && quickAddUrl.isBlank()) {
                val clip = clipboardManager.getText()
                if (clip != null) {
                    val text = clip.text
                    if (text != null && (text.startsWith("https://", ignoreCase = true) || text.startsWith("http://", ignoreCase = true) || text.startsWith("owenkey://", ignoreCase = true))) {
                        quickAddUrl = text.trim()
                    }
                }
            }
        }
        io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = { showQuickAdd = false; quickAddUrl = "" }) {
            Text(
                text = "Add subscription",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Paste subscription link or owenkey:// link to import",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                value = quickAddUrl,
                onValueChange = { quickAddUrl = it },
                label = "Subscription URL",
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showQuickAdd = false; quickAddUrl = "" }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = quickAddUrl.isNotBlank(),
                    onClick = {
                        val url = quickAddUrl.trim()
                        showQuickAdd = false
                        quickAddUrl = ""
                        scope.launch(Dispatchers.IO) {
                            if (url.startsWith("owenkey://", ignoreCase = true)) {
                                val import = io.nekohasekai.sagernet.ktx.parseOwenkeyLink(url)
                                if (import != null) {
                                    GroupManager.createGroup(import.group)
                                    import.profiles.forEach { profile ->
                                        profile.id = 0
                                        profile.groupId = import.group.id
                                        profile.userOrder = SagerDatabase.proxyDao.nextOrder(import.group.id) ?: 1
                                        profile.id = SagerDatabase.proxyDao.addProxy(profile)
                                    }
                                    if (import.group.type == GroupType.SUBSCRIPTION && import.group.subscription?.link?.isNotEmpty() == true) {
                                        val created = SagerDatabase.groupDao.getById(import.group.id)
                                        if (created != null) {
                                            GroupUpdater.executeUpdate(created, true)
                                        }
                                    }
                                }
                            } else {
                                try {
                                    val group = ProxyGroup(
                                        name = "Subscription",
                                        type = GroupType.SUBSCRIPTION,
                                    )
                                    group.subscription = SubscriptionBean().applyDefaultValues().apply {
                                        link = url
                                        type = SubscriptionType.RAW
                                    }
                                    GroupManager.createGroup(group)
                                    val created = SagerDatabase.groupDao.getById(group.id)
                                    if (created != null) {
                                        GroupUpdater.executeUpdate(created, true)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, e.message ?: "Import failed", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) { reloadGroups() }
                        }
                    },
                ) { Text("Add & Download") }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Groups",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val subs = SagerDatabase.groupDao.subscriptions()
                                    .filter { it.subscription?.autoUpdate == true }
                                subs.forEach { GroupUpdater.startUpdate(it, true) }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Update all")
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = { showAddPicker = true },
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(22.dp))
                        }
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
                groups.isEmpty() -> EmptyState(
                    message = "No groups. Tap + to create one.",
                    icon = Icons.Filled.Add,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = io.nekohasekai.sagernet.ui.compose.components.StatsBarBottomInset),
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            selected = group.id == selectedGroupId,
                            profileCount = SagerDatabase.proxyDao.countByGroup(group.id).toInt(),
                            modifier = Modifier.animateItem(),
                            onClick = {
                                selectedGroupId = group.id
                                DataStore.selectedGroup = group.id
                            },
                            onEdit = {
                                DataStore.editingGroup = group.id
                                DataStore.groupName = group.name ?: ""
                                DataStore.groupType = group.type
                                context.startActivity(
                                    Intent(context, ComposeGroupSettingsActivity::class.java).apply {
                                        putExtra("groupId", group.id)
                                        putExtra("groupName", group.name)
                                        putExtra("groupType", group.type)
                                    }
                                )
                            },
                            onUpdate = if (group.type == GroupType.SUBSCRIPTION) {
                                {
                                    scope.launch(Dispatchers.IO) {
                                        val g = SagerDatabase.groupDao.getById(group.id)
                                        if (g != null) {
                                            GroupUpdater.startUpdate(g, true)
                                        }
                                    }
                                }
                            } else null,
                            menuItems = { menuScope ->
                                if (group.type == GroupType.SUBSCRIPTION) {
                                    DropdownMenuItem(
                                        text = { Text("Copy subscription link") },
                                        onClick = {
                                            menuScope.dismiss()
                                            val link = group.subscription?.link ?: ""
                                            if (link.isNotEmpty()) {
                                                SagerNet.trySetPrimaryClip(link)
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Export group to clipboard") },
                                    onClick = {
                                        menuScope.dismiss()
                                        scope.launch(Dispatchers.IO) {
                                            val g = SagerDatabase.groupDao.getById(group.id)
                                            if (g != null) {
                                                val link = io.nekohasekai.sagernet.ktx.groupToOwenkeyLink(g)
                                                if (link != null) {
                                                    withContext(Dispatchers.Main) {
                                                        SagerNet.trySetPrimaryClip(link)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export profiles to clipboard") },
                                    onClick = {
                                        menuScope.dismiss()
                                        scope.launch(Dispatchers.IO) {
                                            val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
                                            val links = profiles.mapNotNull {
                                                try { it.toLink() } catch (_: Exception) { null }
                                            }.joinToString("\n")
                                            withContext(Dispatchers.Main) {
                                                SagerNet.trySetPrimaryClip(links)
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear profiles") },
                                    onClick = {
                                        menuScope.dismiss()
                                        clearGroup = group
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete group") },
                                    onClick = {
                                        menuScope.dismiss()
                                        deleteGroup = group
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
