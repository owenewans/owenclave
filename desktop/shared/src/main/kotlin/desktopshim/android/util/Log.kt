package android.util

import io.nekohasekai.sagernet.DesktopEnv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    private val ringBuffer = ArrayDeque<String>(2048)
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val logFile by lazy {
        rotateSessionLogFile(DesktopEnv.dataDir)
    }
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    var externalSink: ((String) -> Unit)? = null

    /**
     * Eagerly initializes and rotates `logcat.log` for the primary desktop session
     * so opening the Logcat screen immediately after startup shows a clean current
     * session instead of reading an unrotated file from disk.
     */
    @JvmStatic
    fun initSession(): File = logFile

    /**
     * Rotates a non-empty `logcat.log` from the previous run to `logcat.prev.log`
     * and starts a fresh `logcat.log` for the current session so stale crashes or
     * errors from earlier sessions do not accumulate indefinitely in the Logcat tab.
     */
    @JvmStatic
    fun rotateSessionLogFile(dir: File): File {
        dir.mkdirs()
        val current = File(dir, "logcat.log")
        val previous = File(dir, "logcat.prev.log")
        runCatching {
            if (current.isFile && current.length() > 0L) {
                previous.delete()
                if (!current.renameTo(previous)) {
                    current.copyTo(previous, overwrite = true)
                }
            }
            current.writeText("")
        }
        return current
    }

    /**
     * Child plugins launched by `GuardedProcessPool` (such as Chromium-based
     * `naive` and `tun2socks`) write informational and warning lines to `stderr`.
     * Inspects structured severity markers when no `Throwable` is attached so
     * normal connection logs are not mislabeled as `E/`.
     */
    @JvmStatic
    fun classifyStderrLevel(msg: String?): Char {
        if (msg.isNullOrEmpty()) return 'E'
        if (CHROMIUM_INFO_REGEX.containsMatchIn(msg) ||
            msg.contains("level=info", ignoreCase = true) ||
            msg.contains(" [INFO] ")
        ) {
            return 'I'
        }
        if (CHROMIUM_WARN_REGEX.containsMatchIn(msg) ||
            msg.contains("level=warn", ignoreCase = true) ||
            msg.contains(" [WARN] ")
        ) {
            return 'W'
        }
        if (CHROMIUM_VERBOSE_REGEX.containsMatchIn(msg) ||
            msg.contains("level=debug", ignoreCase = true) ||
            msg.contains(" [DEBG] ")
        ) {
            return 'D'
        }
        return 'E'
    }

    private val CHROMIUM_INFO_REGEX = Regex("^\\[\\d+/\\d+\\.\\d+:INFO:")
    private val CHROMIUM_WARN_REGEX = Regex("^\\[\\d+/\\d+\\.\\d+:WARNING:")
    private val CHROMIUM_VERBOSE_REGEX = Regex("^\\[\\d+/\\d+\\.\\d+:VERBOSE\\d*:")

    fun getRecentLines(): List<String> = synchronized(ringBuffer) { ringBuffer.toList() }

    fun clearLines() {
        synchronized(ringBuffer) { ringBuffer.clear() }
        runCatching { logFile.writeText("") }
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    private fun emit(level: Char, tag: String?, msg: String?, tr: Throwable? = null): Int {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val body = buildString {
            append("$timestamp $level/${tag ?: "Owenclave"}: ${msg ?: ""}")
            if (tr != null) {
                append("\n")
                append(tr.stackTraceToString().trimEnd())
            }
        }
        synchronized(ringBuffer) {
            for (line in body.lines()) {
                if (ringBuffer.size >= 2000) ringBuffer.removeFirst()
                ringBuffer.addLast(line)
            }
            runCatching { logFile.appendText(body + "\n") }
        }
        externalSink?.invoke(body) ?: println(body)
        for (l in listeners) {
            runCatching { l(body) }
        }
        return 0
    }

    @JvmStatic fun v(tag: String?, msg: String?): Int = emit('V', tag, msg)
    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int = emit('V', tag, msg, tr)
    @JvmStatic fun d(tag: String?, msg: String?): Int = emit('D', tag, msg)
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int = emit('D', tag, msg, tr)
    @JvmStatic fun i(tag: String?, msg: String?): Int = emit('I', tag, msg)
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int = emit('I', tag, msg, tr)
    @JvmStatic fun w(tag: String?, msg: String?): Int = emit('W', tag, msg)
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = emit('W', tag, msg, tr)
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = emit('W', tag, tr?.message, tr)
    @JvmStatic fun e(tag: String?, msg: String?): Int = emit(classifyStderrLevel(msg), tag, msg)
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int =
        emit(if (tr == null) classifyStderrLevel(msg) else 'E', tag, msg, tr)
}

class TypedValue {
    var resourceId: Int = 0
}
