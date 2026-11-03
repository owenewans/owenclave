package io.nekohasekai.sagernet.database

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class ProtocolRemovalMigrationTest {
    @Test
    fun removesRetiredProfilesAndDependentChainsWithoutLosingOtherData() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection)
            fun insert(id: Int, type: Int, column: String = "socksBean", blob: ByteArray = byteArrayOf(1, 2, 3)) {
                connection.prepareStatement("INSERT INTO proxy_entities (id,groupId,type,userOrder,tx,rx,status,ping,connectedTime,uuid,iconIndex,`$column`) VALUES (?,1,?,0,0,0,0,0,123,'test',7,?)").use {
                    it.setInt(1, id)
                    it.setInt(2, type)
                    it.setBytes(3, blob)
                    it.executeUpdate()
                }
            }
            insert(1, 0)
            insert(2, 31, "olcrtcBean")
            val chain = ChainBean().apply { initializeDefaultValues(); proxies = listOf(1L, 2L) }
            insert(3, 8, "chainBean", KryoConverters.serialize(chain))
            val balancer = BalancerBean().apply { initializeDefaultValues(); proxies = listOf(3L) }
            insert(4, 14, "balancerBean", KryoConverters.serialize(balancer))
            // deleted high IDs must not become reusable after the table rebuild
            insert(100, 31, "olcrtcBean")
            connection.createStatement().use {
                it.execute("INSERT INTO proxy_groups (id,userOrder,ungrouped,type,`order`,frontProxy,landingProxy) VALUES (1,0,0,0,0,3,2)")
                it.execute("INSERT INTO rules (id,name,userOrder,enabled,domains,ip,port,sourcePort,network,source,protocol,attrs,outbound,packages) VALUES (1,'removed',0,1,'','','','','','','','',3,'')")
            }
            SagerDatabase_Migration_40_41.migrate(database(connection))
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id,connectedTime,iconIndex,socksBean,masqueBean FROM proxy_entities").use {
                    assertTrue(it.next())
                    assertEquals(1, it.getInt(1))
                    assertEquals(123, it.getInt(2))
                    assertEquals(7, it.getInt(3))
                    assertArrayEquals(byteArrayOf(1, 2, 3), it.getBytes(4))
                    assertNull(it.getBytes(5))
                    assertFalse(it.next())
                }
                statement.executeQuery("SELECT frontProxy,landingProxy FROM proxy_groups").use {
                    assertTrue(it.next())
                    assertEquals(-1, it.getInt(1))
                    assertEquals(-1, it.getInt(2))
                }
                statement.executeQuery("SELECT enabled,outbound FROM rules WHERE id=1").use {
                    assertTrue(it.next())
                    assertEquals(0, it.getInt(1))
                    assertEquals(0, it.getInt(2))
                }
                statement.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='proxy_entities'").use {
                    assertTrue(it.next())
                    assertEquals(100, it.getInt(1))
                }
            }
        }
    }

    @Test
    fun preservesSequenceWhenEveryProfileIsRemoved() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection)
            connection.createStatement().use {
                it.execute("INSERT INTO proxy_entities (id,groupId,type,userOrder,tx,rx,status,ping,uuid) VALUES (100,1,31,0,0,0,0,0,'removed')")
            }
            SagerDatabase_Migration_40_41.migrate(database(connection))
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='proxy_entities'").use {
                    assertTrue(it.next())
                    assertEquals(100, it.getInt(1))
                }
            }
        }
    }

    private fun createSchema(connection: Connection) {
        val schema = JsonParser.parseString(File("schemas/io.nekohasekai.sagernet.database.SagerDatabase/40.json").readText())
            .asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        for (entity in schema) {
            val table = entity.asJsonObject
            connection.createStatement().use {
                it.execute(table.get("createSql").asString.replace("\${TABLE_NAME}", table.get("tableName").asString))
            }
        }
    }

    private fun database(connection: Connection): SupportSQLiteDatabase = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java),
    ) { _, method, args ->
        when (method.name) {
            "query" -> {
                val statement = connection.createStatement()
                val result = statement.executeQuery(args!![0] as String)
                Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, cursorMethod, cursorArgs ->
                    val column = (cursorArgs?.firstOrNull() as? Int)?.plus(1)
                    when (cursorMethod.name) {
                        "moveToNext", "moveToFirst" -> result.next()
                        "getLong" -> result.getLong(column!!)
                        "getBlob" -> result.getBytes(column!!)
                        "isNull" -> result.getObject(column!!) == null
                        "close" -> { result.close(); statement.close(); null }
                        else -> error("unexpected cursor call: ${cursorMethod.name}")
                    }
                }
            }
            "execSQL" -> connection.prepareStatement(args!![0] as String).use { statement ->
                (args.getOrNull(1) as? Array<*>)?.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.execute()
                null
            }
            else -> error("unexpected database call: ${method.name}")
        }
    } as SupportSQLiteDatabase
}
