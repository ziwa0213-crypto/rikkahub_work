package me.rerere.rikkahub.ui.pages.character

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.CharacterMigrationState
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.data.repository.*
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.BuildConfig
import kotlin.uuid.Uuid

internal class CharacterTransferVM(
    private val settings: SettingsStore,
    private val conversations: ConversationRepository,
    private val memories: MemoryRepository,
    private val files: FilesManager,
    private val chat: ChatService,
) {
    suspend fun candidates(id: Uuid): TransferMemory {
        val assistant = settings.awaitLoaded().assistants.first { it.id == id }
        val scope = assistant.memoryId
        return TransferMemory(scope, memories.getFiles(scope), memories.getRowMemories(scope))
    }

    suspend fun transfer(id: Uuid, move: Boolean, memory: TransferMemory?, moveMemory: Boolean, target: Uuid) = withContext(Dispatchers.IO + NonCancellable) {
        val previous = settings.awaitLoaded()
        val source = previous.assistants.first { it.id == id }
        check(!move || previous.canMoveToCharacter(id))
        check(!moveMemory || previous.canMoveMemory(id, source.memoryId))
        val owners = previous.characters.map { it.id }.toSet() + previous.assistants.map { it.id } + previous.deletedCharacterIds
        val existingRole = previous.findCharacter(target)
        check(existingRole != null || target !in owners)
        val now = System.currentTimeMillis()
        val role = existingRole ?: source.toCharacter(target, previous.assistantTags.filter { it.id in source.tags }.map { it.name }, now)
        val sourceChats = conversations.getConversationsOfAssistant(source.id).first()
        // Register the role first; a failed transfer leaves the source intact and can resume.
        settings.update { if (it.findCharacter(target) == null) it.copy(characters = it.characters + role) else it }
        val missingAttachments = if (!move) copyConversations(sourceChats, target) else 0
        if (move) for (stub in sourceChats) chat.moveConversationToCharacter(stub.id, source.id, target)
        if (memory != null) transferMemory(memory, target.toString(), moveMemory)
        settings.update { latest ->
            latest.copy(
                assistants = if (move) latest.assistants.filterNot { it.id == source.id } else latest.assistants,
                assistantId = if (move && latest.assistantId == source.id) DEFAULT_ASSISTANT_ID else latest.assistantId,
                uiMode = UiMode.ROLE, characterId = target,
            )
        }
        missingAttachments
    }

    suspend fun finish() = settings.update {
        it.copy(characterMigrationState = CharacterMigrationState.DONE, characterMigrationPrompted = true,
            lastRunVersionCode = BuildConfig.VERSION_CODE.toInt())
    }

    private suspend fun copyConversations(source: List<Conversation>, target: Uuid): Int {
        val attachments = CharacterTransferAttachments { url, newId ->
            files.copyCharacterChatAttachment(url.toUri(), newId)?.toString()
        }
        val titles = conversations.getTitlesOfAssistant(target).toMutableSet()
        try {
            source.mapNotNull { stub ->
                val newId = characterCopyId(stub.id, target)
                if (conversations.existsConversationById(newId)) return@mapNotNull null
                val full = checkNotNull(conversations.getConversationById(stub.id))
                full.duplicateForCharacter(target, newId, titles, attachments.forConversation(newId))
                    .also { titles.add(it.title) }
            }.also { conversations.insertCharacterCopies(it) }
        } catch (e: Exception) {
            files.deleteChatFiles(attachments.copiedUrls.map { it.toUri() })
            throw e
        }
        return attachments.missing
    }

    private suspend fun transferMemory(selection: TransferMemory, target: String, move: Boolean) {
        for (file in selection.files) {
            val original = memories.getFile(selection.scope, file.path)
            if (original == null) continue // a prior attempt may already have completed the move
            check(original.version == file.version) { "Source memory changed" }
            val current = memories.getFile(target, file.path)
            if (current == null) {
                requireSaved(memories.writeTransferredFile(target, file.path, file.content, MemoryRepository.NEW_FILE_VERSION))
            } else if (current.content != file.content) {
                check(file.path == MemoryFile.PROFILE_PATH) { "Destination memory already exists" }
                val marker = "<!-- f024-file:${file.version} -->"
                if (marker !in current.content) requireSaved(memories.appendTransferredFile(target, file.path, marker + "\n" + file.content, current.version))
            }
            if (move) requireSaved(memories.deleteFile(selection.scope, file.path, file.version))
        }
        val currentRows = memories.getRowMemories(selection.scope).associateBy { it.id }
        val rows = selection.rows.filter { row -> currentRows[row.id]?.also { check(it == row) { "Source memory changed" } } != null }
        if (rows.isEmpty()) return
        val profile = memories.getFile(target, MemoryFile.PROFILE_PATH)
        val merged = mergeRowMemoriesToProfile(rows, profile?.content, selection.scope) ?: return
        if (merged != profile?.content) requireSaved(memories.writeTransferredFile(target, MemoryFile.PROFILE_PATH, merged, profile?.version ?: MemoryRepository.NEW_FILE_VERSION))
        if (move) memories.deleteRowMemories(selection.scope, rows.map { it.id })
    }

    private fun requireSaved(result: MemoryWriteResult) {
        check(result is MemoryWriteResult.Success) { (result as? MemoryWriteResult.Rejected)?.reason ?: "Memory changed during transfer" }
    }
}

internal data class TransferMemory(val scope: String, val files: List<MemoryFile>, val rows: List<RowMemory>)

internal data class TransferMemorySelection(val filePaths: Set<String>, val rowIds: Set<Int>)

internal fun TransferMemory.selectAll() = TransferMemorySelection(
    filePaths = files.mapTo(linkedSetOf()) { it.path },
    rowIds = rows.mapTo(linkedSetOf()) { it.id },
)

internal fun TransferMemory.invertSelection(selection: TransferMemorySelection) = TransferMemorySelection(
    filePaths = files.asSequence().map { it.path }.filterNot { it in selection.filePaths }.toSet(),
    rowIds = rows.asSequence().map { it.id }.filterNot { it in selection.rowIds }.toSet(),
)

// Group members are snapshots, not Assistant references. Guard the built-in default and any matching member IDs.
internal enum class AssistantMoveBlock { NONE, DEFAULT_ASSISTANT, GROUP_MEMBER }

internal fun Settings.assistantMoveBlock(id: Uuid): AssistantMoveBlock = when {
    id == DEFAULT_ASSISTANT_ID -> AssistantMoveBlock.DEFAULT_ASSISTANT
    groupMembers.any { it.id == id } -> AssistantMoveBlock.GROUP_MEMBER
    else -> AssistantMoveBlock.NONE
}

internal fun Settings.canMoveToCharacter(id: Uuid): Boolean =
    assistantMoveBlock(id) == AssistantMoveBlock.NONE

internal enum class MemoryMoveBlock { NONE, GLOBAL, SHARED_WITH_ASSISTANT, REFERENCED_BY_CHARACTER }

internal fun Settings.memoryMoveBlock(id: Uuid, scope: String): MemoryMoveBlock = when {
    scope == MemoryRepository.GLOBAL_MEMORY_ID -> MemoryMoveBlock.GLOBAL
    assistants.any { it.id != id && it.memoryId == scope } -> MemoryMoveBlock.SHARED_WITH_ASSISTANT
    characters.any { scope in it.referencedMemoryScopes } -> MemoryMoveBlock.REFERENCED_BY_CHARACTER
    else -> MemoryMoveBlock.NONE
}

internal fun Settings.canMoveMemory(id: Uuid, scope: String): Boolean =
    memoryMoveBlock(id, scope) == MemoryMoveBlock.NONE

/** Cache within each copied conversation; only new files belong to failure cleanup. */
internal class CharacterTransferAttachments(private val copy: (String, Uuid) -> String?) {
    private val copied = mutableListOf<String>()
    val copiedUrls: List<String> get() = copied
    var missing: Int = 0
        private set

    fun forConversation(id: Uuid): (String) -> String {
        val replacements = mutableMapOf<String, String>()
        return { url ->
            replacements.getOrPut(url) {
                val result = copy(url, id)
                if (result == null) {
                    missing++
                    url
                } else {
                    copied += result
                    result
                }
            }
        }
    }
}
