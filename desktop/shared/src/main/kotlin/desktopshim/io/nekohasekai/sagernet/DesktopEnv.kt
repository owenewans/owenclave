package io.nekohasekai.sagernet

import java.io.File

/**
 * Per user directories for the desktop client, following the platform
 * conventions of Windows, macOS and Linux.
 */
object DesktopEnv {

    @JvmStatic
    val dataDir: File = resolveDataDir()

    @JvmStatic
    fun file(name: String): File = File(dataDir, name)

    private fun resolveDataDir(): File {
        val override = System.getProperty("owenclave.dataDir")
        if (!override.isNullOrBlank()) {
            return File(override).apply { mkdirs() }
        }
        // Portable build: a `data` directory next to the launcher keeps everything
        // inside the unpacked folder instead of the per user OS location.
        portableDataDir()?.let { return it.apply { mkdirs() } }
        val os = System.getProperty("os.name", "").lowercase()
        val home = System.getProperty("user.home", ".")
        val dir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                File(appData?.takeIf { it.isNotBlank() } ?: home, "Owenclave")
            }
            os.contains("mac") -> File(home, "Library/Application Support/Owenclave")
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                File(xdg?.takeIf { it.isNotBlank() } ?: "$home/.config", "owenclave")
            }
        }
        dir.mkdirs()
        return dir
    }

    /**
     * Returns `<image>/data` (or `<image>.app/data`) when the application runs
     * from a jpackage image that ships a portable data directory. Only applies to
     * packaged applications: development runs always use the OS directory.
     */
    private fun portableDataDir(): File? {
        val launcher = System.getProperty("jpackage.app-path") ?: return null
        var directory: File? = File(launcher).absoluteFile.parentFile
        repeat(4) {
            val current = directory ?: return null
            val data = File(current, "data")
            if (data.isDirectory) return data
            directory = current.parentFile
        }
        return null
    }

}
