package io.nekohasekai.sagernet.ui.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.compose.components.ServiceButton
import io.nekohasekai.sagernet.ui.compose.components.ServiceState
import io.nekohasekai.sagernet.ui.compose.components.StatsBar
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
                val beans = io.nekohasekai.sagernet.ktx.parseShareLinks(link)
                if (beans.isNotEmpty()) {
                    val groupId = io.nekohasekai.sagernet.database.DataStore.selectedGroupForImport()
                    io.nekohasekai.sagernet.database.ProfileManager.createProfile(groupId, beans[0])
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
        floatingActionButton = {
            val transition = serviceState == BaseService.State.Connecting ||
                serviceState == BaseService.State.Stopping
            ServiceButton(
                state = stateForButton,
                onClick = { if (!transition) onServiceToggle() },
            )
        },
        floatingActionButtonPosition = FabPosition.End,
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
                    NavDestination.GROUP -> GroupScreen(
                        onMenuClick = {},
                    )
                    NavDestination.ROUTE -> RouteScreen(
                        onMenuClick = {},
                    )
                    NavDestination.SETTINGS -> SettingsScreen(
                        onMenuClick = {},
                        onThemeChanged = onThemeChanged,
                        onNightThemeChanged = onNightThemeChanged,
                        onServiceModeChanged = onServiceModeChanged,
                    )
                    NavDestination.LOGCAT -> LogcatScreen(
                        onMenuClick = {},
                    )
                    NavDestination.TRAFFIC -> TrafficScreen(
                        onMenuClick = {},
                    )
                    NavDestination.TOOLS -> ToolsScreen(
                        onMenuClick = {},
                    )
                }
            }

            StatsBar(
                statusText = statusText,
                uplinkSpeed = uplinkSpeed,
                downlinkSpeed = downlinkSpeed,
                connected = serviceState == BaseService.State.Connected,
                testProgress = batchTestProgress,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )

            FloatingBottomNav(
                items = NavDestination.entries,
                selected = currentDestination,
                onSelect = { currentDestination = it },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingBottomNav(
    items: List<NavDestination>,
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .width(168.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        LazyRow(
            state = lazyListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items) { destination ->
                val isSelected = destination == selected
                val containerColor by animateColorAsState(
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
                    shape = RoundedCornerShape(20.dp),
                    color = containerColor,
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
    }
}
