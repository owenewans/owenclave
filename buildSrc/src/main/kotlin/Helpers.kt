import cn.hutool.core.codec.Base64
import com.android.build.api.dsl.*
import com.android.build.gradle.AbstractAppExtension
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.apache.tools.ant.filters.StringInputStream
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.*
import java.util.*

private val Project.android
    get() = extensions.getByName<CommonExtension<BuildFeatures, BuildType, DefaultConfig, ProductFlavor, AndroidResources, Installation>>(
        "android"
    )
private val Project.androidApp get() = android as ApplicationExtension

private lateinit var metadata: Properties
private lateinit var localProperties: Properties
private lateinit var flavor: String

fun Project.requireFlavor(): String {
    if (::flavor.isInitialized) return flavor
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        val taskName = gradle.startParameter.taskNames[0]
        when {
            taskName.contains("assemble") -> {
                flavor = taskName.substringAfter("assemble")
                return flavor
            }
            taskName.contains("install") -> {
                flavor = taskName.substringAfter("install")
                return flavor
            }
            taskName.contains("publish") -> {
                flavor = taskName.substringAfter("publish").substringBefore("Bundle")
                return flavor
            }
        }
    }

    flavor = ""
    return flavor
}

fun Project.requireMetadata(): Properties {
    if (!::metadata.isInitialized) {
        metadata = Properties().apply {
            load(rootProject.file("version.properties").inputStream())
        }
    }
    return metadata
}

fun Project.requireLocalProperties(): Properties {
    if (!::localProperties.isInitialized) {
        localProperties = Properties()

        val base64 = System.getenv("LOCAL_PROPERTIES")
        if (!base64.isNullOrBlank()) {

            localProperties.load(StringInputStream(Base64.decodeStr(base64)))
        } else if (project.rootProject.file("local.properties").exists()) {
            localProperties.load(rootProject.file("local.properties").inputStream())
        }
    }
    return localProperties
}

fun Project.requireTargetAbi(): String {
    var targetAbi = ""
    if (gradle.startParameter.taskNames.isNotEmpty()) {
        if (gradle.startParameter.taskNames.size == 1) {
            val targetTask = gradle.startParameter.taskNames[0].lowercase(Locale.ROOT).trim()
            when {
                targetTask.contains("arm64") -> targetAbi = "arm64-v8a"
                targetTask.contains("arm") -> targetAbi = "armeabi-v7a"
                targetTask.contains("x64") -> targetAbi = "x86_64"
                targetTask.contains("x86") -> targetAbi = "x86"
            }
        }
    }
    return targetAbi
}

fun Project.setupCommon() {
    setupCommon("")
}

fun Project.setupCommon(projectName: String) {
    android.apply {
        buildToolsVersion = "35.0.0"
        compileSdk = 35
        defaultConfig {
            minSdk = if (projectName.lowercase(Locale.ROOT) == "naive") 24 else 21
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        lint {
            showAll = true
            checkAllWarnings = true
            checkReleaseBuilds = false
            warningsAsErrors = true
            textOutput = project.file("build/lint.txt")
            htmlOutput = project.file("build/lint.html")
        }
        packaging {
            resources {
                excludes.addAll(
                    listOf(
                        "**/*.kotlin_*",
                        "/META-INF/*.version",
                        "/META-INF/native/**",
                        "/META-INF/native-image/**",
                        "/META-INF/INDEX.LIST",
                        "DebugProbesKt.bin",
                        "com/**",
                        "org/**",
                        "**/*.java",
                        "**/*.proto",
                    )
                )
            }
        }
        packaging {
            jniLibs.useLegacyPackaging = true
        }
        (this as? AbstractAppExtension)?.apply {
            buildTypes {
                getByName("release") {
                    isShrinkResources = true
                }
            }
            applicationVariants.forEach { variant ->
                variant.outputs.forEach {
                    it as BaseVariantOutputImpl
                    it.outputFileName = it.outputFileName.replace(
                        "app", "${project.name}-" + variant.versionName
                    ).replace("-release", "").replace("-oss", "")
                }
            }
        }
    }
    (android as? ApplicationExtension)?.apply {
        defaultConfig {
            targetSdk = 35
        }
    }
}

fun Project.setupAppCommon() {
    setupAppCommon("")
}

fun Project.setupAppCommon(projectName: String) {
    setupCommon(projectName)

    val lp = requireLocalProperties()
    val keystorePwd = lp.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS")
    val alias = lp.getProperty("ALIAS_NAME") ?: System.getenv("ALIAS_NAME")
    val pwd = lp.getProperty("ALIAS_PASS") ?: System.getenv("ALIAS_PASS")

    androidApp.apply {
        if (keystorePwd != null) {
            signingConfigs {
                create("release") {
                    storeFile = rootProject.file("release.keystore")
                    storePassword = keystorePwd
                    keyAlias = alias
                    keyPassword = pwd
                }
            }
        } else if (requireFlavor().contains("OssRelease")) {
            return
        }
        buildTypes {
            val key = signingConfigs.findByName("release")
            if (key != null) {
                if (requireTargetAbi().isBlank()) {
                    getByName("release").signingConfig = key
                }
                getByName("debug").signingConfig = key
            }
        }
    }
}

fun Project.setupPlugin(projectName: String) {
    val propPrefix = projectName.uppercase(Locale.ROOT)
    val projName = projectName.lowercase(Locale.ROOT)
    val verName = requireMetadata().getProperty("${propPrefix}_VERSION_NAME").trim()
    val verCode = requireMetadata().getProperty("${propPrefix}_VERSION").trim().toInt()
    androidApp.defaultConfig {
        versionName = verName
        versionCode = verCode
    }

    apply(plugin = "kotlin-android")

    setupAppCommon(projectName)

    val targetAbi = requireTargetAbi()

    androidApp.apply {
        this as AbstractAppExtension

        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    project(":plugin:api").file("proguard-rules.pro")
                )
            }
        }

        splits.abi {
            isEnable = true
            isUniversalApk = false

            if (targetAbi.isNotBlank()) {
                reset()
                include(targetAbi)
            } else {
                reset()
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            }
        }

        flavorDimensions.add("vendor")
        productFlavors {
            create("oss")
        }

        if (System.getenv("SKIP_BUILD") != "on" && System.getProperty("SKIP_BUILD_$propPrefix") != "on") {
            if (targetAbi.isBlank()) {
                tasks.register<Exec>("externalBuild") {
                    executable(rootProject.file("run"))
                    args("plugin", projName)
                    workingDir(rootProject.projectDir)
                }

                tasks.configureEach {
                    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
                        dependsOn("externalBuild")
                    }
                }
            } else {
                tasks.register<Exec>("externalBuildInit") {
                    executable(rootProject.file("run"))
                    args("plugin", projName, "init")
                    workingDir(rootProject.projectDir)
                }
                tasks.register<Exec>("externalBuild") {
                    executable(rootProject.file("run"))
                    args("plugin", projName, targetAbi)
                    workingDir(rootProject.projectDir)
                    dependsOn("externalBuildInit")
                }
                tasks.register<Exec>("externalBuildEnd") {
                    executable(rootProject.file("run"))
                    args("plugin", projName, "end")
                    workingDir(rootProject.projectDir)
                    dependsOn("externalBuild")
                }
                tasks.configureEach {
                    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
                        dependsOn("externalBuildEnd")
                    }
                }
            }
        }

        applicationVariants.all {

            outputs.all {
                this as BaseVariantOutputImpl
                outputFileName = outputFileName.replace(
                    project.name, "${project.name}-plugin-$versionName"
                ).replace("-release", "").replace("-oss", "")

            }
        }
    }

    dependencies.add("implementation", project(":plugin:api"))

}

fun Project.setupApp() {
    val pkgName = requireMetadata().getProperty("PACKAGE_NAME").trim()
    val verName = requireMetadata().getProperty("VERSION_NAME").trim()
    val verCode = requireMetadata().getProperty("VERSION_CODE").trim().toInt()
    androidApp.apply {
        defaultConfig {
            applicationId = pkgName
            versionCode = verCode
            versionName = verName
        }
    }
    setupAppCommon()

    val targetAbi = requireTargetAbi()

    androidApp.apply {
        this as AbstractAppExtension

        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro")
                )
            }
        }

        splits.abi {
            isEnable = true
            isUniversalApk = false

            if (targetAbi.isNotBlank()) {
                reset()
                include(targetAbi)
            } else {
                reset()
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            }
        }

        flavorDimensions.add("vendor")
        productFlavors {
            create("oss")
        }

        applicationVariants.all {
            outputs.all {
                this as BaseVariantOutputImpl
                outputFileName = outputFileName.replace(project.name, "Exclave-$versionName")
                    .replace("-release", "")
                    .replace("-oss", "")

            }
        }

        tasks.register("downloadAssets") {
            outputs.upToDateWhen {
                requireFlavor().endsWith("Debug")
            }
            doLast {
                downloadRootCAList()
                downloadAssets()
            }
        }
    }

    dependencies {
        add("implementation", project(":plugin:api"))
    }
}