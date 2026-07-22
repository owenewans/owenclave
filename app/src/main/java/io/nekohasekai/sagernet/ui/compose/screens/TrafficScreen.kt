package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.ui.compose.components.EmptyState
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(
    onMenuClick: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Connections", "Traffic")

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Traffic",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onMenuClick,
                actions = {
                    IconButton(onClick = { /* clear stats */ }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            androidx.compose.foundation.layout.Column {
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
                    0 -> EmptyState(message = "No active connections", icon = Icons.Filled.Menu)
                    1 -> EmptyState(message = "No traffic statistics", icon = Icons.Filled.DeleteSweep)
                }
            }
        }
    }
}
