package io.nekohasekai.sagernet.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.nekohasekai.sagernet.Key
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    runCli(args)?.let { code -> exitProcess(code) }

    val initialDeepLinks = DeepLinks.extractDeepLinks(args)
    val pendingDeepLinks = java.util.concurrent.ConcurrentLinkedQueue<String>(initialDeepLinks)
    var onIncomingDeepLinks: (() -> Unit)? = null

    val acquireResult = SingleInstanceManager.acquireOrForward(
        dataDir = DesktopRuntime.dataDir,
        uris = initialDeepLinks,
        onActivate = { remoteUris ->
            remoteUris.forEach { pendingDeepLinks.add(it) }
            onIncomingDeepLinks?.invoke()
        },
    )
    if (acquireResult is SingleInstanceManager.AcquireResult.ForwardedToPrimary) {
        exitProcess(0)
    }
    val instanceHandle = (acquireResult as? SingleInstanceManager.AcquireResult.Primary)?.handle
    android.util.Log.initSession()
    LinuxWindowSupport.configureBeforeWindow()

    runCatching {
        if (java.awt.Desktop.isDesktopSupported()) {
            val awtDesktop = java.awt.Desktop.getDesktop()
            if (awtDesktop.isSupported(java.awt.Desktop.Action.APP_OPEN_URI)) {
                awtDesktop.setOpenURIHandler { event ->
                    val uriStr = event.uri?.toString()?.trim()
                    if (!uriStr.isNullOrEmpty()) {
                        pendingDeepLinks.add(uriStr)
                        onIncomingDeepLinks?.invoke()
                    }
                }
            }
        }
    }

    UrlSchemeRegistrar.ensureRegisteredAsync()

    // Ensure the shared Android application singleton & libexclavecore JNI runtime are initialized.
    io.nekohasekai.sagernet.SagerNet.application

    val systemProxy = SystemProxy()
    val privilegedTun = DesktopPrivilegedTun()
    runCatching { systemProxy.restoreStale() }
    DesktopServiceWiring.install(systemProxy, privilegedTun)
    Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { instanceHandle?.close() }
        runCatching { privilegedTun.stop() }
        runCatching { io.nekohasekai.sagernet.SagerNet.stopService() }
        runCatching { systemProxy.restore() }
    })

    val useLegacyDesktopUi = args.contains("--legacy-ui") ||
        !System.getProperty("owenclave.section").isNullOrBlank()

    val initialWindowSize = LinuxWindowSupport.initialWindowDpSize(1180, 780)

    application {
        val scope = rememberCoroutineScope()
        val state = remember { if (useLegacyDesktopUi) AppState(scope) else null }
        val windowState = rememberWindowState(
            size = DpSize(initialWindowSize.first.dp, initialWindowSize.second.dp),
        )
        val appIcon = remember {
            applyDesktopTaskbarIcon()
            loadDesktopAppIcon()
        }
        Window(
            onCloseRequest = {
                onIncomingDeepLinks = null
                runCatching { instanceHandle?.close() }
                state?.shutdown()
                runCatching { privilegedTun.stop() }
                runCatching { io.nekohasekai.sagernet.SagerNet.stopService() }
                runCatching { systemProxy.restore() }
                exitApplication()
            },
            title = "Owenclave ${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME}",
            icon = appIcon,
            state = windowState,
        ) {
            var linuxScaleOverride by remember {
                mutableStateOf(LinuxWindowSupport.detectLinuxScaleOverride(window))
            }
            DisposableEffect(window) {
                applyDesktopWindowIconImages(window)
                window.minimumSize = LinuxWindowSupport.minimumWindowDimension()
                LinuxWindowSupport.attachWindowFixes(window)
                val refreshScale = java.beans.PropertyChangeListener {
                    val updated = LinuxWindowSupport.detectLinuxScaleOverride(window)
                    if (updated != linuxScaleOverride) {
                        linuxScaleOverride = updated
                    }
                }
                window.addPropertyChangeListener("graphicsConfiguration", refreshScale)
                onDispose {
                    window.removePropertyChangeListener("graphicsConfiguration", refreshScale)
                }
            }

            val baseDensity = LocalDensity.current
            val effectiveDensity = remember(baseDensity, linuxScaleOverride) {
                val scale = linuxScaleOverride
                if (scale != null) Density(density = scale, fontScale = baseDensity.fontScale) else baseDensity
            }

            CompositionLocalProvider(LocalDensity provides effectiveDensity) {
                if (state != null) {
                    App(state)
                } else {
                    val mainActivity = remember { io.nekohasekai.sagernet.ui.compose.ComposeMainActivity() }
                    androidx.activity.DesktopActivityStack.Host(mainActivity)
                    LaunchedEffect(mainActivity, window) {
                        fun drainDeepLinks(bringToFront: Boolean) {
                            if (bringToFront) {
                                windowState.isMinimized = false
                                window.isVisible = true
                                window.toFront()
                                window.requestFocus()
                            }
                            while (true) {
                                val uri = pendingDeepLinks.poll() ?: break
                                val viewIntent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(uri),
                                ).apply {
                                    component = android.content.ComponentName(
                                        mainActivity,
                                        io.nekohasekai.sagernet.ui.compose.ComposeMainActivity::class.java,
                                    )
                                }
                                androidx.activity.DesktopActivityStack.startActivityFromIntent(viewIntent)
                            }
                        }
                        drainDeepLinks(bringToFront = false)
                        onIncomingDeepLinks = {
                            javax.swing.SwingUtilities.invokeLater {
                                drainDeepLinks(bringToFront = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadDesktopAppIcon(): Painter? {
    return runCatching {
        val stream = DesktopRuntime::class.java.getResourceAsStream("/owenclave-icon.png")
            ?: return null
        val bytes = stream.use { it.readBytes() }
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()
}

private fun loadDesktopIconBufferedImages(): List<java.awt.image.BufferedImage> {
    return runCatching {
        val stream = DesktopRuntime::class.java.getResourceAsStream("/owenclave-icon.png")
            ?: return emptyList()
        val master = stream.use { javax.imageio.ImageIO.read(it) } ?: return emptyList()
        listOf(16, 24, 32, 48, 64, 128, 256, 512).map { sz ->
            if (master.width == sz && master.height == sz) {
                master
            } else {
                val dst = java.awt.image.BufferedImage(sz, sz, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = dst.createGraphics()
                try {
                    g.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                    )
                    g.setRenderingHint(
                        java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY,
                    )
                    g.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
                    )
                    g.drawImage(master, 0, 0, sz, sz, null)
                } finally {
                    g.dispose()
                }
                dst
            }
        }
    }.getOrDefault(emptyList())
}

private fun applyDesktopTaskbarIcon() {
    runCatching {
        if (java.awt.Taskbar.isTaskbarSupported()) {
            val taskbar = java.awt.Taskbar.getTaskbar()
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                val stream = DesktopRuntime::class.java.getResourceAsStream("/owenclave-icon.png") ?: return
                val img = stream.use { javax.imageio.ImageIO.read(it) } ?: return
                taskbar.iconImage = img
            }
        }
    }
}

private fun applyDesktopWindowIconImages(window: java.awt.Window) {
    runCatching {
        val icons = loadDesktopIconBufferedImages()
        if (icons.isNotEmpty()) {
            window.iconImages = icons
        }
    }
}

internal object DesktopServiceWiring {
    private val DEFAULT_SYSTEM_PROXY_BYPASS = listOf(
        "localhost",
        "127.0.0.1",
        "::1",
        "192.168.0.0/16",
        "10.0.0.0/8",
        "172.16.0.0/12",
    )

    fun install(systemProxy: SystemProxy, privilegedTun: DesktopPrivilegedTun) {
        io.nekohasekai.sagernet.bg.ProxyService.onPreStart = { service ->
            val ds = io.nekohasekai.sagernet.database.DataStore
            if (ds.serviceMode == Key.MODE_VPN && privilegedTun.isRunning()) {
                val bypassIps = (
                    DesktopPrivilegedTun.resolveBypassServerIps(service.data.proxy) +
                        service.activePhysicalDnsBypassIps
                ).distinct()
                privilegedTun.prepareBypassIps(bypassIps)
            }
        }
        io.nekohasekai.sagernet.bg.ProxyService.onStarted = { service ->
            val ds = io.nekohasekai.sagernet.database.DataStore
            if (ds.serviceMode == Key.MODE_VPN) {
                val bypassIps = (
                    DesktopPrivilegedTun.resolveBypassServerIps(service.data.proxy) +
                        service.activePhysicalDnsBypassIps
                ).distinct()
                privilegedTun.start(
                    PrivilegedTunParams(
                        socksPort = service.activeSocksPort.takeIf { it > 0 } ?: ds.socksPort,
                        socksUsername = ds.socksUsername,
                        socksPassword = ds.socksPassword,
                        localDnsPort = service.activeDnsPort,
                        mtu = ds.mtu,
                        enableIpv6 = ds.enableVPNInterfaceIPv6Address,
                        bypassLan = ds.bypassLan,
                        bypassServerIps = bypassIps,
                    ),
                )
            } else {
                runCatching { privilegedTun.stop() }
            }

            if (service.activeSystemProxyEligible && ds.serviceMode == Key.MODE_PROXY) {
                val bypass = ds.httpProxyException.split("\n", ",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { DEFAULT_SYSTEM_PROXY_BYPASS }
                runCatching {
                    systemProxy.apply(
                        host = "127.0.0.1",
                        httpPort = service.activeHttpPort,
                        socksPort = service.activeSocksPort,
                        bypass = bypass,
                    )
                }.onFailure { io.nekohasekai.sagernet.ktx.Logs.w(it) }
            } else {
                runCatching { systemProxy.restore() }
                if (ds.serviceMode == Key.MODE_VPN) {
                    runCatching {
                        if (systemProxy.clearDeadLoopbackProxy()) {
                            io.nekohasekai.sagernet.ktx.Logs.i("Cleared dead loopback OS system proxy for VPN mode")
                        }
                    }
                }
            }
        }
        io.nekohasekai.sagernet.bg.ProxyService.onStopped = {
            runCatching { privilegedTun.stop() }
            runCatching { systemProxy.restore() }
        }
    }
}

/** Headless entry points used for verification and packaging checks. */
private fun runCli(args: Array<String>): Int? {
    val isCliFlag = args.any {
        it == "--version" ||
            it == "--daemon" ||
            it == "--server" ||
            it == "--print-settings" ||
            it == "--print-config" ||
            it.startsWith("--selftest")
    }
    if (isCliFlag) {
        val forceHeadless = !args.contains("--selftest-gui") ||
            !System.getenv("CI").isNullOrBlank() ||
            !System.getProperty("owenclave.ci").isNullOrBlank()
        if (forceHeadless && System.getProperty("java.awt.headless").isNullOrBlank()) {
            System.setProperty("java.awt.headless", "true")
        }
        if (System.getProperty("apple.awt.UIElement").isNullOrBlank()) {
            System.setProperty("apple.awt.UIElement", "true")
        }
    }
    if (args.any { it.startsWith("--selftest") } && System.getProperty("owenclave.dataDir").isNullOrBlank()) {
        val tempDataDir = java.nio.file.Files.createTempDirectory("owenclave-selftest-").toFile()
        System.setProperty("owenclave.dataDir", tempDataDir.absolutePath)
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { tempDataDir.deleteRecursively() }
        })
    }
    if (args.contains("--version")) {
        println("Owenclave desktop ${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME} (${DesktopRuntime.platformTag})")
        println("data: ${DesktopRuntime.dataDir}")
        println("core: ${DesktopRuntime.coreBinary()?.absolutePath ?: "not found"}")
        println("jni: ${DesktopRuntime.jniLibrary()?.absolutePath ?: "not found"}")
        println("naive: ${DesktopRuntime.naiveBinary()?.absolutePath ?: "not found"}")
        return 0
    }
    if (args.contains("--daemon") || args.contains("--server")) {
        return io.nekohasekai.sagernet.desktop.server.DaemonMain.run(args)
    }
    if (args.contains("--selftest")) {
        return SelfTest.run()
    }
    if (args.contains("--selftest-gui")) {
        return SelfTest.runGui()
    }
    if (args.contains("--selftest-naive")) {
        return SelfTest.runNaive()
    }
    if (args.contains("--selftest-protocols")) {
        return ProtocolSelfTest.run()
    }
    val subscriptionIndex = args.indexOf("--selftest-subscription")
    if (subscriptionIndex >= 0) {
        // An optional URL in the next position turns on the online check; another
        // flag there means the self contained offline check only.
        val target = args.getOrNull(subscriptionIndex + 1)
            ?.takeIf { it.isNotBlank() && !it.startsWith("--") }
        return SubscriptionSelfTest.run(target)
    }
    if (args.contains("--selftest-vless")) {
        return ProtocolSelfTest.run("vless")
    }
    val protocolIndex = args.indexOf("--selftest-protocol")
    if (protocolIndex >= 0) {
        return ProtocolSelfTest.run(args.getOrNull(protocolIndex + 1))
    }
    if (args.contains("--selftest-tun")) {
        return SelfTest.runTun(stub = false)
    }
    if (args.contains("--selftest-tun-stub")) {
        return SelfTest.runTun(stub = true)
    }
    if (args.contains("--selftest-system-proxy")) {
        return SelfTest.runSystemProxyE2e()
    }
    if (args.contains("--selftest-vpn-e2e")) {
        return SelfTest.runVpnE2e()
    }
    if (args.contains("--print-settings")) {
        // Print the stored values of the settings document so the renderer (and the
        // parity verifier) can see current vs Android default side by side.
        val store = ProfileStore().apply { load() }
        print(SettingsCatalogParser.load().dump { key -> store.settings.value(key) })
        return 0
    }
    val printIndex = args.indexOf("--print-config")
    if (printIndex >= 0) {
        val input = args.getOrNull(printIndex + 1)
        if (input == null) {
            println("usage: --print-config <file|url|share-link>")
            return 2
        }
        val text = when {
            File(input).isFile -> File(input).readText()
            input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true) -> {
                val looksLikeProxyUri = runCatching {
                    val uri = java.net.URI.create(input)
                    !uri.rawUserInfo.isNullOrEmpty() ||
                        (uri.rawQuery?.let { it.contains("sni=") || it.contains("allowInsecure=") } == true) ||
                        (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") && uri.port > 0 && !input.contains("/sub")
                }.getOrDefault(false)
                if (looksLikeProxyUri) {
                    input
                } else {
                    runCatching { SubscriptionImporter.download(input) }.getOrElse { input }
                }
            }
            else -> input
        }
        val beans = SubscriptionImporter.parse(text)
        if (beans.isEmpty()) {
            println("no profiles recognised in the input")
            return 1
        }
        println("parsed ${beans.size} profile(s):")
        beans.forEach { println("  - ${it.javaClass.simpleName}: ${it.displayName()}") }
        val supported = beans.firstOrNull { DesktopConfigBuilder.supports(it) }
        if (supported == null) {
            println("none of the parsed profiles is supported by the desktop client yet")
            return 1
        }
        val settings = DesktopSettings(
            bypassPrivateNetworks = !args.contains("--no-bypass-lan"),
            routeMode = if (args.contains("--route-direct")) DesktopSettings.ROUTE_DIRECT else DesktopSettings.ROUTE_GLOBAL,
        )
        println("--- generated core config for ${supported.displayName()} ---")
        println(DesktopConfigBuilder.build(Profile(bean = supported), settings, emptyList(), null).json)
        return 0
    }
    return null
}

@Composable
fun App(state: AppState) {
    // The Android `nightTheme` row picks the desktop theme: 1 = dark, 2 = light,
    // anything else (follow system / auto) follows the OS.
    val night = state.settings.value(io.nekohasekai.sagernet.Key.NIGHT_THEME).toIntOrNull() ?: 1
    val dark = when (night) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val baseScheme = if (dark) darkColorScheme() else lightColorScheme()
    // The theme colour is the Android `appTheme` preference: the desktop client
    // uses the same row (see SettingsSection) as the Compose accent colour.
    val accent = state.settings.value(io.nekohasekai.sagernet.Key.APP_THEME).toLongOrNull()?.toInt()
    val scheme = if (accent == null) baseScheme else baseScheme.copy(primary = Color(accent))
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavRail(state)
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Header(state)
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    when (state.section) {
                        Section.PROFILES -> ProfilesSection(state)
                        Section.SUBSCRIPTIONS -> SubscriptionsSection(state)
                        Section.RULES -> RulesSection(state)
                        Section.SETTINGS -> SettingsSection(state)
                        Section.LOG -> LogsSection(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRail(state: AppState) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text("Owenclave", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            io.nekohasekai.sagernet.BuildConfig.VERSION_NAME,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Section.entries.forEach { entry ->
            if (state.section == entry) {
                Button(
                    onClick = { state.show(entry) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) { Text(entry.label, fontSize = 13.sp) }
            } else {
                OutlinedButton(
                    onClick = { state.show(entry) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) { Text(entry.label, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            state.runtimeLabel,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(state: AppState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.section.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    state.section.hint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val connected = state.isConnected
            OutlinedButton(
                onClick = { if (connected) state.disconnect() else state.connect() },
                enabled = !state.busy && (connected || state.selectedProfile != null),
            ) { Text(if (connected) "Disconnect" else "Connect") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { state.testConnection() }, enabled = !state.busy && state.isConnected) {
                Text("Test")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (state.busy) "⏳ ${state.status}" else state.status, fontSize = 13.sp)
    }
}

// ----------------------------------------------------------------- profiles

@Composable
private fun ProfilesSection(state: AppState) {
    // The Android `alwaysShowAddress` and `profileSecurityAdvisory` rows drive the
    // desktop profile list the same way they drive the Android one.
    val showAddress = state.settings.value(io.nekohasekai.sagernet.Key.ALWAYS_SHOW_ADDRESS) == "true"
    val showAdvisory = state.settings.value(io.nekohasekai.sagernet.Key.PROFILE_SECURITY_ADVISORY) == "true"
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
            Text("Profiles (${state.profiles.size})", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            if (state.profiles.isEmpty()) {
                Text(
                    "No profiles yet. Add a subscription on the Subscriptions tab, or paste a share link on the right.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    val selected = profile.id == state.selectedProfileId
                    val connected = profile.id == state.connectedProfileId
                    val background = when {
                        connected -> MaterialTheme.colorScheme.primaryContainer
                        selected -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    }
                    Card(modifier = Modifier.fillMaxWidth().clickable { state.select(profile) }) {
                        Row(
                            modifier = Modifier.background(background).fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    (if (connected) "● " else "") + profile.displayName,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    if (showAddress) "${profile.protocolName} · ${profile.address}"
                                    else profile.protocolName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                state.subscriptionOf(profile)?.let { subscription ->
                                    Text(
                                        "from ${subscription.displayName}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (showAdvisory && profile.bean?.isInsecure() == true) {
                                    Text(
                                        "⚠ insecure settings (allowInsecure / no TLS verification)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                // Every parsed bean is supported; only a really
                                // empty profile cannot be connected.
                                if (profile.bean == null && profile.customConfig == null) {
                                    Text(
                                        DesktopConfigBuilder.unsupportedReason(null),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            TextButton(onClick = { state.connect(profile) }, enabled = !state.busy) { Text("Connect") }
                            TextButton(onClick = { state.remove(profile) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ImportCard(state)
        }
    }
}

@Composable
private fun ImportCard(state: AppState) {
    var text by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("Import profiles", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("Share links, Clash/Mihomo YAML, V2Ray or sing-box JSON.", fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("naive+https://user:pass@host:443, vless://..., proxies: ...") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Button(
                    onClick = {
                        state.importText(text, "pasted input")
                        text = ""
                    },
                    enabled = text.isNotBlank() && !state.busy,
                ) { Text("Import") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        state.addCustomConfig(text, "Custom config")
                        text = ""
                    },
                    enabled = text.isNotBlank() && !state.busy,
                ) { Text("Keep as raw config") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Import from a file", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("/path/to/config.json") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { state.importFile(File(path.trim())) },
                    enabled = path.isNotBlank() && !state.busy,
                ) { Text("Import") }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            AddHttpProxyForm(state)
        }
    }
}

@Composable
private fun AddHttpProxyForm(state: AppState) {
    var tls by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var allowInsecure by remember { mutableStateOf(false) }

    Text("Add HTTP / HTTPS proxy", fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                tls = false
                if (port == "443") port = "8080"
            },
        ) { Text(if (!tls) "● HTTP" else "HTTP") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                tls = true
                if (port == "8080") port = "443"
            },
        ) { Text(if (tls) "● HTTPS" else "HTTPS") }
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Server host") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.width(100.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username (optional)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    if (tls) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = sni,
                onValueChange = { sni = it },
                label = { Text("SNI (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Checkbox(checked = allowInsecure, onCheckedChange = { allowInsecure = it })
            Text("Allow insecure", fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val parsedPort = port.trim().toIntOrNull() ?: 0
            if (state.addHttpProxy(tls, host, parsedPort, username, password, sni, allowInsecure, false, name) != null) {
                name = ""
                host = ""
                username = ""
                password = ""
                sni = ""
            }
        },
        enabled = host.isNotBlank() && !state.busy,
    ) { Text(if (tls) "Add HTTPS proxy" else "Add HTTP proxy") }
}

// ------------------------------------------------------------ subscriptions

@Composable
private fun SubscriptionsSection(state: AppState) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var sendHwid by remember { mutableStateOf(state.settings.sendHwid) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Add a subscription", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.width(240.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://provider.example/sub/xxxx") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendHwid, onCheckedChange = { sendHwid = it })
                    Text("Send HWID for this subscription", fontSize = 13.sp)
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            state.addSubscription(name, url, sendHwid)
                            name = ""
                            url = ""
                        },
                        enabled = url.isNotBlank() && !state.busy,
                    ) { Text("Add and update") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { state.refreshAllSubscriptions() },
                        enabled = state.subscriptions.isNotEmpty() && !state.busy,
                    ) { Text("Update all") }
                }
                Text(
                    "Updates are fetched through the connected profile when that is enabled in Settings, " +
                        "which also gets around local filtering.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Subscriptions (${state.subscriptions.size})", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        if (state.subscriptions.isEmpty()) {
            Text(
                "No subscriptions yet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(state.subscriptions, key = { it.id }) { subscription ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(subscription.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    subscription.url,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    buildString {
                                        append("${subscription.profileCount} profile(s)")
                                        if (subscription.lastUpdated > 0) {
                                            append(" · updated ${formatTime(subscription.lastUpdated)}")
                                        }
                                        if (subscription.sendHwid) append(" · HWID")
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { state.refreshSubscription(subscription) },
                                enabled = !state.busy,
                            ) { Text("Update") }
                            TextButton(onClick = { state.removeSubscription(subscription) }) { Text("Delete") }
                        }
                        subscription.lastError?.let { error ->
                            Spacer(Modifier.height(4.dp))
                            Text("last error: $error", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Text(
                                "the Log tab keeps the full attempt history",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- rules

@Composable
private fun RulesSection(state: AppState) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(RuleTarget.PROXY) }
    var domains by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Add a rule", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Action: ", fontSize = 13.sp)
                    RuleTarget.entries.forEach { candidate ->
                        if (candidate == target) {
                            Button(onClick = { target = candidate }) { Text(candidate.label) }
                        } else {
                            OutlinedButton(onClick = { target = candidate }) { Text(candidate.label) }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = { Text("Domains: example.com, full:api.example.com, keyword:ads, regexp:^a.*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("IP CIDR") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = network,
                        onValueChange = { network = it },
                        label = { Text("Network (tcp/udp)") },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = protocol,
                        onValueChange = { protocol = it },
                        label = { Text("Sniffed protocol") },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            state.addRule(
                                RoutingRule(
                                    name = name.trim(),
                                    target = target.tag,
                                    domains = domains.trim(),
                                    ip = ip.trim(),
                                    port = port.trim(),
                                    network = network.trim(),
                                    protocol = protocol.trim(),
                                )
                            )
                            name = ""
                            domains = ""
                            ip = ""
                            port = ""
                            network = ""
                            protocol = ""
                        },
                        enabled = !state.busy,
                    ) { Text("Add rule") }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Rules are evaluated top to bottom, the first match wins, and they apply on the next connect.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Rules (${state.rules.size})", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        if (state.rules.isEmpty()) {
            Text(
                "No rules. Traffic goes through the selected profile, except private networks when " +
                    "\"Bypass LAN\" is on.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(state.rules, key = { it.id }) { rule ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = rule.enabled, onCheckedChange = { state.toggleRule(rule) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                rule.summary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            RuleTarget.entries.firstOrNull { it.tag == rule.target }?.label ?: rule.target,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { state.removeRule(rule) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- settings

/**
 * The Settings tab is the Android global settings screen, rendered from the
 * catalog that [SettingsCatalogParser] builds out of the real Android XML.
 *
 * Entries keep the Android order and section titles. Android-only preferences
 * stay in their original position but disabled with a reason, and the desktop
 * only settings (TUN mode, subscription fetching, HWID identity, runtime paths)
 * follow in one clearly separated section.
 */
@Composable
private fun SettingsSection(state: AppState) {
    val catalog = remember { SettingsCatalogParser.load() }
    val settings = state.settings
    var filter by remember { mutableStateOf("") }
    val needle = filter.trim().lowercase()
    val sections = if (needle.isEmpty()) {
        catalog.sections
    } else {
        catalog.sections.mapNotNull { section ->
            val entries = section.entries.filter { entry ->
                entry.key.lowercase().contains(needle) ||
                    entry.title.lowercase().contains(needle) ||
                    entry.summary.lowercase().contains(needle)
            }
            if (entries.isEmpty()) null else section.copy(entries = entries)
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Generated from the Android app's global settings " +
                "(app/src/main/res/xml/global_preferences.xml), one row per Android preference. " +
                "Rows marked \"Android only\" stay in place but are disabled.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter settings (key, title, summary)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (needle.isNotEmpty()) {
            Text(
                "${sections.sumOf { it.entries.size }} of ${catalog.entries.size} settings match",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sections.forEach { section ->
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(section.title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            section.entries.forEach { entry ->
                SettingsRow(state, entry, settings.value(entry.key))
            }
        }
        if (needle.isEmpty()) DesktopOnlySection(state)
    }
}

@Composable
private fun SettingsRow(state: AppState, entry: CatalogEntry, current: String) {
    val action = (entry.binding as? DesktopAction)?.action
    val rowModifier = if (action != null && entry.enabled) {
        Modifier.fillMaxWidth().clickable { runDesktopAction(state, action) }
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = rowModifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title.ifBlank { entry.key }, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                if (entry.summary.isNotEmpty()) {
                    Text(entry.summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!entry.enabled) {
                    Text(
                        entry.disabledReason.orEmpty(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (entry.note != null) {
                    Text(
                        "desktop: ${entry.note}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            when (entry.widget) {
                WidgetKind.SWITCH -> Switch(
                    checked = current.equals("true", ignoreCase = true),
                    onCheckedChange = { state.setPreference(entry.key, it.toString()) },
                    enabled = entry.enabled,
                )
                WidgetKind.MENU -> MenuControl(state, entry, current)
                WidgetKind.COLOR -> ColorControl(state, entry, current)
                WidgetKind.PLAIN -> Text(
                    when {
                        !entry.enabled -> "—"
                        action != null -> "Run ›"
                        else -> "›"
                    },
                    fontSize = 13.sp,
                    color = if (entry.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WidgetKind.EDIT, WidgetKind.NUMBER, WidgetKind.LINK -> Unit
            }
        }
        if (entry.widget == WidgetKind.EDIT ||
            entry.widget == WidgetKind.NUMBER ||
            entry.widget == WidgetKind.LINK
        ) {
            Spacer(Modifier.height(4.dp))
            val multiline = entry.key in MULTILINE_KEYS
            val numeric = entry.widget == WidgetKind.NUMBER || entry.key in NUMERIC_KEYS
            OutlinedTextField(
                value = current,
                onValueChange = { input ->
                    val value = if (numeric) input.filter { it.isDigit() }.take(6) else input
                    state.setPreference(entry.key, value)
                },
                enabled = entry.enabled,
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                visualTransformation = if (entry.key in PASSWORD_KEYS) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The Android plain-Preference actions the desktop implements. */
private fun runDesktopAction(state: AppState, action: String) {
    when (action) {
        "resetHwid" -> state.resetHwid()
    }
}

/**
 * Preferences whose value is a list (one entry per line on Android): the desktop
 * renders them as a multi-line field. These are rendering choices only, the
 * catalog widget kind still comes from the Android XML.
 */
private val MULTILINE_KEYS = setOf(
    Key.DNS_HOSTS,
    Key.HTTP_PROXY_EXCEPTION,
    Key.STUN_SERVERS,
    Key.EXPERIMENTAL_FLAGS,
)

/** Secrets: masked in the UI. */
private val PASSWORD_KEYS = setOf(
    Key.SOCKS_PASSWORD,
    Key.HTTP_PASSWORD,
    Key.SOCKS_PROXY_CHAIN_PASSWORD,
)

/** Ports and sizes: the Android XML has no `inputType`, but these are integers. */
private val NUMERIC_KEYS = setOf(
    Key.SOCKS_PORT,
    Key.HTTP_PORT,
    Key.TRANSPROXY_PORT,
    Key.LOCAL_DNS_PORT,
    Key.MTU,
    Key.SOCKS_PROXY_CHAIN_PORT,
)

@Composable
private fun MenuControl(state: AppState, entry: CatalogEntry, current: String) {
    var expanded by remember { mutableStateOf(false) }
    val selected = entry.choices.firstOrNull { it.value == current }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = entry.enabled) {
            Text(selected?.title?.ifBlank { "(none)" } ?: current.ifBlank { "(none)" }, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entry.choices.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title.ifBlank { "(none)" }, fontSize = 12.sp) },
                    onClick = {
                        expanded = false
                        state.setPreference(entry.key, option.value)
                    },
                )
            }
        }
    }
}

/** Accent colours offered for the Android `appTheme` row. */
private val ACCENT_PALETTE = listOf(
    0xFF80CBC4, 0xFF90CAF9, 0xFFA5D6A7, 0xFFFFCC80,
    0xFFEF9A9A, 0xFFCE93D8, 0xFFF48FB1, 0xFFB0BEC5,
)

@Composable
private fun ColorControl(state: AppState, entry: CatalogEntry, current: String) {
    val selected = current.toLongOrNull()?.toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ACCENT_PALETTE.forEach { argb ->
            val value = argb.toInt()
            val border = if (value == selected) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(22.dp)
                    .then(border)
                    .padding(3.dp)
                    .background(Color(value), CircleShape)
                    .clickable(enabled = entry.enabled) {
                        state.setPreference(entry.key, value.toString())
                    },
            )
        }
    }
}

/**
 * Settings the Android global screen has no entry for. They stay in the same tab
 * so nothing the desktop client offered before this parity work is lost.
 */
@Composable
private fun DesktopOnlySection(state: AppState) {
    val settings = state.settings
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(10.dp))
    Text("Desktop only", fontWeight = FontWeight.Medium, fontSize = 16.sp)
    Text(
        "Settings without an Android global preference; kept so no desktop functionality is lost.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Point the system proxy or a browser at 127.0.0.1:${settings.socksPort} (SOCKS5) " +
            "or 127.0.0.1:${settings.httpPort} (HTTP).",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Text("Service mode", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Text(
        when (settings.serviceMode) {
            DesktopSettings.SERVICE_VPN ->
                "VPN (TUN device): all system traffic is routed through the selected profile on " +
                    "connect; the routes and the device are removed on disconnect."
            DesktopSettings.SERVICE_SYSTEM ->
                "System proxy: the OS proxy is pointed at 127.0.0.1:${settings.httpPort} " +
                    "(SOCKS 127.0.0.1:${settings.socksPort}) on connect and the previous settings " +
                    "are restored on disconnect."
            else ->
                "Proxy only: only the local inbounds are served; point apps at " +
                    "127.0.0.1:${settings.socksPort} (SOCKS5) or 127.0.0.1:${settings.httpPort} (HTTP)."
        },
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Selected with the \"Service mode\" row in App settings above (VPN / System proxy / Proxy only).",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text("Transparent mode (TUN)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = settings.tunInterface,
            onValueChange = { value -> state.updateSettings { it.copy(tunInterface = value.trim()) } },
            label = { Text("Interface name (Linux, optional)") },
            singleLine = true,
            modifier = Modifier.width(280.dp),
        )
    }
    Text(
        "The MTU and the IPv6 switch are the Android \"MTU\" and \"IPv6 route\" rows in App " +
            "settings above. TUN mode needs administrator rights: on Linux start the client as " +
            "root (or grant CAP_NET_ADMIN), on macOS with sudo, on Windows as Administrator. It " +
            "applies when you connect, and the routes are restored when you disconnect.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "tun2socks: " + (
            DesktopRuntime.tun2socksBinary()?.absolutePath
                ?: "not found (run ./run desktop tun2socks download)"
            ),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Text("System proxy", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val proxyStatus = remember(settings.serviceMode) {
        if (settings.serviceMode != DesktopSettings.SERVICE_SYSTEM) {
            null
        } else {
            val proxy = SystemProxy()
            proxy.unavailableReason()?.let { "unavailable: $it" } ?: "backend: ${proxy.backendName()}"
        }
    }
    Text(
        "When Service mode = System proxy, the OS proxy is set on connect and the previous " +
            "settings are restored on disconnect, on exit and on the next start after a crash. " +
            "GNOME (gsettings), KDE (kwriteconfig) and the macOS / Windows proxy settings are " +
            "supported.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (proxyStatus != null) {
        Text(
            proxyStatus,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (proxyStatus.startsWith("unavailable")) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    Spacer(Modifier.height(12.dp))
    Text("Per-app routing", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    if (DesktopRuntime.os == "linux") {
        Text(
            "With Service mode = VPN and the Android \"Proxy apps\" row on, only the processes " +
                "listed here are routed through the TUN device; every other process keeps the " +
                "normal route (cgroup v2 + nftables/iptables marks + policy routing). One process " +
                "name or absolute path per line. Needs root; the rules are removed on disconnect. " +
                "A process that is not running yet has to be started and the connection re-done.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = settings.perAppProcesses,
            onValueChange = { value -> state.updateSettings { it.copy(perAppProcesses = value) } },
            label = { Text("Processes routed through the TUN device") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            if (DesktopRuntime.os == "windows") {
                "Not implemented on Windows: per-app routing needs a WFP callout driver, which " +
                    "is out of scope for this application. Use the rule list instead."
            } else {
                "Not implemented on macOS: per-app routing needs a NetworkExtension, which is " +
                    "out of scope for this application. Use the rule list instead."
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text("Device identity (HWID)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { state.resetHwid() }) { Text("Generate a new identity") }
        Spacer(Modifier.width(12.dp))
        Text(
            "The \"Send HWID\" switch is the Android row above.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "Current: ${settings.currentHwid()}",
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Text("Subscriptions", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = settings.fetchSubscriptionsThroughProxy,
            onCheckedChange = { value ->
                state.updateSettings { it.copy(fetchSubscriptionsThroughProxy = value) }
            },
        )
        Text("Fetch through the connected profile when one is running", fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text("Runtime", fontWeight = FontWeight.Medium, fontSize = 13.sp)
    InfoLine("Platform", DesktopRuntime.platformTag)
    InfoLine("Data directory", DesktopRuntime.dataDir.absolutePath)
    InfoLine("Core", DesktopRuntime.coreBinary()?.absolutePath ?: "not found")
    InfoLine("libexclavecore JNI", DesktopRuntime.jniLibrary()?.absolutePath ?: "not found")
    InfoLine("NaiveProxy", DesktopRuntime.naiveBinary()?.absolutePath ?: "not found")
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(140.dp))
        SelectionContainer {
            Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ---------------------------------------------------------------------- log

@Composable
private fun LogsSection(state: AppState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) listState.scrollToItem(state.logs.size - 1)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Log (${state.logs.size} lines)", fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(StringSelection(state.logText()), null)
                    state.appendLog("log copied to the clipboard")
                },
            ) { Text("Copy") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { state.clearLogs() }) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                SelectionContainer {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.logs) { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)
