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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
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

    if (showQuickAdd) {
        AlertDialog(
            onDismissRequest = { showQuickAdd = false; quickAddUrl = "" },
            title = { Text("Add subscription") },
            text = {
                Column {
                    Text(
                        text = "Paste subscription link to download and add automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    OutlinedTextField(
                        value = quickAddUrl,
                        onValueChange = { quickAddUrl = it },
                        label = { Text("Subscription URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = quickAddUrl.isNotBlank(),
                    onClick = {
                        val url = quickAddUrl.trim()
                        showQuickAdd = false
                        quickAddUrl = ""
                        scope.launch(Dispatchers.IO) {
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
                            withContext(Dispatchers.Main) { reloadGroups() }
                        }
                    },
                ) { Text("Add & Download") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAdd = false; quickAddUrl = "" }) { Text("Cancel") }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Groups",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val subs = SagerDatabase.groupDao.subscriptions()
                                .filter { it.subscription?.autoUpdate == true }
                            subs.forEach { GroupUpdater.startUpdate(it, true) }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Update all")
                    }
                    var addMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { addMenuExpanded = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("New group") },
                                onClick = {
                                    addMenuExpanded = false
                                    DataStore.groupName = ""
                                    DataStore.groupType = GroupType.BASIC
                                    context.startActivity(
                                        Intent(context, ComposeGroupSettingsActivity::class.java)
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add subscription from URL") },
                                onClick = {
                                    addMenuExpanded = false
                                    showQuickAdd = true
                                },
                            )
                        }
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
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear profiles") },
                                    onClick = {
                                        menuScope.dismiss()
                                        clearGroup = group
                                    },
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
