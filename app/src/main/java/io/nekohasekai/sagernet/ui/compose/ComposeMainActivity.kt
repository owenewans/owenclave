package io.nekohasekai.sagernet.ui.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.compose.components.ServiceState
import io.nekohasekai.sagernet.ui.compose.screens.ConfigurationScreen
import io.nekohasekai.sagernet.ui.compose.screens.GroupScreen
import io.nekohasekai.sagernet.ui.compose.screens.LogcatScreen
import io.nekohasekai.sagernet.ui.compose.screens.RouteScreen
import io.nekohasekai.sagernet.ui.compose.screens.SettingsScreen
import io.nekohasekai.sagernet.ui.compose.screens.ToolsScreen
import io.nekohasekai.sagernet.ui.compose.screens.TrafficScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    CONFIGURATION("configuration", R.string.menu_configuration, Icons.Filled.Description),
    GROUP("group", R.string.menu_group, Icons.AutoMirrored.Filled.List),
    ROUTE("route", R.string.menu_route, Icons.Filled.Directions),
    LOGCAT("logcat", R.string.menu_log, Icons.Filled.BugReport),
    TRAFFIC("traffic", R.string.menu_traffic, Icons.Filled.Transform),
    TOOLS("tools", R.string.menu_tools, Icons.Filled.Construction),
    SETTINGS("settings", R.string.settings, Icons.Filled.Settings),
}

class ComposeMainActivity : ComponentActivity(), SagerConnection.Callback {

    private val connection = SagerConnection()
    private var serviceState = mutableStateOf(BaseService.State.Idle)
    private var serviceMessage = mutableStateOf<String?>(null)
    private var uplinkSpeed = mutableStateOf("")
    private var downlinkSpeed = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        connection.connect(this, this)

        setContent {
            var themeId by remember { mutableIntStateOf(DataStore.appTheme) }
            var nightTheme by remember { mutableIntStateOf(DataStore.nightTheme) }
            val scrim = remember { io.nekohasekai.sagernet.ui.compose.components.ScrimController() }
            val blurRadius by androidx.compose.animation.core.animateDpAsState(
                targetValue = scrim.blurRadiusDp.dp,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "rootBlur",
            )
            OwenclaveTheme(themeId = themeId, nightTheme = nightTheme) {
                androidx.compose.runtime.CompositionLocalProvider(
                    io.nekohasekai.sagernet.ui.compose.components.LocalScrimController provides scrim,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(blurRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        MainScreen(
                            serviceState = serviceState.value,
                            onServiceToggle = { toggleService() },
                            uplinkSpeed = uplinkSpeed.value,
                            downlinkSpeed = downlinkSpeed.value,
                            onThemeChanged = { newId -> themeId = newId },
                            onNightThemeChanged = { newNight -> nightTheme = newNight },
                            onServiceModeChanged = {
                                val wasRunning = serviceState.value.canStop
                                if (wasRunning) {
                                    SagerNet.stopService()
                                }
                                runOnDefaultDispatcher {
                                    if (wasRunning) delay(500)
                                    connection.disconnect(this@ComposeMainActivity)
                                    connection.connect(this@ComposeMainActivity, this@ComposeMainActivity)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            handleViewIntent(intent)
        }
    }

    private fun handleViewIntent(intent: Intent) {
        val uri = intent.data ?: return
        val link = uri.toString()
        io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher {
            try {
                if (link.startsWith("owenkey://", ignoreCase = true)) {
                    val group = io.nekohasekai.sagernet.ktx.parseOwenkeyLink(link)
                    if (group != null) {
                        io.nekohasekai.sagernet.database.GroupManager.createGroup(group)
                        if (group.type == io.nekohasekai.sagernet.GroupType.SUBSCRIPTION && group.subscription?.link?.isNotEmpty() == true) {
                            val created = io.nekohasekai.sagernet.database.SagerDatabase.groupDao.getById(group.id)
                            if (created != null) {
                                io.nekohasekai.sagernet.group.GroupUpdater.executeUpdate(created, true)
                            }
                        }
                    }
                } else {
                    val beans = io.nekohasekai.sagernet.ktx.parseShareLinks(link)
                    if (beans.isNotEmpty()) {
                        val groupId = io.nekohasekai.sagernet.database.DataStore.selectedGroupForImport()
                        io.nekohasekai.sagernet.database.ProfileManager.createProfile(groupId, beans[0])
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun toggleService() {
        val state = serviceState.value
        when {
            state == BaseService.State.Idle || state == BaseService.State.Stopped ->
                SagerNet.startService()
            state == BaseService.State.Connected ->
                SagerNet.stopService()
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        serviceState.value = state
        serviceMessage.value = msg
    }

    override fun trafficUpdated(profileId: Long, stats: TrafficStats, isCurrent: Boolean) {
        uplinkSpeed.value = formatSpeed(stats.txRateProxy + stats.txRateDirect)
        downlinkSpeed.value = formatSpeed(stats.rxRateProxy + stats.rxRateDirect)
    }

    override fun statsUpdated(stats: List<io.nekohasekai.sagernet.aidl.AppStats>) {
    }

    override fun onServiceConnected(service: io.nekohasekai.sagernet.aidl.ISagerNetService) {
    }

    override fun onServiceDisconnected() {
    }

    override fun onBinderDied() {
    }

    override fun onStart() {
        super.onStart()
        GroupManager.userInterface = object : GroupManager.Interface {
            override suspend fun confirm(message: String): Boolean = true
            override suspend fun onUpdateSuccess(
                group: ProxyGroup, changed: Int, added: List<String>,
                updated: Map<String, String>, deleted: List<String>, duplicate: List<String>,
            ) {
                io.nekohasekai.sagernet.ktx.runOnMainDispatcher {
                    val msg = if (changed == 0) {
                        getString(io.nekohasekai.sagernet.R.string.group_no_difference, group.displayName())
                    } else {
                        "Updated ${group.displayName()}: $changed changed"
                    }
                    android.widget.Toast.makeText(this@ComposeMainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            override suspend fun onUpdateFailure(group: ProxyGroup, message: String) {
                io.nekohasekai.sagernet.ktx.runOnMainDispatcher {
                    android.widget.Toast.makeText(this@ComposeMainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStop() {
        GroupManager.userInterface = null
        super.onStop()
    }

    override fun routeAlert(type: Int, routeName: String) {
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
    }

    override fun onDestroy() {
        connection.disconnect(this)
        super.onDestroy()
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            bytesPerSecond < 1024 * 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024))
            else -> "%.2f GB/s".format(bytesPerSecond / (1024.0 * 1024 * 1024))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    serviceState: BaseService.State,
    onServiceToggle: () -> Unit,
    uplinkSpeed: String,
    downlinkSpeed: String,
    onThemeChanged: (Int) -> Unit = {},
    onNightThemeChanged: (Int) -> Unit = {},
    onServiceModeChanged: () -> Unit = {},
) {
    var currentDestination by remember { mutableStateOf(NavDestination.CONFIGURATION) }
    val snackbarHostState = remember { SnackbarHostState() }
    var batchTestProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val stateForButton = when (serviceState) {
        BaseService.State.Idle -> ServiceState.IDLE
        BaseService.State.Connecting -> ServiceState.CONNECTING
        BaseService.State.Connected -> ServiceState.CONNECTED
        BaseService.State.Stopping -> ServiceState.CONNECTING
        BaseService.State.Stopped -> ServiceState.IDLE
    }

    val statusText = when (serviceState) {
        BaseService.State.Idle -> "Idle"
        BaseService.State.Connecting -> "Connecting..."
        BaseService.State.Connected -> "Connected"
        BaseService.State.Stopping -> "Stopping..."
        BaseService.State.Stopped -> "Stopped"
    }

    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    val fromIndex = NavDestination.entries.indexOf(initialState)
                    val toIndex = NavDestination.entries.indexOf(targetState)
                    val direction = if (toIndex >= fromIndex) 1 else -1
                    (slideInHorizontally(tween(380, easing = EmphasizedEasing)) { fullWidth -> direction * fullWidth }) togetherWith
                        (slideOutHorizontally(tween(380, easing = EmphasizedEasing)) { fullWidth -> -direction * fullWidth })
                },
                label = "screenTransition",
            ) { destination ->
                when (destination) {
                    NavDestination.CONFIGURATION -> ConfigurationScreen(
                        onMenuClick = {},
                        serviceRunning = serviceState.canStop,
                        serviceConnected = serviceState == BaseService.State.Connected,
                        batchTestProgress = batchTestProgress,
                        onBatchTestProgress = { batchTestProgress = it },
                    )
                    NavDestination.GROUP -> GroupScreen(onMenuClick = {})
                    NavDestination.ROUTE -> RouteScreen(onMenuClick = {})
                    NavDestination.SETTINGS -> SettingsScreen(
                        onMenuClick = {},
                        onThemeChanged = onThemeChanged,
                        onNightThemeChanged = onNightThemeChanged,
                        onServiceModeChanged = onServiceModeChanged,
                    )
                    NavDestination.LOGCAT -> LogcatScreen(onMenuClick = {})
                    NavDestination.TRAFFIC -> TrafficScreen(onMenuClick = {})
                    NavDestination.TOOLS -> ToolsScreen(onMenuClick = {})
                }
            }

            UnifiedBottomBar(
                items = NavDestination.entries,
                selected = currentDestination,
                onSelect = { currentDestination = it },
                uplinkSpeed = uplinkSpeed,
                downlinkSpeed = downlinkSpeed,
                connected = serviceState == BaseService.State.Connected,
                connecting = serviceState == BaseService.State.Connecting || serviceState == BaseService.State.Stopping,
                testProgress = batchTestProgress,
                onPowerClick = onServiceToggle,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnifiedBottomBar(
    items: List<NavDestination>,
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    uplinkSpeed: String,
    downlinkSpeed: String,
    connected: Boolean,
    connecting: Boolean,
    testProgress: Pair<Int, Int>?,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testing = testProgress != null
    val containerColor by animateColorAsState(
        targetValue = if (testing)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "unifiedContainer",
    )
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(32.dp),
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = if (testing) 12.dp else 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Left: Status ──
            StatusSection(
                uplinkSpeed = uplinkSpeed,
                downlinkSpeed = downlinkSpeed,
                connected = connected,
                connecting = connecting,
                testProgress = testProgress,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp),
            )

            // ── Divider ──
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .padding(horizontal = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
            )

            // ── Center: Nav (2-3 items visible, clipped with rounded corners) ──
            LazyRow(
                state = lazyListState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                items(items) { destination ->
                    val isSelected = destination == selected
                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        label = "navItemColor",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "navItemIcon",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "navItemScale",
                    )
                    Surface(
                        onClick = {
                            onSelect(destination)
                            scope.launch {
                                val index = items.indexOf(destination)
                                lazyListState.animateScrollToItem(index)
                            }
                        },
                        shape = if (isSelected) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(24.dp),
                        color = itemColor,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                                tint = iconColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            // ── Divider ──
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .padding(horizontal = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
            )

            // ── Right: Power button ──
            PowerButton(
                connected = connected,
                connecting = connecting,
                onClick = onPowerClick,
                modifier = Modifier.padding(start = 4.dp, end = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PowerButton(
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        connected -> MaterialTheme.colorScheme.primary
        connecting -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        connected -> MaterialTheme.colorScheme.onPrimary
        connecting -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val size by animateFloatAsState(
        targetValue = if (connected) 52f else 44f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "powerSize",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (connecting) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "powerPress",
    )

    Surface(
        onClick = { if (!connecting) onClick() },
        modifier = modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(24.dp),
        color = color,
        tonalElevation = if (connected) 6.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (connecting) {
                androidx.compose.material3.CircularWavyProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.2f),
                )
            } else {
                Icon(
                    imageVector = if (connected) Icons.Filled.Bolt else Icons.Filled.PowerSettingsNew,
                    contentDescription = if (connected) "Stop" else "Start",
                    tint = contentColor,
                    modifier = Modifier.size(if (connected) 26.dp else 22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusSection(
    uplinkSpeed: String,
    downlinkSpeed: String,
    connected: Boolean,
    connecting: Boolean,
    testProgress: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    val testing = testProgress != null
    val progress by animateFloatAsState(
        targetValue = if (testing && testProgress != null && testProgress.second > 0)
            testProgress.first.toFloat() / testProgress.second
        else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "testProgress",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (testing) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
                val total = testProgress!!.second
                val done = testProgress!!.first
                val text = if (total > 9) "$done/$total" else "$done/$total"
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (total > 9) 8.sp else 10.sp,
                    maxLines = 1,
                )
            }
        } else {
            // Animated expressive status indicator (no text)
            StatusPulse(connected = connected, connecting = connecting)
            if (connected && (uplinkSpeed.isNotEmpty() || downlinkSpeed.isNotEmpty())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SpeedMini(label = "↑", value = uplinkSpeed, color = MaterialTheme.colorScheme.primary)
                    SpeedMini(label = "↓", value = downlinkSpeed, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusPulse(connected: Boolean, connecting: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = if (connected) 0.7f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (connecting) 500 else if (connected) 900 else 1600,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val color = when {
        connecting -> MaterialTheme.colorScheme.primary
        connected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (connected || connecting) 1f - (scale - 0.7f) / 0.3f * 0.6f else 0.4f
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f)),
        )
        // Inner solid dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun SpeedMini(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}
