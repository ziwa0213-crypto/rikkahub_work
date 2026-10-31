package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Recreate itemized memory without removing the upstream file archive. */
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `MemoryEntity` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`assistant_id` TEXT NOT NULL, `content` TEXT NOT NULL)"
            )
            val profiles = mutableListOf<Pair<String, String>>()
            db.query("SELECT assistant_id, content FROM memory_file WHERE path = '/profile.md' ORDER BY assistant_id").use { cursor ->
                while (cursor.moveToNext()) profiles += cursor.getString(0) to cursor.getString(1)
            }
            profiles.forEach { (scope, profile) ->
                profile.lineSequence().map { it.trim() }
                    .filter { it.startsWith("- [stated]") }
                    .map { it.removePrefix("- [stated]").trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { content ->
                        db.execSQL(
                            "INSERT INTO MemoryEntity (assistant_id, content) VALUES (?, ?)",
                            arrayOf<Any?>(scope, content),
                        )
                    }
                // Preserve the original file and metadata. A repeat run sees no stated rows.
                if ("- [stated]" in profile) db.execSQL(
                    "UPDATE memory_file SET content = ? WHERE assistant_id = ? AND path = '/profile.md'",
                    arrayOf<Any?>(profile.replace("- [stated]", "- [migrated]"), scope),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
