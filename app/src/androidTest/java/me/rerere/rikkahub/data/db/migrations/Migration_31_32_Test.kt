package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_31_32_Test {
    private val testDb = "migration-31-32-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun restoresStatedProfileLinesOnceAndPreservesFileMemory() {
        helper.createDatabase(testDb, 31).apply {
            execSQL(
                "INSERT INTO memory_file (assistant_id, path, content, updated_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(
                    "assistant",
                    "/profile.md",
                    "---\nname: profile\n---\n\n- [stated] Avoid cilantro\n- [migrated] Old fact\n- ordinary note\n",
                    123L,
                ),
            )
            execSQL(
                "INSERT INTO memory_file (assistant_id, path, content, updated_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("assistant", "/preferences.md", "- concise answers\n", 456L),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 32, true, Migration_31_32)
        val rows = db.query("SELECT assistant_id, content FROM MemoryEntity ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            }
        }
        val profile = db.query("SELECT path, content, updated_at FROM memory_file WHERE path = '/profile.md'").use { cursor ->
            cursor.moveToFirst()
            Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2))
        }
        val preferences = db.query("SELECT content FROM memory_file WHERE path = '/preferences.md'").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

        // A second execution is safe: the marker has already changed, so no duplicate row is added.
        Migration_31_32.migrate(db)
        val rowCount = db.query("SELECT COUNT(*) FROM MemoryEntity").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        db.close()

        assertEquals(listOf("assistant" to "Avoid cilantro"), rows)
        assertEquals(1, rowCount)
        assertEquals("/profile.md", profile.first)
        assertEquals(123L, profile.third)
        assertEquals(
            "---\nname: profile\n---\n\n- [migrated] Avoid cilantro\n- [migrated] Old fact\n- ordinary note\n",
            profile.second,
        )
        assertEquals("- concise answers\n", preferences)
    }
}
