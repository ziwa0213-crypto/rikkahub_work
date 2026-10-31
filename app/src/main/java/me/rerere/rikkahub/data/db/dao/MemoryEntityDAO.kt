package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEntity

@Dao
interface MemoryEntityDAO {
    @Query("SELECT * FROM MemoryEntity WHERE assistant_id = :scope ORDER BY id")
    fun getMemoriesOfAssistantFlow(scope: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM MemoryEntity WHERE assistant_id = :scope ORDER BY id")
    suspend fun getMemoriesOfAssistant(scope: String): List<MemoryEntity>

    @Query("SELECT * FROM MemoryEntity ORDER BY id")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM MemoryEntity WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Query("UPDATE MemoryEntity SET content = :content WHERE id = :id AND assistant_id = :scope")
    suspend fun updateContent(scope: String, id: Int, content: String): Int

    @Query("DELETE FROM MemoryEntity WHERE assistant_id = :scope AND id IN (:ids)")
    suspend fun deleteMemories(scope: String, ids: List<Int>)

    @Query("DELETE FROM MemoryEntity WHERE assistant_id = :scope")
    suspend fun deleteAllOfAssistant(scope: String)

    @Transaction
    suspend fun transferRows(fromScope: String, toScope: String, ids: List<Int>?, move: Boolean) {
        if (fromScope == toScope) return
        val selected = ids?.toSet()
        val rows = getMemoriesOfAssistant(fromScope).filter { selected == null || it.id in selected }
        insertMemories(rows.map { MemoryEntity(assistantId = toScope, content = it.content) })
        if (move) deleteMemories(fromScope, rows.map { it.id })
    }
}
