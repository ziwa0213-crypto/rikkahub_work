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
        if (!move) copyConversations(sourceChats, target)
        if (move) for (stub in sourceChats) chat.moveConversationToCharacter(stub.id, source.id, target)
        if (memory != null) transferMemory(memory, target.toString(), moveMemory)
        settings.update { latest ->
            latest.copy(
                assistants = if (move) latest.assistants.filterNot { it.id == source.id } else latest.assistants,
                assistantId = if (move && latest.assistantId == source.id) DEFAULT_ASSISTANT_ID else latest.assistantId,
                uiMode = UiMode.ROLE, characterId = target,
            )
        }
    }

    suspend fun finish() = settings.update {
        it.copy(characterMigrationState = CharacterMigrationState.DONE, characterMigrationPrompted = true,
            lastRunVersionCode = BuildConfig.VERSION_CODE.toInt())
    }

    private suspend fun copyConversations(source: List<Conversation>, target: Uuid) {
        val copiedUris = mutableListOf<android.net.Uri>()
        val titles = conversations.getTitlesOfAssistant(target).toMutableSet()
        try {
            source.mapNotNull { stub ->
                val newId = characterCopyId(stub.id, target)
                if (conversations.existsConversationById(newId)) return@mapNotNull null
                val full = checkNotNull(conversations.getConversationById(stub.id))
                val attachments = mutableMapOf<String, String>()
                full.duplicateForCharacter(target, newId, titles) { url ->
                    attachments.getOrPut(url) {
                        files.copyCharacterChatAttachment(url.toUri(), newId).also(copiedUris::add).toString()
                    }
                }.also { titles.add(it.title) }
            }.also { conversations.insertCharacterCopies(it) }
        } catch (e: Exception) {
            files.deleteChatFiles(copiedUris)
            throw e
        }
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
internal fun Settings.canMoveToCharacter(id: Uuid): Boolean =
    id != DEFAULT_ASSISTANT_ID && groupMembers.none { it.id == id }

internal fun Settings.canMoveMemory(id: Uuid, scope: String): Boolean =
    scope != MemoryRepository.GLOBAL_MEMORY_ID && assistants.none { it.id != id && it.memoryId == scope } &&
        characters.none { scope in it.referencedMemoryScopes }
