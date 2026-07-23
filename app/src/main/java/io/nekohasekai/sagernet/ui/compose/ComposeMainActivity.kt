package io.nekohasekai.sagernet.ui.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.compose.components.ServiceButton
import io.nekohasekai.sagernet.ui.compose.components.ServiceState
import io.nekohasekai.sagernet.ui.compose.components.StatsBar
import io.nekohasekai.sagernet.ui.compose.screens.AboutScreen
import io.nekohasekai.sagernet.ui.compose.screens.ConfigurationScreen
import io.nekohasekai.sagernet.ui.compose.screens.GroupScreen
import io.nekohasekai.sagernet.ui.compose.screens.LogcatScreen
import io.nekohasekai.sagernet.ui.compose.screens.RouteScreen
import io.nekohasekai.sagernet.ui.compose.screens.SettingsScreen
import io.nekohasekai.sagernet.ui.compose.screens.ToolsScreen
import io.nekohasekai.sagernet.ui.compose.screens.TrafficScreen
import kotlinx.coroutines.launch

enum class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    CONFIGURATION("configuration", R.string.menu_configuration, Icons.Filled.Description),
    GROUP("group", R.string.menu_group, Icons.Filled.List),
    ROUTE("route", R.string.menu_route, Icons.Filled.Directions),
    SETTINGS("settings", R.string.settings, Icons.Filled.Settings),
    LOGCAT("logcat", R.string.menu_log, Icons.Filled.BugReport),
    TRAFFIC("traffic", R.string.menu_traffic, Icons.Filled.Transform),
    TOOLS("tools", R.string.menu_tools, Icons.Filled.Construction),
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
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        MainScreen(
                            serviceState = serviceState.value,
                            onServiceToggle = { toggleService() },
                            uplinkSpeed = uplinkSpeed.value,
                            downlinkSpeed = downlinkSpeed.value,
                            onThemeChanged = { newId -> themeId = newId },
                            onNightThemeChanged = { newNight -> nightTheme = newNight },
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
        kotlinx.coroutines.runBlocking {
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
        if (serviceState.value.canStop) {
            SagerNet.stopService()
        } else {
            SagerNet.startService()
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
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentDestination by remember { mutableStateOf(NavDestination.CONFIGURATION) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentDestination = currentDestination,
                onDestinationSelected = { destination ->
                    currentDestination = destination
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            bottomBar = {
                StatsBar(
                    statusText = statusText,
                    uplinkSpeed = uplinkSpeed,
                    downlinkSpeed = downlinkSpeed,
                    connected = serviceState == BaseService.State.Connected,
                )
            },
            floatingActionButton = {
                ServiceButton(
                    state = stateForButton,
                    onClick = onServiceToggle,
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        (fadeIn(tween(380, easing = EmphasizedEasing)) +
                            slideInHorizontally(tween(380, easing = EmphasizedEasing)) { it / 4 }) togetherWith
                            (fadeOut(tween(380, easing = EmphasizedEasing)) +
                            slideOutHorizontally(tween(380, easing = EmphasizedEasing)) { -it / 4 })
                    },
                    label = "screenTransition",
                ) { destination ->
                    when (destination) {
                        NavDestination.CONFIGURATION -> ConfigurationScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                        NavDestination.GROUP -> GroupScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                        NavDestination.ROUTE -> RouteScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                        NavDestination.SETTINGS -> SettingsScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onThemeChanged = onThemeChanged,
                            onNightThemeChanged = onNightThemeChanged,
                        )
                        NavDestination.LOGCAT -> LogcatScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                        NavDestination.TRAFFIC -> TrafficScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                        NavDestination.TOOLS -> ToolsScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    currentDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 28.dp, bottom = 20.dp),
        )

        val groups = listOf(
            NavDestination.CONFIGURATION,
            NavDestination.GROUP,
            NavDestination.ROUTE,
            NavDestination.SETTINGS,
        )
        val misc = listOf(
            NavDestination.LOGCAT,
            NavDestination.TRAFFIC,
            NavDestination.TOOLS,
        )

        groups.forEach { destination ->
            DrawerItem(
                destination = destination,
                selected = currentDestination == destination,
                onClick = { onDestinationSelected(destination) },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 28.dp),
        )

        misc.forEach { destination ->
            DrawerItem(
                destination = destination,
                selected = currentDestination == destination,
                onClick = { onDestinationSelected(destination) },
            )
        }
    }
}

@Composable
private fun DrawerItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                stringResource(destination.labelRes),
                style = MaterialTheme.typography.labelLarge,
            )
        },
        icon = {
            io.nekohasekai.sagernet.ui.compose.components.ShapedIcon(
                icon = destination.icon,
                containerColor = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                size = 40.dp,
                pressed = selected,
            )
        },
        selected = selected,
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
    )
}
