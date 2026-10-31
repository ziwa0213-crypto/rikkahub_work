package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.MemoryEntityDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity

internal class FakeMemoryEntityDAO : MemoryEntityDAO {
    private val rows = MutableStateFlow<List<MemoryEntity>>(emptyList())
    private var nextId = 1

    override fun getMemoriesOfAssistantFlow(scope: String): Flow<List<MemoryEntity>> =
        rows.map { all -> all.filter { it.assistantId == scope }.sortedBy { it.id } }

    override suspend fun getMemoriesOfAssistant(scope: String): List<MemoryEntity> =
        rows.value.filter { it.assistantId == scope }.sortedBy { it.id }

    override suspend fun getAllMemories(): List<MemoryEntity> = rows.value.sortedBy { it.id }

    override suspend fun getMemoryById(id: Int): MemoryEntity? = rows.value.find { it.id == id }

    override suspend fun insertMemory(memory: MemoryEntity): Long {
        val id = if (memory.id == 0) nextId++ else memory.id.also { nextId = maxOf(nextId, it + 1) }
        rows.value = rows.value + memory.copy(id = id)
        return id.toLong()
    }

    override suspend fun insertMemories(memories: List<MemoryEntity>) {
        memories.forEach { insertMemory(it) }
    }

    override suspend fun updateContent(scope: String, id: Int, content: String): Int {
        val current = rows.value
        if (current.none { it.id == id && it.assistantId == scope }) return 0
        rows.value = current.map { if (it.id == id && it.assistantId == scope) it.copy(content = content) else it }
        return 1
    }

    override suspend fun deleteMemories(scope: String, ids: List<Int>) {
        val selected = ids.toSet()
        rows.value = rows.value.filterNot { it.assistantId == scope && it.id in selected }
    }

    override suspend fun deleteAllOfAssistant(scope: String) {
        rows.value = rows.value.filterNot { it.assistantId == scope }
    }
}
