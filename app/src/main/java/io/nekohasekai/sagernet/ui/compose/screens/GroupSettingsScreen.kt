package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.PreferenceItem
import io.nekohasekai.sagernet.ui.compose.components.SectionCard
import io.nekohasekai.sagernet.ui.compose.components.SwitchPreferenceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupName: String,
    groupType: Int,
    onBack: () -> Unit,
    onSave: (name: String, type: Int) -> Unit,
) {
    var name by remember { mutableStateOf(groupName) }
    var type by remember { mutableStateOf(groupType) }
    var order by remember { mutableStateOf(0) }
    var dedup by remember { mutableStateOf(false) }
    var autoUpdate by remember { mutableStateOf(false) }
    var subscriptionLink by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var updateWhenConnectedOnly by remember { mutableStateOf(false) }
    var frontProxy by remember { mutableStateOf("") }
    var landingProxy by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Group Settings",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    Button(onClick = { onSave(name, type) }) { Text("Save") }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                PreferenceHeader("General")
                SectionCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Group Type",
                        subtitle = if (type == GroupType.BASIC) "Basic" else "Subscription",
                        onClick = { type = if (type == GroupType.BASIC) GroupType.SUBSCRIPTION else GroupType.BASIC },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Order",
                        subtitle = when (order) {
                            GroupOrder.ORIGIN -> "Origin"
                            GroupOrder.BY_NAME -> "By Name"
                            GroupOrder.BY_DELAY -> "By Delay"
                            else -> "Origin"
                        },
                        onClick = { order = (order + 1) % 3 },
                    )
                }

                if (type == GroupType.SUBSCRIPTION) {
                    PreferenceHeader("Subscription")
                    SectionCard {
                        OutlinedTextField(
                            value = subscriptionLink,
                            onValueChange = { subscriptionLink = it },
                            label = { Text("Subscription Link") },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            singleLine = true,
                        )
                        DividerItem()
                        SwitchPreferenceItem(
                            title = "Deduplication",
                            checked = dedup,
                            onCheckedChange = { dedup = it },
                        )
                        DividerItem()
                        SwitchPreferenceItem(
                            title = "Update When Connected Only",
                            checked = updateWhenConnectedOnly,
                            onCheckedChange = { updateWhenConnectedOnly = it },
                        )
                        DividerItem()
                        SwitchPreferenceItem(
                            title = "Auto Update",
                            checked = autoUpdate,
                            onCheckedChange = { autoUpdate = it },
                        )
                        DividerItem()
                        OutlinedTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = { Text("User Agent") },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            singleLine = true,
                        )
                    }
                }

                PreferenceHeader("Chain")
                SectionCard {
                    PreferenceItem(
                        title = "Front Proxy",
                        subtitle = frontProxy.ifEmpty { "None" },
                        onClick = { /* select front proxy */ },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Landing Proxy",
                        subtitle = landingProxy.ifEmpty { "None" },
                        onClick = { /* select landing proxy */ },
                    )
                }
            }
        }
    }
}
