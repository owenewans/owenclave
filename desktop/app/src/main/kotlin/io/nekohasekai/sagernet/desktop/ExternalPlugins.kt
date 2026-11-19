package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Starts and stops the external proxy engines (NaiveProxy).
 *
 * This mirrors `bg/proto/V2RayInstance.kt` on Android: the core is configured
 * with a `socks` outbound to a random localhost port, and the engine is started
 * as a child process listening on exactly that port. Every engine is waited for
 * (its local SOCKS listener has to accept a TCP connection) before the caller
 * starts the core, so a slow engine cannot make the first request fail.
 *
 * Unlike Android there is no positive list of engines: whatever
 * `buildV2RayConfig` reports through `V2rayBuildResult.index` is started here.
 * Today that is only NaiveProxy; ShadowQUIC runs natively inside the
 * desktop core.
 */
class ExternalPlugins(private val log: (String) -> Unit) {

    private val processes = ArrayList<Pair<String, Process>>()
    private val configFiles = ArrayList<File>()

    /** True while at least one external engine is still running. */
    val alive: Boolean get() = processes.any { it.second.isAlive }

    /** True when every started external engine is still alive (or none were needed). */
    val allAlive: Boolean get() = processes.all { it.second.isAlive }

    /**
     * Starts every plugin in order.
     *
     * @param awaitPort blocking readiness check, usually `CoreRunner.awaitPort`,
     *   invoked as `(port, process, what)`.
     */
    fun start(plugins: List<ExternalPlugin>, awaitPort: (Int, Process, String) -> Unit) {
        plugins.forEach { plugin ->
            when (val bean = plugin.bean) {
                is NaiveBean -> startNaive(bean, plugin, awaitPort)
                else -> throw UnsupportedProfileException(
                    "${bean.javaClass.simpleName.removeSuffix("Bean")} runs inside the core and needs no plugin"
                )
            }
        }
    }

    private fun startNaive(
        bean: NaiveBean,
        plugin: ExternalPlugin,
        awaitPort: (Int, Process, String) -> Unit,
    ) {
        val binary = DesktopRuntime.naiveBinary()
            ?: throw UnsupportedProfileException(
                "NaiveProxy binary is not available for ${DesktopRuntime.platformTag}"
            )

        val configFile = File(DesktopRuntime.dataDir, "naive_${plugin.port}.json")
        configFile.writeText(bean.buildNaiveConfig(plugin.port, plugin.username, plugin.password))
        configFiles += configFile

        val environment = HashMap<String, String>()
        if (bean.certificate.isNotEmpty()) {
            val caFile = File(DesktopRuntime.dataDir, "naive_${plugin.port}.ca")
            caFile.writeText(bean.certificate)
            configFiles += caFile
            environment["SSL_CERT_FILE"] = caFile.absolutePath
        }

        log("starting NaiveProxy plugin on 127.0.0.1:${plugin.port}")
        log("launching ${binary.absolutePath} ${configFile.absolutePath}")
        val process = ProcessBuilder(binary.absolutePath, configFile.absolutePath)
            .directory(DesktopRuntime.dataDir)
            .redirectErrorStream(true)
            .apply { if (environment.isNotEmpty()) environment().putAll(environment) }
            .start()
        processes += "naive" to process
        pumpOutput("naive", process)

        awaitPort(plugin.port, process, "NaiveProxy plugin")
    }

    fun stop() {
        processes.forEach { (tag, process) ->
            if (!process.isAlive) return@forEach
            log("stopping $tag plugin")
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        processes.clear()
        configFiles.forEach { runCatching { it.delete() } }
        configFiles.clear()
    }

    private fun pumpOutput(tag: String, process: Process) {
        Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line -> log("[$tag] $line") }
            }
        }.apply { isDaemon = true }.start()
    }

}
