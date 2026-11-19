package io.nekohasekai.sagernet.desktop.server

import io.nekohasekai.sagernet.SagerNet
import java.util.concurrent.CountDownLatch

/**
 * CLI entry point for `--daemon` / `--server` mode.
 */
object DaemonMain {

    @JvmStatic
    fun main(args: Array<String>) {
        kotlin.system.exitProcess(run(args))
    }

    fun run(args: Array<String>): Int {
        // Initialize the shared application & JNI runtime (needed for HappLink / Hwid / Certs)
        runCatching { SagerNet.application }

        val config = DaemonConfig.load(args)
        val engine = DaemonEngine(config)
        val web = WebControllerServer(engine)
        val stopLatch = CountDownLatch(1)

        Runtime.getRuntime().addShutdownHook(
            Thread({
                runCatching { web.stop() }
                runCatching { engine.stop() }
                stopLatch.countDown()
            }, "owenclave-daemon-shutdown"),
        )

        engine.start()
        web.start()

        if (args.contains("--daemon-once")) {
            web.stop()
            engine.stop()
            return 0
        }

        stopLatch.await()
        return 0
    }

}

