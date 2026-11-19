package io.nekohasekai.sagernet.utils

import android.util.Log
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.ktx.app
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CrashHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e("Owenclave", "uncaughtException", throwable)

        runCatching {
            val logDir = File(app.externalCacheDir, "log").also { it.mkdirs() }
            val logFile = File.createTempFile("Owenclave Crash Report ", ".log", logDir)
            val report = buildString {
                append(buildReportHeader())
                append("\nThread: ").append(thread).append("\n\n")
                append(formatThrowable(throwable)).append("\n")
            }
            logFile.writeText(report)
            Log.e("CrashHandler", "Saved crash report to ${logFile.absolutePath}")
        }
    }

    fun formatThrowable(throwable: Throwable): String {
        var format = throwable.javaClass.name
        val message = throwable.message
        if (!message.isNullOrBlank()) {
            format += ": $message"
        }
        format += "\n"

        format += throwable.stackTrace.joinToString("\n") {
            "    at ${it.className}.${it.methodName}(${it.fileName}:${if (it.isNativeMethod) "native" else it.lineNumber})"
        }

        val cause = throwable.cause
        if (cause != null) {
            format += "\n\nCaused by: " + formatThrowable(cause)
        }

        return format
    }

    fun buildReportHeader(): String = buildString {
        append("Owenclave ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) DESKTOP\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        append("Date: ${dateFormat.format(Date())}\n\n")
        append("OS_NAME: ${System.getProperty("os.name")}\n")
        append("OS_VERSION: ${System.getProperty("os.version")}\n")
        append("OS_ARCH: ${System.getProperty("os.arch")}\n")
        append("JAVA_VERSION: ${System.getProperty("java.runtime.version") ?: System.getProperty("java.version")}\n")
        append("JAVA_VENDOR: ${System.getProperty("java.vendor")}\n\n")

        try {
            append("Settings:\n")
            for (pair in PublicDatabase.kvPairDao.all()) {
                append("\n").append(pair.key).append(": ").append(pair.toString())
            }
        } catch (e: Exception) {
            append("Export settings failed: ").append(formatThrowable(e))
        }
        append("\n\n")
    }
}

