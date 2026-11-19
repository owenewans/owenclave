package androidx.sqlite.db

interface SupportSQLiteDatabase {
    fun execSQL(sql: String) {}
    fun execSQL(sql: String, bindArgs: Array<out Any?>) {}
}

