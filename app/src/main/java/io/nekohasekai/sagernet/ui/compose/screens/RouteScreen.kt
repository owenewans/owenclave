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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ui.compose.ComposeRouteSettingsActivity
import io.nekohasekai.sagernet.ui.compose.components.EmptyState
import io.nekohasekai.sagernet.ui.compose.components.LoadingState
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.RouteCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    onMenuClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var rules by remember { mutableStateOf<List<RuleEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun reloadRules() {
        scope.launch(Dispatchers.IO) {
            rules = SagerDatabase.rulesDao.allRules()
            loading = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadRules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Routes",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                actions = {
                    IconButton(onClick = {
                        DataStore.routeName = ""
                        context.startActivity(
                            Intent(context, ComposeRouteSettingsActivity::class.java)
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
                rules.isEmpty() -> EmptyState(
                    message = "No routes. Tap + to add one.",
                    icon = Icons.Filled.Add,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RouteCard(
                            name = rule.displayName(),
                            type = rule.mkSummary().take(50),
                            outbound = rule.displayOutbound(),
                            enabled = rule.enabled,
                            modifier = Modifier.animateItem(),
                            onEnabledChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    rule.enabled = enabled
                                    SagerDatabase.rulesDao.updateRule(rule)
                                }
                            },
                            onEdit = {
                                context.startActivity(
                                    Intent(context, ComposeRouteSettingsActivity::class.java).apply {
                                        putExtra("ruleId", rule.id)
                                    }
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
