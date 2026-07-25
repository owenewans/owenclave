package io.nekohasekai.sagernet.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.fmt.hysteria2.Hysteria2Bean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http3.Http3Bean
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.olcrtc.OLCRTCBean
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.trusttunnel.TrustTunnelBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.tuic5.Tuic5Bean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ui.compose.screens.AssetsScreen
import io.nekohasekai.sagernet.ui.compose.screens.AppItem
import io.nekohasekai.sagernet.ui.compose.screens.AppListScreen
import io.nekohasekai.sagernet.ui.compose.screens.ConfigEditScreen
import io.nekohasekai.sagernet.ui.compose.screens.GroupSettingsScreen
import io.nekohasekai.sagernet.ui.compose.screens.GroupSettingsData
import io.nekohasekai.sagernet.ui.compose.screens.SubscriptionSettings
import io.nekohasekai.sagernet.ui.compose.screens.ProbeCertScreen
import io.nekohasekai.sagernet.ui.compose.screens.ProfileFieldState
import io.nekohasekai.sagernet.ui.compose.screens.ProfileSelectScreen
import io.nekohasekai.sagernet.ui.compose.screens.QuickSwitchScreen
import io.nekohasekai.sagernet.ui.compose.screens.RouteSettingsScreen
import io.nekohasekai.sagernet.ui.compose.screens.ScannerScreen
import io.nekohasekai.sagernet.ui.compose.screens.StunScreen
import io.nekohasekai.sagernet.ui.compose.screens.UniversalProfileSettingsScreen
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ComposeAssetsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwenclaveTheme {
                AssetsScreen(
                    assets = emptyList(),
                    onBack = { finish() },
                    onAdd = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
    }
}

class ComposeAppListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }

        val pm = packageManager
        var apps by mutableStateOf<List<AppItem>>(emptyList())
        var loading by mutableStateOf(true)
        var bypass by mutableStateOf(DataStore.bypass)

        fun loadApps() {
            lifecycleScope.launch {
                loading = true
                val loaded = withContext(Dispatchers.IO) {
                    PackageCache.awaitLoadSync()
                    val individual = DataStore.individual
                        .split("\n").filter { it.isNotBlank() }.toSet()
                    PackageCache.installedPackages.toMutableMap().apply {
                        remove(BuildConfig.APPLICATION_ID)
                    }.map { (packageName, packageInfo) ->
                        val appInfo = packageInfo.applicationInfo!!
                        AppItem(
                            packageName = packageName,
                            label = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                            enabled = individual.contains(packageName),
                        )
                    }.sortedWith(compareBy({ !it.enabled }, { it.label }))
                }
                apps = loaded
                loading = false
            }
        }

        fun saveApps(updated: List<AppItem>) {
            apps = updated
            DataStore.individual = updated.filter { it.enabled }
                .joinToString("\n") { it.packageName }
        }

        loadApps()

        setContent {
            OwenclaveTheme {
                AppListScreen(
                    apps = apps,
                    loading = loading,
                    bypass = bypass,
                    onBypassChange = { bypass = it; DataStore.bypass = it },
                    onBack = { finish() },
                    onToggle = { app ->
                        saveApps(apps.map {
                            if (it.packageName == app.packageName) it.copy(enabled = !it.enabled)
                            else it
                        }.sortedWith(compareBy({ !it.enabled }, { it.label })))
                    },
                    onInvert = {
                        saveApps(apps.map { it.copy(enabled = !it.enabled) }
                            .sortedWith(compareBy({ !it.enabled }, { it.label })))
                    },
                    onClear = {
                        saveApps(apps.map { it.copy(enabled = false) }
                            .sortedWith(compareBy({ !it.enabled }, { it.label })))
                    },
                    onCopy = {
                        val text = "${DataStore.bypass}\n${DataStore.individual}"
                        SagerNet.trySetPrimaryClip(text)
                    },
                    onDisable = {
                        DataStore.proxyApps = false
                        finish()
                    },
                )
            }
        }
    }
}

class ComposeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwenclaveTheme {
                ScannerScreen(
                    onBack = { finish() },
                    onImportFile = {},
                    onToggleFlash = {},
                    onSwitchCamera = {},
                )
            }
        }
    }
}

class ComposeGroupSettingsActivity : ComponentActivity() {

    private var editingGroupId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        editingGroupId = intent.getLongExtra("groupId", 0L)

        var initialName = ""
        var initialType = GroupType.BASIC
        var initialIconIndex = 0
        var initialOrder = 0
        var initialFrontProxy = -1L
        var initialLandingProxy = -1L
        var initialSubscription = SubscriptionSettings()

        if (editingGroupId > 0L) {
            runBlocking {
                val group = SagerDatabase.groupDao.getById(editingGroupId)
                if (group != null) {
                    initialName = group.name ?: ""
                    initialType = group.type
                    initialIconIndex = group.iconIndex
                    initialOrder = group.order
                    initialFrontProxy = group.frontProxy
                    initialLandingProxy = group.landingProxy
                    val sub = group.subscription
                    if (sub != null) {
                        initialSubscription = SubscriptionSettings(
                            link = sub.link ?: "",
                            deduplication = sub.deduplication ?: false,
                            updateWhenConnectedOnly = sub.updateWhenConnectedOnly ?: false,
                            autoUpdate = sub.autoUpdate ?: false,
                            customUserAgent = sub.customUserAgent ?: "",
                        )
                    }
                }
            }
        }

        setContent {
            OwenclaveTheme {
                GroupSettingsScreen(
                    groupName = initialName,
                    groupType = initialType,
                    initialIconIndex = initialIconIndex,
                    initialOrder = initialOrder,
                    initialFrontProxy = initialFrontProxy,
                    initialLandingProxy = initialLandingProxy,
                    initialSubscription = initialSubscription,
                    onBack = { finish() },
                    onSave = { data ->
                        runBlocking {
                            if (editingGroupId > 0L) {
                                val group = SagerDatabase.groupDao.getById(editingGroupId)
                                if (group != null) {
                                    group.name = data.name.ifEmpty { if (data.type == GroupType.SUBSCRIPTION) "Subscription" else "Group" }
                                    group.type = data.type
                                    group.iconIndex = data.iconIndex
                                    group.order = data.order
                                    group.frontProxy = data.frontProxy
                                    group.landingProxy = data.landingProxy
                                    if (data.type == GroupType.SUBSCRIPTION) {
                                        val sub = group.subscription ?: SubscriptionBean().applyDefaultValues()
                                        sub.link = data.subscription.link
                                        sub.deduplication = data.subscription.deduplication
                                        sub.updateWhenConnectedOnly = data.subscription.updateWhenConnectedOnly
                                        sub.autoUpdate = data.subscription.autoUpdate
                                        sub.customUserAgent = data.subscription.customUserAgent
                                        group.subscription = sub
                                    } else {
                                        group.subscription = null
                                    }
                                    GroupManager.updateGroup(group)
                                }
                            } else {
                                val group = ProxyGroup(
                                    name = data.name.ifEmpty { if (data.type == GroupType.SUBSCRIPTION) "Subscription" else "Group" },
                                    type = data.type,
                                    iconIndex = data.iconIndex,
                                    order = data.order,
                                    frontProxy = data.frontProxy,
                                    landingProxy = data.landingProxy,
                                )
                                if (data.type == GroupType.SUBSCRIPTION) {
                                    group.subscription = SubscriptionBean().applyDefaultValues().apply {
                                        link = data.subscription.link
                                        deduplication = data.subscription.deduplication
                                        updateWhenConnectedOnly = data.subscription.updateWhenConnectedOnly
                                        autoUpdate = data.subscription.autoUpdate
                                        customUserAgent = data.subscription.customUserAgent
                                    }
                                }
                                GroupManager.createGroup(group)
                                if (data.type == GroupType.SUBSCRIPTION && data.subscription.link.isNotEmpty()) {
                                    val created = SagerDatabase.groupDao.getById(group.id)
                                    if (created != null) {
                                        GroupUpdater.executeUpdate(created, true)
                                    }
                                }
                            }
                        }
                        finish()
                    },
                )
            }
        }
    }
}

class ComposeRouteSettingsActivity : ComponentActivity() {

    private var editingRuleId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        editingRuleId = intent.getLongExtra("ruleId", 0L)

        var initialName = ""
        var initialDomains = ""
        var initialIP = ""
        var initialPort = ""
        var initialSourcePort = ""
        var initialNetwork = ""
        var initialSource = ""
        var initialProtocol = ""
        var initialOutbound = 0
        var initialPackages = ""

        if (editingRuleId > 0L) {
            runBlocking {
                val rule = SagerDatabase.rulesDao.getById(editingRuleId)
                if (rule != null) {
                    initialName = rule.name
                    initialDomains = rule.domains ?: ""
                    initialIP = rule.ip ?: ""
                    initialPort = rule.port ?: ""
                    initialSourcePort = rule.sourcePort ?: ""
                    initialNetwork = rule.network ?: ""
                    initialSource = rule.source ?: ""
                    initialProtocol = rule.protocol ?: ""
                    initialOutbound = when (rule.outbound) {
                        0L -> 0
                        -1L -> 1
                        -2L -> 2
                        else -> 3
                    }
                    initialPackages = rule.packages.joinToString("\n")
                }
            }
        }

        setContent {
            OwenclaveTheme {
                RouteSettingsScreen(
                    routeName = initialName,
                    routeDomain = initialDomains,
                    routeIP = initialIP,
                    routePort = initialPort,
                    routeSourcePort = initialSourcePort,
                    routeNetwork = initialNetwork,
                    routeSource = initialSource,
                    routeProtocol = initialProtocol,
                    routeOutbound = initialOutbound,
                    routePackages = initialPackages,
                    onBack = { finish() },
                    onSave = { name, domains, ip, port, sourcePort, network, source, protocol, outbound, packages ->
                        runBlocking {
                            val rule = if (editingRuleId > 0L) {
                                SagerDatabase.rulesDao.getById(editingRuleId) ?: RuleEntity()
                            } else {
                                RuleEntity()
                            }
                            rule.name = name
                            rule.domains = domains
                            rule.ip = ip
                            rule.port = port
                            rule.sourcePort = sourcePort
                            rule.network = network
                            rule.source = source
                            rule.protocol = protocol
                            rule.outbound = when (outbound) {
                                0 -> 0L
                                1 -> -1L
                                2 -> -2L
                                else -> 0L
                            }
                            rule.packages = packages.split("\n").filter { it.isNotEmpty() }
                            if (editingRuleId == 0L) {
                                rule.enabled = true
                                ProfileManager.createRule(rule)
                            } else {
                                ProfileManager.updateRule(rule)
                            }
                        }
                        finish()
                    },
                )
            }
        }
    }
}

class ComposeConfigEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val content = intent.getStringExtra("content") ?: ""
        setContent {
            OwenclaveTheme {
                ConfigEditScreen(
                    initialContent = content,
                    onBack = { finish() },
                    onSave = { finish() },
                )
            }
        }
    }
}

class ComposeStunActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwenclaveTheme {
                StunScreen(onBack = { finish() })
            }
        }
    }
}

class ComposeProbeCertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwenclaveTheme {
                ProbeCertScreen(onBack = { finish() })
            }
        }
    }
}

class ComposeProfileSelectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_PROFILE_ID = "profileId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val selectedId = intent.getLongExtra(EXTRA_SELECTED, 0L)
        setContent {
            OwenclaveTheme {
                ProfileSelectScreen(
                    profiles = emptyList(),
                    selectedId = selectedId,
                    onSelect = { profile ->
                        setResult(RESULT_OK, intent.putExtra(EXTRA_PROFILE_ID, profile.id))
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

class ComposeQuickToggleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwenclaveTheme {
                QuickSwitchScreen(
                    profiles = emptyList(),
                    currentProfileId = DataStore.selectedProxy,
                    onSelect = { profile ->
                        DataStore.selectedProxy = profile.id
                        io.nekohasekai.sagernet.SagerNet.reloadService()
                        finish()
                    },
                )
            }
        }
    }
}

class ComposeProfileSettingsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_PROFILE_TYPE = "profileType"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
        val profileType = intent.getIntExtra(EXTRA_PROFILE_TYPE, ProxyEntity.TYPE_SOCKS)

        var entity: ProxyEntity? = null
        var initialState = ProfileFieldState()

        if (profileId > 0L) {
            runBlocking {
                entity = SagerDatabase.proxyDao.getById(profileId)
                if (entity != null) {
                    initialState = beanToState(entity!!, profileType)
                }
            }
        }

        setContent {
            OwenclaveTheme {
                UniversalProfileSettingsScreen(
                    profileType = profileType,
                    initialState = initialState,
                    onBack = { finish() },
                    onSave = { state ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            saveProfile(entity, profileId, profileType, state)
                            withContext(Dispatchers.Main) { finish() }
                        }
                    },
                )
            }
        }
    }

    private fun beanToState(entity: ProxyEntity, type: Int): ProfileFieldState {
        val bean = entity.requireBean()
        val s = ProfileFieldState(
            name = bean.name ?: "",
            serverAddress = bean.serverAddress ?: "",
            serverPort = bean.serverPort?.toString() ?: "",
            iconIndex = entity.iconIndex,
        )
        return when (type) {
            ProxyEntity.TYPE_SOCKS -> {
                val b = entity.socksBean ?: return s
                s.copy(username = b.username ?: "", password = b.password ?: "")
            }
            ProxyEntity.TYPE_HTTP -> {
                val b = entity.httpBean ?: return s
                s.copy(username = b.username ?: "", password = b.password ?: "")
            }
            ProxyEntity.TYPE_SS -> {
                val b = entity.ssBean ?: return s
                s.copy(method = b.method ?: "", password = b.password ?: "")
            }
            ProxyEntity.TYPE_SSR -> {
                val b = entity.ssrBean ?: return s
                s.copy(password = b.password ?: "", method = b.method ?: "",
                    ssrProtocol = b.protocol ?: "origin", protocolParam = b.protocolParam ?: "",
                    obfs = b.obfs ?: "plain", obfsParam = b.obfsParam ?: "")
            }
            ProxyEntity.TYPE_VMESS -> {
                val b = entity.vmessBean ?: return s
                s.copy(uuid = b.uuid ?: "", encryption = b.encryption ?: "auto",
                    network = b.type ?: "tcp", security = b.security ?: "none",
                    sni = b.sni ?: "", alpn = b.alpn ?: "", host = b.host ?: "", path = b.path ?: "")
            }
            ProxyEntity.TYPE_VLESS -> {
                val b = entity.vlessBean ?: return s
                s.copy(uuid = b.uuid ?: "", flow = b.flow ?: "", encryption = b.encryption ?: "none",
                    network = b.type ?: "tcp", security = b.security ?: "none",
                    sni = b.sni ?: "", alpn = b.alpn ?: "", host = b.host ?: "", path = b.path ?: "")
            }
            ProxyEntity.TYPE_TROJAN -> {
                val b = entity.trojanBean ?: return s
                s.copy(password = b.password ?: "",
                    network = b.type ?: "tcp", security = b.security ?: "none",
                    sni = b.sni ?: "", alpn = b.alpn ?: "")
            }
            ProxyEntity.TYPE_NAIVE -> {
                val b = entity.naiveBean ?: return s
                s.copy(network = b.proto ?: "https", username = b.username ?: "",
                    password = b.password ?: "", sni = b.sni ?: "", certificates = b.certificate ?: "")
            }
            ProxyEntity.TYPE_HYSTERIA2 -> {
                val b = entity.hysteria2Bean ?: return s
                s.copy(password = b.auth ?: "", sni = b.sni ?: "",
                    serverPorts = b.serverPorts ?: "",
                    obfsType = b.obfsType ?: "", obfsPassword = b.obfsPassword ?: "",
                    uploadSpeed = b.uploadMbps?.toString() ?: "", downloadSpeed = b.downloadMbps?.toString() ?: "",
                    congestionControl = b.congestionControl ?: "bbr",
                    certificates = b.certificates ?: "", allowInsecure = b.allowInsecure ?: false,
                    echEnabled = b.echEnabled ?: false, echConfig = b.echConfig ?: "")
            }
            ProxyEntity.TYPE_SSH -> {
                val b = entity.sshBean ?: return s
                s.copy(username = b.username ?: "root", authType = b.authType ?: 0,
                    password = b.password ?: "", privateKey = b.privateKey ?: "",
                    publicKey = b.publicKey ?: "")
            }
            ProxyEntity.TYPE_WG -> {
                val b = entity.wgBean ?: return s
                s.copy(localAddress = b.localAddress ?: "", privateKeyWg = b.privateKey ?: "",
                    peerPublicKey = b.peerPublicKey ?: "", peerPreSharedKey = b.peerPreSharedKey ?: "",
                    mtu = b.mtu?.toString() ?: "1420", reserved = b.reserved ?: "")
            }
            ProxyEntity.TYPE_TUIC5 -> {
                val b = entity.tuic5Bean ?: return s
                s.copy(uuid = b.uuid ?: "", password = b.password ?: "",
                    sni = b.sni ?: "", alpn = b.alpn ?: "",
                    congestionControl = b.congestionControl ?: "cubic",
                    certificates = b.certificates ?: "", allowInsecure = b.allowInsecure ?: false,
                    disableSNI = b.disableSNI ?: false, zeroRTT = b.zeroRTTHandshake ?: false,
                    echEnabled = b.echEnabled ?: false, echConfig = b.echConfig ?: "")
            }
            ProxyEntity.TYPE_JUICITY -> {
                val b = entity.juicityBean ?: return s
                s.copy(uuid = b.uuid ?: "", password = b.password ?: "",
                    sni = b.sni ?: "", certificates = b.certificates ?: "",
                    allowInsecure = b.allowInsecure ?: false,
                    echEnabled = b.echEnabled ?: false, echConfig = b.echConfig ?: "")
            }
            ProxyEntity.TYPE_HTTP3 -> {
                val b = entity.http3Bean ?: return s
                s.copy(username = b.username ?: "", password = b.password ?: "",
                    sni = b.sni ?: "", certificates = b.certificates ?: "",
                    allowInsecure = b.allowInsecure ?: false,
                    echEnabled = b.echEnabled ?: false, echConfig = b.echConfig ?: "")
            }
            ProxyEntity.TYPE_ANYTLS -> {
                val b = entity.anytlsBean ?: return s
                s.copy(password = b.password ?: "", security = b.security ?: "tls",
                    sni = b.sni ?: "", alpn = b.alpn ?: "",
                    certificates = b.certificates ?: "", utlsFingerprint = b.utlsFingerprint ?: "",
                    allowInsecure = b.allowInsecure ?: false,
                    echEnabled = b.echEnabled ?: false, echConfig = b.echConfig ?: "",
                    realityPublicKey = b.realityPublicKey ?: "", realityShortId = b.realityShortId ?: "")
            }
            ProxyEntity.TYPE_SHADOWQUIC -> {
                val b = entity.shadowquicBean ?: return s
                s.copy(username = b.username ?: "", password = b.password ?: "",
                    sni = b.sni ?: "", alpn = b.alpn ?: "",
                    congestionControl = b.congestionControl ?: "bbr",
                    zeroRTT = b.zeroRTT ?: false, disableSNI = b.disableALPN ?: false)
            }
            ProxyEntity.TYPE_TRUSTTUNNEL -> {
                val b = entity.trustTunnelBean ?: return s
                s.copy(network = b.protocol ?: "https", username = b.username ?: "",
                    password = b.password ?: "", sni = b.sni ?: "",
                    certificates = b.certificate ?: "", utlsFingerprint = b.utlsFingerprint ?: "",
                    allowInsecure = b.allowInsecure ?: false,
                    echEnabled = b.echEnabled ?: false, echConfig = b.echConfig ?: "")
            }
            ProxyEntity.TYPE_SNELL -> {
                val b = entity.snellBean ?: return s
                s.copy(psk = b.psk ?: "", snellVersion = b.version ?: 4,
                    obfsType = b.obfsMode ?: "none", host = b.obfsHost ?: "")
            }
            ProxyEntity.TYPE_OLCRTC -> {
                val b = entity.olcrtcBean ?: return s
                s.copy(authProvider = b.authProvider ?: "jitsi", transport = b.transport ?: "datachannel",
                    roomId = b.roomId ?: "", encryptionKey = b.encryptionKey ?: "",
                    dnsServer = b.dnsServer ?: "8.8.8.8:53", socksHost = b.socksHost ?: "127.0.0.1",
                    socksPort = b.socksPort?.toString() ?: "8808")
            }
            else -> s
        }
    }

    private suspend fun saveProfile(existing: ProxyEntity?, profileId: Long, type: Int, state: ProfileFieldState) {
        val entity = existing ?: ProxyEntity(type = type)
        entity.type = type

        when (type) {
            ProxyEntity.TYPE_SOCKS -> {
                val b = entity.socksBean ?: SOCKSBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 1080
                b.username = state.username
                b.password = state.password
                entity.socksBean = b
            }
            ProxyEntity.TYPE_HTTP -> {
                val b = entity.httpBean ?: HttpBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 8080
                b.username = state.username
                b.password = state.password
                entity.httpBean = b
            }
            ProxyEntity.TYPE_SS -> {
                val b = entity.ssBean ?: ShadowsocksBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 8388
                b.method = state.method
                b.password = state.password
                entity.ssBean = b
            }
            ProxyEntity.TYPE_SSR -> {
                val b = entity.ssrBean ?: ShadowsocksRBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 8388
                b.password = state.password
                b.method = state.method
                b.protocol = state.ssrProtocol
                b.protocolParam = state.protocolParam
                b.obfs = state.obfs
                b.obfsParam = state.obfsParam
                entity.ssrBean = b
            }
            ProxyEntity.TYPE_VMESS -> {
                val b = entity.vmessBean ?: VMessBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.uuid = state.uuid
                b.encryption = state.encryption.ifEmpty { "auto" }
                b.type = state.network
                b.security = state.security
                b.sni = state.sni
                b.alpn = state.alpn
                b.host = state.host
                b.path = state.path
                b.allowInsecure = state.allowInsecure
                b.utlsFingerprint = state.utlsFingerprint
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                b.mux = state.mux
                b.singMux = state.singMux
                entity.vmessBean = b
            }
            ProxyEntity.TYPE_VLESS -> {
                val b = entity.vlessBean ?: VLESSBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.uuid = state.uuid
                b.flow = state.flow
                b.encryption = state.encryption.ifEmpty { "none" }
                b.type = state.network
                b.security = state.security
                b.sni = state.sni
                b.alpn = state.alpn
                b.host = state.host
                b.path = state.path
                b.allowInsecure = state.allowInsecure
                b.utlsFingerprint = state.utlsFingerprint
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                b.mux = state.mux
                b.singMux = state.singMux
                entity.vlessBean = b
            }
            ProxyEntity.TYPE_TROJAN -> {
                val b = entity.trojanBean ?: TrojanBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.password = state.password
                b.type = state.network
                b.security = state.security
                b.sni = state.sni
                b.alpn = state.alpn
                b.host = state.host
                b.path = state.path
                b.allowInsecure = state.allowInsecure
                b.utlsFingerprint = state.utlsFingerprint
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                b.mux = state.mux
                b.singMux = state.singMux
                entity.trojanBean = b
            }
            ProxyEntity.TYPE_NAIVE -> {
                val b = entity.naiveBean ?: NaiveBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.proto = state.network.ifEmpty { "https" }
                b.username = state.username
                b.password = state.password
                b.sni = state.sni
                b.certificate = state.certificates
                entity.naiveBean = b
            }
            ProxyEntity.TYPE_HYSTERIA2 -> {
                val b = entity.hysteria2Bean ?: Hysteria2Bean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPorts = state.serverPorts.ifEmpty { state.serverPort }
                b.auth = state.password
                b.sni = state.sni
                b.obfsType = state.obfsType
                b.obfsPassword = state.obfsPassword
                b.uploadMbps = state.uploadSpeed.toLongOrNull() ?: 0L
                b.downloadMbps = state.downloadSpeed.toLongOrNull() ?: 0L
                b.congestionControl = state.congestionControl
                b.certificates = state.certificates
                b.allowInsecure = state.allowInsecure
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                entity.hysteria2Bean = b
            }
            ProxyEntity.TYPE_SSH -> {
                val b = entity.sshBean ?: SSHBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 22
                b.username = state.username
                b.authType = state.authType
                b.password = state.password
                b.privateKey = state.privateKey
                b.publicKey = state.publicKey
                entity.sshBean = b
            }
            ProxyEntity.TYPE_WG -> {
                val b = entity.wgBean ?: WireGuardBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 51820
                b.localAddress = state.localAddress
                b.privateKey = state.privateKeyWg
                b.peerPublicKey = state.peerPublicKey
                b.peerPreSharedKey = state.peerPreSharedKey
                b.mtu = state.mtu.toIntOrNull() ?: 1420
                b.reserved = state.reserved
                entity.wgBean = b
            }
            ProxyEntity.TYPE_TUIC5 -> {
                val b = entity.tuic5Bean ?: Tuic5Bean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.uuid = state.uuid
                b.password = state.password
                b.sni = state.sni
                b.alpn = state.alpn
                b.congestionControl = state.congestionControl
                b.certificates = state.certificates
                b.allowInsecure = state.allowInsecure
                b.disableSNI = state.disableSNI
                b.zeroRTTHandshake = state.zeroRTT
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                entity.tuic5Bean = b
            }
            ProxyEntity.TYPE_JUICITY -> {
                val b = entity.juicityBean ?: JuicityBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.uuid = state.uuid
                b.password = state.password
                b.sni = state.sni
                b.certificates = state.certificates
                b.allowInsecure = state.allowInsecure
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                entity.juicityBean = b
            }
            ProxyEntity.TYPE_HTTP3 -> {
                val b = entity.http3Bean ?: Http3Bean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.username = state.username
                b.password = state.password
                b.sni = state.sni
                b.certificates = state.certificates
                b.allowInsecure = state.allowInsecure
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                entity.http3Bean = b
            }
            ProxyEntity.TYPE_ANYTLS -> {
                val b = entity.anytlsBean ?: AnyTLSBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.password = state.password
                b.security = state.security
                b.sni = state.sni
                b.alpn = state.alpn
                b.certificates = state.certificates
                b.utlsFingerprint = state.utlsFingerprint
                b.allowInsecure = state.allowInsecure
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                b.realityPublicKey = state.realityPublicKey
                b.realityShortId = state.realityShortId
                entity.anytlsBean = b
            }
            ProxyEntity.TYPE_SHADOWQUIC -> {
                val b = entity.shadowquicBean ?: ShadowQUICBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.username = state.username
                b.password = state.password
                b.sni = state.sni
                b.alpn = state.alpn
                b.congestionControl = state.congestionControl
                b.zeroRTT = state.zeroRTT
                b.disableALPN = state.disableSNI
                entity.shadowquicBean = b
            }
            ProxyEntity.TYPE_TRUSTTUNNEL -> {
                val b = entity.trustTunnelBean ?: TrustTunnelBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 443
                b.protocol = state.network.ifEmpty { "https" }
                b.username = state.username
                b.password = state.password
                b.sni = state.sni
                b.certificate = state.certificates
                b.utlsFingerprint = state.utlsFingerprint
                b.allowInsecure = state.allowInsecure
                b.echEnabled = state.echEnabled
                b.echConfig = state.echConfig
                entity.trustTunnelBean = b
            }
            ProxyEntity.TYPE_SNELL -> {
                val b = entity.snellBean ?: SnellBean().applyDefaultValues()
                b.name = state.name
                b.serverAddress = state.serverAddress
                b.serverPort = state.serverPort.toIntOrNull() ?: 8388
                b.psk = state.psk
                b.version = state.snellVersion
                b.obfsMode = state.obfsType
                b.obfsHost = state.host
                entity.snellBean = b
            }
            ProxyEntity.TYPE_OLCRTC -> {
                val b = entity.olcrtcBean ?: OLCRTCBean().applyDefaultValues()
                b.name = state.name
                b.authProvider = state.authProvider
                b.transport = state.transport
                b.roomId = state.roomId
                b.encryptionKey = state.encryptionKey
                b.dnsServer = state.dnsServer
                b.socksHost = state.socksHost
                b.socksPort = state.socksPort.toIntOrNull() ?: 8808
                entity.olcrtcBean = b
            }
        }

        entity.iconIndex = state.iconIndex

        if (profileId > 0L) {
            ProfileManager.updateProfile(entity)
        } else {
            val groupId = DataStore.currentGroupId()
            entity.groupId = groupId
            ProfileManager.createProfile(groupId, entity.requireBean())
        }
    }
}
