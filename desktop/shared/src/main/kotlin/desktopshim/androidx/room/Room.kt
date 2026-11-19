package androidx.room

import android.content.Context
import android.os.Parcel
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.StatsEntity
import io.nekohasekai.sagernet.database.preference.InMemoryDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.DesktopEnv
import io.nekohasekai.sagernet.fmt.KryoConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
annotation class Database(
    val entities: Array<KClass<*>> = [],
    val version: Int = 1,
    val exportSchema: Boolean = true,
    val autoMigrations: Array<AutoMigration> = []
)

@Retention(AnnotationRetention.BINARY)
annotation class AutoMigration(
    val from: Int,
    val to: Int,
    val spec: KClass<*> = Any::class
)

@Retention(AnnotationRetention.BINARY)
annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = []
)

@Retention(AnnotationRetention.BINARY)
annotation class Index(
    vararg val value: String = [],
    val name: String = "",
    val unique: Boolean = false
)

@Retention(AnnotationRetention.BINARY)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Retention(AnnotationRetention.BINARY)
annotation class ColumnInfo(
    val name: String = "",
    val defaultValue: String = ""
)

@Retention(AnnotationRetention.BINARY)
annotation class Ignore

@Retention(AnnotationRetention.BINARY)
annotation class Dao

@Retention(AnnotationRetention.BINARY)
annotation class Query(val value: String)

@Retention(AnnotationRetention.BINARY)
annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)

@Retention(AnnotationRetention.BINARY)
annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT)

@Retention(AnnotationRetention.BINARY)
annotation class Delete

@Retention(AnnotationRetention.BINARY)
annotation class TypeConverters(vararg val value: KClass<*>)

@Retention(AnnotationRetention.BINARY)
@Repeatable
annotation class DeleteColumn(
    val tableName: String,
    val columnName: String
)

@Retention(AnnotationRetention.BINARY)
@Repeatable
annotation class DeleteTable(
    val tableName: String
)

object OnConflictStrategy {
    const val NONE = 0
    const val REPLACE = 1
    const val ROLLBACK = 2
    const val ABORT = 3
    const val FAIL = 4
    const val IGNORE = 5
}

abstract class RoomDatabase {
    open fun clearAllTables() {}
    open fun close() {}

    class Builder<T : RoomDatabase>(
        private val context: Context?,
        private val klass: Class<T>,
        private val name: String?
    ) {
        fun addMigrations(vararg migrations: Migration): Builder<T> = this
        fun allowMainThreadQueries(): Builder<T> = this
        fun enableMultiInstanceInvalidation(): Builder<T> = this
        fun fallbackToDestructiveMigration(dropAllTables: Boolean = true): Builder<T> = this
        fun fallbackToDestructiveMigration(): Builder<T> = this
        fun fallbackToDestructiveMigrationOnDowngrade(dropAllTables: Boolean = true): Builder<T> = this
        fun fallbackToDestructiveMigrationOnDowngrade(): Builder<T> = this
        fun setQueryExecutor(executor: java.util.concurrent.Executor): Builder<T> = this

        @Suppress("UNCHECKED_CAST")
        fun build(): T {
            return when (klass) {
                SagerDatabase::class.java -> DesktopSagerDatabase as T
                PublicDatabase::class.java -> DesktopPublicDatabase as T
                InMemoryDatabase::class.java -> DesktopInMemoryDatabase as T
                else -> klass.getDeclaredConstructor().newInstance()
            }
        }
    }
}

object Room {
    @JvmStatic
    fun <T : RoomDatabase> databaseBuilder(
        context: Context,
        klass: Class<T>,
        name: String?
    ): RoomDatabase.Builder<T> = RoomDatabase.Builder(context, klass, name)

    @JvmStatic
    fun <T : RoomDatabase> inMemoryDatabaseBuilder(
        context: Context,
        klass: Class<T>
    ): RoomDatabase.Builder<T> = RoomDatabase.Builder(context, klass, null)
}

class DesktopKeyValueDao(private val backingFile: File?) : KeyValuePair.Dao {
    private val map = ConcurrentHashMap<String, KeyValuePair>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        val file = backingFile ?: return
        if (!file.isFile) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { dis ->
                val count = dis.readInt()
                repeat(count) {
                    val key = dis.readUTF()
                    val type = dis.readInt()
                    val len = dis.readInt()
                    val bytes = ByteArray(len)
                    dis.readFully(bytes)
                    map[key] = KeyValuePair(key).apply {
                        valueType = type
                        value = bytes
                    }
                }
            }
        }
    }

    @Synchronized
    private fun save() {
        val file = backingFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { dos ->
                val snapshot = map.values.toList()
                dos.writeInt(snapshot.size)
                for (kv in snapshot) {
                    dos.writeUTF(kv.key)
                    dos.writeInt(kv.valueType)
                    dos.writeInt(kv.value.size)
                    dos.write(kv.value)
                }
            }
            tmp.renameTo(file)
        }
    }

    override fun all(): List<KeyValuePair> = map.values.toList()

    override fun get(key: String): KeyValuePair? = map[key]

    override fun put(value: KeyValuePair): Long {
        map[value.key] = value
        save()
        return 1L
    }

    override fun delete(key: String): Int {
        val removed = map.remove(key) != null
        if (removed) save()
        return if (removed) 1 else 0
    }

    override fun reset(): Int {
        val size = map.size
        map.clear()
        save()
        return size
    }

    override fun insert(list: List<KeyValuePair>) {
        for (kv in list) {
            map[kv.key] = kv
        }
        save()
    }
}

object DesktopPublicDatabase : PublicDatabase() {
    private val dao by lazy {
        DesktopKeyValueDao(File(DesktopEnv.dataDir, "configuration_public.bin"))
    }
    override fun keyValuePairDao(): KeyValuePair.Dao = dao
}

object DesktopInMemoryDatabase : InMemoryDatabase() {
    private val dao = DesktopKeyValueDao(null)
    override fun keyValuePairDao(): KeyValuePair.Dao = dao
}

object DesktopSagerDatabase : SagerDatabase() {
    @Volatile
    var rulesDaoOverride: RuleEntity.Dao? = null

    @Volatile
    var enabledRulesOverride: List<RuleEntity>? = null

    private val dbFile: File
        get() = File(DesktopEnv.dataDir, "sager_database.bin")

    private val groups = ConcurrentHashMap<Long, ProxyGroup>()
    private val proxies = ConcurrentHashMap<Long, ProxyEntity>()
    private val rules = ConcurrentHashMap<Long, RuleEntity>()
    private val stats = ConcurrentHashMap<Int, StatsEntity>()
    private val assets = ConcurrentHashMap<Long, AssetEntity>()

    private val nextGroupId = AtomicLong(1L)
    private val nextProxyId = AtomicLong(1L)
    private val nextRuleId = AtomicLong(1L)
    private val nextAssetId = AtomicLong(1L)

    private val proxiesStateFlow = MutableStateFlow<List<ProxyEntity>>(emptyList())

    init {
        load()
    }

    private fun notifyProxiesChanged() {
        proxiesStateFlow.value = proxies.values.sortedBy { it.userOrder }
        save()
    }

    @Synchronized
    private fun load() {
        val file = dbFile
        if (!file.isFile) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { dis ->
                val version = dis.readInt()
                if (version != 1) return

                val groupCount = dis.readInt()
                repeat(groupCount) {
                    val len = dis.readInt()
                    val bytes = ByteArray(len)
                    dis.readFully(bytes)
                    val group = KryoConverters.deserialize(ProxyGroup(), bytes)
                    groups[group.id] = group
                    if (group.id >= nextGroupId.get()) {
                        nextGroupId.set(group.id + 1L)
                    }
                }

                val proxyCount = dis.readInt()
                repeat(proxyCount) {
                    val len = dis.readInt()
                    val bytes = ByteArray(len)
                    dis.readFully(bytes)
                    val entity = KryoConverters.deserialize(ProxyEntity(), bytes)
                    proxies[entity.id] = entity
                    if (entity.id >= nextProxyId.get()) {
                        nextProxyId.set(entity.id + 1L)
                    }
                }

                val ruleCount = dis.readInt()
                repeat(ruleCount) {
                    val rule = RuleEntity(
                        id = dis.readLong(),
                        name = dis.readUTF(),
                        userOrder = dis.readLong(),
                        enabled = dis.readBoolean(),
                        domains = dis.readUTF(),
                        ip = dis.readUTF(),
                        port = dis.readUTF(),
                        sourcePort = dis.readUTF(),
                        network = dis.readUTF(),
                        source = dis.readUTF(),
                        protocol = dis.readUTF(),
                        attrs = dis.readUTF(),
                        outbound = dis.readLong(),
                        packages = List(dis.readInt()) { dis.readUTF() },
                        ssid = dis.readUTF(),
                        networkType = List(dis.readInt()) { dis.readUTF() }.toSet(),
                        customPackageNames = List(dis.readInt()) { dis.readUTF() },
                    )
                    rules[rule.id] = rule
                    if (rule.id >= nextRuleId.get()) {
                        nextRuleId.set(rule.id + 1L)
                    }
                }

                val assetCount = dis.readInt()
                repeat(assetCount) {
                    val asset = AssetEntity(
                        id = dis.readLong(),
                        url = dis.readUTF(),
                        name = dis.readUTF()
                    )
                    assets[asset.id] = asset
                    if (asset.id >= nextAssetId.get()) {
                        nextAssetId.set(asset.id + 1L)
                    }
                }
            }
            proxiesStateFlow.value = proxies.values.sortedBy { it.userOrder }
        }
    }

    @Synchronized
    private fun save() {
        val file = dbFile
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { dos ->
                dos.writeInt(1)

                val groupList = groups.values.toList()
                dos.writeInt(groupList.size)
                for (g in groupList) {
                    val bytes = KryoConverters.serialize(g)
                    dos.writeInt(bytes.size)
                    dos.write(bytes)
                }

                val proxyList = proxies.values.toList()
                dos.writeInt(proxyList.size)
                for (p in proxyList) {
                    val bytes = KryoConverters.serialize(p)
                    dos.writeInt(bytes.size)
                    dos.write(bytes)
                }

                val ruleList = rules.values.toList()
                dos.writeInt(ruleList.size)
                for (r in ruleList) {
                    dos.writeLong(r.id)
                    dos.writeUTF(r.name)
                    dos.writeLong(r.userOrder)
                    dos.writeBoolean(r.enabled)
                    dos.writeUTF(r.domains)
                    dos.writeUTF(r.ip)
                    dos.writeUTF(r.port)
                    dos.writeUTF(r.sourcePort)
                    dos.writeUTF(r.network)
                    dos.writeUTF(r.source)
                    dos.writeUTF(r.protocol)
                    dos.writeUTF(r.attrs)
                    dos.writeLong(r.outbound)
                    dos.writeInt(r.packages.size)
                    for (pkg in r.packages) {
                        dos.writeUTF(pkg)
                    }
                    dos.writeUTF(r.ssid)
                    dos.writeInt(r.networkType.size)
                    for (nt in r.networkType) {
                        dos.writeUTF(nt)
                    }
                    dos.writeInt(r.customPackageNames.size)
                    for (cp in r.customPackageNames) {
                        dos.writeUTF(cp)
                    }
                }

                val assetList = assets.values.toList()
                dos.writeInt(assetList.size)
                for (a in assetList) {
                    dos.writeLong(a.id)
                    dos.writeUTF(a.url)
                    dos.writeUTF(a.name)
                }
            }
            tmp.renameTo(file)
        }
    }

    private val groupDaoImpl = object : ProxyGroup.Dao {
        override fun allGroups(): List<ProxyGroup> =
            groups.values.sortedBy { it.userOrder }

        override fun subscriptions(): List<ProxyGroup> =
            groups.values.filter { it.type == io.nekohasekai.sagernet.GroupType.SUBSCRIPTION }
                .sortedBy { it.userOrder }

        override fun nextOrder(): Long? =
            groups.values.maxOfOrNull { it.userOrder }?.plus(1L)

        override fun getById(groupId: Long): ProxyGroup? = groups[groupId]

        override fun deleteById(groupId: Long): Int {
            val removed = groups.remove(groupId) != null
            if (removed) save()
            return if (removed) 1 else 0
        }

        override fun deleteGroup(group: ProxyGroup) {
            groups.remove(group.id)
            save()
        }

        override fun deleteGroup(groupList: List<ProxyGroup>) {
            for (g in groupList) groups.remove(g.id)
            save()
        }

        override fun createGroup(group: ProxyGroup): Long {
            if (group.id == 0L) {
                group.id = nextGroupId.getAndIncrement()
            } else if (group.id >= nextGroupId.get()) {
                nextGroupId.set(group.id + 1L)
            }
            groups[group.id] = group
            save()
            return group.id
        }

        override fun updateGroup(group: ProxyGroup) {
            groups[group.id] = group
            save()
        }

        override fun reset() {
            groups.clear()
            save()
        }

        override fun insert(groupList: List<ProxyGroup>) {
            for (g in groupList) {
                if (g.id == 0L) {
                    g.id = nextGroupId.getAndIncrement()
                } else if (g.id >= nextGroupId.get()) {
                    nextGroupId.set(g.id + 1L)
                }
                groups[g.id] = g
            }
            save()
        }
    }

    private val proxyDaoImpl = object : ProxyEntity.Dao {
        override fun getAll(): List<ProxyEntity> = proxies.values.toList()

        override fun getIdsByGroup(groupId: Long): List<Long> =
            getByGroup(groupId).map { it.id }

        override fun getByGroup(groupId: Long): List<ProxyEntity> =
            proxies.values.filter { it.groupId == groupId }.sortedBy { it.userOrder }

        override fun getByGroupFlow(groupId: Long): Flow<List<ProxyEntity>> =
            proxiesStateFlow.map { list ->
                list.filter { it.groupId == groupId }.sortedBy { it.userOrder }
            }

        override fun getEntities(proxyIds: List<Long>): List<ProxyEntity> =
            proxyIds.mapNotNull { proxies[it] }

        override fun countByGroup(groupId: Long): Long =
            proxies.values.count { it.groupId == groupId }.toLong()

        override fun nextOrder(groupId: Long): Long? =
            proxies.values.filter { it.groupId == groupId }.maxOfOrNull { it.userOrder }?.plus(1L)

        override fun getById(proxyId: Long): ProxyEntity? = proxies[proxyId]

        override fun isIdInGroup(proxyId: Long, groupId: Long): Long =
            if (proxies[proxyId]?.groupId == groupId) 1L else 0L

        override fun deleteById(proxyId: Long): Int {
            val removed = proxies.remove(proxyId) != null
            if (removed) notifyProxiesChanged()
            return if (removed) 1 else 0
        }

        override fun deleteByGroup(groupId: Long) {
            val toRemove = proxies.values.filter { it.groupId == groupId }.map { it.id }
            for (id in toRemove) proxies.remove(id)
            if (toRemove.isNotEmpty()) notifyProxiesChanged()
        }

        override fun deleteByGroup(groupId: LongArray) {
            val set = groupId.toSet()
            val toRemove = proxies.values.filter { it.groupId in set }.map { it.id }
            for (id in toRemove) proxies.remove(id)
            if (toRemove.isNotEmpty()) notifyProxiesChanged()
        }

        override fun deleteProxy(proxy: ProxyEntity): Int = deleteById(proxy.id)

        override fun deleteProxy(proxies: List<ProxyEntity>): Int {
            var count = 0
            for (p in proxies) {
                if (this@DesktopSagerDatabase.proxies.remove(p.id) != null) count++
            }
            if (count > 0) notifyProxiesChanged()
            return count
        }

        override fun updateProxy(proxy: ProxyEntity): Int {
            proxies[proxy.id] = proxy
            notifyProxiesChanged()
            return 1
        }

        override fun updateProxy(proxies: List<ProxyEntity>): Int {
            for (p in proxies) {
                this@DesktopSagerDatabase.proxies[p.id] = p
            }
            if (proxies.isNotEmpty()) notifyProxiesChanged()
            return proxies.size
        }

        override fun addProxy(proxy: ProxyEntity): Long {
            if (proxy.id == 0L) {
                proxy.id = nextProxyId.getAndIncrement()
            } else if (proxy.id >= nextProxyId.get()) {
                nextProxyId.set(proxy.id + 1L)
            }
            proxies[proxy.id] = proxy
            notifyProxiesChanged()
            return proxy.id
        }

        override fun insert(proxies: List<ProxyEntity>) {
            for (p in proxies) {
                if (p.id == 0L) {
                    p.id = nextProxyId.getAndIncrement()
                } else if (p.id >= nextProxyId.get()) {
                    nextProxyId.set(p.id + 1L)
                }
                this@DesktopSagerDatabase.proxies[p.id] = p
            }
            if (proxies.isNotEmpty()) notifyProxiesChanged()
        }

        override fun deleteAll(groupId: Long): Int {
            val toRemove = proxies.values.filter { it.groupId == groupId }.map { it.id }
            for (id in toRemove) proxies.remove(id)
            if (toRemove.isNotEmpty()) notifyProxiesChanged()
            return toRemove.size
        }

        override fun reset() {
            proxies.clear()
            notifyProxiesChanged()
        }

        override fun updateTraffic(id: Long, tx: Long?, rx: Long?): Int {
            val entity = proxies[id] ?: return 0
            if (tx != null) entity.tx = tx
            if (rx != null) entity.rx = rx
            notifyProxiesChanged()
            return 1
        }

        override fun updateConnectedTime(id: Long, time: Long): Int {
            val entity = proxies[id] ?: return 0
            entity.connectedTime = time
            notifyProxiesChanged()
            return 1
        }
    }

    private val rulesDaoImpl = object : RuleEntity.Dao {
        override fun allRules(): List<RuleEntity> =
            rules.values.sortedBy { it.userOrder }

        override fun enabledRules(enabled: Boolean): List<RuleEntity> {
            val source = enabledRulesOverride ?: rules.values
            return source.filter { it.enabled == enabled }.sortedBy { it.userOrder }
        }

        override fun nextOrder(): Long? =
            rules.values.maxOfOrNull { it.userOrder }?.plus(1L)

        override fun getById(ruleId: Long): RuleEntity? = rules[ruleId]

        override fun deleteById(ruleId: Long): Int {
            val removed = rules.remove(ruleId) != null
            if (removed) save()
            return if (removed) 1 else 0
        }

        override fun deleteRule(rule: RuleEntity) {
            rules.remove(rule.id)
            save()
        }

        override fun deleteRules(rules: List<RuleEntity>) {
            for (r in rules) this@DesktopSagerDatabase.rules.remove(r.id)
            save()
        }

        override fun createRule(rule: RuleEntity): Long {
            if (rule.id == 0L) {
                rule.id = nextRuleId.getAndIncrement()
            } else if (rule.id >= nextRuleId.get()) {
                nextRuleId.set(rule.id + 1L)
            }
            rules[rule.id] = rule
            save()
            return rule.id
        }

        override fun updateRule(rule: RuleEntity) {
            rules[rule.id] = rule
            save()
        }

        override fun updateRules(rules: List<RuleEntity>) {
            for (r in rules) this@DesktopSagerDatabase.rules[r.id] = r
            save()
        }

        override fun reset() {
            rules.clear()
            save()
        }

        override fun deleteByNamePrefix(prefix: String): Int {
            val toRemove = rules.values.filter { it.name.startsWith(prefix) }.map { it.id }
            for (id in toRemove) rules.remove(id)
            if (toRemove.isNotEmpty()) save()
            return toRemove.size
        }

        override fun insert(rules: List<RuleEntity>) {
            for (r in rules) {
                if (r.id == 0L) {
                    r.id = nextRuleId.getAndIncrement()
                } else if (r.id >= nextRuleId.get()) {
                    nextRuleId.set(r.id + 1L)
                }
                this@DesktopSagerDatabase.rules[r.id] = r
            }
            save()
        }

        override fun enableAll(enabled: Boolean) {
            for (r in rules.values) r.enabled = enabled
            save()
        }
    }

    private val statsDaoImpl = object : StatsEntity.Dao {
        override fun all(): List<StatsEntity> = stats.values.toList()

        override fun get(uid: String): StatsEntity? =
            uid.toIntOrNull()?.let { stats[it] }

        override fun delete(uid: String): Int {
            val id = uid.toIntOrNull() ?: return 0
            return if (stats.remove(id) != null) 1 else 0
        }

        override fun create(stats: StatsEntity) {
            this@DesktopSagerDatabase.stats[stats.uid] = stats
        }

        override fun update(stats: List<StatsEntity>) {
            for (s in stats) this@DesktopSagerDatabase.stats[s.uid] = s
        }

        override fun deleteAll() {
            stats.clear()
        }
    }

    private val assetDaoImpl = object : AssetEntity.Dao {
        override fun getAll(): List<AssetEntity> = assets.values.toList()

        override fun getAllByName(name: String): List<AssetEntity> =
            assets.values.filter { it.name == name }

        override fun getById(id: Long): AssetEntity? = assets[id]

        override fun delete(name: String): Int {
            val toRemove = assets.values.filter { it.name == name }.map { it.id }
            for (id in toRemove) assets.remove(id)
            if (toRemove.isNotEmpty()) save()
            return toRemove.size
        }

        override fun create0(asset: AssetEntity) {
            if (asset.id == 0L) {
                asset.id = nextAssetId.getAndIncrement()
            } else if (asset.id >= nextAssetId.get()) {
                nextAssetId.set(asset.id + 1L)
            }
            assets[asset.id] = asset
            save()
        }

        override fun update0(asset: AssetEntity) {
            assets[asset.id] = asset
            save()
        }

        override fun reset() {
            assets.clear()
            save()
        }

        override fun insert(assets: List<AssetEntity>) {
            for (a in assets) {
                if (a.id == 0L) {
                    a.id = nextAssetId.getAndIncrement()
                } else if (a.id >= nextAssetId.get()) {
                    nextAssetId.set(a.id + 1L)
                }
                this@DesktopSagerDatabase.assets[a.id] = a
            }
            save()
        }
    }

    override fun groupDao(): ProxyGroup.Dao = groupDaoImpl
    override fun proxyDao(): ProxyEntity.Dao = proxyDaoImpl
    override fun rulesDao(): RuleEntity.Dao = rulesDaoOverride ?: rulesDaoImpl
    override fun statsDao(): StatsEntity.Dao = statsDaoImpl
    override fun assetDao(): AssetEntity.Dao = assetDaoImpl

    override fun clearAllTables() {
        groups.clear()
        proxies.clear()
        rules.clear()
        stats.clear()
        assets.clear()
        notifyProxiesChanged()
    }
}
