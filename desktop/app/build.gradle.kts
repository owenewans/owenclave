import org.gradle.api.tasks.Sync
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    // The Kotlin plugin is already on the build classpath through buildSrc
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

/*
 * Owenclave desktop client.
 *
 * The proxy engine is the very same Go core that the Android app links through
 * gomobile; on desktop it runs as `owenclave-core`, a child process that speaks
 * the same V2Ray-format JSON produced for Android. NaiveProxy is shipped the
 * same way as on Android as well: as an external `naive` process the core dials
 * into through a local SOCKS listener.
 */

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersion = versionProperties.getProperty("VERSION_NAME").trim()

/*
 * jpackage refuses an app-version whose first component is zero on macOS
 * ("The first number in an app-version cannot be zero or negative"), while the
 * Android app versions are 0.x. Desktop artifacts therefore map 0.a.b to 1.a.b so
 * that all three desktop platforms carry the same, valid version.
 */
val desktopVersion = appVersion.replaceFirst(Regex("^0+(?=\\.)"), "1")

/** Build host == packaging target for jpackage, so the host tuple selects the binaries. */
val hostOs = System.getProperty("os.name").lowercase().let {
    when {
        it.contains("win") -> "windows"
        it.contains("mac") || it.contains("darwin") -> "darwin"
        else -> "linux"
    }
}
val hostArch = System.getProperty("os.arch").lowercase().let {
    when (it) {
        "aarch64", "arm64" -> "arm64"
        "x86", "i386", "i486", "i586", "i686" -> "386"
        else -> "amd64"
    }
}
val platformTag = "$hostOs-$hostArch"
val coreBinaryName = if (hostOs == "windows") "owenclave-core.exe" else "owenclave-core"
val jniLibraryName = when (hostOs) {
    "windows" -> "gojni.dll"
    "darwin" -> "libgojni.dylib"
    else -> "libgojni.so"
}
val naiveBinaryName = if (hostOs == "windows") "naive.exe" else "naive"
val tun2socksBinaryName = if (hostOs == "windows") "tun2socks.exe" else "tun2socks"

val coreDistDir = rootProject.layout.projectDirectory.dir("desktop/core/dist/$platformTag")
val naiveDistDir = rootProject.layout.projectDirectory.dir("desktop/naive/dist/$platformTag")
val tun2socksDistDir = rootProject.layout.projectDirectory.dir("desktop/tun2socks/dist/$platformTag")

/** Directory that gets bundled into the application jar as `/bin/<binary>`. */
val runtimeResourcesDir = layout.buildDirectory.dir("desktop-resources")

val prepareDesktopRuntime = tasks.register<Sync>("prepareDesktopRuntime") {
    description = "Stages the core, libgojni, NaiveProxy and tun2socks binaries for the current platform"
    into(runtimeResourcesDir.map { it.dir("bin") })
    from(coreDistDir) {
        include(coreBinaryName)
        include(jniLibraryName)
    }
    from(naiveDistDir) {
        include(naiveBinaryName)
    }
    from(tun2socksDistDir) {
        include(tun2socksBinaryName)
        // only present on Windows, and only when wintun.net was reachable
        include("wintun.dll")
    }
    if (hostOs == "linux") {
        from(rootProject.file("packaging/linux/owenclave-tun-helper"))
    }
    doLast {
        logger.lifecycle("Staged desktop runtime for $platformTag in ${runtimeResourcesDir.get().asFile}")
    }
}

val sharedAndroidUiSrc = rootProject.file("app/src/main/java")

sourceSets {
    named("main") {
        kotlin {
            setSrcDirs(listOf("src/main/kotlin", sharedAndroidUiSrc))
            include("desktopshim/**")
            include("io/nekohasekai/sagernet/desktop/**")
            include("io/nekohasekai/sagernet/ui/compose/**")
        }
    }
}

sourceSets["main"].resources.srcDir(runtimeResourcesDir)
tasks.named("processResources") { dependsOn(prepareDesktopRuntime) }

/*
 * Settings parity: the desktop Settings tab is generated from the real Android
 * preference XML instead of a hand written list. The XML plus every value
 * resource it resolves (`@string/...` and `@array/...`) is copied into the
 * application resources, so the desktop catalog parser reads exactly the same
 * definitions the Android app ships.
 *
 * The task is deliberately a copy, never a rewrite: `app/` stays untouched and
 * adding a preference on Android flows into the desktop catalog (see
 * `AndroidPreferences.kt`) without touching desktop code.
 *
 * `preference.xml` exists twice in the Android tree: the base res/values copy and
 * the res/values-v29 override. Modern Android (API 29+) uses the override, so the
 * desktop catalog prefers it, exactly like the platform resource merger.
 */
val androidPreferencesDir = layout.buildDirectory.dir("android-preferences")

val copyAndroidPreferences = tasks.register<Sync>("copyAndroidPreferences") {
    description = "Copies the Android global preferences XML and its value resources into the desktop resources"
    from(rootProject.file("app/src/main/res/xml/global_preferences.xml")) { into("android-preferences") }
    from(rootProject.file("app/src/main/res/values/strings.xml")) { into("android-preferences") }
    from(rootProject.file("app/src/main/res/values/arrays.xml")) { into("android-preferences") }
    from(rootProject.file("app/src/main/res/values/locale.xml")) { into("android-preferences") }
    from(rootProject.file("app/src/main/res/values/preference.xml")) { into("android-preferences") }
    // API 29+ override of the same file; kept under a distinct name so both copies
    // can be staged and the parser can apply the platform precedence.
    from(rootProject.file("app/src/main/res/values-v29/preference.xml")) {
        into("android-preferences")
        rename { "preference-v29.xml" }
    }
    from(rootProject.file("packaging/icons/owenclave.png")) {
        rename { "owenclave-icon.png" }
    }
    into(androidPreferencesDir)
    doLast {
        logger.lifecycle("Copied Android preference resources into ${androidPreferencesDir.get().asFile}")
    }
}

sourceSets["main"].resources.srcDir(androidPreferencesDir)
tasks.named("processResources") { dependsOn(copyAndroidPreferences) }

/*
 * Portable build: the jpackage application image zipped as is, so it can be
 * unpacked and started without an installer. A `data` directory next to the
 * launcher makes the app keep profiles and settings inside the unpacked folder
 * instead of the per user OS directory (`DesktopEnv` picks it up).
 */
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app")
val portableStaging = layout.buildDirectory.dir("portable")

val preparePortable = tasks.register<Sync>("preparePortable") {
    description = "Stages the application image for the portable archive"
    dependsOn("createDistributable")
    from(appImageDir)
    into(portableStaging)
    // jpackage ships read only legal files, so a previous staging has to go away
    // before syncing again (otherwise the copy fails with "access denied").
    doFirst {
        val staging = portableStaging.get().asFile
        if (staging.exists()) staging.deleteRecursively()
    }
    doLast {
        val root = portableStaging.get().asFile.listFiles()
            ?.firstOrNull { it.isDirectory } ?: return@doLast
        File(root, "data").mkdirs()
    }
}

val packagePortable = tasks.register<Zip>("packagePortable") {
    description = "Builds the portable (unpack and run) archive for the current platform"
    dependsOn(preparePortable)
    from(portableStaging)
    archiveFileName.set("owenclave-desktop-$desktopVersion-$platformTag-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    // Gradle does not keep unix permissions in archives, but a portable build has
    // to keep the launcher and the bundled JRE binaries executable.
    eachFile {
        if (isDirectory || file.canExecute()) {
            permissions { unix("rwxr-xr-x") }
        } else {
            permissions { unix("rw-r--r--") }
        }
    }
    doLast {
        logger.lifecycle("Portable archive: ${archiveFile.get().asFile}")
    }
}

val windowsSkikoRuntime by configurations.creating

val windowsPortableStaging = layout.buildDirectory.dir("portable-windows-amd64")

val prepareWindowsPortable = tasks.register("prepareWindowsPortable") {
    description = "Stages a cross-platform Windows (windows-amd64) portable distribution"
    dependsOn("jar", configurations.named("runtimeClasspath"), windowsSkikoRuntime)
    val winCoreDir = rootProject.layout.projectDirectory.dir("desktop/core/dist/windows-amd64")
    val winNaiveDir = rootProject.layout.projectDirectory.dir("desktop/naive/dist/windows-amd64")
    val winTun2socksDir = rootProject.layout.projectDirectory.dir("desktop/tun2socks/dist/windows-amd64")
    val runtimeClasspath = configurations.named("runtimeClasspath")
    val appJarTask = tasks.named<org.gradle.jvm.tasks.Jar>("jar")

    doLast {
        val stagingRoot = windowsPortableStaging.get().asFile
        if (stagingRoot.exists()) stagingRoot.deleteRecursively()
        val appRoot = File(stagingRoot, "Owenclave").apply { mkdirs() }
        val binDir = File(appRoot, "bin").apply { mkdirs() }
        val libDir = File(appRoot, "app").apply { mkdirs() }
        File(appRoot, "data").mkdirs()

        listOf(
            winCoreDir.asFile.resolve("owenclave-core.exe"),
            winCoreDir.asFile.resolve("gojni.dll"),
            winNaiveDir.asFile.resolve("naive.exe"),
            winTun2socksDir.asFile.resolve("tun2socks.exe"),
            winTun2socksDir.asFile.resolve("wintun.dll"),
        ).forEach { src ->
            if (src.isFile) {
                src.copyTo(File(binDir, src.name), overwrite = true)
            }
        }

        val jarFile = appJarTask.get().archiveFile.get().asFile
        jarFile.copyTo(File(libDir, jarFile.name), overwrite = true)
        val usedWinJarNames = HashSet<String>().apply { add(jarFile.name) }
        runtimeClasspath.get().files.forEachIndexed { idx, dep ->
            if (dep.isFile && !dep.name.startsWith("skiko-awt-runtime-linux-")) {
                val dstName = if (usedWinJarNames.add(dep.name)) dep.name else "$idx-${dep.name}"
                dep.copyTo(File(libDir, dstName), overwrite = true)
            }
        }
        windowsSkikoRuntime.files.forEachIndexed { idx, dep ->
            if (dep.isFile) {
                val dstName = if (usedWinJarNames.add(dep.name)) dep.name else "win-$idx-${dep.name}"
                dep.copyTo(File(libDir, dstName), overwrite = true)
            }
        }

        val batContent = buildString {
            append("@echo off\r\n")
            append("setlocal\r\n")
            append("set \"APP_HOME=%~dp0\"\r\n")
            append("if exist \"%APP_HOME%..\\app\" set \"APP_HOME=%~dp0..\\\\\"\r\n")
            append("cd /d \"%APP_HOME%\"\r\n")
            append("if exist \"%APP_HOME%runtime\\bin\\java.exe\" (\r\n")
            append("  \"%APP_HOME%runtime\\bin\\java.exe\" -Dowenclave.gojni.path=\"%APP_HOME%bin\\gojni.dll\" -cp \"%APP_HOME%app\\*\" io.nekohasekai.sagernet.desktop.MainKt %*\r\n")
            append(") else (\r\n")
            append("  java -Dowenclave.gojni.path=\"%APP_HOME%bin\\gojni.dll\" -cp \"%APP_HOME%app\\*\" io.nekohasekai.sagernet.desktop.MainKt %*\r\n")
            append(")\r\n")
        }
        File(appRoot, "Owenclave.bat").writeText(batContent)
        File(binDir, "Owenclave.bat").writeText(batContent)

        val launcherSrc = rootProject.file("packaging/windows/launcher.c")
        val launcherRc = rootProject.file("packaging/windows/launcher.rc")
        val rootExe = File(appRoot, "Owenclave.exe")
        val binExe = File(binDir, "Owenclave.exe")
        if (launcherSrc.isFile) {
            val resObj = File(stagingRoot, "launcher.res.o")
            val hasRes = launcherRc.isFile && runCatching {
                ProcessBuilder(
                    "x86_64-w64-mingw32-windres",
                    "-i",
                    launcherRc.absolutePath,
                    "-o",
                    resObj.absolutePath,
                ).directory(launcherRc.parentFile).inheritIO().start().waitFor() == 0 && resObj.isFile
            }.getOrDefault(false)
            val gccCmd = buildList {
                add("x86_64-w64-mingw32-gcc")
                add("-O2")
                add("-municode")
                add("-s")
                add(launcherSrc.absolutePath)
                if (hasRes) add(resObj.absolutePath)
                add("-o")
                add(rootExe.absolutePath)
            }
            val gccProc = ProcessBuilder(gccCmd).inheritIO().start()
            check(gccProc.waitFor() == 0) { "Failed to compile Owenclave.exe with x86_64-w64-mingw32-gcc" }
            resObj.delete()
            rootExe.copyTo(binExe, overwrite = true)
            rootExe.copyTo(File(appRoot, "owenclave.exe"), overwrite = true)
            rootExe.copyTo(File(binDir, "owenclave.exe"), overwrite = true)
        }

        val winJmodsDir = System.getenv("WIN_JMODS_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("user.home"), ".gradle/jdks")
                .walkTopDown()
                .maxDepth(3)
                .firstOrNull { it.isDirectory && it.name == "jmods" && it.absolutePath.contains("windows") }
        val hostJlink = System.getenv("JLINK_BIN")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("java.home"), "bin/jlink")
        val runtimeOut = File(appRoot, "runtime")
        if (winJmodsDir != null && winJmodsDir.isDirectory && hostJlink.isFile) {
            val jlinkProc = ProcessBuilder(
                hostJlink.absolutePath,
                "--module-path",
                winJmodsDir.absolutePath,
                "--add-modules",
                "java.base,java.datatransfer,java.xml,java.prefs,java.desktop,java.logging,java.net.http,jdk.crypto.ec,jdk.httpserver,java.sql,java.naming,jdk.unsupported",
                "--strip-debug",
                "--no-man-pages",
                "--no-header-files",
                "--output",
                runtimeOut.absolutePath,
            ).inheritIO().start()
            check(jlinkProc.waitFor() == 0) { "Failed to create Windows runtime image via jlink" }
        }
    }
}

val packageWindowsPortable = tasks.register<Zip>("packageWindowsPortable") {
    description = "Builds the cross-packaged Windows (windows-amd64) portable archive"
    dependsOn(prepareWindowsPortable)
    from(windowsPortableStaging)
    archiveFileName.set("owenclave-desktop-$desktopVersion-windows-amd64-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    doLast {
        logger.lifecycle("Windows portable archive: ${archiveFile.get().asFile}")
    }
}

val serverDistDir = layout.buildDirectory.dir("server-dist")

val prepareServerDist = tasks.register("prepareServerDist") {
    description = "Stages a slim headless server distribution for Docker / Mihomo-compatible daemon mode"
    dependsOn("jar", configurations.named("runtimeClasspath"), prepareDesktopRuntime)
    val runtimeClasspath = configurations.named("runtimeClasspath")
    val appJarTask = tasks.named<org.gradle.jvm.tasks.Jar>("jar")
    val assetsDir = rootProject.file("app/src/main/assets/owenclave-core")

    doLast {
        val outRoot = serverDistDir.get().asFile
        if (outRoot.exists()) outRoot.deleteRecursively()
        val libDir = File(outRoot, "lib").apply { mkdirs() }
        val binDir = File(outRoot, "bin").apply { mkdirs() }
        val outAssets = File(outRoot, "assets").apply { mkdirs() }

        val jarFile = appJarTask.get().archiveFile.get().asFile
        jarFile.copyTo(File(libDir, jarFile.name), overwrite = true)
        val usedServerJarNames = HashSet<String>().apply { add(jarFile.name) }
        runtimeClasspath.get().files.forEachIndexed { idx, dep ->
            // Headless daemon never opens a Skiko window; omit GUI native runtimes to keep the image slim.
            if (dep.isFile && !dep.name.startsWith("skiko-awt-runtime-")) {
                val dstName = if (usedServerJarNames.add(dep.name)) dep.name else "$idx-${dep.name}"
                dep.copyTo(File(libDir, dstName), overwrite = true)
            }
        }

        listOf(
            coreDistDir.asFile.resolve(coreBinaryName),
            coreDistDir.asFile.resolve(jniLibraryName),
            naiveDistDir.asFile.resolve(naiveBinaryName),
            tun2socksDistDir.asFile.resolve(tun2socksBinaryName),
        ).forEach { src ->
            if (src.isFile) {
                val dst = File(binDir, src.name)
                src.copyTo(dst, overwrite = true)
                dst.setExecutable(true, false)
            }
        }

        listOf("geoip.dat", "geosite.dat").forEach { asset ->
            val src = File(assetsDir, asset)
            if (src.isFile) src.copyTo(File(outAssets, asset), overwrite = true)
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":desktop:shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.materialIconsExtended)
    implementation("androidx.lifecycle:lifecycle-runtime-desktop:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-desktop:2.11.0")
    implementation(libs.kotlinx.coroutines.swing)
    // Clash YAML subscriptions are imported with the same parser as on Android
    implementation(libs.snakeyaml)
    windowsSkikoRuntime("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Tests resolve the development core/plugin binaries straight from the
    // repository, and validate generated configs with `owenclave-core test`.
    systemProperty("owenclave.devRoot", rootProject.projectDir.absolutePath)
    systemProperty("owenclave.skipSchemeRegister", "true")
    // Opt-in host tests (they really set the system proxy):
    //   ./gradlew :desktop:app:test --tests '*SystemProxyE2eTest*' \
    //     -Dowenclave.e2e.systemProxy=true
    (project.findProperty("owenclave.e2e.systemProxy")
        ?: System.getProperty("owenclave.e2e.systemProxy"))?.let {
        systemProperty("owenclave.e2e.systemProxy", it.toString())
    }
}

compose.desktop {
    application {
        mainClass = "io.nekohasekai.sagernet.desktop.MainKt"
        jvmArgs(
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Owenclave"
            packageVersion = desktopVersion
            description = "Owenclave desktop proxy client"
            vendor = "owenewans"
            copyright = "GPL-3.0"

            // jlink does not add java.net.http on its own. SubscriptionImporter uses
            // HttpClient to download subscriptions, so without this the packaged
            // application dies with NoClassDefFoundError as soon as a subscription is
            // fetched - a path that no self test exercised, which is why it shipped.
            modules(
                "java.net.http",
                "jdk.crypto.ec",
                "jdk.httpserver",
                "java.sql",
                "java.naming",
                "jdk.unsupported",
            )

            linux {
                packageName = "owenclave"
                debMaintainer = "owenewans"
                menuGroup = "Network"
                appCategory = "Network"
                shortcut = true
                iconFile.set(rootProject.file("packaging/icons/owenclave.png"))
            }
            macOS {
                bundleID = "org.owenewans.owenclave.desktop"
                packageName = "Owenclave"
                dockName = "Owenclave"
                iconFile.set(rootProject.file("packaging/macos/Owenclave.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                          <dict>
                            <key>CFBundleURLName</key>
                            <string>org.owenewans.owenclave.url</string>
                            <key>CFBundleURLSchemes</key>
                            <array>
                              <string>owenclave</string>
                              <string>owenkey</string>
                              <string>happ</string>
                              <string>clash</string>
                              <string>sn</string>
                              <string>exclave</string>
                              <string>vless</string>
                              <string>vmess</string>
                              <string>trojan</string>
                              <string>ss</string>
                              <string>ssr</string>
                              <string>hysteria2</string>
                              <string>hy2</string>
                              <string>tuic</string>
                              <string>juicity</string>
                              <string>naive+https</string>
                              <string>naive+quic</string>
                              <string>anytls</string>
                              <string>shadowquic</string>
                              <string>sq</string>
                              <string>wireguard</string>
                              <string>wg</string>
                              <string>socks</string>
                              <string>socks5</string>
                            </array>
                          </dict>
                        </array>
                    """.trimIndent()
                }
            }
            windows {
                menuGroup = "Owenclave"
                dirChooser = true
                shortcut = true
                iconFile.set(rootProject.file("packaging/windows/owenclave.ico"))
                upgradeUuid = "6f2f2d3c-31a5-4d3f-9a3e-8f0b6f0f0f01"
            }
        }
    }
}

/** Development runs resolve the binaries straight from the repository. */
tasks.configureEach {
    if (this is JavaExec && (name == "run" || name.startsWith("run"))) {
        dependsOn(prepareDesktopRuntime)
        jvmArgs(
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        )
        systemProperty("owenclave.devRoot", rootProject.projectDir.absolutePath)
        // `./gradlew :desktop:app:run -Psection=RULES` opens a specific tab, used
        // when verifying that every screen renders.
        (project.findProperty("section") as String?)?.let { systemProperty("owenclave.section", it) }
    }
    if (name.startsWith("package") || name.startsWith("createDistributable")) {
        dependsOn(prepareDesktopRuntime)
    }
    if (hostOs == "linux" && name == "createDistributable") {
        val helperScript = rootProject.file("packaging/linux/owenclave-tun-helper")
        val tun2socksBin = tun2socksDistDir.asFile.resolve(tun2socksBinaryName)
        doLast {
            val binDir = appImageDir.get().asFile.resolve("Owenclave/bin")
            if (binDir.isDirectory) {
                if (helperScript.isFile) {
                    val dst = File(binDir, "owenclave-tun-helper")
                    helperScript.copyTo(dst, overwrite = true)
                    dst.setExecutable(true, false)
                }
                if (tun2socksBin.isFile) {
                    val dst = File(binDir, tun2socksBin.name)
                    tun2socksBin.copyTo(dst, overwrite = true)
                    dst.setExecutable(true, false)
                }
            }
        }
    }
    if (hostOs == "darwin" && name == "createDistributable") {
        doLast {
            val appBundle = appImageDir.get().asFile.resolve("Owenclave.app")
            val codesign = File("/usr/bin/codesign")
            if (appBundle.isDirectory && codesign.isFile) {
                runCatching {
                    ProcessBuilder("/usr/bin/xattr", "-cr", appBundle.absolutePath).start().waitFor()
                }
                val proc = ProcessBuilder(
                    codesign.absolutePath,
                    "--force",
                    "--deep",
                    "--sign",
                    "-",
                    appBundle.absolutePath,
                ).inheritIO().start()
                if (proc.waitFor() == 0) {
                    logger.lifecycle("Ad-hoc signed macOS bundle: $appBundle")
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("java.awt.headless", "true")
    systemProperty("apple.awt.UIElement", "true")
    val isolatedTestDataDir = layout.buildDirectory.dir("tmp/test-data").get().asFile
    doFirst {
        isolatedTestDataDir.deleteRecursively()
        isolatedTestDataDir.mkdirs()
    }
    systemProperty("owenclave.dataDir", isolatedTestDataDir.absolutePath)
}

tasks.named("build") { dependsOn(prepareDesktopRuntime) }
