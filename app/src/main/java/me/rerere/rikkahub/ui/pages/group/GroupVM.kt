package me.rerere.rikkahub.ui.pages.group

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import me.rerere.rikkahub.data.model.ContextUsage
import me.rerere.rikkahub.data.model.contextUsage as calculateGroupContextUsage
import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.data.model.GroupCompressionGate
import me.rerere.rikkahub.data.model.buildTieredCompressContent
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_GROUP_COMPRESS_PROMPT
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.data.model.normalizeGroupData
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GROUP_CHAT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.isGroupChat
import me.rerere.rikkahub.data.model.copyForGroup
import me.rerere.rikkahub.data.model.defaultConversationTitle
import me.rerere.rikkahub.data.model.Tag
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

class GroupVM(
    private val settingsStore: SettingsStore,
    private val filesManager: FilesManager,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val chatService: ChatService,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    val settings = settingsStore.settingsFlow
    val inputState = ChatInputState()
    private val selection = MutableStateFlow<Uuid?>(null)
    val selectedGroupId = selection.asStateFlow()
    private val conversationSelection = MutableStateFlow<Map<Uuid, Uuid?>>(emptyMap())
    private val conversationMutex = Mutex()
    private val deletingConversations = mutableSetOf<Uuid>()
    private fun isAvailable(id: Uuid): Boolean = id !in deletingConversations &&
        settings.value.groups.any { id in it.conversationIds }
    val selectedConversationId = combine(settings, selection, conversationSelection) { settings, groupId, choices ->
        settings.selectedGroup(groupId)?.let { group ->
            if (choices.containsKey(group.id)) choices[group.id]?.takeIf { it in group.conversationIds }
            else group.conversationIds.firstOrNull()
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val conversation = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf<Conversation?>(null) else flow<Conversation?> {
            chatService.addConversationReference(id)
            try {
                val exists = conversationMutex.withLock {
                    if (conversationRepository.getConversationById(id)?.isGroupChat() == true) {
                        chatService.initializeConversation(id)
                        true
                    } else false
                }
                if (!exists) { emit(null); return@flow }
                emitAll(chatService.getConversationFlow(id))
            } finally { chatService.removeConversationReference(id) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val conversationJob = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null) else chatService.getGenerationJobStateFlow(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val processingStatus = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null) else chatService.getProcessingStatusFlow(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val messageQueue = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf(me.rerere.rikkahub.service.MessageQueueState()) else chatService.getMessageQueueFlow(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, me.rerere.rikkahub.service.MessageQueueState())
    val conversations = combine(settings, selection) { settings, id -> settings.selectedGroup(id)?.conversationIds.orEmpty() }
        .distinctUntilChanged().flatMapLatest { conversationRepository.getGroupConversations(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val errors = chatService.errors
    private val duplicating = MutableStateFlow(false)
    val isDuplicating = duplicating.asStateFlow()
    private data class ContextState(val conversation: Conversation, val group: Group, val usage: ContextUsage)
    private val contextState: StateFlow<ContextState?> = combine(conversation, settings) { conversation, settings ->
        val group = conversation?.let { conv -> settings.groups.find { conv.id in it.conversationIds } }
        if (conversation == null || group == null) null else ContextState(conversation, group, group.calculateGroupContextUsage(conversation.currentMessages))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val contextUsage: StateFlow<ContextUsage?> = contextState.map { it?.usage }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val compressionGate = GroupCompressionGate()
    private val compressPrompt = MutableStateFlow<Uuid?>(null)
    val compressionPromptConversationId = compressPrompt.asStateFlow()
    private val compressing = MutableStateFlow(false)
    val isCompressing = compressing.asStateFlow()
    private val compressionDone = MutableStateFlow<Int?>(null)
    val compressedMessageCount = compressionDone.asStateFlow()

    init {
        val checked = chatService.completedGroupRounds.value.toMutableMap()
        viewModelScope.launch {
            combine(contextState, conversationJob, chatService.completedGroupRounds) { state, job, rounds ->
                Triple(state, job, rounds)
            }.collect { (state, job, rounds) ->
                if (state == null || job != null || compressing.value || settings.value.uiMode != me.rerere.rikkahub.data.datastore.UiMode.GROUP ||
                    state.conversation.id != selectedConversationId.value ||
                    state.conversation != chatService.getConversationFlow(state.conversation.id).value ||
                    state.conversation.currentMessages.any { it.getTools().any { tool -> tool.isPending } }) return@collect
                val id = state.conversation.id
                val completed = rounds[id] ?: return@collect
                if (completed == checked[id]) return@collect
                checked[id] = completed
                val count = state.conversation.currentMessages.size
                if (compressionGate.shouldPrompt(id, state.usage, state.group, count)) {
                    compressionGate.markPrompted(id, state.usage, count)
                    compressPrompt.value = id
                }
            }
        }
    }

    fun dismissCompressionPrompt() { compressPrompt.value = null }
    fun dismissCompressionDone() { compressionDone.value = null }

    fun handleAutoCompress(): Job {
        val id = compressPrompt.value
        compressPrompt.value = null
        val group = settings.value.groups.find { id in it.conversationIds }
        return handleCompressContext("", group?.compressTargetTokens ?: 2000, group?.compressKeepRecent ?: 32, id)
    }

    fun handleCompressContext(additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int,
        conversationId: Uuid? = selectedConversationId.value): Job = viewModelScope.launch {
        try {
            conversationMutex.withLock {
                val id = conversationId ?: error(appContext.getString(R.string.group_chat_missing))
                check(!compressing.value && chatService.getGenerationJobStateFlow(id).first() == null) {
                    appContext.getString(R.string.group_compress_busy)
                }
                val snapshot = settings.value
                val group = snapshot.groups.find { id in it.conversationIds } ?: error(appContext.getString(R.string.group_chat_missing))
                // The drawer's lightweight Conversation has no message nodes. Load the complete row.
                val full = conversationRepository.getConversationById(id)?.takeIf { it.isGroupChat() }
                    ?: error(appContext.getString(R.string.group_chat_missing))
                check(full.currentMessages.none { it.getTools().any { tool -> tool.isPending } }) {
                    appContext.getString(R.string.group_compress_busy)
                }
                compressing.value = true
                try {
                    val owner = snapshot.displaySetting.userNickname.ifBlank { appContext.getString(R.string.group_owner_name) }
                    val builder: ((List<me.rerere.ai.ui.UIMessage>) -> String)? = if (group.enableTieredCompress) { messages ->
                        val content = buildTieredCompressContent(messages, group.deputyMemberId, owner)
                        val template = if (snapshot.groupCompressPrompt == DEFAULT_GROUP_COMPRESS_PROMPT)
                            appContext.getString(R.string.group_compress_prompt_template) else snapshot.groupCompressPrompt
                        template.applyPlaceholders("primary" to content.primary, "others" to content.others,
                            "target_tokens" to targetTokens.toString(), "additional_context" to additionalPrompt,
                            "locale" to Locale.getDefault().displayName)
                    } else null
                    chatService.compressConversation(id, full, additionalPrompt, targetTokens.coerceAtLeast(1),
                        keepRecentMessages.coerceAtLeast(0),
                        messageFormatter = { it.summaryAsTextWithSender(owner, maxLength = 4000) }, tieredBuilder = builder).getOrThrow()
                    updateGroup(group.id) { it.copy(compressKeepRecent = keepRecentMessages.coerceAtLeast(0),
                        compressTargetTokens = targetTokens.coerceAtLeast(1)) }
                    compressionGate.reset(id)
                    compressionDone.value = (full.currentMessages.size - keepRecentMessages.coerceAtLeast(0)).coerceAtLeast(0)
                } finally { compressing.value = false }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, conversationId,
            title = appContext.getString(R.string.error_title_compress_conversation)) }
    }

    fun updateGroupCompressPrompt(prompt: String) = viewModelScope.launch {
        settingsStore.update { it.copy(groupCompressPrompt = prompt) }
    }

    fun newConversation(groupId: Uuid) {
        conversationSelection.value = conversationSelection.value + (groupId to null)
        inputState.clearInput()
    }

    fun selectConversation(groupId: Uuid, id: Uuid) {
        if (settings.value.groups.find { it.id == groupId }?.conversationIds?.contains(id) != true) return
        conversationSelection.value = conversationSelection.value + (groupId to id)
        inputState.clearInput()
    }

    private suspend fun ensureConversation(groupId: Uuid): Uuid {
        val group = settings.value.groups.find { it.id == groupId } ?: error("Group no longer exists")
        val choice = if (conversationSelection.value.containsKey(groupId)) conversationSelection.value[groupId]
            else group.conversationIds.firstOrNull()
        if (choice != null && choice in group.conversationIds &&
            conversationRepository.getConversationById(choice)?.isGroupChat() == true) {
            chatService.initializeConversation(choice)
            if (choice == group.conversationIds.firstOrNull()) memoryRepository.moveScope(
                MemoryRepository.scopeOfChatGroup(groupId), MemoryRepository.scopeOfChatGroup(groupId, choice))
            return choice
        }
        // Keep the Room row and DataStore membership consistent across cancellation.
        return withContext(NonCancellable) {
            val titles = conversationRepository.getGroupConversations(group.conversationIds).first().map { it.title }
            val created = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID,
                title = defaultConversationTitle(appContext.getString(R.string.group_chat_new_conversation_title), titles),
                messageNodes = emptyList())
            conversationRepository.insertConversation(created)
            try {
                settingsStore.update { settings ->
                    check(settings.groups.any { it.id == groupId }) { "Group no longer exists" }
                    settings.copy(groups = settings.groups.map {
                        if (it.id == groupId) it.copy(conversationIds = it.conversationIds + created.id) else it
                    }).normalizeGroupData()
                }
            } catch (error: Exception) {
                conversationRepository.deleteConversation(created)
                throw error
            }
            conversationSelection.value = conversationSelection.value + (groupId to created.id)
            if (group.conversationIds.isEmpty()) memoryRepository.moveScope(
                MemoryRepository.scopeOfChatGroup(groupId), MemoryRepository.scopeOfChatGroup(groupId, created.id))
            chatService.initializeConversation(created.id)
            created.id
        }
    }

    fun sendMessage(groupId: Uuid, parts: List<me.rerere.ai.ui.UIMessagePart>, answer: Boolean = true,
        quote: me.rerere.ai.ui.UIMessageAnnotation.GroupQuote? = null) = viewModelScope.launch {
        try { conversationMutex.withLock { chatService.sendMessage(ensureConversation(groupId), parts, answer,
            annotations = listOfNotNull(quote)) } }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, selectedConversationId.value) }
    }

    fun toggleConversationPin(groupId: Uuid, conversation: Conversation) = viewModelScope.launch {
        try {
            conversationMutex.withLock {
                if (settings.value.groups.find { it.id == groupId }?.conversationIds?.contains(conversation.id) != true) return@withLock
                val latest = conversationRepository.getConversationById(conversation.id)?.takeIf { it.isGroupChat() } ?: return@withLock
                conversationRepository.updatePinStatus(latest.id, !latest.isPinned)
                chatService.updateConversationState(latest.id) { it.copy(isPinned = !latest.isPinned) }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, conversation.id) }
    }

    fun renameConversation(groupId: Uuid, id: Uuid, title: String) = viewModelScope.launch {
        try {
            conversationMutex.withLock {
                if (title.isBlank() || settings.value.groups.find { it.id == groupId }?.conversationIds?.contains(id) != true) return@withLock
                if (conversationRepository.getConversationById(id)?.isGroupChat() != true) return@withLock
                conversationRepository.updateConversationTitle(id, title.trim())
                chatService.updateConversationState(id) { it.copy(title = title.trim()) }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, id) }
    }

    fun deleteConversation(groupId: Uuid, id: Uuid) = viewModelScope.launch {
        try {
            conversationMutex.withLock {
                val group = settings.value.groups.find { it.id == groupId } ?: return@withLock
                if (id !in group.conversationIds) return@withLock
                withContext(NonCancellable) {
                    deletingConversations += id
                    try {
                        chatService.stopGeneration(id)
                        conversationRepository.getConversationById(id)?.takeIf { it.isGroupChat() }?.let {
                            conversationRepository.deleteConversation(it)
                        }
                        memoryRepository.deleteMemoriesOfAssistant(MemoryRepository.scopeOfChatGroup(groupId, id))
                        settingsStore.update { it.copy(groups = it.groups.map { group ->
                            if (group.id == groupId) group.copy(conversationIds = group.conversationIds - id) else group
                        }) }
                        conversationSelection.value = conversationSelection.value - groupId
                    } finally { deletingConversations -= id }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, id) }
    }

    fun stopGeneration() = selectedConversationId.value?.let { id -> viewModelScope.launch { chatService.stopGeneration(id) } }
    fun handleToolApproval(id: String, approved: Boolean, reason: String) = selectedConversationId.value?.let {
        if (isAvailable(it)) chatService.handleToolApproval(it, id, approved, reason)
    }
    fun handleToolAnswer(id: String, answer: String) = selectedConversationId.value?.let {
        if (isAvailable(it)) chatService.handleToolApproval(it, id, approved = true, answer = answer)
    }
    fun dismissError(id: Uuid) = chatService.dismissError(id)
    fun clearErrors() = chatService.clearAllErrors()

    fun updateWorkspaceCwd(groupId: Uuid, conversationId: Uuid, cwd: String?) = viewModelScope.launch {
        try {
            conversationMutex.withLock {
                if (settings.value.groups.find { it.id == groupId }?.conversationIds?.contains(conversationId) != true) return@withLock
                conversationRepository.updateGroupWorkspaceCwd(conversationId, cwd)
                chatService.updateConversationState(conversationId) { it.copy(workspaceCwd = cwd) }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, conversationId) }
    }

    fun regenerate(message: me.rerere.ai.ui.UIMessage) = selectedConversationId.value?.let {
        if (isAvailable(it) && conversationJob.value == null && !compressing.value) chatService.regenerateAtMessage(it, message)
    }
    fun deleteMessage(message: me.rerere.ai.ui.UIMessage) = selectedConversationId.value?.let { id ->
        viewModelScope.launch { conversationMutex.withLock {
            if (isAvailable(id) && conversationJob.value == null) chatService.deleteMessage(id, message)
        } }
    }
    fun editMessage(parts: List<me.rerere.ai.ui.UIMessagePart>, messageId: Uuid) = selectedConversationId.value?.let { id ->
        viewModelScope.launch { conversationMutex.withLock {
            if (isAvailable(id) && conversationJob.value == null) chatService.editMessage(id, messageId, parts)
        } }
    }
    fun updateNode(node: me.rerere.rikkahub.data.model.MessageNode) = selectedConversationId.value?.let { id ->
        viewModelScope.launch {
            conversationMutex.withLock {
                if (isAvailable(id) && conversationJob.value == null) {
                    val current = chatService.getConversationFlow(id).value
                    chatService.saveConversation(id, current.copy(messageNodes = current.messageNodes.map {
                        if (it.id == node.id) node else it
                    }))
                }
            }
        }
    }
    fun forkMessage(groupId: Uuid, message: me.rerere.ai.ui.UIMessage) = selectedConversationId.value?.let { id ->
        viewModelScope.launch {
            try {
                conversationMutex.withLock {
                    if (!isAvailable(id) || conversationJob.value != null || settings.value.groups.find { it.id == groupId }
                            ?.conversationIds?.contains(id) != true) return@withLock
                    withContext(NonCancellable) {
                        val fork = chatService.forkConversationAtMessage(id, message.id)
                        try {
                            settingsStore.update {
                                check(it.groups.any { group -> group.id == groupId }) { "Group no longer exists" }
                                it.copy(groups = it.groups.map { group ->
                                    if (group.id == groupId) group.copy(conversationIds = group.conversationIds + fork.id) else group
                                })
                            }
                        } catch (error: Exception) {
                            conversationRepository.deleteConversation(fork)
                            throw error
                        }
                        memoryRepository.copyMemories(MemoryRepository.scopeOfChatGroup(groupId, id),
                            MemoryRepository.scopeOfChatGroup(groupId, fork.id))
                        selectConversation(groupId, fork.id)
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { chatService.addError(error, id) }
        }
    }
    fun removeQueuedMessage(id: Uuid) = selectedConversationId.value?.let { chatService.removeQueuedMessage(it, id) }
    fun beginEditQueuedMessage(id: Uuid) = selectedConversationId.value?.let { chatService.beginEditQueuedMessage(it, id) }
    fun finishEditQueuedMessage(id: Uuid, parts: List<me.rerere.ai.ui.UIMessagePart>?) =
        selectedConversationId.value?.let { chatService.finishEditQueuedMessage(it, id, parts) }
    fun resumeMessageQueue() = selectedConversationId.value?.let { if (isAvailable(it)) chatService.resumeMessageQueue(it) }

    fun selectGroup(id: Uuid) { selection.value = id }

    fun saveMember(member: GroupMember, onSaved: () -> Unit = {}) = viewModelScope.launch {
        settingsStore.update { settings ->
            settings.copy(groupMembers = if (settings.groupMembers.any { it.id == member.id }) {
                settings.groupMembers.map { if (it.id == member.id) member else it }
            } else settings.groupMembers + member).normalizeGroupData()
        }
        onSaved()
    }

    fun deleteMember(id: Uuid) = viewModelScope.launch {
        settingsStore.update { it.copy(groupMembers = it.groupMembers.filterNot { member -> member.id == id }).normalizeGroupData() }
    }

    fun saveGroup(group: Group, onSaved: () -> Unit = {}) = viewModelScope.launch {
        persistGroup(group)
        onSaved()
    }

    private suspend fun persistGroup(group: Group) {
        settingsStore.update { settings ->
            settings.copy(groups = if (settings.groups.any { it.id == group.id }) {
                settings.groups.map { if (it.id == group.id) group.copy(conversationIds = it.conversationIds) else it }
            } else settings.groups + group).normalizeGroupData()
        }
    }

    fun updateGroupTags(tags: List<Tag>) = viewModelScope.launch {
        settingsStore.update { it.copy(groupTags = (it.groupTags + tags).distinctBy { tag -> tag.id }).normalizeGroupData() }
    }

    fun duplicateGroup(source: Group, onFinished: (Int) -> Unit = {}) = viewModelScope.launch {
        if (duplicating.value) return@launch
        duplicating.value = true
        try {
            val count = conversationMutex.withLock {
                withContext(Dispatchers.IO + NonCancellable) {
                    val original = settings.value.groups.find { it.id == source.id } ?: error("Group no longer exists")
                    val targetId = Uuid.random()
                    val copies = mutableListOf<Conversation>()
                    val copiedFiles = mutableSetOf<String>()
                    fun copyPart(part: UIMessagePart): UIMessagePart {
                        if (part is UIMessagePart.Tool) return part.copy(output = part.output.map(::copyPart))
                        val oldUrl = part.fileUrl()
                        val copied = with(chatService) { part.copyWithForkedFileUrl() }
                        val newUrl = copied.fileUrl()
                        check(oldUrl?.startsWith("file:") != true || newUrl != oldUrl) { "Failed to copy attachment" }
                        if (newUrl != oldUrl && newUrl?.startsWith("file:") == true) copiedFiles += newUrl
                        return copied
                    }
                    try {
                        val background = original.background?.let { url ->
                            (copyPart(UIMessagePart.Image(url)) as UIMessagePart.Image).url
                        }
                        for (id in original.conversationIds) {
                            val conversation = conversationRepository.getConversationById(id)?.takeIf { it.isGroupChat() } ?: continue
                            val copy = conversation.copyForGroup(::copyPart)
                            copies += copy
                            conversationRepository.insertConversation(copy)
                            memoryRepository.copyMemories(MemoryRepository.scopeOfChatGroup(original.id, id),
                                MemoryRepository.scopeOfChatGroup(targetId, copy.id))
                        }
                        memoryRepository.copyMemories(MemoryRepository.scopeOfChatGroup(original.id),
                            MemoryRepository.scopeOfChatGroup(targetId))
                        persistGroup(original.copy(id = targetId,
                            name = appContext.getString(R.string.group_copy_name, original.name),
                            createdAt = System.currentTimeMillis(), isPinned = false,
                            background = background, conversationIds = copies.map { it.id }))
                        copies.size
                    } catch (error: Exception) {
                        runCatching { settingsStore.update { it.copy(groups = it.groups.filterNot { it.id == targetId }) } }
                            .onFailure { error.addSuppressed(it) }
                        copies.forEach { copy ->
                            runCatching { conversationRepository.deleteConversation(copy) }.onFailure { error.addSuppressed(it) }
                            runCatching { memoryRepository.deleteMemoriesOfAssistant(MemoryRepository.scopeOfChatGroup(targetId, copy.id)) }
                                .onFailure { error.addSuppressed(it) }
                        }
                        runCatching { memoryRepository.deleteMemoriesOfAssistant(MemoryRepository.scopeOfChatGroup(targetId)) }
                            .onFailure { error.addSuppressed(it) }
                        runCatching { filesManager.deleteChatFiles(copiedFiles.map { it.toUri() }) }.onFailure { error.addSuppressed(it) }
                        throw error
                    }
                }
            }
            onFinished(count)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, selectedConversationId.value) }
        finally { duplicating.value = false }
    }

    fun updateGroup(id: Uuid, transform: (Group) -> Group) = viewModelScope.launch {
        var replaced: Pair<Group, Group>? = null
        settingsStore.update { settings ->
            settings.copy(groups = settings.groups.map { if (it.id == id) {
                val updated = transform(it)
                replaced = it to updated
                updated
            } else it }).normalizeGroupData()
        }
        replaced?.let { (old, new) ->
            if (old.background != new.background) deleteBackground(old)
            if (old.workspaceId != new.workspaceId) {
                conversationMutex.withLock {
                    new.conversationIds.forEach { conversationId ->
                        conversationRepository.updateGroupWorkspaceCwd(conversationId, null)
                        chatService.updateConversationState(conversationId) { it.copy(workspaceCwd = null) }
                    }
                }
            }
        }
    }

    fun deleteGroup(id: Uuid) = viewModelScope.launch {
        try {
            conversationMutex.withLock {
                val group = settings.value.groups.firstOrNull { it.id == id } ?: return@withLock
                withContext(NonCancellable) {
                    deletingConversations += group.conversationIds
                    try {
                        group.conversationIds.forEach { conversationId ->
                            chatService.stopGeneration(conversationId)
                            conversationRepository.getConversationById(conversationId)?.takeIf { it.isGroupChat() }?.let {
                                conversationRepository.deleteConversation(it)
                            }
                            memoryRepository.deleteMemoriesOfAssistant(MemoryRepository.scopeOfChatGroup(id, conversationId))
                        }
                        memoryRepository.deleteMemoriesOfAssistant(MemoryRepository.scopeOfChatGroup(id))
                        settingsStore.update { settings ->
                            settings.copy(groups = settings.groups.filterNot { it.id == id })
                        }
                        deleteBackground(group)
                        conversationSelection.value = conversationSelection.value - id
                        if (selection.value == id) selection.value = null
                    } finally { deletingConversations -= group.conversationIds.toSet() }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { chatService.addError(error, selectedConversationId.value) }
    }

    fun reorderMembers(fromId: Uuid, toId: Uuid) = viewModelScope.launch {
        settingsStore.update { settings ->
            val from = settings.groupMembers.indexOfFirst { it.id == fromId }
            val to = settings.groupMembers.indexOfFirst { it.id == toId }
            if (from < 0 || to < 0 || from == to) return@update settings
            settings.copy(groupMembers = settings.groupMembers.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun reorderGroups(fromId: Uuid, toId: Uuid) = viewModelScope.launch {
        settingsStore.update { settings ->
            val from = settings.groups.indexOfFirst { it.id == fromId }
            val to = settings.groups.indexOfFirst { it.id == toId }
            if (from < 0 || to < 0 || from == to) return@update settings
            settings.copy(groups = settings.groups.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    private suspend fun deleteBackground(group: Group) {
        val background = group.background ?: return
        val settings = settings.value
        // Copies can share an image URI; retain files still used by another group or assistant.
        if (settings.groups.any { it.background == background } ||
            settings.assistants.any { it.background == background }) return
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = background.toUri()
                if (uri.scheme == "content" || uri.scheme == "file") filesManager.deleteChatFiles(listOf(uri))
            }.onFailure { Log.w("GroupVM", "Failed to delete group background", it) }
        }
    }
}

private fun UIMessagePart.fileUrl(): String? = when (this) {
    is UIMessagePart.Image -> url
    is UIMessagePart.Document -> url
    is UIMessagePart.Video -> url
    is UIMessagePart.Audio -> url
    else -> null
}

@Composable
fun rememberGroupVM(): GroupVM = koinViewModel(
    viewModelStoreOwner = checkNotNull(LocalActivity.current as? ComponentActivity),
)

fun Settings.selectedGroup(id: Uuid?): Group? = groups.find { it.id == id } ?: groups.firstOrNull()
