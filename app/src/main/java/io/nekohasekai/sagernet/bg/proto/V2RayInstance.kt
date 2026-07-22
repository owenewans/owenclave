/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.bg.proto

import android.annotation.SuppressLint
import android.os.SystemClock
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.nekohasekai.sagernet.RootCAProvider
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.AbstractInstance
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.V2rayBuildResult
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowquic.buildShadowQUICConfig
import io.nekohasekai.sagernet.fmt.olcrtc.OLCRTCBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager
import kotlinx.coroutines.*
import libexclavecore.V2RayInstance
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

    abstract class V2RayInstance(
        val profile: ProxyEntity,
    ) : AbstractInstance {

        companion object {
            // How long to keep the connecting phase while waiting for an
            // external engine's local SOCKS port to come up.
            private const val READINESS_TIMEOUT_MS = 30_000L
            private const val READINESS_POLL_INTERVAL_MS = 250L
            private const val READINESS_CONNECT_TIMEOUT_MS = 500
        }

        protected fun buildOlcrtcYaml(bean: OLCRTCBean, port: Int, username: String, password: String): String {
            // olcrtc resolves a relative `data` dir against the executable dir
            // (read-only nativeLibraryDir on Android), so use an absolute writable path.
            val dataDir = File(SagerNet.application.noBackupFilesDir, "olcrtc_data").apply { mkdirs() }
            return buildString {
                appendLine("mode: cnc")
                appendLine("auth:")
                appendLine("  provider: ${bean.authProvider}")
                appendLine("room:")
                appendLine("  id: \"${bean.roomId}\"")
                appendLine("crypto:")
                appendLine("  key: \"${bean.encryptionKey}\"")
                appendLine("net:")
                appendLine("  transport: ${bean.transport}")
                appendLine("  dns: \"${bean.dnsServer}\"")
                appendLine("socks:")
                appendLine("  host: \"127.0.0.1\"")
                appendLine("  port: $port")
                if (username.isNotEmpty()) appendLine("  user: \"$username\"")
                if (password.isNotEmpty()) appendLine("  pass: \"$password\"")
                appendLine("data: \"${dataDir.absolutePath}\"")
                appendLine("debug: true")
            }.also {
                Logs.d("olcrtc yaml config for port $port:\n$it")
            }
        }

    lateinit var config: V2rayBuildResult
    lateinit var v2rayPoint: V2RayInstance
    private lateinit var wsForwarder: WebView
    private lateinit var shForwarder: WebView

    val pluginPath = hashMapOf<String, PluginManager.InitResult>()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    val externalInstances = hashMapOf<Int, AbstractInstance>()

    // Local SOCKS ports of external engines that need to finish bringing up
    // their transport before they can pass traffic (e.g. olcrtc WebRTC).
    // awaitReady() waits for these to accept connections.
    private val readinessPorts = mutableListOf<Int>()
    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized
    }

    protected fun initPlugin(name: String): PluginManager.InitResult {
        return pluginPath.getOrPut(name) { PluginManager.init(name)!! }
    }

    protected open fun buildConfig() {
        config = buildV2RayConfig(profile)
    }

    protected open fun loadConfig() {
        v2rayPoint.loadConfig(config.config)
    }

    open suspend fun init() {
        v2rayPoint = V2RayInstance()
        buildConfig()
        for ((_, chain) in config.index) {
            chain.entries.forEachIndexed { _, (triple, profile) ->
                val port = triple.first
                val username = triple.second
                val password = triple.third
                when (val bean = profile.requireBean()) {
                    is NaiveBean -> {
                        initPlugin("naive-plugin")
                        pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port, username, password)
                    }
                    is ShadowQUICBean -> {
                        initPlugin("shadowquic-plugin")
                        pluginConfigs[port] = profile.type to bean.buildShadowQUICConfig(
                            port, username, password,
                            {
                                File(app.noBackupFilesDir, "shadowquic_" + SystemClock.elapsedRealtime() + ".pem").apply {
                                    parentFile?.mkdirs()
                                    cacheFiles.add(this)
                                }
                            }
                        )
                    }
                    is OLCRTCBean -> {
                        val yamlConfig = buildOlcrtcYaml(bean, port, username, password)
                        pluginConfigs[port] = profile.type to yamlConfig
                    }
                }
            }
        }
        loadConfig()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun launch() {
        val context = SagerNet.application
        for ((_, chain) in config.index) {
            chain.entries.forEachIndexed { _, (triple, profile) ->
                val port = triple.first
                val bean = profile.requireBean()
                val (_, config) = pluginConfigs[port] ?: (0 to "")
                val env = mutableMapOf<String, String>()
                if (DataStore.providerRootCA != RootCAProvider.SYSTEM) {
                    env["SSL_CERT_FILE"] = when (DataStore.providerRootCA) {
                        RootCAProvider.MOZILLA -> {
                            File(app.filesDir, "mozilla_included.pem").canonicalPath
                        }
                        RootCAProvider.SYSTEM_AND_USER -> {
                            File(app.filesDir, "android_included.pem").canonicalPath
                        }
                        RootCAProvider.CUSTOM -> {
                            File(app.externalAssets, "root_store.certs").canonicalPath
                        }
                        else -> error("impossible")
                    }
                }
                when {
                    externalInstances.containsKey(port) -> {
                        externalInstances[port]!!.launch()
                    }
                    bean is NaiveBean -> {
                        val configFile = File(
                            context.noBackupFilesDir,
                            "naive_" + SystemClock.elapsedRealtime() + ".json"
                        )
                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)
                        if (bean.certificate.isNotEmpty()) {
                            val caFile = File(
                                context.noBackupFilesDir,
                                "naive_" + SystemClock.elapsedRealtime() + ".ca"
                            )
                            caFile.parentFile?.mkdirs()
                            caFile.writeText(bean.certificate)
                            cacheFiles.add(caFile)
                            env["SSL_CERT_FILE"] = caFile.absolutePath
                        }
                        val commands = mutableListOf(
                            initPlugin("naive-plugin").path, configFile.absolutePath
                        )
                        processes.start(commands, env)
                    }
                    bean is ShadowQUICBean -> {
                        val configFile = File(
                            context.noBackupFilesDir,
                            "shadowquic_" + SystemClock.elapsedRealtime() + ".yaml"
                        )
                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)
                        if (DataStore.providerRootCA == RootCAProvider.SYSTEM) {
                            // https://github.com/rustls/rustls-native-certs/issues/3
                            env["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
                        }
                        val commands = mutableListOf(
                            initPlugin("shadowquic-plugin").path,
                            "-c",
                            configFile.absolutePath,
                        )
                        processes.start(commands, env)
                    }
                    bean is OLCRTCBean -> {
                        val configFile = File(
                            context.noBackupFilesDir,
                            "olcrtc_" + SystemClock.elapsedRealtime() + ".yaml"
                        )
                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)
                        val olcrtcBin = File(context.applicationInfo.nativeLibraryDir, "libolcrtc.so")
                        Logs.i("olcrtc: nativeLibraryDir=${context.applicationInfo.nativeLibraryDir}")
                        Logs.i("olcrtc: binary=${olcrtcBin.absolutePath} exists=${olcrtcBin.exists()} canExecute=${olcrtcBin.canExecute()} size=${if (olcrtcBin.exists()) olcrtcBin.length() else -1}")
                        Logs.i("olcrtc: config file=${configFile.absolutePath} exists=${configFile.exists()}")
                        Logs.d("olcrtc: config content:\n$config")
                        if (!olcrtcBin.exists()) {
                            Logs.e("olcrtc: binary libolcrtc.so NOT FOUND in nativeLibraryDir. " +
                                "Check that jniLibs/<abi>/libolcrtc.so is packaged and extractNativeLibs/useLegacyPackaging is enabled.")
                        }
                        val commands = mutableListOf(
                            olcrtcBin.absolutePath,
                            configFile.absolutePath
                        )
                        Logs.i("olcrtc: launching: ${commands.joinToString(" ")}")
                        processes.start(commands, env)
                        // olcrtc needs to negotiate WebRTC before its local
                        // SOCKS listener is usable; awaitReady() waits on it.
                        readinessPorts.add(port)
                    }
                }
            }
        }
        v2rayPoint.start()
        if (config.requireWs) {
            val url = "http://" + joinHostPort(LOCALHOST, config.wsPort) + "/"
            runOnMainDispatcher {
                wsForwarder = WebView(context)
                wsForwarder.settings.javaScriptEnabled = true
                wsForwarder.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        Logs.d("WebView load r: $error")
                        runOnMainDispatcher {
                            wsForwarder.loadUrl("about:blank")
                            delay(1000L)
                            wsForwarder.loadUrl(url)
                        }
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        Logs.d("WebView loaded: ${view.title}")
                    }
                }
                wsForwarder.loadUrl(url)
            }
        }
        if (config.requireSh) {
            val url = "http://" + joinHostPort(LOCALHOST, config.shPort) + "/"
            runOnMainDispatcher {
                shForwarder = WebView(context)
                shForwarder.settings.javaScriptEnabled = true
                shForwarder.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        Logs.d("WebView load r: $error")
                        runOnMainDispatcher {
                            shForwarder.loadUrl("about:blank")
                            delay(1000L)
                            shForwarder.loadUrl(url)
                        }
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        Logs.d("WebView loaded: ${view.title}")
                    }
                }
                shForwarder.loadUrl(url)
            }
        }
    }

    /**
     * Wait until every external engine's local SOCKS port accepts a TCP
     * connection, so the service stays in the Connecting state until traffic
     * can really flow (olcrtc must finish WebRTC negotiation first).
     *
     * Times out after [READINESS_TIMEOUT_MS] and returns anyway, so a stuck
     * engine still surfaces to the user as connected-but-failing rather than
     * hanging the connect flow forever.
     */
    override suspend fun awaitReady() {
        if (readinessPorts.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (port in readinessPorts) {
                val deadline = SystemClock.elapsedRealtime() + READINESS_TIMEOUT_MS
                Logs.i("readiness: waiting for local SOCKS 127.0.0.1:$port to come up (timeout ${READINESS_TIMEOUT_MS}ms)")
                var ready = false
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (!isActive) return@withContext
                    try {
                        Socket().use { sock ->
                            sock.connect(InetSocketAddress(LOCALHOST, port), READINESS_CONNECT_TIMEOUT_MS)
                        }
                        ready = true
                        break
                    } catch (_: Exception) {
                        delay(READINESS_POLL_INTERVAL_MS)
                    }
                }
                if (ready) {
                    Logs.i("readiness: local SOCKS 127.0.0.1:$port is up")
                } else {
                    Logs.w("readiness: local SOCKS 127.0.0.1:$port did not come up within ${READINESS_TIMEOUT_MS}ms; continuing anyway")
                }
            }
        }
    }

    private var isClosed = false

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun close() {
        if (isClosed) return

        for (instance in externalInstances.values) {
            runCatching {
                instance.close()
            }
        }

        cacheFiles.removeAll { it.delete(); true }

        if (::wsForwarder.isInitialized) {
            runBlocking {
                onMainDispatcher {
                    wsForwarder.loadUrl("about:blank")
                    wsForwarder.destroy()
                }
            }
        }

        if (::shForwarder.isInitialized) {
            runBlocking {
                onMainDispatcher {
                    shForwarder.loadUrl("about:blank")
                    shForwarder.destroy()
                }
            }
        }

        if (::processes.isInitialized) processes.close(GlobalScope + Dispatchers.IO)

        if (::v2rayPoint.isInitialized) {
            v2rayPoint.close()
        }

        isClosed = true
    }

}
