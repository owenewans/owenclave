package android.database.sqlite

open class SQLiteException(message: String? = null) : RuntimeException(message)
class SQLiteCantOpenDatabaseException(message: String? = null) : SQLiteException(message)

