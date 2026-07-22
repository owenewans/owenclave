package io.nekohasekai.sagernet.ui.compose.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ui.compose.ComposeProfileSettingsActivity
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

    var profiles by remember { mutableStateOf<List<ProxyEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedProfileId by remember { mutableStateOf(DataStore.selectedProxy) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val groupId = DataStore.currentGroupId()
            profiles = SagerDatabase.proxyDao.getByGroup(groupId)
            loading = false
        }
    }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Configuration",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                actions = {
                    IconButton(onClick = { /* search */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(context, ComposeProfileSettingsActivity::class.java).apply {
                                putExtra(ComposeProfileSettingsActivity.EXTRA_PROFILE_TYPE, ProxyEntity.TYPE_SOCKS)
                            }
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
                profiles.isEmpty() -> EmptyState(
                    message = "No profiles. Tap + to add one.",
                    icon = Icons.Filled.Add,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(profiles, key = { it.id }) { entity ->
                        ProfileCard(
                            entity = entity,
                            selected = entity.id == selectedProfileId,
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
                        )
                    }
                }
            }
        }
    }
}
