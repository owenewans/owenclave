package io.nekohasekai.sagernet.ui.compose.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
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
    var menuGroup by remember { mutableStateOf<ProxyGroup?>(null) }
    var deleteGroup by remember { mutableStateOf<ProxyGroup?>(null) }
    var clearGroup by remember { mutableStateOf<ProxyGroup?>(null) }

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
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            title = { Text("Delete group") },
            text = { Text("Are you sure you want to delete \"${deleteGroup!!.displayName()}\" and all its profiles?") },
            confirmButton = {
                TextButton(onClick = {
                    val g = deleteGroup!!
                    deleteGroup = null
                    scope.launch(Dispatchers.IO) {
                        GroupManager.deleteGroup(g.id)
                        withContext(Dispatchers.Main) { reloadGroups() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteGroup = null }) { Text("Cancel") } },
        )
    }

    if (clearGroup != null) {
        AlertDialog(
            onDismissRequest = { clearGroup = null },
            title = { Text("Clear profiles") },
            text = { Text("Remove all profiles from \"${clearGroup!!.displayName()}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val g = clearGroup!!
                    clearGroup = null
                    scope.launch(Dispatchers.IO) {
                        GroupManager.clearGroup(g.id)
                        withContext(Dispatchers.Main) { reloadGroups() }
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearGroup = null }) { Text("Cancel") } },
        )
    }

    menuGroup?.let { group ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { menuGroup = null },
        ) {
            if (group.type == GroupType.SUBSCRIPTION) {
                DropdownMenuItem(
                    text = { Text("Copy subscription link") },
                    onClick = {
                        menuGroup = null
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
                    menuGroup = null
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
                    clearGroup = group
                    menuGroup = null
                },
            )
            DropdownMenuItem(
                text = { Text("Delete group") },
                onClick = {
                    deleteGroup = group
                    menuGroup = null
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            )
        }
    }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Groups",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                actions = {
                    IconButton(onClick = { /* update all subscriptions */ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Update all")
                    }
                    IconButton(onClick = {
                        DataStore.groupName = ""
                        DataStore.groupType = GroupType.BASIC
                        context.startActivity(
                            Intent(context, ComposeGroupSettingsActivity::class.java)
                        )
                    }) {
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
                groups.isEmpty() -> EmptyState(
                    message = "No groups. Tap + to create one.",
                    icon = Icons.Filled.Add,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
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
                            onMenu = { menuGroup = group },
                            onUpdate = if (group.type == GroupType.SUBSCRIPTION) {
                                { /* update subscription */ }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
