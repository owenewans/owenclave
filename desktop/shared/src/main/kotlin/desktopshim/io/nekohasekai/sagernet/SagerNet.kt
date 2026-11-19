package io.nekohasekai.sagernet

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.sagernet.bg.ProxyService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.bg.test.DebugInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.CrashHandler
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.DeviceStorageApp
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import libexclavecore.AndroidCAStore
import libexclavecore.Libexclavecore
import libexclavecore.UidDumper
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.File
import java.io.StringWriter
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.security.KeyStore

class SagerNet : Application(),
    UidDumper,
    AndroidCAStore {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        application = this
    }

    val externalAssets: File by lazy { getExternalFilesDir(null) ?: filesDir }

    fun initCore() {
        ensureCoreAssetsExtracted()
        runCatching {
            Libexclavecore.setenv("exclave.conf.geoloader", "memconservative")
            externalAssets.mkdirs()
            Libexclavecore.initializeV2Ray(
                externalAssets.canonicalPath + File.separator,
                "owenclave-core/",
            )
        }.onFailure {
            Logs.w(it)
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        DataStore.init()
        updateNotificationChannels()
        Seq.setContext(this)

        if (DataStore.goDebug.isNotEmpty()) {
            runCatching { Libexclavecore.setenv("GODEBUG", DataStore.goDebug) }
        }
        if (DataStore.environmentVariables.isNotEmpty()) {
            for (variable in DataStore.environmentVariables.split("\n")) {
                if (!variable.contains("=")) continue
                runCatching {
                    Libexclavecore.setenv(
                        variable.substringBefore("="),
                        variable.substringAfter("=")
                    )
                }
            }
        }

        runOnDefaultDispatcher {
            PackageCache.register()
        }

        runCatching {
            Libexclavecore.setUidDumper(this, false)
            if (DataStore.enableDebug && DataStore.pprofServer.isNotEmpty()) {
                DebugInstance().launch()
            }
        }

        runOnDefaultDispatcher {
            runCatching {
                SubscriptionUpdater.reconfigureUpdater()
            }
        }

        initCore()

        try {
            Libexclavecore.registerAndroidCAStore(this)
            val provider = when (DataStore.providerRootCA) {
                RootCAProvider.MOZILLA -> RootCAProvider.ANDROID
                else -> DataStore.providerRootCA
            }
            Libexclavecore.updateSystemRoots(provider)
        } catch (e: Exception) {
            Logs.w(e)
        }

        Theme.apply(this)
        Theme.applyNightTheme()
    }

    private fun ensureCoreAssetsExtracted() {
        externalAssets.mkdirs()
        for (name in listOf("geoip.version.txt", "geosite.version.txt", "geoip.dat", "geosite.dat")) {
            val target = File(externalAssets, name)
            if (!target.exists()) {
                DesktopResourceLoader.openAssetStream("owenclave-core/$name")?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    override fun certificates(): ByteArray {
        if (DataStore.providerRootCA == RootCAProvider.MOZILLA) {
            DesktopResourceLoader.openAssetStream("mozilla_included.pem")?.use {
                return it.readBytes()
            }
        }
        try {
            val trustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(null as KeyStore?)
            val stringWriter = StringWriter()
            val pemWriter = PemWriter(stringWriter)
            for (tm in trustManagerFactory.trustManagers) {
                if (tm is javax.net.ssl.X509TrustManager) {
                    for (cert in tm.acceptedIssuers) {
                        pemWriter.writeObject(PemObject("CERTIFICATE", cert.encoded))
                    }
                }
            }
            pemWriter.close()
            val bytes = stringWriter.toString().toByteArray()
            if (bytes.isNotEmpty()) return bytes
        } catch (_: Exception) {
        }
        return DesktopResourceLoader.openAssetStream("mozilla_included.pem")?.use { it.readBytes() }
            ?: ByteArray(0)
    }

    override fun dumpUid(
        ipProto: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int
    ): Int {
        return connectivity.getConnectionOwnerUid(
            ipProto, InetSocketAddress(srcIp, srcPort), InetSocketAddress(destIp, destPort)
        )
    }

    override fun getPackageName(uid: Int): String? {
        PackageCache.awaitLoadSync()
        if (uid == 1000) return "android"
        val packageNames = PackageCache.uidMap[uid]
        if (!packageNames.isNullOrEmpty()) return packageNames.first()
        return null
    }

    fun getPackageInfo(packageName: String): PackageInfo =
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)!!

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

    companion object {
        @Volatile
        var started = false

        init {
            Context.serviceStarter = { _, intent ->
                startDesktopService(intent)
            }
            Context.serviceBinder = { _, intent, conn, _ ->
                bindDesktopService(intent, conn)
            }
        }

        private var appInstance: SagerNet = SagerNet()
        @Volatile
        private var coreInitialized: Boolean = false
        @Volatile
        private var isCreated: Boolean = false
        @Volatile
        private var inGetApplication: Boolean = false

        var application: SagerNet
            get() {
                if (!coreInitialized) {
                    synchronized(this) {
                        if (!coreInitialized) {
                            coreInitialized = true
                            appInstance.initCore()
                        }
                    }
                }
                if (!isCreated && !inGetApplication) {
                    synchronized(this) {
                        if (!isCreated && !inGetApplication) {
                            inGetApplication = true
                            try {
                                val props: java.util.Properties? = DataStore.experimentalFlagsProperties
                                if (props != null) {
                                    isCreated = true
                                    appInstance.onCreate()
                                }
                            } finally {
                                inGetApplication = false
                            }
                        }
                    }
                }
                return appInstance
            }
            set(value) {
                appInstance = value
            }

        val activeService: ProxyService by lazy {
            ProxyService().apply { onCreate() }
        }

        fun startDesktopService(intent: Intent) {
            activeService.onStartCommand(intent, 0, 0)
        }

        fun bindDesktopService(intent: Intent, conn: ServiceConnection): Boolean {
            val binder = activeService.onBind(intent) ?: return false
            conn.onServiceConnected(
                intent.component ?: ComponentName(application, ProxyService::class.java),
                binder
            )
            return true
        }

        val isTv: Boolean = false

        val deviceStorage by lazy {
            DeviceStorageApp(appInstance)
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it,
                    0,
                    Intent(),
                    PendingIntent.FLAG_IMMUTABLE
                )
            }
        }

        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val ime by lazy { application.getSystemService<InputMethodManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val uiMode by lazy { application.getSystemService<UiModeManager>()!! }
        val power by lazy { application.getSystemService<PowerManager>()!! }
        val wifi by lazy { application.getSystemService<WifiManager>()!! }
        val location by lazy { application.getSystemService<LocationManager>()!! }
        val locale by lazy { application.getSystemService<LocaleManager>()!! }

        val currentProfile get() = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)

        fun getClipboardText(): String {
            return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: ""
        }

        fun trySetPrimaryClip(clip: String) = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, clip))
            true
        } catch (e: RuntimeException) {
            Logs.w(e)
            false
        }

        fun updateNotificationChannels() {}

        var currentNetwork: Network? = null
        var currentLinkAddresses: Set<LinkAddress>? = null

        fun reloadNetwork(network: Network?) {
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return
            val linkProperties = connectivity.getLinkProperties(network) ?: return

            val networkType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "data"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "ethernet"
            }
            runCatching { Libexclavecore.setNetworkType(networkType) }
            runCatching { Libexclavecore.setSSID("") }

            val linkAddresses = linkProperties.linkAddresses.toSet()
            runCatching {
                Libexclavecore.setDiscardIPv6(
                    !linkAddresses.any { it.address is Inet6Address && !it.address.isLinkLocalAddress }
                )
            }

            if (DataStore.interruptReusedConnections) {
                val networkChanged = currentNetwork != null && currentNetwork != network
                val linkAddressesChanged = currentLinkAddresses != null && !linkAddresses.containsAll(currentLinkAddresses!!)
                if (networkChanged || linkAddressesChanged) {
                    runCatching { Libexclavecore.interfaceUpdate() }
                }
            }

            currentNetwork = network
            currentLinkAddresses = linkAddresses
        }

        fun startService() = ContextCompat.startForegroundService(
            application, Intent(application, SagerConnection.serviceClass)
        )

        fun reloadService() =
            application.sendBroadcast(Intent(Action.RELOAD).setPackage(application.packageName))

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))
    }
}
