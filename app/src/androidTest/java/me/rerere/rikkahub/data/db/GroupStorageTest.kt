package me.rerere.rikkahub.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.data.model.GROUP_CHAT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.model.withGroupSender
import me.rerere.rikkahub.data.model.copyForGroup
import me.rerere.rikkahub.data.sync.DatabaseBackup
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class GroupStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "group-storage-${Uuid.random()}.db"
    private lateinit var database: AppDatabase

    @Before fun setUp() { database = AppDatabaseFactory.create(context, name) }
    @After fun tearDown() { database.close(); context.deleteDatabase(name) }

    private suspend fun store(conversation: Conversation) {
        database.conversationDao().insert(ConversationEntity(id = conversation.id.toString(),
            assistantId = conversation.assistantId.toString(), title = conversation.title, nodes = "[]",
            createAt = 0, updateAt = 0, chatSuggestions = "[]", isPinned = false))
        conversation.messageNodes.forEachIndexed { index, node ->
            database.messageNodeDao().insert(MessageNodeEntity(id = node.id.toString(),
                conversationId = conversation.id.toString(), nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages), selectIndex = node.selectIndex))
        }
    }

    @Test fun groupContentNeverEntersFtsAndOldIndexEntriesAreNotReturned() = runBlocking {
        val normal = Conversation(assistantId = Uuid.random(), title = "Normal",
            messageNodes = listOf(UIMessage.user("groupmarker normal").toMessageNode()))
        val group = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID, title = "Group",
            messageNodes = listOf(UIMessage.user("groupmarker secret").toMessageNode()))
        store(normal); store(group)
        val fts = MessageFtsManager(database)
        fts.indexConversation(normal); fts.indexConversation(group)
        val db = database.openHelper.writableDatabase
        db.query("SELECT COUNT(*) FROM message_fts WHERE conversation_id = ?", arrayOf(group.id.toString())).use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        db.execSQL("INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("groupmarker stale", "stale", "stale", group.id.toString(), "Group", "0"))
        assertEquals(listOf(normal.id.toString()), fts.search("groupmarker").map { it.conversationId })
        assertTrue(fts.search("groupmarker", assistantId = GROUP_CHAT_ASSISTANT_ID.toString()).isEmpty())
        fts.indexConversation(group)
        db.query("SELECT COUNT(*) FROM message_fts WHERE conversation_id = ?", arrayOf(group.id.toString())).use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun senderSurvivesDatabaseJsonAndGroupDeletionCascadesMessages() = runBlocking {
        val member = GroupMember(name = "Alice")
        val reply = UIMessage.assistant("answer").withGroupSender(member, "fallback")
        val group = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID, messageNodes = listOf(reply.toMessageNode()))
        store(group)
        val row = database.messageNodeDao().getNodesOfConversation(group.id.toString()).single()
        val restored = JsonInstant.decodeFromString<List<UIMessage>>(row.messages).single()
        assertEquals(UIMessageAnnotation.GroupSender(member.id.toString(), "Alice"),
            restored.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().single())
        val entity = database.conversationDao().getConversationById(group.id.toString())!!
        database.conversationDao().delete(entity)
        assertTrue(database.messageNodeDao().getNodesOfConversation(group.id.toString()).isEmpty())
    }

    @Test fun renamingAndPinningOnlyUpdateMetadataAndNeverEraseMessageBranches() = runBlocking {
        val first = UIMessage.user("question")
        val reply = UIMessage.assistant("answer")
        val group = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID, title = "Before",
            messageNodes = listOf(first.toMessageNode(), reply.toMessageNode()))
        store(group)
        val before = database.messageNodeDao().getNodesOfConversation(group.id.toString())
        database.conversationDao().updateTitle(group.id.toString(), "After")
        database.conversationDao().updatePinStatus(group.id.toString(), true)
        database.conversationDao().updateWorkspaceCwd(group.id.toString(), "project", GROUP_CHAT_ASSISTANT_ID.toString())
        val entity = database.conversationDao().getConversationById(group.id.toString())!!
        assertEquals("After", entity.title)
        assertTrue(entity.isPinned)
        assertEquals("project", entity.workspaceCwd)
        assertEquals(before, database.messageNodeDao().getNodesOfConversation(group.id.toString()))
    }

    @Test fun recentConversationsFilterGroupsBeforeLimitAndRetainAssistantIsolation() = runBlocking {
        val assistant = Uuid.random()
        val chatA = Conversation(assistantId = assistant, title = "A", messageNodes = emptyList())
        val chatB = Conversation(assistantId = Uuid.random(), title = "B", messageNodes = emptyList())
        val group = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID, title = "Group", messageNodes = emptyList())
        store(chatA); store(chatB); store(group)
        database.conversationDao().updatePinStatus(group.id.toString(), true)
        val global = database.conversationDao().getRecentConversationsExcludingAssistant(GROUP_CHAT_ASSISTANT_ID.toString(), 2)
        assertEquals(setOf(chatA.id.toString(), chatB.id.toString()), global.map { it.id }.toSet())
        assertEquals(listOf(chatA.id.toString()), database.conversationDao().getRecentConversationsOfAssistant(assistant.toString(), 10).map { it.id })
        database.conversationDao().updateWorkspaceCwd(chatA.id.toString(), "must-not-write", GROUP_CHAT_ASSISTANT_ID.toString())
        assertEquals("", database.conversationDao().getConversationById(chatA.id.toString())!!.workspaceCwd)
    }

    @Test fun workspaceDeletionOnlyRemovesTheSelectedRecord() = runBlocking {
        val dao = database.workspaceDao()
        val source = WorkspaceEntity("source", "Source", "source-root", createdAt = 1, updatedAt = 2, shellStatus = "READY")
        val group = source.copy(id = "group", root = "group-root", owner = "group")
        val other = source.copy(id = "other", root = "other-root")
        dao.upsert(source); dao.upsert(group); dao.upsert(other)
        dao.deleteById(source.id)
        assertNull(dao.getById(source.id))
        assertEquals(group, dao.getById(group.id))
        assertEquals(other, dao.getById(other.id))
    }

    @Test fun fullDatabaseSnapshotIncludesGroupConversationsAndMessages() = runBlocking {
        val group = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID,
            messageNodes = listOf(UIMessage.user("keep in backup").toMessageNode()))
        store(group)
        assertTrue(database.conversationDao().getAll().first().any { it.id == group.id.toString() })
        val snapshot = File(context.cacheDir, "group-snapshot-${Uuid.random()}.db")
        try {
            DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
            val copied = AppDatabaseFactory.create(context, snapshot.absolutePath)
            try {
                assertNotNull(copied.conversationDao().getConversationById(group.id.toString()))
                assertEquals(1, copied.messageNodeDao().getNodesOfConversation(group.id.toString()).size)
            } finally { copied.close() }
        } finally { snapshot.delete(); DatabaseBackup.removeSidecars(snapshot) }
    }

    @Test fun copiedConversationKeepsQuoteIdsButHasIndependentRowsAndDeletion() = runBlocking {
        val user = UIMessage.user("question")
        val reply = UIMessage.assistant("answer").copy(annotations = listOf(
            UIMessageAnnotation.GroupQuote(user.id.toString(), "User", "question")))
        val source = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID,
            messageNodes = listOf(user.toMessageNode(), reply.toMessageNode()))
        val copy = source.copyForGroup { it }
        store(source); store(copy)
        val originalRows = database.messageNodeDao().getNodesOfConversation(source.id.toString())
        val copyRows = database.messageNodeDao().getNodesOfConversation(copy.id.toString())
        assertTrue(originalRows.map { it.id }.intersect(copyRows.map { it.id }.toSet()).isEmpty())
        val restored = copyRows.flatMap { JsonInstant.decodeFromString<List<UIMessage>>(it.messages) }
        assertEquals(listOf(user.id, reply.id), restored.map { it.id })
        assertEquals(user.id.toString(), restored.last().annotations.filterIsInstance<UIMessageAnnotation.GroupQuote>()
            .single().targetMessageId)
        database.conversationDao().delete(database.conversationDao().getConversationById(source.id.toString())!!)
        assertEquals(2, database.messageNodeDao().getNodesOfConversation(copy.id.toString()).size)
        database.conversationDao().delete(database.conversationDao().getConversationById(copy.id.toString())!!)
        assertTrue(database.messageNodeDao().getNodesOfConversation(copy.id.toString()).isEmpty())
    }
}
