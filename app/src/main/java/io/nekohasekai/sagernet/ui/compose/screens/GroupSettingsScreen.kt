package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.PreferenceItem
import io.nekohasekai.sagernet.ui.compose.components.ProfileIconSet
import io.nekohasekai.sagernet.ui.compose.components.SectionCard
import io.nekohasekai.sagernet.ui.compose.components.ShapedIconStatic
import io.nekohasekai.sagernet.ui.compose.components.SwitchPreferenceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class SubscriptionSettings(
    val link: String = "",
    val deduplication: Boolean = false,
    val updateWhenConnectedOnly: Boolean = false,
    val autoUpdate: Boolean = false,
    val customUserAgent: String = "",
)

data class GroupSettingsData(
    val name: String = "",
    val type: Int = 0,
    val iconIndex: Int = 0,
    val order: Int = 0,
    val frontProxy: Long = -1L,
    val landingProxy: Long = -1L,
    val subscription: SubscriptionSettings = SubscriptionSettings(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupName: String,
    groupType: Int,
    initialIconIndex: Int = 0,
    initialOrder: Int = 0,
    initialFrontProxy: Long = -1L,
    initialLandingProxy: Long = -1L,
    initialSubscription: SubscriptionSettings = SubscriptionSettings(),
    onBack: () -> Unit,
    onSave: (data: GroupSettingsData) -> Unit,
) {
    var name by remember { mutableStateOf(groupName) }
    var type by remember { mutableStateOf(groupType) }
    var iconIndex by remember { mutableIntStateOf(initialIconIndex) }
    var order by remember { mutableIntStateOf(initialOrder) }
    var dedup by remember { mutableStateOf(initialSubscription.deduplication) }
    var autoUpdate by remember { mutableStateOf(initialSubscription.autoUpdate) }
    var subscriptionLink by remember { mutableStateOf(initialSubscription.link) }
    var userAgent by remember { mutableStateOf(initialSubscription.customUserAgent) }
    var updateWhenConnectedOnly by remember { mutableStateOf(initialSubscription.updateWhenConnectedOnly) }
    var frontProxy by remember { mutableStateOf(initialFrontProxy) }
    var landingProxy by remember { mutableStateOf(initialLandingProxy) }
    var showFrontProxyPicker by remember { mutableStateOf(false) }
    var showLandingProxyPicker by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "Group Settings",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    Button(onClick = {
                        onSave(GroupSettingsData(
                            name = name,
                            type = type,
                            iconIndex = iconIndex,
                            order = order,
                            frontProxy = frontProxy,
                            landingProxy = landingProxy,
                            subscription = SubscriptionSettings(
                                link = subscriptionLink,
                                deduplication = dedup,
                                updateWhenConnectedOnly = updateWhenConnectedOnly,
                                autoUpdate = autoUpdate,
                                customUserAgent = userAgent,
                            ),
                        ))
                    }) { Text("Save") }
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
                    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Group Name",
                        modifier = Modifier.padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    GroupIconPicker(
                        selected = iconIndex,
                        onSelect = { iconIndex = it },
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
                            GroupOrder.BY_DELAY -> "By Delay (fastest on top)"
                            else -> "Origin"
                        },
                        onClick = { order = (order + 1) % 3 },
                    )
                }

                if (type == GroupType.SUBSCRIPTION) {
                    PreferenceHeader("Subscription")
                    SectionCard {
                        io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                            value = subscriptionLink,
                            onValueChange = { subscriptionLink = it },
                            label = "Subscription Link",
                            modifier = Modifier.padding(16.dp),
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
                        io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = "User Agent",
                            modifier = Modifier.padding(16.dp),
                            singleLine = true,
                        )
                    }
                }

                PreferenceHeader("Chain")
                SectionCard {
                    PreferenceItem(
                        title = "Front Proxy",
                        subtitle = if (frontProxy > 0) {
                            runBlocking { SagerDatabase.proxyDao.getById(frontProxy)?.displayName() } ?: "None"
                        } else {
                            "None"
                        },
                        onClick = { showFrontProxyPicker = true },
                    )
                    DividerItem()
                    PreferenceItem(
                        title = "Landing Proxy",
                        subtitle = if (landingProxy > 0) {
                            runBlocking { SagerDatabase.proxyDao.getById(landingProxy)?.displayName() } ?: "None"
                        } else {
                            "None"
                        },
                        onClick = { showLandingProxyPicker = true },
                    )
                }
            }
        }
    }

    if (showFrontProxyPicker || showLandingProxyPicker) {
        ProxyChainPickerDialog(
            title = if (showFrontProxyPicker) "Front Proxy" else "Landing Proxy",
            selectedId = if (showFrontProxyPicker) frontProxy else landingProxy,
            onSelect = { id ->
                if (showFrontProxyPicker) frontProxy = id else landingProxy = id
                showFrontProxyPicker = false
                showLandingProxyPicker = false
            },
            onDismiss = {
                showFrontProxyPicker = false
                showLandingProxyPicker = false
            },
        )
    }
}

@Composable
private fun ProxyChainPickerDialog(
    title: String,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var profiles by remember { mutableStateOf<List<io.nekohasekai.sagernet.database.ProxyEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            profiles = io.nekohasekai.sagernet.database.SagerDatabase.proxyDao.getAll()
        }
    }
    io.nekohasekai.sagernet.ui.compose.components.ExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val items = listOf<Pair<String, Long>>("None" to -1L) +
                profiles.map { it.displayName() to it.id }
            items.forEach { (label, id) ->
                val isSelected = id == selectedId
                Surface(
                    onClick = { onSelect(id) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun GroupIconPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentIcon = if (selected > 0 && selected - 1 in ProfileIconSet.indices) {
        ProfileIconSet[selected - 1]
    } else {
        Icons.Filled.Folder
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable { expanded = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShapedIconStatic(
                icon = currentIcon,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 40.dp,
            )
            Column {
                Text(
                    text = "Group Icon",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (selected == 0) "Auto" else "Custom",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.width(220.dp).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Surface(
                    onClick = { onSelect(0); expanded = false },
                    shape = if (selected == 0) RoundedCornerShape(12.dp) else CircleShape,
                    color = if (selected == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Auto",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                ProfileIconSet.forEachIndexed { index, icon ->
                    val isSelected = selected == index + 1
                    Surface(
                        onClick = { onSelect(index + 1); expanded = false },
                        shape = if (isSelected) RoundedCornerShape(12.dp) else CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
