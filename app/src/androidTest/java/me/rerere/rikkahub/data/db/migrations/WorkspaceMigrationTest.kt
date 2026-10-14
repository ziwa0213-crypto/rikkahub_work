package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        listOf(Migration_8_9(), Migration_16_17(), Migration_22_23()), FrameworkSQLiteOpenHelperFactory())

    @Test fun oldWorkspaceRetainsFilesApprovalsAndUnassignedOwnership() {
        val name = "workspace-migration-25-26"
        helper.createDatabase(name, 25).apply {
            execSQL("INSERT INTO workspaces(id,name,root,shell_status,created_at,updated_at,tool_approvals) VALUES ('old','Legacy','root','READY',1,2,'{}')")
            close()
        }
        helper.runMigrationsAndValidate(name, 26, true).use { db ->
            db.query("SELECT name,root,shell_status,tool_approvals,owner,shared_rootfs_from FROM workspaces WHERE id='old'").use {
                assertTrue(it.moveToFirst()); assertEquals("Legacy", it.getString(0)); assertEquals("root", it.getString(1))
                assertEquals("READY", it.getString(2)); assertEquals("{}", it.getString(3)); assertTrue(it.isNull(4) && it.isNull(5))
            }
        }
    }

    @Test fun migrationFromVersion15RetainsMemory() {
        val name = "workspace-migration-15-26"
        helper.createDatabase(name, 15).apply {
            execSQL("INSERT INTO memoryentity(id,assistant_id,content) VALUES (901,'legacy','preserve memory')")
            close()
        }
        helper.runMigrationsAndValidate(name, 26, true, Migration_15_16).use { db ->
            db.query("SELECT content FROM memoryentity WHERE id=901").use {
                assertTrue(it.moveToFirst()); assertEquals("preserve memory", it.getString(0))
            }
        }
    }
}
