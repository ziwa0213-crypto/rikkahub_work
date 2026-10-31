package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemoryEntityDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.RowMemory
import me.rerere.rikkahub.data.db.entity.MemoryFileEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import kotlin.uuid.Uuid

sealed interface MemoryWriteResult {
    /** [file] 为 null 表示文件已被删除 */
    data class Success(val file: MemoryFile?) : MemoryWriteResult

    /** 版本对不上；[current] 是最新内容，为 null 表示文件不存在 */
    data class Conflict(val current: MemoryFile?) : MemoryWriteResult

    data class Rejected(val reason: String) : MemoryWriteResult
}

/**
 * 记忆库：每个助手（或全局）一组 Markdown 文件。
 *
 * 写操作都带 ifVersion 做乐观锁：传 [NEW_FILE_VERSION] 表示新建，传版本号表示基于该版本修改，
 * 传 null 则不校验（用户在界面里直接编辑）。
 */
enum class MemoryMigrationMode { COPY, MOVE }

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val memoryEntityDAO: MemoryEntityDAO,
) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
        const val NEW_FILE_VERSION = "new"

        /** Kept consistent with the scope used by group generation in ChatService. */
        const val GROUP_CHAT_MEMORY_PREFIX = "grp:"

        /** User-created memory-group bucket prefix. */
        const val GROUP_MEMORY_PREFIX = "g:"

        /** Legacy group bucket, moved to the first conversation when it is created. */
        fun scopeOfChatGroup(groupId: Uuid): String =
            "$GROUP_CHAT_MEMORY_PREFIX$groupId"

        fun scopeOfChatGroup(groupId: Uuid, conversationId: Uuid): String =
            "$GROUP_CHAT_MEMORY_PREFIX$groupId:$conversationId"

        fun scopeOf(memoryGroupId: Uuid): String = "$GROUP_MEMORY_PREFIX$memoryGroupId"
    }

    // 读取、校验版本、写入必须一起完成，否则两个写入方会互相覆盖
    private val writeLock = Mutex()

    fun getRowMemoriesFlow(scope: String): Flow<List<RowMemory>> =
        memoryEntityDAO.getMemoriesOfAssistantFlow(scope).map { rows -> rows.map { it.toRowMemory() } }

    suspend fun getRowMemories(scope: String): List<RowMemory> =
        memoryEntityDAO.getMemoriesOfAssistant(scope).map { it.toRowMemory() }

    suspend fun addRowMemory(scope: String, content: String): RowMemory {
        require(content.isNotBlank()) { "Memory content must not be blank" }
        val entity = MemoryEntity(assistantId = scope, content = content.trim())
        return entity.copy(id = memoryEntityDAO.insertMemory(entity).toInt()).toRowMemory()
    }

    suspend fun updateRowMemory(scope: String, id: Int, content: String): RowMemory {
        require(content.isNotBlank()) { "Memory content must not be blank" }
        check(memoryEntityDAO.updateContent(scope, id, content.trim()) == 1) {
            "Memory does not belong to this scope or no longer exists"
        }
        return RowMemory(id, content.trim())
    }

    suspend fun deleteRowMemory(scope: String, id: Int) {
        check(memoryEntityDAO.getMemoryById(id)?.assistantId == scope) { "Memory does not belong to this scope" }
        memoryEntityDAO.deleteMemories(scope, listOf(id))
    }

    suspend fun deleteRowMemories(scope: String, ids: List<Int>) {
        if (ids.isEmpty()) return
        memoryEntityDAO.deleteMemories(scope, ids)
    }

    suspend fun copyRowMemories(fromScope: String, toScope: String) =
        memoryEntityDAO.transferRows(fromScope, toScope, null, move = false)

    /** Move a whole bucket of row memories and memory files to the first conversation. */
    suspend fun moveAllRowMemories(fromScope: String, toScope: String) {
        if (fromScope == toScope) return
        memoryEntityDAO.transferRows(fromScope, toScope, ids = null, move = true)
        val files = memoryDAO.getFilesOfAssistant(fromScope)
        if (files.isNotEmpty()) {
            memoryDAO.upsertFiles(files.map { it.copy(assistantId = toScope) })
            memoryDAO.deleteFilesOfAssistant(fromScope)
        }
    }

    suspend fun migrateRowMemories(fromScope: String, toScope: String, ids: List<Int>, mode: MemoryMigrationMode) =
        memoryEntityDAO.transferRows(fromScope, toScope, ids, move = mode == MemoryMigrationMode.MOVE)

    suspend fun deleteAllRowMemories(scope: String) = memoryEntityDAO.deleteAllOfAssistant(scope)

    fun getFilesFlow(assistantId: String): Flow<List<MemoryFile>> =
        memoryDAO.getFilesOfAssistantFlow(assistantId).map { entities -> entities.map { it.toMemoryFile() } }

    suspend fun getFiles(assistantId: String): List<MemoryFile> =
        memoryDAO.getFilesOfAssistant(assistantId).map { it.toMemoryFile() }

    suspend fun getFile(assistantId: String, path: String): MemoryFile? {
        val normalized = MemoryFile.normalizePath(path) ?: return null
        return memoryDAO.getFile(assistantId, normalized)?.toMemoryFile()
    }

    /** 新建或整体覆盖 */
    suspend fun writeFile(
        assistantId: String,
        path: String,
        content: String,
        ifVersion: String? = null,
    ): MemoryWriteResult = update(assistantId, path, ifVersion, allowCreate = true) { Edit.Content(content) }

    /** 把唯一匹配的 [oldStr] 换成 [newStr]，[newStr] 为空即删除 */
    suspend fun replaceInFile(
        assistantId: String,
        path: String,
        oldStr: String,
        newStr: String,
        ifVersion: String?,
    ): MemoryWriteResult = update(assistantId, path, ifVersion) { current ->
        if (oldStr.isEmpty()) return@update Edit.Reject("old_str must not be empty.")
        val index = current.indexOf(oldStr)
        when {
            index < 0 -> Edit.Reject("old_str was not found in $path.")
            current.indexOf(oldStr, index + 1) >= 0 ->
                Edit.Reject(
                    "old_str matches more than one place in $path; include more surrounding text to make it unique."
                )

            else -> Edit.Content(current.replaceAt(index, oldStr.length, newStr))
        }
    }

    /** 在文件末尾追加一行 */
    suspend fun appendToFile(
        assistantId: String,
        path: String,
        line: String,
        ifVersion: String?,
    ): MemoryWriteResult = update(assistantId, path, ifVersion) { current ->
        val separator = if (current.isEmpty() || current.endsWith("\n")) "" else "\n"
        Edit.Content(current + separator + line.trimEnd('\n', '\r') + "\n")
    }

    suspend fun deleteFile(
        assistantId: String,
        path: String,
        ifVersion: String? = null,
    ): MemoryWriteResult = writeLock.withLock {
        val normalized = MemoryFile.normalizePath(path) ?: return invalidPath(path)
        val current = memoryDAO.getFile(assistantId, normalized)?.toMemoryFile()
        if (current == null || (ifVersion != null && ifVersion != current.version)) {
            return MemoryWriteResult.Conflict(current)
        }
        memoryDAO.deleteFile(assistantId, normalized)
        MemoryWriteResult.Success(null)
    }

    suspend fun deleteFilesOfAssistant(assistantId: String) {
        memoryDAO.deleteFilesOfAssistant(assistantId)
    }

    suspend fun copyFiles(fromAssistantId: String, toAssistantId: String) {
        val files = memoryDAO.getFilesOfAssistant(fromAssistantId)
        if (files.isEmpty()) return
        memoryDAO.upsertFiles(files.map { it.copy(assistantId = toAssistantId) })
    }

    private sealed interface Edit {
        data class Content(val content: String) : Edit
        data class Reject(val reason: String) : Edit
    }

    private suspend fun update(
        assistantId: String,
        path: String,
        ifVersion: String?,
        allowCreate: Boolean = false,
        edit: (current: String) -> Edit,
    ): MemoryWriteResult = writeLock.withLock {
        val normalized = MemoryFile.normalizePath(path) ?: return invalidPath(path)
        val current = memoryDAO.getFile(assistantId, normalized)?.toMemoryFile()
        val versionMatches = when (ifVersion) {
            null -> current != null || allowCreate
            NEW_FILE_VERSION -> current == null && allowCreate
            else -> current?.version == ifVersion
        }
        if (!versionMatches) return MemoryWriteResult.Conflict(current)

        val content = when (val result = edit(current?.content.orEmpty())) {
            is Edit.Reject -> return MemoryWriteResult.Rejected(result.reason)
            is Edit.Content -> result.content
        }
        // 只拦会让文件变大的超限写入：迁移来的旧记忆可能本来就超限，得允许一点点删小
        val size = content.toByteArray().size
        if (size > MemoryFile.MAX_BYTES && size > current?.content?.toByteArray()?.size ?: 0) {
            return MemoryWriteResult.Rejected(
                "$normalized would exceed the ${MemoryFile.MAX_BYTES / 1024}KB limit. " +
                    "Merge duplicates and drop outdated details instead of adding more."
            )
        }
        val entity = MemoryFileEntity(
            assistantId = assistantId,
            path = normalized,
            content = content,
            updatedAt = System.currentTimeMillis(),
        )
        memoryDAO.upsertFile(entity)
        MemoryWriteResult.Success(entity.toMemoryFile())
    }

    private fun invalidPath(path: String) = MemoryWriteResult.Rejected(
        "Invalid path \"$path\". Use an absolute path ending in .md, e.g. /topics/food.md."
    )
}

/** 助手读写的记忆库：开启全局记忆后所有助手共用一份 */
val Assistant.memoryId: String
    get() = when {
        useGlobalMemory -> MemoryRepository.GLOBAL_MEMORY_ID
        memoryGroupId != null -> "${MemoryRepository.GROUP_MEMORY_PREFIX}$memoryGroupId"
        else -> id.toString()
    }

private fun MemoryEntity.toRowMemory() = RowMemory(id = id, content = content)

private fun MemoryFileEntity.toMemoryFile() = MemoryFile(path = path, content = content, updatedAt = updatedAt)

// 删掉的是完整一行时连换行一起去掉，不留空行
private fun String.replaceAt(index: Int, length: Int, replacement: String): String {
    var start = index
    var end = index + length
    if (replacement.isEmpty()) {
        val atLineStart = start == 0 || this[start - 1] == '\n'
        val atLineEnd = end == this.length || this[end] == '\n'
        if (atLineStart && atLineEnd) {
            if (end < this.length) {
                end++
            } else if (start > 0) {
                start--
            }
        }
    }
    return substring(0, start) + replacement + substring(end)
}
