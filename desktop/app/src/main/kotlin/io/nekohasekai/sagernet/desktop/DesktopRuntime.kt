package io.nekohasekai.sagernet.desktop

import io.nekohasekai.sagernet.DesktopEnv
import java.io.File

/**
 * Locates the platform binaries (the Go core and the NaiveProxy plugin) and the
 * per user data directory.
 *
 * Lookup order:
 *  1. explicit `-Dowenclave.<name>=<path>` override
 *  2. binaries bundled inside the application jar (`/bin/<name>`, extracted to
 *     the data directory on first use)
 *  3. a development checkout (`-Dowenclave.devRoot=<repo>`), where the binaries
 *     are read straight from `desktop/<tool>/dist/<platform>`
 */
object DesktopRuntime {

    val os: String = System.getProperty("os.name", "").lowercase().let {
        when {
            it.contains("win") -> "windows"
            it.contains("mac") || it.contains("darwin") -> "darwin"
            else -> "linux"
        }
    }

    val arch: String = System.getProperty("os.arch", "").lowercase().let {
        when (it) {
            "aarch64", "arm64" -> "arm64"
            "x86", "i386", "i486", "i586", "i686" -> "386"
            else -> "amd64"
        }
    }

    val platformTag: String = "$os-$arch"

    val isWindows: Boolean = os == "windows"
    val isLinux: Boolean = os == "linux"
    val isMac: Boolean = os == "darwin"

    val dataDir: File get() = DesktopEnv.dataDir

    private val cache = HashMap<String, File?>()

    @Synchronized
    fun coreBinary(): File? = cache.getOrPut("core") { locate("owenclave-core") }

    @Synchronized
    fun jniLibrary(): File? = cache.getOrPut("gojni") {
        val libName = when (os) {
            "windows" -> "gojni.dll"
            "darwin" -> "libgojni.dylib"
            else -> "libgojni.so"
        }
        locateFile(libName)
    }

    @Synchronized
    fun naiveBinary(): File? = cache.getOrPut("naive") { locate("naive") }

    /** Userspace TUN pump used by the transparent mode (ensures `wintun.dll` sits alongside `tun2socks.exe` on Windows). */
    @Synchronized
    fun tun2socksBinary(): File? = cache.getOrPut("tun2socks") {
        val bin = locate("tun2socks") ?: return@getOrPut null
        if (isWindows) {
            ensureWindowsWintunDll(bin.parentFile)
        }
        bin
    }

    private fun ensureWindowsWintunDll(targetDir: File?) {
        if (targetDir == null) return
        val alongside = File(targetDir, "wintun.dll")
        if (alongside.isFile && alongside.length() > 0L) return
        val source = locateFile("wintun.dll") ?: return
        if (source.canonicalFile != alongside.canonicalFile) {
            runCatching {
                targetDir.mkdirs()
                val tmp = File(targetDir, "wintun.dll.tmp")
                source.copyTo(tmp, overwrite = true)
                alongside.delete()
                if (!tmp.renameTo(alongside)) {
                    tmp.copyTo(alongside, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    /** Privileged Linux TUN & policy-routing helper invoked via Polkit (`pkexec`). */
    @Synchronized
    fun tunHelperScript(): File? = cache.getOrPut("tunHelper") {
        val installed = File("/opt/owenclave/bin/owenclave-tun-helper")
        if (installed.isFile && installed.canExecute()) return@getOrPut installed
        System.getProperty("owenclave.devRoot")?.takeIf { it.isNotBlank() }?.let { devRoot ->
            val devScript = File(devRoot, "packaging/linux/owenclave-tun-helper")
            if (devScript.isFile) {
                devScript.setExecutable(true, false)
                return@getOrPut devScript
            }
        }
        extractBundled("owenclave-tun-helper")
    }

    /**
     * Directory containing `geoip.dat` and `geosite.dat` for `V2RAY_LOCATION_ASSET`.
     * Extracts the bundled Android assets into `dataDir/runtime` when running from
     * a packaged jar without an external asset directory.
     */
    @Synchronized
    fun assetDir(): File? = cache.getOrPut("assetDir") { locateAssetDir() }

    fun executableName(baseName: String): String = if (isWindows) "$baseName.exe" else baseName

    private fun locateAssetDir(): File? {
        val candidates = ArrayList<File>()
        System.getenv("OWENCLAVE_ASSET_DIR")?.takeIf { it.isNotBlank() }?.let { candidates.add(File(it)) }
        System.getenv("V2RAY_LOCATION_ASSET")?.takeIf { it.isNotBlank() }?.let { candidates.add(File(it)) }
        System.getProperty("owenclave.assetDir")?.takeIf { it.isNotBlank() }?.let { candidates.add(File(it)) }
        System.getProperty("owenclave.devRoot")?.takeIf { it.isNotBlank() }?.let { devRoot ->
            candidates.add(File(devRoot, "app/src/main/assets/owenclave-core"))
        }
        val workingDir = File(System.getProperty("user.dir", "."))
        candidates.add(File(workingDir, "assets"))
        candidates.add(File(workingDir, "app/src/main/assets/owenclave-core"))
        candidates.add(File("/usr/share/owenclave/assets"))
        candidates.add(dataDir)
        candidates.firstOrNull { File(it, "geosite.dat").isFile && File(it, "geoip.dat").isFile }?.let { return it }

        val targetDir = File(dataDir, "runtime").apply { mkdirs() }
        var extracted = false
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val target = File(targetDir, name)
            val stream = DesktopRuntime::class.java.getResourceAsStream("/android_assets/owenclave-core/$name")
                ?: continue
            stream.use { input ->
                if (!target.isFile || target.length() == 0L) {
                    val tmp = File(targetDir, "$name.tmp")
                    tmp.outputStream().use { out -> input.copyTo(out) }
                    target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                }
                extracted = true
            }
        }
        return targetDir.takeIf { extracted && File(it, "geosite.dat").isFile }
    }

    private fun locate(baseName: String): File? = locateFile(executableName(baseName), baseName)

    private fun locateFile(fileName: String, propKey: String = fileName): File? {
        val devRoot = System.getProperty("owenclave.devRoot")
        val candidates = ArrayList<File>()

        System.getProperty("owenclave.$propKey")?.takeIf { it.isNotBlank() }?.let { candidates.add(File(it)) }
        System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let { resources ->
            candidates.add(File(resources, "bin/$fileName"))
            candidates.add(File(resources, fileName))
        }
        if (!devRoot.isNullOrBlank()) {
            candidates.add(File(devRoot, "desktop/core/dist/$platformTag/$fileName"))
            candidates.add(File(devRoot, "desktop/naive/dist/$platformTag/$fileName"))
            candidates.add(File(devRoot, "desktop/tun2socks/dist/$platformTag/$fileName"))
        }
        if (!isWindows) {
            candidates.add(File("/opt/owenclave/bin/$fileName"))
        }
        val workingDir = File(System.getProperty("user.dir", "."))
        candidates.add(File(workingDir, "bin/$fileName"))
        candidates.add(File(workingDir, fileName))
        candidates.add(File(workingDir, "resources/bin/$fileName"))
        candidates.add(File(workingDir, "../Resources/bin/$fileName"))
        candidates.add(File(workingDir, "../app/resources/bin/$fileName"))

        candidates.firstOrNull { it.isFile }?.let { return it }
        return extractBundled(fileName)
    }

    /** Extracts a binary that was bundled into the jar as `/bin/<fileName>`. */
    private fun extractBundled(fileName: String): File? {
        val stream = DesktopRuntime::class.java.getResourceAsStream("/bin/$fileName") ?: return null
        val targetDir = File(dataDir, "runtime").apply { mkdirs() }
        val target = File(targetDir, fileName)
        stream.use { input ->
            val temporary = File(targetDir, "$fileName.tmp")
            temporary.outputStream().use { output -> input.copyTo(output) }
            val same = target.isFile && target.length() == temporary.length() && sha256(target) == sha256(temporary)
            if (!same) {
                target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target.setExecutable(true, false)
                if (isMac) {
                    runCatching { ProcessBuilder("/usr/bin/xattr", "-c", target.absolutePath).start().waitFor() }
                    runCatching {
                        ProcessBuilder("/usr/bin/codesign", "--force", "--sign", "-", target.absolutePath).start().waitFor()
                    }
                }
            } else {
                temporary.delete()
                target.setExecutable(true, false)
            }
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
