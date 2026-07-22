package io.nekohasekai.sagernet.ui.compose.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    var groups by remember { mutableStateOf<List<ProxyGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedGroupId by remember { mutableStateOf(DataStore.selectedGroup) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            groups = SagerDatabase.groupDao.allGroups()
            loading = false
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
                            onClick = {
                                selectedGroupId = group.id
                                DataStore.selectedGroup = group.id
                            },
                            onEdit = {
                                context.startActivity(
                                    Intent(context, ComposeGroupSettingsActivity::class.java).apply {
                                        putExtra("groupId", group.id)
                                        putExtra("groupName", group.name)
                                        putExtra("groupType", group.type)
                                    }
                                )
                            },
                            onMenu = { /* show popup menu */ },
                            onUpdate = if (group.type == io.nekohasekai.sagernet.GroupType.SUBSCRIPTION) {
                                { /* update subscription */ }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
