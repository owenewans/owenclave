package android.system

import java.net.InetAddress

class ErrnoException(val functionName: String, val errno: Int, cause: Throwable? = null) : Exception(functionName, cause)

object OsConstants {
    const val AF_INET = 2
    const val AF_INET6 = 10
    const val SIGKILL = 9
    const val SIGTERM = 15
    const val ESRCH = 3
}

object Os {
    @JvmStatic
    fun kill(pid: Int, signal: Int) {
        ProcessHandle.of(pid.toLong()).ifPresent { handle ->
            if (signal == OsConstants.SIGKILL) handle.destroyForcibly() else handle.destroy()
        }
    }

    @JvmStatic
    fun inet_pton(family: Int, address: String?): InetAddress? {
        if (address.isNullOrBlank()) return null
        val trimmed = address.trim()
        return try {
            when (family) {
                OsConstants.AF_INET -> {
                    val parts = trimmed.split('.')
                    if (parts.size != 4) return null
                    val bytes = ByteArray(4)
                    for (i in 0..3) {
                        val n = parts[i].toIntOrNull() ?: return null
                        if (n !in 0..255) return null
                        bytes[i] = n.toByte()
                    }
                    InetAddress.getByAddress(bytes)
                }
                OsConstants.AF_INET6 -> {
                    if (!trimmed.contains(':')) return null
                    InetAddress.getByName(trimmed)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

