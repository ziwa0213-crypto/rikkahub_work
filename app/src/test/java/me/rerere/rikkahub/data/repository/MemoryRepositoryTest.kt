package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.uuid.Uuid

class MemoryRepositoryTest {
    private lateinit var dao: FakeMemoryDao
    private lateinit var repository: MemoryRepository

    @Before
    fun setUp() {
        dao = FakeMemoryDao()
        repository = MemoryRepository(dao)
    }

    @Test
    fun `private memory uses assistant id`() {
        val assistant = Assistant()

        assertEquals(assistant.id.toString(), MemoryRepository.scopeOf(assistant))
    }

    @Test
    fun `group memory uses stable uuid namespace`() {
        val groupId = Uuid.random()
        val assistant = Assistant(memoryGroupId = groupId)

        assertEquals("g:$groupId", MemoryRepository.scopeOf(assistant))
        assertEquals("g:$groupId", MemoryRepository.scopeOf(groupId))
    }

    @Test
    fun `global memory takes priority over group`() {
        val assistant = Assistant(
            useGlobalMemory = true,
            memoryGroupId = Uuid.random(),
        )

        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, MemoryRepository.scopeOf(assistant))
    }

    @Test
    fun `chat group uses a namespace distinct from all existing scopes`() {
        val id = Uuid.random()
        val scope = MemoryRepository.scopeOfChatGroup(id)
        assertEquals("grp:$id", scope)
        assertFalse(scope == MemoryRepository.scopeOf(id))
        assertFalse(scope == MemoryRepository.scopeOf(Assistant(id = id)))
        assertFalse(scope == MemoryRepository.GLOBAL_MEMORY_ID)
    }

    @Test
    fun `chat group memory writes and deletes do not affect other scopes`() = kotlinx.coroutines.runBlocking {
        val id = Uuid.random()
        val chatScope = MemoryRepository.scopeOfChatGroup(id)
        val otherScope = MemoryRepository.scopeOfChatGroup(Uuid.random())
        repository.addMemory(id.toString(), "assistant")
        repository.addMemory(MemoryRepository.scopeOf(id), "memory group")
        repository.addMemory(otherScope, "other chat group")
        val memory = repository.addMemory(chatScope, "chat group")
        repository.updateContent(memory.id, "edited")
        assertEquals(listOf("edited"), repository.getMemoriesOfAssistant(chatScope).map { it.content })
        repository.deleteMemory(memory.id)
        assertEquals(emptyList<String>(), dao.contents(chatScope))
        assertEquals(listOf("assistant"), dao.contents(id.toString()))
        assertEquals(listOf("memory group"), dao.contents(MemoryRepository.scopeOf(id)))
        assertEquals(listOf("other chat group"), dao.contents(otherScope))
    }

    @Test
    fun `legacy assistant json defaults to private memory`() {
        val assistant = JsonInstant.decodeFromString<Assistant>("{}")

        assertFalse(assistant.useGlobalMemory)
        assertNull(assistant.memoryGroupId)
        assertEquals(assistant.id.toString(), MemoryRepository.scopeOf(assistant))
    }

    @Test
    fun `copy migrates only selected memories and keeps source`() = kotlinx.coroutines.runBlocking {
        dao.seed(
            MemoryEntity(id = 1, assistantId = "source", content = "m1"),
            MemoryEntity(id = 2, assistantId = "source", content = "m2"),
        )

        repository.migrateMemories(
            fromScope = "source",
            toScope = "target",
            memoryIds = setOf(2),
            mode = MemoryMigrationMode.COPY,
        )

        assertEquals(listOf("m1", "m2"), dao.contents("source"))
        assertEquals(listOf("m2"), dao.contents("target"))
    }

    @Test
    fun `move migrates selected memories and removes original rows`() = kotlinx.coroutines.runBlocking {
        dao.seed(
            MemoryEntity(id = 1, assistantId = "source", content = "m1"),
            MemoryEntity(id = 2, assistantId = "source", content = "m2"),
        )

        repository.migrateMemories(
            fromScope = "source",
            toScope = "target",
            memoryIds = setOf(1),
            mode = MemoryMigrationMode.MOVE,
        )

        assertEquals(listOf("m2"), dao.contents("source"))
        assertEquals(listOf("m1"), dao.contents("target"))
    }

    @Test
    fun `empty selection and same scope do not change memories`() = kotlinx.coroutines.runBlocking {
        dao.seed(MemoryEntity(id = 1, assistantId = "source", content = "m1"))

        repository.migrateMemories(
            fromScope = "source",
            toScope = "target",
            memoryIds = emptySet(),
            mode = MemoryMigrationMode.MOVE,
        )
        repository.migrateMemories(
            fromScope = "source",
            toScope = "source",
            memoryIds = setOf(1),
            mode = MemoryMigrationMode.MOVE,
        )

        assertEquals(listOf("m1"), dao.contents("source"))
        assertEquals(emptyList<String>(), dao.contents("target"))
    }

    @Test
    fun `ids from another bucket are ignored`() = kotlinx.coroutines.runBlocking {
        dao.seed(
            MemoryEntity(id = 1, assistantId = "source", content = "m1"),
            MemoryEntity(id = 2, assistantId = "other", content = "m2"),
        )

        repository.migrateMemories(
            fromScope = "source",
            toScope = "target",
            memoryIds = setOf(1, 2),
            mode = MemoryMigrationMode.MOVE,
        )

        assertEquals(emptyList<String>(), dao.contents("source"))
        assertEquals(listOf("m1"), dao.contents("target"))
        assertEquals(listOf("m2"), dao.contents("other"))
    }

    @Test
    fun `conversation scopes isolate groups conversations and ordinary memories`() = kotlinx.coroutines.runBlocking {
        val group = Uuid.random()
        val a = MemoryRepository.scopeOfChatGroup(group, Uuid.random())
        val b = MemoryRepository.scopeOfChatGroup(group, Uuid.random())
        val c = MemoryRepository.scopeOfChatGroup(Uuid.random(), Uuid.random())
        listOf(a, b, c, MemoryRepository.GLOBAL_MEMORY_ID).forEach { repository.addMemory(it, it) }
        val memory = repository.getMemoriesOfAssistant(a).single()
        repository.updateContentInScope(a, memory.id, "edited")
        assertEquals(listOf("edited"), dao.contents(a))
        listOf(b, c, MemoryRepository.GLOBAL_MEMORY_ID).forEach { assertEquals(listOf(it), dao.contents(it)) }
        repository.deleteMemoriesOfAssistant(a)
        assertEquals(emptyList<String>(), dao.contents(a))
        assertEquals(listOf(b), dao.contents(b))
    }

    @Test
    fun `legacy group migration preserves ids merges target and is idempotent`() = kotlinx.coroutines.runBlocking {
        val group = Uuid.random()
        val old = MemoryRepository.scopeOfChatGroup(group)
        val target = MemoryRepository.scopeOfChatGroup(group, Uuid.random())
        val legacy = repository.addMemory(old, "legacy")
        repository.addMemory(target, "existing")
        repository.moveScope(old, target)
        repository.moveScope(old, target)
        assertEquals(emptyList<String>(), dao.contents(old))
        assertEquals(listOf("legacy", "existing"), dao.contents(target))
        assertEquals(target, dao.getMemoryById(legacy.id)?.assistantId)
        assertEquals(legacy, repository.getMemoriesOfAssistant(target).first())
    }

    @Test
    fun `guessed ids cannot edit or delete another conversation memory`() = kotlinx.coroutines.runBlocking {
        val own = MemoryRepository.scopeOfChatGroup(Uuid.random(), Uuid.random())
        val other = MemoryRepository.scopeOfChatGroup(Uuid.random(), Uuid.random())
        val memory = repository.addMemory(other, "keep")
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repository.updateContentInScope(own, memory.id, "bad") }
        }
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repository.deleteMemoryInScope(own, memory.id) }
        }
        assertEquals(listOf("keep"), dao.contents(other))
        repository.deleteMemoryInScope(other, memory.id)
        assertEquals(emptyList<String>(), dao.contents(other))
    }

    @Test
    fun `duplicated group memories use new rows and remain independent after edits and deletion`() = kotlinx.coroutines.runBlocking {
        val source = MemoryRepository.scopeOfChatGroup(Uuid.random(), Uuid.random())
        val target = MemoryRepository.scopeOfChatGroup(Uuid.random(), Uuid.random())
        val first = repository.addMemory(source, "first")
        val second = repository.addMemory(source, "second")
        repository.copyMemories(source, target)
        val copies = repository.getMemoriesOfAssistant(target)
        assertEquals(listOf("first", "second"), copies.map { it.content })
        assertFalse(copies.any { it.id == first.id || it.id == second.id })
        repository.updateContentInScope(target, copies.first().id, "copy edit")
        assertEquals(listOf("first", "second"), dao.contents(source))
        repository.deleteMemoriesOfAssistant(source)
        assertEquals(listOf("copy edit", "second"), dao.contents(target))
        repository.deleteMemoriesOfAssistant(target)
        assertEquals(emptyList<String>(), dao.contents(target))
    }

    private class FakeMemoryDao : MemoryDAO {
        private val rows = mutableListOf<MemoryEntity>()
        private var nextId = 1

        fun seed(vararg memories: MemoryEntity) {
            rows += memories
            nextId = maxOf(nextId, (memories.maxOfOrNull { it.id } ?: 0) + 1)
        }

        fun contents(scope: String): List<String> = rows
            .filter { it.assistantId == scope }
            .map { it.content }

        override fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>> = emptyFlow()

        override suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity> =
            rows.filter { it.assistantId == assistantId }

        override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = emptyFlow()

        override suspend fun getAllMemories(): List<MemoryEntity> = rows.toList()

        override suspend fun getMemoryById(id: Int): MemoryEntity? = rows.find { it.id == id }

        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val id = if (memory.id == 0) nextId++ else memory.id
            rows += memory.copy(id = id)
            return id.toLong()
        }

        override suspend fun insertMemories(memories: List<MemoryEntity>) {
            memories.forEach { insertMemory(it) }
        }

        override suspend fun updateMemory(memory: MemoryEntity) {
            rows.replaceAll { if (it.id == memory.id) memory else it }
        }

        override suspend fun deleteMemory(id: Int) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteMemoriesByIds(ids: List<Int>) {
            rows.removeAll { it.id in ids }
        }

        override suspend fun deleteMemoriesOfAssistant(assistantId: String) {
            rows.removeAll { it.assistantId == assistantId }
        }
    }
}
