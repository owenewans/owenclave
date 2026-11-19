package androidx.room.migration

import androidx.sqlite.db.SupportSQLiteDatabase

interface AutoMigrationSpec {
    fun onPostMigrate(db: SupportSQLiteDatabase) {}
}

abstract class Migration(val startVersion: Int, val endVersion: Int) {
    abstract fun migrate(database: SupportSQLiteDatabase)
}

