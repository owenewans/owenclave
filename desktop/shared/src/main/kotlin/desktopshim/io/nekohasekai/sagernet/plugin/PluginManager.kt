package io.nekohasekai.sagernet.plugin

import io.nekohasekai.sagernet.DesktopEnv
import io.nekohasekai.sagernet.SagerNet
import java.io.File

object PluginManager {
    class PluginNotFoundException(val plugin: String) : Exception("Plugin not found: $plugin")

    class InitResult(
        val path: String,
    )

    fun init(pluginId: String): InitResult? {
        if (pluginId.isBlank()) return null
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val binaryName = when (pluginId) {
            "naive-plugin" -> if (isWindows) "naive.exe" else "naive"
            else -> if (isWindows) "$pluginId.exe" else pluginId
        }
        val os = System.getProperty("os.name", "").lowercase().let {
            when {
                it.contains("win") -> "windows"
                it.contains("mac") || it.contains("darwin") -> "darwin"
                else -> "linux"
            }
        }
        val arch = System.getProperty("os.arch", "").lowercase().let {
            when (it) {
                "aarch64", "arm64" -> "arm64"
                "x86", "i386", "i486", "i586", "i686" -> "386"
                else -> "amd64"
            }
        }
        val platformTag = "$os-$arch"
        val devRoot = System.getProperty("owenclave.devRoot")
        val workingDir = File(System.getProperty("user.dir", "."))
        val resourcesDir = System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let(::File)
        val directCandidates = listOfNotNull(
            System.getProperty("owenclave.naive")?.takeIf { pluginId == "naive-plugin" && it.isNotBlank() }?.let(::File),
            System.getenv("OWENCLAVE_NAIVE_BIN")?.takeIf { pluginId == "naive-plugin" && it.isNotBlank() }?.let(::File),
            resourcesDir?.resolve("bin/$binaryName"),
            resourcesDir?.resolve(binaryName),
            devRoot?.takeIf { it.isNotBlank() }?.let { File(it, "desktop/naive/dist/$platformTag/$binaryName") },
            File(workingDir, "bin/$binaryName"),
            File(workingDir, binaryName),
            File(workingDir, "resources/bin/$binaryName"),
            File(workingDir, "../Resources/bin/$binaryName"),
            File(workingDir, "../app/resources/bin/$binaryName"),
        )
        val found = directCandidates.firstOrNull { it.isFile }
            ?: extractBundledPlugin(binaryName, isMac = os == "darwin")
            ?: listOfNotNull(
                File(DesktopEnv.dataDir, "runtime/$binaryName"),
                File(DesktopEnv.dataDir, "bin/$binaryName"),
                DesktopEnv.dataDir.parentFile?.resolve("bin/$binaryName"),
                File(SagerNet.application.applicationInfo.nativeLibraryDir, binaryName),
            ).firstOrNull { it.isFile }
            ?: throw PluginNotFoundException(pluginId)
        return InitResult(found.absolutePath)
    }

    private fun extractBundledPlugin(binaryName: String, isMac: Boolean): File? {
        val stream = PluginManager::class.java.getResourceAsStream("/bin/$binaryName")
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("bin/$binaryName")
            ?: return null
        val runtimeDir = File(DesktopEnv.dataDir, "runtime").apply { mkdirs() }
        val target = File(runtimeDir, binaryName)
        val temp = File(runtimeDir, "$binaryName.tmp")
        stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        val same = target.isFile && target.length() == temp.length() && sha256(target) == sha256(temp)
        if (!same) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target.setExecutable(true, false)
            if (isMac) {
                runCatching { ProcessBuilder("/usr/bin/xattr", "-c", target.absolutePath).start().waitFor() }
                runCatching {
                    ProcessBuilder("/usr/bin/codesign", "--force", "--sign", "-", target.absolutePath).start().waitFor()
                }
            }
        } else {
            temp.delete()
            target.setExecutable(true, false)
        }
        return target
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(16_384)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
