package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.GROUP_CHAT_ASSISTANT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Android; a compiled test is not evidence of a real overlay-install validation. */
@RunWith(AndroidJUnit4::class)
class ForkMigration_26_31_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, listOf(Migration_28_29()),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test fun preservesForkWorkspaceColumnsScopedMemoriesAndGroupAnnotations() {
        val name = "fork-26-31-preservation"
        val groupScope = "grp:group-id:conversation-id"
        val annotations = """[{"type":"group_sender","memberId":"member-id","memberName":"Member"}]"""
        val messages = """[{"role":"assistant","annotations":$annotations,"parts":[{"type":"text","text":"reply"}],"usage":{"promptTokens":100,"completionTokens":40}}]"""
        helper.createDatabase(name, 26).apply {
            execSQL("INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at) VALUES ('conv', ?, 'Group', '[]', 1000, 1000)",
                arrayOf(GROUP_CHAT_ASSISTANT_ID.toString()))
            execSQL("INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES ('node', 'conv', 0, ?, 0)", arrayOf(messages))
            execSQL("INSERT INTO MemoryEntity (assistant_id, content) VALUES (?, 'group memory')", arrayOf(groupScope))
            execSQL("INSERT INTO MemoryEntity (assistant_id, content) VALUES ('g:shared-id', 'shared memory')")
            execSQL("INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, owner, shared_rootfs_from) VALUES ('work', 'Workspace', '/test/work', 'DISABLED', 1, 2, 'group', 'legacy-root')")
            close()
        }
        // Auto migrations 28->29 and 29->30 are obtained from AppDatabase_Impl by the helper.
        val db = helper.runMigrationsAndValidate(name, 31, true,
            Migration_26_27, Migration_27_28, Migration_30_31)
        db.use {
            it.query("SELECT owner, shared_rootfs_from, root FROM workspaces WHERE id = 'work'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("group", cursor.getString(0))
                assertEquals("legacy-root", cursor.getString(1))
                assertEquals("/test/work", cursor.getString(2))
            }
            it.query("SELECT assistant_id, content FROM memory_file ORDER BY assistant_id").use { cursor ->
                val stores = mutableMapOf<String, String>()
                while (cursor.moveToNext()) stores[cursor.getString(0)] = cursor.getString(1)
                assertTrue(stores.getValue(groupScope).contains("group memory"))
                assertTrue(stores.getValue("g:shared-id").contains("shared memory"))
            }
            it.query("SELECT messages FROM message_node WHERE id = 'node'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(messages, cursor.getString(0))
            }
            it.query("SELECT count(*) FROM usage_record").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            it.query("SELECT count(*) FROM media_creation_session").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
