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
