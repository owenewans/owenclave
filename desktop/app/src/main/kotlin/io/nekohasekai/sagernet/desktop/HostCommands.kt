package io.nekohasekai.sagernet.desktop

import java.util.concurrent.TimeUnit

/** Result of one host command. */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0

    /** Everything the command printed, for error messages. */
    val output: String
        get() = listOf(stdout, stderr)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}

/**
 * The single seam through which the desktop client changes host state.
 *
 * Both the system proxy layer ([SystemProxy]) and the Linux per-app routing
 * ([PerAppRouting]) run *every* command through this interface. Production uses
 * [ProcessCommandRunner]; tests inject a fake, so a unit test never touches the
 * developer machine (no registry, no gsettings, no nftables, no routes).
 */
fun interface CommandRunner {
    fun run(command: List<String>): CommandResult
}

/** Runs commands as real child processes. */
object ProcessCommandRunner : CommandRunner {

    private const val TIMEOUT_SECONDS = 20L

    override fun run(command: List<String>): CommandResult = try {
        val pb = ProcessBuilder(command)
            // stderr is merged into stdout so reading one stream cannot deadlock.
            .redirectErrorStream(true)
        if (!DesktopRuntime.isWindows) {
            val currentPath = pb.environment()["PATH"].orEmpty()
            val extra = listOf("/usr/sbin", "/sbin", "/usr/bin", "/bin", "/usr/local/bin", "/opt/homebrew/bin")
            pb.environment()["PATH"] = (currentPath.split(':').filter { it.isNotBlank() } + extra)
                .distinct()
                .joinToString(":")
        }
        val process = pb.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            CommandResult(-1, output, "timed out after ${TIMEOUT_SECONDS}s")
        } else {
            CommandResult(process.exitValue(), output, "")
        }
    } catch (e: Exception) {
        CommandResult(-1, "", e.message ?: e.toString())
    }

}

/**
 * Splits a newline (or comma) separated user list, trimming blanks and `#`
 * comments. Shared by the per-app process list and the proxy exception list.
 */
internal fun parseHostList(raw: String): List<String> = raw
    .lineSequence()
    .flatMap { line -> line.split(',').asSequence() }
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .distinct()
    .toList()
