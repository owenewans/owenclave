package io.nekohasekai.sagernet.desktop

/** Raised when per-app routing cannot be installed or removed. */
class PerAppRoutingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Linux per-app routing.
 *
 * The core has no process/package based routing (a V2Ray `process_name` rule is
 * rejected with "this rule has no effective fields"), and the Android app
 * expresses per-app proxying through `VpnService` package UIDs, which do not
 * exist on desktop. On Linux the operating system itself can do it, so this file
 * implements the include list the user configures:
 *
 * ```
 * listed processes -> cgroup v2 slice -> nftables mark -> policy route -> TUN device
 * unlisted processes -> the normal routing table (and the normal proxy settings)
 * ```
 *
 * 1. a cgroup v2 slice (`/sys/fs/cgroup/owenclave.slice`) holds the listed PIDs;
 * 2. `nft` marks packets whose socket belongs to that cgroup
 *    (`meta cgroup <slice inode> meta mark set 0x1`); when `nft` is missing the
 *    classic `iptables -m cgroup --path` fallback is used;
 * 3. `ip rule add fwmark 0x1 lookup 1188` plus a default route in table 1188
 *    pointing at the TUN device sends only the marked traffic into the tunnel.
 *
 * The global split default routes of [TunSession] are *not* installed in this
 * mode (see `TunSession.start(..., perApp = true)`), otherwise every process
 * would be captured and the include list would be meaningless.
 *
 * Everything needs root; every command goes through the injected [CommandRunner].
 */
class PerAppRouting(
    private val runner: CommandRunner = ProcessCommandRunner,
    private val log: (String) -> Unit = {},
    private val cgroupRoot: String = "/sys/fs/cgroup",
) {

    private val sliceName = "owenclave.slice"
    private val table = "1188"
    private val mark = "0x1"
    private val teardown = ArrayList<() -> Unit>()

    @Volatile
    var installed: Boolean = false
        private set

    private var device: String = ""

    /**
     * Creates the slice, the marks and the policy route and moves every running
     * process from [processes] into the slice. Throws [PerAppRoutingException]
     * with the concrete reason when the host cannot support it.
     *
     * Processes that are not running at connect time cannot be moved; the returned
     * message says how many matched.
     */
    fun install(processes: List<String>, tunDevice: String): String {
        val wanted = processes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) {
            throw PerAppRoutingException("the per-app list is empty; add at least one process name")
        }
        if (tunDevice.isBlank()) {
            throw PerAppRoutingException("per-app routing needs a running TUN device")
        }

        requireRoot()
        requireCgroupV2()
        // A previous run may have crashed; make the install idempotent.
        cleanupLeftovers()

        val slicePath = "$cgroupRoot/$sliceName"
        run(listOf("mkdir", "-p", slicePath))
        val inode = run(listOf("stat", "-c", "%i", slicePath)).trim()
        if (inode.isEmpty() || !inode.all { it.isDigit() }) {
            throw PerAppRoutingException("cannot read the cgroup inode of $slicePath")
        }

        try {
            installMarks(inode, slicePath)
            installPolicyRouting(tunDevice)
        } catch (e: Exception) {
            remove()
            throw e
        }

        device = tunDevice
        val moved = attachProcesses(wanted, slicePath)
        installed = true
        val message = "per-app routing active: $moved of ${wanted.size} " +
            "process(es) in $sliceName marked into $tunDevice"
        log(message)
        return message
    }

    /** Removes every rule, route and mark installed by [install]. Idempotent. */
    fun remove() {
        for (action in teardown.asReversed()) {
            runCatching { action() }.onFailure { log("per-app cleanup: ${it.message}") }
        }
        teardown.clear()
        if (device.isNotBlank()) {
            runCatching { runner.run(listOf("rmdir", "$cgroupRoot/$sliceName")) }
        }
        installed = false
        device = ""
    }

    // ------------------------------------------------------------------- steps

    private fun requireRoot() {
        val result = runner.run(listOf("id", "-u"))
        val uid = result.stdout.trim()
        if (!result.ok || uid != "0") {
            throw PerAppRoutingException(
                "per-app routing needs root: run Owenclave as root or grant CAP_NET_ADMIN " +
                    "(current uid: ${uid.ifEmpty { "unknown" }})"
            )
        }
    }

    private fun requireCgroupV2() {
        val result = runner.run(listOf("cat", "$cgroupRoot/cgroup.controllers"))
        if (!result.ok || result.stdout.isBlank()) {
            throw PerAppRoutingException(
                "cgroup v2 is not mounted at $cgroupRoot (per-app routing needs the unified hierarchy)"
            )
        }
    }

    private fun cleanupLeftovers() {
        // Best effort: the slice directory and the rules may be left over from a
        // crashed run. Nothing here is fatal.
        runCatching { runner.run(listOf("nft", "delete", "table", "inet", "owenclave")) }
        runCatching { runner.run(listOf("iptables", "-t", "mangle", "-D", "OUTPUT", "-j", "OWENCLAVE")) }
        runCatching { runner.run(listOf("iptables", "-t", "mangle", "-F", "OWENCLAVE")) }
        runCatching { runner.run(listOf("iptables", "-t", "mangle", "-X", "OWENCLAVE")) }
        runCatching { runner.run(listOf("ip", "rule", "del", "fwmark", mark, "lookup", table)) }
        runCatching { runner.run(listOf("ip", "route", "flush", "table", table)) }
    }

    private fun installMarks(inode: String, slicePath: String) {
        val nft = hasCommand("nft")
        if (nft) {
            run(listOf("nft", "add", "table", "inet", "owenclave"))
            teardown += { runCatching { runner.run(listOf("nft", "delete", "table", "inet", "owenclave")) } }
            run(
                listOf(
                    "nft", "add", "chain", "inet", "owenclave", "output",
                    "{", "type", "route", "hook", "output", "priority", "mangle;",
                    "policy", "accept;", "}",
                )
            )
            run(
                listOf(
                    "nft", "add", "rule", "inet", "owenclave", "output",
                    "meta", "cgroup", inode, "meta", "mark", "set", mark,
                )
            )
            log("per-app routing: nftables marks cgroup $inode (inode of $slicePath)")
            return
        }

        if (!hasCommand("iptables")) {
            throw PerAppRoutingException(
                "neither nft nor iptables is available; per-app routing cannot mark the cgroup"
            )
        }
        run(listOf("iptables", "-t", "mangle", "-N", "OWENCLAVE"))
        teardown += { runCatching { runner.run(listOf("iptables", "-t", "mangle", "-F", "OWENCLAVE")) } }
        teardown += { runCatching { runner.run(listOf("iptables", "-t", "mangle", "-X", "OWENCLAVE")) } }
        run(
            listOf(
                "iptables", "-t", "mangle", "-A", "OWENCLAVE",
                "-m", "cgroup", "--path", sliceName, "-j", "MARK", "--set-mark", mark,
            )
        )
        run(listOf("iptables", "-t", "mangle", "-A", "OUTPUT", "-j", "OWENCLAVE"))
        teardown += {
            runCatching { runner.run(listOf("iptables", "-t", "mangle", "-D", "OUTPUT", "-j", "OWENCLAVE")) }
        }
        log("per-app routing: iptables marks $sliceName")
    }

    private fun installPolicyRouting(tunDevice: String) {
        run(listOf("ip", "rule", "add", "fwmark", mark, "lookup", table))
        teardown += { runCatching { runner.run(listOf("ip", "rule", "del", "fwmark", mark, "lookup", table)) } }
        run(listOf("ip", "route", "add", "default", "dev", tunDevice, "table", table))
        teardown += {
            runCatching { runner.run(listOf("ip", "route", "del", "default", "dev", tunDevice, "table", table)) }
        }
        log("per-app routing: fwmark $mark -> table $table -> $tunDevice")
    }

    /** Moves every running process whose name (or path) matches into the slice. */
    private fun attachProcesses(processes: List<String>, slicePath: String): Int {
        var moved = 0
        for (process in processes) {
            val pids = pidsOf(process)
            if (pids.isEmpty()) {
                log("per-app routing: no running process matches \"$process\"; start it and reconnect")
                continue
            }
            for (pid in pids) {
                run(listOf("sh", "-c", "echo $pid > $slicePath/cgroup.procs"))
                moved++
            }
        }
        return moved
    }

    private fun pidsOf(process: String): List<String> {
        val args = if (process.contains('/')) {
            listOf("pgrep", "-f", process)
        } else {
            listOf("pgrep", "-x", process)
        }
        val result = runner.run(args)
        if (!result.ok) return emptyList()
        return result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.all(Char::isDigit) }
            .toList()
    }

    private fun hasCommand(name: String): Boolean {
        val result = runner.run(listOf("sh", "-c", "command -v $name"))
        return result.ok && result.stdout.isNotBlank()
    }

    private fun run(command: List<String>): String {
        val result = runner.run(command)
        if (!result.ok) {
            throw PerAppRoutingException(
                "${command.joinToString(" ")} failed (${result.exitCode}): ${result.output}"
            )
        }
        return result.stdout
    }

    companion object {

        /** Default TUN interface name, matching [TunSession.DEFAULT_LINUX_DEVICE]. */
        const val CGROUP_SLICE = "owenclave.slice"

        /** Routing table used for the marked traffic. */
        const val ROUTING_TABLE = "1188"
    }

}
