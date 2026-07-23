package io.nekohasekai.sagernet.ui.compose

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.ParcelizeBridge
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.ktx.contains
import io.nekohasekai.sagernet.ktx.getInt
import io.nekohasekai.sagernet.ktx.getStringArray
import io.nekohasekai.sagernet.ktx.parseJson
import kotlin.io.encoding.Base64

/**
 * Backup/restore helpers shared by the Compose Tools screen. Ports the logic
 * from the legacy [io.nekohasekai.sagernet.ui.BackupFragment] into plain
 * functions with no View dependencies.
 */
object BackupUtil {

    private fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        writeToParcel(parcel, 0)
        try {
            return Base64.encode(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    fun doBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
        val out = JsonObject()
        out.addProperty("version", 1)
        if (profile) {
            out.add("profiles", JsonArray().apply {
                SagerDatabase.proxyDao.getAll().forEach { add(it.toBase64Str()) }
            })
            out.add("groups", JsonArray().apply {
                SagerDatabase.groupDao.allGroups().forEach { add(it.toBase64Str()) }
            })
        }
        if (rule) {
            out.add("rules", JsonArray().apply {
                SagerDatabase.rulesDao.allRules().forEach { add(it.toBase64Str()) }
            })
            out.add("assets", JsonArray().apply {
                SagerDatabase.assetDao.getAll().forEach { add(it.toBase64Str()) }
            })
        }
        if (setting) {
            out.add("settings", JsonArray().apply {
                PublicDatabase.kvPairDao.all().forEach { add(it.toBase64Str()) }
            })
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(out)
    }

    /** Parses backup JSON; returns null if invalid or unsupported version. */
    fun parseBackup(text: String): JsonObject? {
        val content = try {
            parseJson(text).asJsonObject
        } catch (_: Exception) {
            return null
        }
        val version = content.getInt("version")
        if (version == null || version != 1) return null
        return content
    }

    fun finishImport(
        content: JsonObject, profile: Boolean, rule: Boolean, setting: Boolean,
    ) {
        if (profile && content.contains("profiles")) {
            val profiles = mutableListOf<ProxyEntity>()
            content.getStringArray("profiles")?.forEach {
                val data = Base64.decode(it)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                profiles.add(ProxyEntity.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.proxyDao.reset()
            SagerDatabase.proxyDao.insert(profiles)

            val groups = mutableListOf<ProxyGroup>()
            content.getStringArray("groups")?.forEach {
                val data = Base64.decode(it)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                groups.add(ProxyGroup.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.groupDao.reset()
            SagerDatabase.groupDao.insert(groups)
        }
        if (rule && content.contains("rules")) {
            val rules = mutableListOf<RuleEntity>()
            content.getStringArray("rules")?.forEach {
                val data = Base64.decode(it)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                rules.add(ParcelizeBridge.createRule(parcel))
                parcel.recycle()
            }
            SagerDatabase.rulesDao.reset()
            SagerDatabase.rulesDao.insert(rules)

            val assets = mutableListOf<AssetEntity>()
            content.getStringArray("assets")?.forEach {
                val data = Base64.decode(it)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                assets.add(ParcelizeBridge.createAsset(parcel))
                parcel.recycle()
            }
            SagerDatabase.assetDao.reset()
            SagerDatabase.assetDao.insert(assets)
        }
        if (setting && content.contains("settings")) {
            val settings = mutableListOf<KeyValuePair>()
            content.getStringArray("settings")?.forEach {
                val data = Base64.decode(it)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                settings.add(KeyValuePair.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            PublicDatabase.kvPairDao.reset()
            PublicDatabase.kvPairDao.insert(settings)
        }
    }

    /** Wipes all stored settings (KV pairs), reverting every setting to default. */
    fun resetSettings() {
        PublicDatabase.kvPairDao.reset()
    }
}
