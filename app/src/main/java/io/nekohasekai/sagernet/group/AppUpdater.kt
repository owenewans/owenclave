package io.nekohasekai.sagernet.group

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.getArray
import io.nekohasekai.sagernet.ktx.getString
import io.nekohasekai.sagernet.ktx.parseJson
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libexclavecore.Libexclavecore
import java.io.File

object AppUpdater {

    private const val GITHUB_API = "https://api.github.com/repos/owenewans/owenclave/releases/latest"
    private const val GITHUB_RELEASES_PAGE = "https://github.com/owenewans/owenclave/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val tagName: String,
        val apkUrl: String,
        val apkName: String,
        val releaseNotes: String,
        val htmlUrl: String,
    )

    fun compareVersions(current: String, remote: String): Boolean {
        val currentParts = current.removeSuffix("-beta.1").split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = remote.removeSuffix("-beta.1").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        val currentBeta = current.contains("-beta")
        val remoteBeta = remote.contains("-beta")
        return !currentBeta && remoteBeta
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val client = Libexclavecore.newHttpClient().apply {
            keepAlive()
            if (SagerNet.started && DataStore.startedProfile > 0) {
                useUDS(SagerNet.deviceStorage.noBackupFilesDir.toString() + "/ipc.sock")
            }
        }

        try {
            val response = client.newRequest().apply {
                setURL(GITHUB_API)
                setUserAgent(USER_AGENT)
                setHeader("Accept", "application/vnd.github+json")
            }.execute()

            val release = parseJson(response.contentString).asJsonObject
            val tagName = release.getString("tag_name") ?: return@withContext null
            val remoteVersion = tagName.removePrefix("v")

            if (!compareVersions(BuildConfig.VERSION_NAME, remoteVersion)) {
                return@withContext null
            }

            val assets = release.getArray("assets") ?: return@withContext null
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val apkAsset = assets.find {
                val name = it.getString("name") ?: ""
                name.endsWith(".apk") && name.contains(abi)
            } ?: assets.find {
                val name = it.getString("name") ?: ""
                name.endsWith(".apk") && name.contains("arm64-v8a")
            } ?: return@withContext null

            val apkUrl = apkAsset.getString("browser_download_url") ?: return@withContext null
            val apkName = apkAsset.getString("name") ?: "owenclave-update.apk"
            val releaseNotes = release.getString("body") ?: ""
            val htmlUrl = release.getString("html_url") ?: GITHUB_RELEASES_PAGE

            UpdateInfo(
                versionName = remoteVersion,
                tagName = tagName,
                apkUrl = apkUrl,
                apkName = apkName,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
            )
        } finally {
            client.close()
        }
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit = {}) {
        val client = Libexclavecore.newHttpClient().apply {
            keepAlive()
            if (SagerNet.started && DataStore.startedProfile > 0) {
                useUDS(SagerNet.deviceStorage.noBackupFilesDir.toString() + "/ipc.sock")
            }
        }

        try {
            val response = client.newRequest().apply {
                setURL(info.apkUrl)
                setUserAgent(USER_AGENT)
            }.execute()

            val apkFile = File(context.cacheDir, info.apkName)
            apkFile.parentFile?.mkdirs()

            response.writeTo(apkFile.canonicalPath)

            runOnMainDispatcher {
                installApk(context, apkFile)
            }
        } finally {
            client.close()
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".cache",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }

    fun canInstallApk(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPermission(activity)) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }
    }

    private fun canInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openReleasesPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_PAGE)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
