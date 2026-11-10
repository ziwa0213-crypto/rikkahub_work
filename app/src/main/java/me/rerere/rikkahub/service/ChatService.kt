package me.rerere.rikkahub.service

import android.app.Application
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebCapabilityRefusalException
import me.rerere.ai.provider.VideoInputException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebImageException
import me.rerere.rikkahub.data.ai.DeepSeekWebOutputGuard
import me.rerere.rikkahub.data.ai.DeepSeekWebPseudoToolException
import me.rerere.rikkahub.data.model.availableGroupQuotes
import me.rerere.rikkahub.data.model.applyGroupQuotes
import me.rerere.rikkahub.data.model.completedDispatchText
import me.rerere.rikkahub.data.model.displayNameOf
import me.rerere.rikkahub.data.model.isGroupChat
import me.rerere.rikkahub.data.model.isPulseChat
import me.rerere.rikkahub.data.model.resolveDispatch
import me.rerere.rikkahub.data.model.speakingMembers
import me.rerere.rikkahub.data.model.stripSelfSpeakerPrefix
import me.rerere.rikkahub.data.model.toAssistant
import me.rerere.rikkahub.data.model.withGroupSender
import me.rerere.rikkahub.service.pulse.isAwaitingPulseReply
import me.rerere.rikkahub.service.pulse.persistPulseResponse
import java.util.concurrent.ConcurrentHashMap
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.agent.AgentEvent
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.agent.RetryAttempt
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.ChatTurnContext
import me.rerere.rikkahub.data.ai.ConversationMetaGenerator
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.event.GenerationOutcome
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.bindConfig
import me.rerere.rikkahub.data.model.cancelInterruptedTools
import me.rerere.rikkahub.data.model.deleteMessage
import me.rerere.rikkahub.data.model.editMessage
import me.rerere.rikkahub.data.model.fillModelSnapshots
import me.rerere.rikkahub.data.model.fork
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.getStoredAssistantOf
import me.rerere.rikkahub.data.model.insertContextCheckpoint
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.mapLocalFileUrls
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceUserRegexes
import me.rerere.rikkahub.data.model.selectBranch
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.model.withAssistantUpdate
import me.rerere.rikkahub.data.model.withNodeFavorite
import me.rerere.rikkahub.data.model.withToolApproval
import me.rerere.rikkahub.data.model.withTranslation
import me.rerere.rikkahub.data.model.withoutConversationFields
import me.rerere.rikkahub.data.model.withoutUnresumableToolCalls
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
    val autoDismiss: Boolean = true,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

internal fun deepSeekWebChatError(
    error: Throwable,
    conversationId: Uuid,
    getString: (Int, String?) -> String,
): ChatError? {
    val titleResource: Int
    val messageResource: Int
    val toolName: String?
    when (error) {
        is DeepSeekWebImageException -> {
            toolName = error.message
            titleResource = R.string.chat_error_dsweb_image_title
            messageResource = R.string.chat_error_dsweb_image_message
        }
        is DeepSeekWebPseudoToolException -> {
            toolName = error.toolName
            titleResource = if (error.blocked) R.string.chat_error_dsweb_pseudo_tool_title
                else R.string.chat_error_dsweb_tool_format_title
            messageResource = if (error.blocked) R.string.chat_error_dsweb_pseudo_tool_message
                else R.string.chat_error_dsweb_tool_format_message
        }
        is DeepSeekWebCapabilityRefusalException -> {
            toolName = error.blockedToolName
            when {
                !toolName.isNullOrBlank() -> {
                    titleResource = R.string.chat_error_dsweb_tool_blocked_title
                    messageResource = R.string.chat_error_dsweb_tool_blocked_message
                }
                error.isLocalExecution -> {
                    titleResource = R.string.chat_error_dsweb_execute_title
                    messageResource = R.string.chat_error_dsweb_execute_message
                }
                else -> {
                    titleResource = R.string.chat_error_dsweb_capability_title
                    messageResource = R.string.chat_error_dsweb_capability_message
                }
            }
        }
        else -> return null
    }
    return ChatError(
        title = getString(titleResource, null),
        error = IllegalStateException(getString(messageResource, toolName), error),
        conversationId = conversationId,
        autoDismiss = false,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val requestFactory: ChatRequestFactory,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val translationHandler: TranslationHandler,
    private val metaGenerator: ConversationMetaGenerator,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val folderRepository: FolderRepository,
    private val backgroundShellManager: BackgroundShellManager,
) {
    private val pulseEffects by lazy {
        val preferences = context.getSharedPreferences("pulse_runs", android.content.Context.MODE_PRIVATE)
        me.rerere.rikkahub.service.pulse.PulseEffects(
            load = { preferences.getString(it, null) },
            save = { key, value -> check(preferences.edit().putString(key, value).commit()) { "Cannot record Pulse write" } },
        )
    }
    private val groupRounds = ConcurrentHashMap<Uuid, GroupGenerationRound>()
    private val _completedGroupRounds = MutableStateFlow<Map<Uuid, Long>>(emptyMap())
    internal val completedGroupRounds = _completedGroupRounds.asStateFlow()

    /** 一轮生成的结果 */
    private sealed interface TurnResult {
        data class Finished(val reason: AgentFinishReason) : TurnResult

        /** 没能完成：错误已经报告给用户，消息队列也已暂停 */
        data object Failed : TurnResult
    }

    private val conversationStore = object : ConversationStore {
        override suspend fun prepareWrite(): (Conversation) -> Conversation {
            // 会话落库即视为开始，此时把助手的配置固定到会话上
            val settings = settingsStore.awaitLoaded()
            return { it.bindConfig(settings).fillModelSnapshots(settings) }
        }

        override suspend fun write(previous: Conversation?, current: Conversation) {
            if (previous == null && !conversationRepo.existsConversationById(current.id)) {
                conversationRepo.insertConversation(current)
            } else {
                conversationRepo.updateConversation(current)
            }
            if (previous != null && previous.messageNodes !== current.messageNodes) {
                withContext(Dispatchers.IO) { deleteUnreferencedFiles(previous, current) }
                val currentNodeIds = current.messageNodes.mapTo(HashSet()) { it.id }
                val removedNodeIds = previous.messageNodes.map { it.id }.filterNot { it in currentNodeIds }
                if (removedNodeIds.isNotEmpty()) conversationRepo.deleteNodeFavorites(current.id, removedNodeIds)
            }
        }
    }

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        store = conversationStore,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
        onSessionRemoved = memoryConsolidationScheduler::onConversationLeft,
    )

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
        autoDismiss: Boolean = true,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(
                title = title,
                error = error,
                conversationId = conversationId,
                solution = solution,
                autoDismiss = autoDismiss,
            )
        }
    }

    private fun addGenerationError(error: Throwable, conversationId: Uuid, title: String) {
        if (error is VideoInputException) {
            val message = when (error.reason) {
                VideoInputException.Reason.DISABLED -> R.string.chat_error_video_disabled
                VideoInputException.Reason.UNSUPPORTED_PROTOCOL -> R.string.chat_error_video_protocol
                VideoInputException.Reason.ENCODING_FAILED -> R.string.chat_error_video_encoding
            }
            addError(IllegalArgumentException(context.getString(message)), conversationId, title = title, autoDismiss = false)
            return
        }
        val capabilityError = deepSeekWebChatError(error, conversationId) { resource, toolName ->
            if (toolName == null) context.getString(resource) else context.getString(resource, toolName)
        }
        if (capabilityError != null) _errors.update { it + capabilityError }
        else addError(error, conversationId, title = title)
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    init {
        appScope.launch {
            backgroundShellManager.noticeEvents.collect { event ->
                when (event) {
                    is BackgroundShellNoticeEvent.Posted -> postNotice(event.conversationId, event.key, event.notice)
                    is BackgroundShellNoticeEvent.Withdrawn ->
                        sessionManager.get(event.conversationId)?.inbox?.withdraw(event.key)
                }
            }
        }
    }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        // 取消只来自停止（已自行暂停）或删除，这里再暂停会盖掉停止后用户发送时恢复的队列
        if (cause != null && cause !is CancellationException) session.inbox.pause()
        if (session.state.value.isAwaitingToolApproval) {
            session.inbox.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch { dispatchInbox(session.id) }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    /** 会话的最新内容：已加载的以内存为准，否则读数据库 */
    suspend fun getConversation(conversationId: Uuid): Conversation? =
        sessionManager.get(conversationId)?.takeIf { it.isInitialized }?.state?.value
            ?: conversationRepo.getConversationById(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getReplyMessageIdFlow(conversationId: Uuid): StateFlow<Uuid?> =
        sessionManager.getReplyMessageIdFlow(conversationId)

    fun getGenerationStartedAtFlow(conversationId: Uuid): StateFlow<Long?> =
        sessionManager.getGenerationStartedAtFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                // Completion, user cancellation and real failures own this job's lifetime.
                // Device sleep and elapsed/idle time do not impose an app-level deadline.
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    /** Kai-style temporary history: no transcript writes until the caller accepts the result. */
    suspend fun runPulseTurn(
        conversationId: Uuid,
        instruction: String,
        toolContext: me.rerere.rikkahub.service.pulse.PulseToolContext? = null,
        skipIfUnanswered: Boolean = false,
        onStarted: suspend () -> Unit = {},
    ): me.rerere.rikkahub.service.pulse.PulseTurnResult? = withLoadedSession(conversationId, createIfMissing = false) { session ->
        check(session.state.value.isPulseChat())
        val result = CompletableDeferred<me.rerere.rikkahub.service.pulse.PulseTurnResult?>()
        val incarnation = session.incarnation
        val job = launchGenerationJob(conversationId) {
            try {
                withContext(toolContext ?: kotlin.coroutines.EmptyCoroutineContext) {
                    val conversation = session.state.value
                    if (conversation.isAwaitingToolApproval || (skipIfUnanswered && isAwaitingPulseReply(conversation.currentMessages))) {
                        result.complete(null)
                        return@withContext
                    }
                    onStarted()
                    val input = me.rerere.rikkahub.service.pulse.pulseTurnInput(conversation.currentMessages, instruction)
                    val turn = requestFactory.createContext(
                        settingsStore.awaitLoaded(), conversation,
                        pulseInstruction = input.systemInstruction,
                    )
                    val (request, hooks) = requestFactory.createAgent(turn, input.messages, session.processingStatus)
                    var response: UIMessage? = null
                    var reason: AgentFinishReason? = null
                    runAgent(request, hooks).flowOn(Dispatchers.IO).collect { event ->
                        when (event) {
                            is AgentEvent.MessageUpdated -> {
                                val checked = if (turn.providerSetting is ProviderSetting.DeepSeekWeb)
                                    DeepSeekWebOutputGuard.sanitize(listOf(event.message), turn.providerSetting.unlock) else null
                                checked?.let {
                                    val match = it.matches.firstOrNull { entry -> entry.blocked } ?: it.matches.first()
                                    throw DeepSeekWebPseudoToolException(match.toolName, match.blocked)
                                }
                                response = event.message
                            }
                            is AgentEvent.Retrying -> session.processingStatus.value = retryStatus(event.attempt)
                            is AgentEvent.Finished -> {
                                reason = event.reason
                                response = event.message
                            }
                        }
                    }
                    val message = checkNotNull(response) { "Empty Pulse response" }
                    if (turn.providerSetting is ProviderSetting.DeepSeekWeb) {
                        DeepSeekWebOutputGuard.sanitize(listOf(message), (turn.providerSetting as ProviderSetting.DeepSeekWeb).unlock)?.let { checked ->
                            val match = checked.matches.firstOrNull { it.blocked } ?: checked.matches.first()
                            throw DeepSeekWebPseudoToolException(match.toolName, match.blocked)
                        }
                    }
                    result.complete(me.rerere.rikkahub.service.pulse.pulseTurnResult(conversationId, incarnation, message, reason))
                }
            } catch (error: CancellationException) {
                result.cancel(error)
                throw error
            } catch (error: Exception) {
                addGenerationError(error, conversationId, context.getString(R.string.error_title_generation))
                _errors.update { errors -> errors.map { if (it.conversationId == conversationId) it.copy(autoDismiss = false) else it } }
                result.completeExceptionally(error)
            } finally {
                session.processingStatus.value = null
            }
        }
        job.invokeOnCompletion { cause -> if (!result.isCompleted) result.completeExceptionally(cause ?: IllegalStateException("Pulse stopped")) }
        synchronized(session) {
            if (session.getJob()?.isCompleted == false) {
                job.cancel()
                error("Pulse is already generating")
            }
            session.setJob(job, cancelPrevious = false)
        }
        try {
            result.await().also { job.join() }
        } finally {
            if (!job.isCompleted) job.cancel()
        }
    }

    suspend fun persistPulseTurn(result: me.rerere.rikkahub.service.pulse.PulseTurnResult) {
        withLoadedSession(result.conversationId, createIfMissing = false) { session ->
            session.persistPulseResponse(result) { saved ->
                val settings = settingsStore.awaitLoaded()
                val provider = settings.getChatModelOf(saved)?.findProvider(settings.providers)
                if (settings.pulse.enableMemory && provider != null && provider !is ProviderSetting.DeepSeekWeb) {
                    memoryConsolidationScheduler.onTurnFinished(saved)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        withLoadedSession(conversationId) { session ->
            if (!session.state.value.isGroupChat() && !session.state.value.isPulseChat()) {
                settingsStore.selectAssistant(session.state.value.assistantId)
            }
        }
    }

    private suspend fun ensureInitialized(session: ConversationSession) {
        session.initialize(
            load = { loadConversation(session.id) },
            create = { newConversation(session.id) },
        )
    }

    /** 持有会话引用并确保已加载后执行 [block]；[createIfMissing] 为 false 时数据库里没有就抛 NotFoundException */
    private suspend fun <T> withLoadedSession(
        conversationId: Uuid,
        createIfMissing: Boolean = true,
        block: suspend (ConversationSession) -> T,
    ): T = sessionManager.withSession(conversationId) { session ->
        session.initialize(
            load = { loadConversation(conversationId) },
            create = {
                if (!createIfMissing) throw NotFoundException("Conversation not found")
                newConversation(conversationId)
            },
        )
        block(session)
    }

    // 新建对话, 并添加预设消息
    private suspend fun newConversation(conversationId: Uuid): Conversation {
        val assistant = settingsStore.awaitLoaded().getCurrentAssistant()
        return Conversation.ofId(
            id = conversationId,
            assistantId = assistant.id,
            newConversation = true
        ).updateCurrentMessages(assistant.presetMessages)
    }

    // 引入会话配置之前创建的会话没有固定配置，加载时按助手当前的值补上，之后不再随助手变化。
    private suspend fun loadConversation(conversationId: Uuid): Conversation? {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return null
        // settingsFlow 在启动初期还是占位值，固定配置必须基于真实设置
        val bound = conversation.bindConfig(settingsStore.awaitLoaded())
        if (bound !== conversation) conversationRepo.updateConversationConfig(bound)
        return bound
    }

    /**
     * 加载会话后在最新状态上修改并落库。
     * 删除消息或切换分支也可能解除工具审批阻塞，落库后重新检查队列。
     */
    private suspend fun mutate(conversationId: Uuid, transform: (Conversation) -> Conversation): Conversation =
        withLoadedSession(conversationId) { session ->
            session.update(transform)
            session.flush().also { dispatchInbox(conversationId) }
        }

    // 只删上次落库时引用、这次落库不再引用的文件；只在内存里出现过的文件宁可漏删也不误删
    private fun deleteUnreferencedFiles(previous: Conversation, current: Conversation) {
        val session = sessionManager.get(current.id)
        val queuedFiles = (session?.inbox?.state?.value?.messages.orEmpty() +
            listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val retainedFiles = (current.files + queuedFiles).toSet()
        val deletedFiles = previous.files.filter { it !in retainedFiles }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "deleteUnreferencedFiles: $deletedFiles")
        }
    }

    // ---- 发送消息 ----

    fun getInboxFlow(conversationId: Uuid): StateFlow<InboxState> =
        sessionManager.getInboxFlow(conversationId)

    /** 删除收件箱里的一项：用户输入连同只被它引用的附件一起删，通知直接丢弃 */
    fun removeInboxItem(conversationId: Uuid, itemId: Uuid) {
        (sessionManager.get(conversationId)?.inbox?.remove(itemId) as? QueuedMessage)
            ?.let(::cleanupQueuedAttachments)
        dispatchInbox(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.inbox?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.inbox?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchInbox(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.inbox.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeInbox(conversationId: Uuid) {
        sessionManager.get(conversationId)?.inbox?.resume()
        dispatchInbox(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true,
        annotations: List<UIMessageAnnotation> = emptyList()) {
        if (content.isEmptyInputMessage()) return
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            // 没有排队的用户输入时，新消息就是用户要继续的意思；等待中的通知跟着这一轮发出
            if (session.inbox.state.value.messages.isEmpty()) session.inbox.resume()
            session.inbox.enqueue(content, answer, annotations = annotations)
            dispatchInbox(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.inbox.state.value.paused || session.inbox.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(!session.state.value.isAwaitingToolApproval) {
                context.getString(R.string.chat_page_voice_tools_before_resume)
            }
            if (session.inbox.state.value.messages.isEmpty()) session.inbox.resume()
            session.inbox.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchInbox(conversationId)
        }
        return reply
    }

    /**
     * 放入一条运行时通知，例如后台命令结束。
     * 正在生成时这一轮在下一步请求模型前取走；空闲时主动开一轮，不用等用户再发消息。
     */
    fun postNotice(conversationId: Uuid, key: String, notice: UIMessagePart.Notice) {
        sessionManager.getOrCreate(conversationId).inbox.post(key, notice)
        dispatchInbox(conversationId)
    }

    /** 会话空闲时从收件箱开始下一轮：先发用户输入，只剩通知时单独开一轮 */
    private fun dispatchInbox(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.isAwaitingToolApproval) return null
            if (session.isCompressing) return null
            return when (val next = session.inbox.takeNext() ?: return null) {
                is InboxTurn.Message -> {
                    session.submittingMessage = next.message
                    sendQueuedMessage(session, next.message)
                }

                InboxTurn.Notices -> sendQueuedNotices(session)
            }
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        // 新消息开始初始化前就清掉旧回复，初始化失败或任务启动前取消也不能留下旧预览。
        session.replyMessageId.value = null
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = queued.answer,
        ) {
            try {
                ensureInitialized(session)
                finishInterruptedPendingTools(session)

                val conversation = session.state.value
                if (conversation.isGroupChat()) groupRounds.remove(conversationId)
                val assistant = settingsStore.awaitLoaded().getStoredAssistantOf(conversation)
                val processedContent = if (conversation.isGroupChat()) queued.parts else queued.parts.replaceUserRegexes(assistant)

                // 添加消息到列表
                val previousIds = session.state.value.currentMessages.mapTo(HashSet()) { it.id }
                // 等待中的通知存成用户消息之前的节点：写进回复的话，重新生成时就丢了。
                // 不回复时留在收件箱，这一轮结束后单独开一轮
                val notices = if (queued.answer) session.inbox.takeNotices() else emptyList()
                session.update {
                    it.copy(
                        messageNodes = it.messageNodes +
                            listOfNotNull(notices.takeIf { n -> n.isNotEmpty() }?.toNoticeNode()) +
                            UIMessage(
                                role = MessageRole.USER,
                                parts = processedContent,
                                annotations = queued.annotations,
                            ).toMessageNode(),
                    )
                }
                session.flush()
                session.submittingMessage = null

                // 开始补全
                val result = if (queued.answer) runTurn(session) else null

                queued.reply?.completeWith(runCatching {
                    check(result != TurnResult.Failed) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check((result as? TurnResult.Finished)?.reason !is AgentFinishReason.AwaitingApproval) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    session.state.value.currentMessages
                        .filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                reportFailure(session, e, R.string.error_title_send_message)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    /** 只有通知在等时开的一轮：通知存成一个 user 节点，模型的回复是它的下一个节点 */
    private fun sendQueuedNotices(session: ConversationSession): Job {
        val conversationId = session.id
        session.replyMessageId.value = null
        val job = launchGenerationJob(conversationId = conversationId) {
            try {
                // 通知来自已经开始过的会话；会话刚被删除时不能重新创建
                session.initialize(
                    load = { loadConversation(conversationId) },
                    create = { throw NotFoundException("Conversation not found") },
                )
                // 加载前不知道是否在等审批；审批后执行完工具，这一轮会自己取走通知
                if (session.state.value.isAwaitingToolApproval) return@launchGenerationJob
                val notices = session.inbox.takeNotices()
                if (notices.isEmpty()) return@launchGenerationJob
                // 存成 user 节点：重新生成和分支都和普通消息一样
                session.update { it.copy(messageNodes = it.messageNodes + notices.toNoticeNode()) }
                session.flush()
                runTurn(session)
                _generationDoneFlow.emit(conversationId)
            } catch (e: NotFoundException) {
                Log.w(TAG, "sendQueuedNotices: conversation $conversationId not found, dropping notices")
                session.inbox.takeNotices()
            } catch (e: Exception) {
                reportFailure(session, e, R.string.error_title_generation)
            }
        }
        session.setJob(job)
        return job
    }

    private fun List<UIMessagePart.Notice>.toNoticeNode() =
        UIMessage(role = MessageRole.USER, parts = this).toMessageNode()

    // 发送、重新生成和审批失败时报告错误并暂停队列，排队的消息等用户处理完再继续
    private fun reportFailure(session: ConversationSession, error: Exception, @StringRes title: Int) {
        if (error is CancellationException) throw error
        session.inbox.pause()
        addGenerationError(error, session.id, title = context.getString(title))
    }

    // ---- 重新生成消息 ----

    /** 重新生成用户消息时去掉它之后的节点再回复；重新生成助手消息时在它的节点上新开一个分支 */
    fun regenerateAtMessage(conversationId: Uuid, messageId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()
            val job = launchGenerationJob(conversationId = conversationId) {
                try {
                    previousJob?.join()
                    session.replyMessageId.value = null
                    ensureInitialized(session)
                    val node = session.state.value.getMessageNodeByMessageId(messageId)
                        ?: return@launchGenerationJob

                    if (node.role == MessageRole.USER) {
                        session.tryUpdate { conversation ->
                            val index = conversation.messageNodes.indexOfFirst { it.id == node.id }
                            // 查找之后这条消息被删掉了
                            if (index == -1) return@tryUpdate null
                            conversation.copy(messageNodes = conversation.messageNodes.take(index + 1))
                        } ?: return@launchGenerationJob
                        session.flush()
                        runTurn(session)
                    } else {
                        runTurn(session, regenerateNodeId = node.id)
                    }

                    _generationDoneFlow.emit(conversationId)
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_regenerate_message)
                }
            }

            session.setJob(job)
        }
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val previousJob = session.getJob()

            val hasOtherPendingTools = session.state.value.currentMessages.any { message ->
                message.getTools().any { it.isPending && it.toolCallId != toolCallId }
            }

            val job = launchGenerationJob(
                conversationId = conversationId,
                keepAliveInBackground = !hasOtherPendingTools,
            ) {
                try {
                    afterPreviousGeneration(previousJob) {
                        ensureInitialized(session)
                        val newApprovalState = when {
                            answer != null -> ToolApprovalState.Answered(answer)
                            approved -> ToolApprovalState.Approved
                            else -> ToolApprovalState.Denied(reason)
                        }

                        val updated = session.tryUpdate { it.withToolApproval(toolCallId, newApprovalState) }
                            ?: return@afterPreviousGeneration
                        session.flush()

                        // Only continue generation when all pending tools are handled
                        if (!updated.isAwaitingToolApproval) {
                            runTurn(session)
                        }

                        _generationDoneFlow.emit(conversationId)
                    }
                } catch (e: Exception) {
                    reportFailure(session, e, R.string.error_title_tool_approval)
                }
            }

            session.setJob(job, cancelPrevious = false)
        }
    }

    // ---- 生成回复 ----

    /**
     * 生成一轮回复：请求模型、执行工具，直到模型不再调用工具、有工具需要审批或达到步数上限。
     *
     * 默认接着当前分支的末尾回复；[regenerateNodeId] 不为空时只发送这个节点之前的消息，回复作为它的新分支。
     * 发送的最后一条是助手消息时从它继续（比如审批之后），回复写回那条消息。
     * 失败时报告错误、暂停消息队列并返回 [TurnResult.Failed]；取消时直接抛出。
     */
    private suspend fun runTurn(session: ConversationSession, regenerateNodeId: Uuid? = null): TurnResult {
        if (session.state.value.isGroupChat()) return runGroupTurn(session, regenerateNodeId)
        val conversationId = session.id
        // 先清理上一轮状态；确定历史后，仅为真正的续写恢复消息 ID。
        session.replyMessageId.value = null
        // 生成开始前失败时通知只用来取消 Live Update，用不到发送者名称
        var senderName = ""
        try {
            val settings = settingsStore.awaitLoaded()
            // 清掉上一轮的建议；没法继续执行的工具调用发给模型会被拒绝，先去掉。
            // 重新生成时只清理要发给模型的节点，被重新生成的节点保持原样，旧回复留作分支
            val conversation = session.update { latest ->
                val untilNode = regenerateNodeId
                    ?.let { nodeId -> latest.messageNodes.indexOfFirst { it.id == nodeId } }
                    ?.takeIf { it != -1 }
                    ?: latest.messageNodes.size
                latest.copy(chatSuggestions = emptyList()).withoutUnresumableToolCalls(untilNode)
            }

            val nodes = conversation.messageNodes
            val history = if (regenerateNodeId != null) {
                val index = nodes.indexOfFirst { it.id == regenerateNodeId }
                check(index != -1) { "Message node $regenerateNodeId not found" }
                conversation.currentMessages.take(index)
            } else {
                conversation.currentMessages
            }
            val replyNodeId = when {
                history.lastOrNull()?.role == MessageRole.ASSISTANT -> nodes[history.lastIndex].id
                regenerateNodeId != null -> regenerateNodeId
                else -> Uuid.random()
            }
            // 接着上一条助手消息写时就是它，否则等第一次输出。要在 createContext 之前换掉，
            // 它会挂起也可能失败，期间或失败后不能还指着上一轮的回复
            session.replyMessageId.value = history.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }?.id

            val turn = requestFactory.createContext(settings, conversation)
            senderName = if (turn.assistant.useAssistantAvatar) {
                turn.assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
            } else {
                turn.model.displayName
            }
            warnIfToolsUnavailable(turn, conversationId)

            var finishReason: AgentFinishReason? = null
            try {
                val pulseContext = if (conversation.isPulseChat()) {
                    me.rerere.rikkahub.service.pulse.PulseToolContext { name, args, block ->
                        // Approval resumption and retries share the persisted reply node identity.
                        pulseEffects.execute("reply:$conversationId:$replyNodeId", name, args, block)
                    }
                } else kotlin.coroutines.EmptyCoroutineContext
                finishReason = withContext(pulseContext) { runAgentForSession(
                    session = session,
                    turn = turn,
                    history = history,
                    replyNodeId = replyNodeId,
                    senderName = senderName,
                    takePendingParts = session.inbox::takeNotices,
                ) }
            } finally {
                withContext(NonCancellable) {
                    session.processingStatus.value = null
                    // 可能被取消了，或者意外结束，兜底保存
                    val finished = session.finishGeneration()
                    // 生成结束：取消 Live Update 通知，后台时发送完成通知
                    if (finishReason != null) appEventBus.emit(
                        AppEvent.ChatGenerationEnded(
                            conversationId = conversationId,
                            senderName = senderName,
                            contentPreview = finished.currentMessages.lastOrNull()?.toText()?.take(50)?.trim().orEmpty(),
                            outcome = GenerationOutcome.SUCCESS,
                        ),
                    )
                }
            }

            val reason = checkNotNull(finishReason)
            // 等待审批时这一轮还没结束，标题、建议和记忆整理等审批之后再做
            if (reason !is AgentFinishReason.AwaitingApproval) {
                sessionManager.launchWithSession(conversationId) { generateTitle(conversationId) }
                sessionManager.launchWithSession(conversationId) { generateSuggestion(conversationId) }
                if (turn.assistant.enableMemory && turn.providerSetting !is ProviderSetting.DeepSeekWeb) {
                    memoryConsolidationScheduler.onTurnFinished(session.state.value)
                }
            }
            return TurnResult.Finished(reason)
        } catch (e: CancellationException) {
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null, GenerationOutcome.CANCELLED))
            throw e
        } catch (e: InvalidMcpServerNamesException) {
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(
                conversationId, senderName, null, GenerationOutcome.FAILED, e.message,
            ))
            session.inbox.pause()
            addError(
                error = IllegalStateException(
                    context.getString(R.string.error_mcp_invalid_server_name, e.names.joinToString(", "))
                ),
                conversationId = conversationId,
            )
            return TurnResult.Failed
        } catch (e: Exception) {
            // 兜底取消 Live Update 通知（生成开始前失败时上面的 finally 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(
                conversationId, senderName, null, GenerationOutcome.FAILED, e.message,
            ))
            session.inbox.pause()
            e.printStackTrace()
            addGenerationError(e, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "runTurn: $e")
            Logging.log(TAG, e.stackTraceToString())
            return TurnResult.Failed
        }
    }

    /** Shared collector: refuse pseudo calls before AgentLoop can execute any tool. */
    private suspend fun runAgentForSession(
        session: ConversationSession,
        turn: ChatTurnContext,
        history: List<UIMessage>,
        replyNodeId: Uuid,
        senderName: String,
        takePendingParts: suspend () -> List<UIMessagePart> = session.inbox::takeNotices,
        transform: (UIMessage) -> UIMessage = { it },
    ): AgentFinishReason {
        val (request, hooks) = requestFactory.createAgent(turn, history, session.processingStatus,
            finishTransform = transform, takePendingParts = takePendingParts)
        var reason: AgentFinishReason? = null
        runAgent(request, hooks).flowOn(Dispatchers.IO).collect { event ->
            when (event) {
                is AgentEvent.MessageUpdated -> {
                    session.processingStatus.value = null
                    val sanitized = if (turn.providerSetting is ProviderSetting.DeepSeekWeb)
                        DeepSeekWebOutputGuard.sanitize(listOf(event.message), turn.providerSetting.unlock) else null
                    val message = transform(sanitized?.messages?.single() ?: event.message)
                    session.update { it.upsertMessage(replyNodeId, message) }
                    appEventBus.tryEmit(AppEvent.ChatGenerationUpdate(session.id, message, senderName))
                    sanitized?.let { result ->
                        val match = result.matches.firstOrNull { it.blocked } ?: result.matches.first()
                        throw DeepSeekWebPseudoToolException(match.toolName, match.blocked)
                    }
                }
                is AgentEvent.Retrying -> {
                    logRetry(event.attempt)
                    session.processingStatus.value = retryStatus(event.attempt)
                }
                is AgentEvent.Finished -> reason = event.reason
            }
        }
        return checkNotNull(reason)
    }

    private suspend fun runGroupTurn(session: ConversationSession, regenerateNodeId: Uuid?): TurnResult {
        val conversationId = session.id
        var senderName = ""
        var outcome = GenerationOutcome.SUCCESS
        var failureMessage: String? = null
        try {
            var regenerateSender: UIMessageAnnotation.GroupSender? = null
            if (regenerateNodeId != null) {
                groupRounds.remove(conversationId)
                session.update { latest ->
                    val index = latest.messageNodes.indexOfFirst { it.id == regenerateNodeId }
                    check(index >= 0) { "Message node not found" }
                    regenerateSender = latest.messageNodes[index].currentMessage.annotations
                        .filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()
                    latest.copy(messageNodes = latest.messageNodes.take(index))
                }
                session.flush()
            }
            val round = groupRounds[conversationId] ?: run {
                val settings = settingsStore.awaitLoaded()
                val group = settings.groups.find { conversationId in it.conversationIds }
                    ?: error(context.getString(R.string.group_chat_missing))
                val members = group.speakingMembers(settings,
                    session.state.value.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty(),
                    deputyFirst = group.enableDeputyDispatch)
                check(members.isNotEmpty()) { context.getString(R.string.group_chat_no_enabled_members) }
                val pendingMessage = session.state.value.currentMessages.lastOrNull()?.takeIf { message ->
                    message.getTools().any { !it.isExecuted }
                }
                val sender = regenerateSender ?: pendingMessage?.annotations?.filterIsInstance<UIMessageAnnotation.GroupSender>()?.firstOrNull()
                val turns = members.map { member -> GroupTurn(member,
                    if (group.enableDeputyDispatch && member.id == group.deputyMemberId) GroupTurnKind.DISPATCH else GroupTurnKind.SPEAK) }
                val createdRound = GroupGenerationRound(settings, group, turns)
                // Rebuild the dynamic suffix after process recreation or regeneration using the
                // already-persisted dispatch reply. Exclude the pending reply being resumed.
                val history = session.state.value.currentMessages.let { if (pendingMessage != null) it.dropLast(1) else it }
                val currentHistory = history.drop(history.indexOfLast { it.role == MessageRole.USER } + 1)
                val dispatchText = currentHistory.completedDispatchText(group.deputyMemberId, sender?.memberId)
                val previousDispatch = group.resolveDispatch(settings, dispatchText)
                if (previousDispatch.matched) createdRound.dispatch(previousDispatch.members)
                val resumeIndex = sender?.let { marker -> createdRound.turns.indexOfLast { it.member.id.toString() == marker.memberId } }
                check(resumeIndex == null || resumeIndex >= 0) { context.getString(R.string.group_chat_missing) }
                createdRound.also {
                    it.index = resumeIndex ?: 0
                    it.resuming = pendingMessage != null
                    groupRounds[conversationId] = it
                }
            }
            val done = round.run { turn, resuming ->
                val member = turn.member
                currentCoroutineContext().ensureActive()
                val groupSenderName = round.group.displayNameOf(member)
                    .ifBlank { context.getString(R.string.group_member_default_name) }
                val groupSender = member.copy(name = groupSenderName)
                senderName = groupSenderName
                val conversation = session.state.value
                val ownerName = round.settings.displaySetting.userNickname.ifBlank { context.getString(R.string.group_owner_name) }
                val availableQuotes = round.availableQuotes(round.index) {
                    val history = conversation.currentMessages
                    (if (resuming) history.dropLast(1) else history).availableGroupQuotes(ownerName)
                }
                val framePrompt = context.getString(
                    R.string.group_chat_frame_prompt, round.group.name, senderName,
                    round.members.filter { it.id != member.id }.joinToString(", ") { participant ->
                        round.group.displayNameOf(participant).ifBlank { context.getString(R.string.group_member_default_name) }
                    },
                ) + "\n\n" + round.members.joinToString("\n") { participant ->
                    val tags = round.settings.groupTags.filter { it.id in participant.tags }.joinToString(", ") { it.name }
                    "- [$tags] ${round.group.displayNameOf(participant)}"
                } + when (turn.kind) {
                    GroupTurnKind.DISPATCH -> "\n\n" + context.getString(R.string.group_chat_deputy_dispatch_prompt)
                    GroupTurnKind.SUMMARY -> "\n\n" + context.getString(R.string.group_chat_deputy_summary_prompt)
                    GroupTurnKind.SPEAK -> if (member.id == round.group.deputyMemberId) "\n\n" + context.getString(R.string.group_chat_deputy_prompt) else ""
                } +
                    "\n\n" + context.getString(R.string.group_chat_quote_hint) + "\n" + availableQuotes.joinToString("\n") {
                        "- [msg:${it.targetMessageId}] ${it.targetSenderName}:${it.preview}"
                    }
                val assistant = member.toAssistant(round.group, framePrompt)
                val requestTurn = requestFactory.createContext(
                    settings = round.settings,
                    conversation = conversation,
                    assistantOverride = assistant,
                    memoryScope = "grp:" + round.group.id + ":" + conversationId,
                )
                val firstResponseIndex = if (resuming) conversation.currentMessages.lastIndex else conversation.currentMessages.size
                val turnResponseIndex = round.responseStart(round.index) {
                    if (!resuming) firstResponseIndex else {
                        val lastOther = conversation.currentMessages.indexOfLast { message ->
                            message.role != MessageRole.ASSISTANT || message.annotations
                                .filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()?.memberId != member.id.toString()
                        }
                        lastOther + 1
                    }
                }
                // A fresh member must start a new reply, not continue the preceding member's message.
                val replyNodeId = if (resuming) conversation.messageNodes.last().id else Uuid.random()
                if (!resuming) {
                    val response = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList(), modelId = requestTurn.model.id)
                        .withGroupSender(groupSender, groupSenderName)
                    session.update { it.upsertMessage(replyNodeId, response) }
                }
                val reason = runAgentForSession(session, requestTurn, session.state.value.currentMessages,
                    replyNodeId, senderName) { message ->
                    message.withGroupSender(groupSender, groupSenderName)
                        .stripSelfSpeakerPrefix(groupSenderName).applyGroupQuotes(availableQuotes)
                }
                session.finishGeneration()
                val completed = reason == AgentFinishReason.Completed
                if (completed && turn.kind == GroupTurnKind.DISPATCH) {
                    val text = session.state.value.currentMessages.drop(turnResponseIndex)
                        .filter { it.role == MessageRole.ASSISTANT }.joinToString("\n") { it.toText() }
                    val dispatch = round.group.resolveDispatch(round.settings, text)
                    if (dispatch.matched) round.dispatch(dispatch.members)
                }
                if (reason == AgentFinishReason.StepLimitReached) error("Group turn reached the agent step limit")
                completed
            }
            if (done) {
                groupRounds.remove(conversationId)
                _completedGroupRounds.update { it + (conversationId to ((it[conversationId] ?: 0) + 1)) }
            }
            return TurnResult.Finished(if (done) AgentFinishReason.Completed else AgentFinishReason.AwaitingApproval(
                session.state.value.currentMessages.flatMap { it.getTools() }.filter { it.isPending }
                    .mapTo(linkedSetOf()) { it.toolCallId }
            ))
        } catch (error: CancellationException) {
            outcome = GenerationOutcome.CANCELLED
            groupRounds.remove(conversationId)
            throw error
        } catch (error: Exception) {
            outcome = GenerationOutcome.FAILED
            failureMessage = error.message
            groupRounds.remove(conversationId)
            session.inbox.pause()
            addGenerationError(error, conversationId, context.getString(R.string.error_title_generation))
            return TurnResult.Failed
        } finally {
            withContext(NonCancellable) {
                session.processingStatus.value = null
                val finished = session.finishGeneration()
                appEventBus.tryEmit(AppEvent.ChatGenerationEnded(
                    conversationId,
                    senderName,
                    finished.currentMessages.lastOrNull()?.toText()?.take(50),
                    outcome,
                    failureMessage,
                ))
            }
        }
    }

    // 模型不支持工具调用时，搜索和 MCP 工具都不会生效，提醒一下
    private fun warnIfToolsUnavailable(turn: ChatTurnContext, conversationId: Uuid) {
        if (ModelAbility.TOOL in turn.model.abilities) return
        if (shouldUseExternalWebSearch(turn.assistant, turn.model) ||
            mcpManager.getAllAvailableTools(turn.assistant).isNotEmpty()
        ) {
            addError(
                IllegalStateException(context.getString(R.string.tools_warning)),
                conversationId,
                title = context.getString(R.string.error_title_tool_unavailable)
            )
        }
    }

    private fun logRetry(attempt: RetryAttempt) {
        Log.w(
            TAG,
            "Provider connection failed, retrying in ${attempt.delay} (${attempt.retry}/${attempt.maxRetries})",
            attempt.cause,
        )
    }

    private fun retryStatus(attempt: RetryAttempt): String {
        val reason = when (attempt.cause) {
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            else -> R.string.chat_generation_network_disconnected
        }
        return context.getString(
            R.string.chat_generation_network_retrying,
            context.getString(reason),
            attempt.retry,
            attempt.maxRetries,
        )
    }

    private suspend fun finishInterruptedPendingTools(session: ConversationSession) {
        session.tryUpdate { it.cancelInterruptedTools() }
        session.flush()
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        force: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        withLoadedSession<Unit>(conversationId) { session ->
            val incarnation = session.incarnation
            val conversation = session.state.value
            if (conversation.isGroupChat()) return@withLoadedSession
            if (!force && conversation.title.isNotBlank()) return@withLoadedSession

            val settings = settingsStore.awaitLoaded()
            // 关掉自动生成后只跳过对话结束时的自动调用，手动重新生成照常执行
            if (!force && !settings.enableAutoTitle) return@withLoadedSession

            runCatching {
                val title = metaGenerator.generateTitle(settings, conversation)
                // 只写标题这一列：这时可能已经在生成下一轮，不能把还没生成完的内容一起落库。
                // 生成期间会话被删除的话不写入
                session.updateMetadata(
                    transform = { it.copy(title = title) },
                    persist = { conversationRepo.updateTitle(conversationId, title) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
                addError(
                    error = it,
                    conversationId = conversationId,
                    title = context.getString(R.string.error_title_generate_title),
                    solution = ChatErrorSolution.CheckFastModelSettings,
                )
            }
        }
    }

    // ---- 生成建议 ----

    private suspend fun generateSuggestion(conversationId: Uuid): Unit = withContext(Dispatchers.IO) {
        sessionManager.withSession<Unit>(conversationId) { session ->
            runCatching {
                val settings = settingsStore.awaitLoaded()
                if (!settings.enableSuggestion) return@runCatching

                // 上一轮的建议在这一轮开始时已经清掉了
                ensureInitialized(session)
                if (session.state.value.isGroupChat()) return@runCatching
                val incarnation = session.incarnation
                val suggestions = metaGenerator.generateSuggestions(settings, session.state.value)
                    ?: return@runCatching

                // 只写建议这一列，理由同标题
                session.updateMetadata(
                    transform = { it.copy(chatSuggestions = suggestions) },
                    persist = { conversationRepo.updateChatSuggestions(conversationId, suggestions) },
                    expectedIncarnation = incarnation,
                )
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    // ---- 压缩对话历史 ----

    /**
     * 把上一个检查点之后的消息压缩成摘要检查点。
     *
     * [keepRecentMessages] 为 0 时压缩全部消息，请求和上一轮对话前缀一致，缓存命中最好；
     * 大于 0 时最近的这些消息不参与压缩、留在检查点之后，对话已经超出模型上下文、整段发不出去时用来缩小压缩请求。
     */
    suspend fun compressConversation(
        conversationId: Uuid,
        additionalPrompt: String,
        keepRecentMessages: Int = 0,
    ): Result<Unit> = runCatching {
        withLoadedSession<Unit>(conversationId) { session ->
            // 生成中的回复还没写完，压缩不到它，检查点也不能插到它前面
            synchronized(session) {
                check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }
                session.isCompressing = true
            }
            try {
                val conversation = session.state.value
                val nodes = conversation.messageNodes
                val checkpointIndex = nodes.indexOfLast { it.currentMessage.isContextCheckpoint }
                // 压缩范围是 (checkpointIndex, endIndex)，之后的节点原样保留
                val endIndex = nodes.size - keepRecentMessages.coerceAtLeast(0)
                // 上一个检查点之后没有可压缩的消息
                if (endIndex <= checkpointIndex + 1) {
                    throw IllegalStateException(
                        context.getString(
                            if (keepRecentMessages > 0) {
                                R.string.chat_page_compress_not_enough_messages
                            } else {
                                R.string.chat_page_compress_nothing_to_compress
                            }
                        )
                    )
                }
                // 未完成的工具调用没有结果，请求会被服务端拒绝；压到检查点之前也再不会执行。
                // 保留区里的不发给压缩请求，压缩后照常留在检查点之后，不用检查
                check(nodes.subList(checkpointIndex + 1, endIndex).none { node ->
                    node.currentMessage.getTools().any { it.isPending || it.canResumeExecution }
                }) { context.getString(R.string.chat_page_compress_handle_pending_tools) }

                // 必须和对话用同一个模型、同一套请求组装，才能命中提示词缓存
                val turn = requestFactory.createContext(settingsStore.awaitLoaded(), conversation)
                val summary = requestFactory.generateCompressionSummary(
                    turn = turn,
                    messages = conversation.currentMessages.take(endIndex),
                    compressPrompt = buildCompressPrompt(
                        additionalPrompt = additionalPrompt,
                        locale = Locale.getDefault().displayName,
                    ),
                )

                // 原消息原样保留，摘要插在压缩范围的最后一条消息之后。摘要生成期间对话可能已变化，
                // 因此按节点定位插入到最新状态。
                session.update { latest ->
                    check(!session.isGenerating) { context.getString(R.string.chat_page_compress_blocked_generating) }
                    val updated = latest.insertContextCheckpoint(
                        afterNodeId = nodes[endIndex - 1].id,
                        summary = summary,
                    ) ?: throw IllegalStateException(
                        context.getString(R.string.chat_page_compress_conversation_changed)
                    )
                    updated.copy(chatSuggestions = emptyList())
                }
                session.flush()
            } finally {
                // 补上压缩期间收到的通知和消息
                session.isCompressing = false
                dispatchInbox(conversationId)
            }
        }
    }

    /** Group compression uses the live session and adds a checkpoint, retaining every original node. */
    suspend fun compressGroupConversation(
        conversationId: Uuid,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32,
        messageFormatter: (UIMessage) -> String,
        tieredBuilder: ((List<UIMessage>) -> String)? = null,
    ): Result<Unit> = runCatching {
        withLoadedSession<Unit>(conversationId) { session ->
            val reservation = checkNotNull(currentCoroutineContext()[Job])
            val conversation = synchronized(session) {
                check(!session.isGenerating && session.state.value.isGroupChat() &&
                    !session.state.value.isAwaitingToolApproval) { context.getString(R.string.group_compress_busy) }
                session.setJob(reservation, cancelPrevious = false)
                session.state.value
            }
            val incarnation = session.incarnation
            val nodes = conversation.messageNodes
            val checkpointIndex = nodes.indexOfLast { it.currentMessage.isContextCheckpoint }
            val endIndex = nodes.size - keepRecentMessages.coerceAtLeast(0)
            check(endIndex > checkpointIndex + 1) {
                context.getString(R.string.chat_page_compress_not_enough_messages)
            }
            val messages = conversation.currentMessages.subList(checkpointIndex.coerceAtLeast(0), endIndex)
            check(messages.none { message -> message.getTools().any { it.isPending || it.canResumeExecution } }) {
                context.getString(R.string.chat_page_compress_handle_pending_tools)
            }
            val settings = settingsStore.awaitLoaded()
            val prompt = tieredBuilder?.invoke(messages) ?: settings.compressPrompt.applyPlaceholders(
                "content" to messages.joinToString("\n\n", transform = messageFormatter),
                "target_tokens" to targetTokens.coerceAtLeast(1).toString(),
                "additional_context" to additionalPrompt,
                "locale" to Locale.getDefault().displayName,
            )
            val summary = requestFactory.generateGroupCompressionSummary(settings, conversationId, prompt)
            session.update { latest ->
                check(session.incarnation == incarnation && latest.messageNodes == nodes) {
                    context.getString(R.string.chat_page_compress_conversation_changed)
                }
                checkNotNull(latest.insertContextCheckpoint(nodes[endIndex - 1].id, summary))
                    .copy(chatSuggestions = emptyList())
            }
            session.flush()
        }
    }

    // ---- 聊天页配置 ----

    /**
     * 聊天页对助手的修改：会话持有的字段只改当前会话，其余写回助手设置。
     *
     * @param update 接收会话视角下的助手，返回修改后的助手
     */
    suspend fun updateChatAssistant(conversationId: Uuid, update: (Assistant) -> Assistant) {
        withLoadedSession(conversationId) { session ->
            val settings = settingsStore.awaitLoaded()
            val conversation = session.state.value
            val stored = settings.getStoredAssistantOf(conversation)
            if (conversation.isPulseChat()) {
                settingsStore.update { latest ->
                    val updated = update(latest.pulse.toAssistant(latest))
                    latest.copy(pulse = latest.pulse.withAssistantUpdate(updated))
                }
                return@withLoadedSession
            }
            val updated = update(settings.getAssistantOf(conversation))
            val assistant = updated.withoutConversationFields(conversation, stored)
            session.updateMetadata(
                transform = { it.withAssistantUpdate(updated, settings) },
                persist = conversationRepo::updateConversationConfig,
            )
            if (assistant != stored) {
                settingsStore.update { latest ->
                    latest.copy(assistants = latest.assistants.map { if (it.id == assistant.id) assistant else it })
                }
            }
        }
    }

    /**
     * 切换搜索方式，传 null 的一项保持不变。
     * 模型内置搜索在会话开始前是模型自身的开关，开始后固定在会话上。
     */
    suspend fun updateChatSearch(
        conversationId: Uuid,
        enableWebSearch: Boolean? = null,
        builtInSearch: Boolean? = null,
    ) {
        withLoadedSession(conversationId) { session ->
            val conversation = session.state.value
            if (conversation.isPulseChat()) {
                settingsStore.update { current -> current.copy(pulse = current.pulse.copy(
                    enableWebSearch = enableWebSearch ?: current.pulse.enableWebSearch,
                )) }
                return@withLoadedSession
            }
            if (conversation.config != null) {
                session.updateMetadata(
                    transform = {
                        it.copy(
                            config = it.config?.let { config ->
                                config.copy(
                                    enableWebSearch = enableWebSearch ?: config.enableWebSearch,
                                    builtInSearch = builtInSearch ?: config.builtInSearch,
                                )
                            }
                        )
                    },
                    persist = conversationRepo::updateConversationConfig,
                )
                return@withLoadedSession
            }
            settingsStore.update { settings ->
                val assistant = settings.getAssistantOf(conversation)
                val model = settings.getChatModelOf(conversation)
                settings.copy(
                    assistants = if (enableWebSearch == null) {
                        settings.assistants
                    } else {
                        settings.assistants.map {
                            if (it.id == assistant.id) it.copy(enableWebSearch = enableWebSearch) else it
                        }
                    },
                    providers = if (builtInSearch == null || model == null) {
                        settings.providers
                    } else {
                        settings.providers.map { provider ->
                            provider.editModel(
                                model.copy(
                                    tools = if (builtInSearch) {
                                        model.tools + BuiltInTools.Search
                                    } else {
                                        model.tools - BuiltInTools.Search
                                    }
                                )
                            )
                        }
                    },
                )
            }
        }
    }

    // 工作目录和配置一起按列落库；还没落过库的新会话先留在内存里，发出第一条消息时一起写入
    suspend fun updateWorkspaceCwd(conversationId: Uuid, workspaceCwd: String?) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(
                transform = { it.copy(workspaceCwd = workspaceCwd) },
                persist = conversationRepo::updateConversationConfig,
            )
        }
    }

    suspend fun updateConversationSystemPrompt(conversationId: Uuid, systemPrompt: String?) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(customSystemPrompt = systemPrompt) },
            persist = { conversationRepo.updateCustomSystemPrompt(conversationId, systemPrompt) },
        )
    }

    suspend fun updateTitle(conversationId: Uuid, title: String) {
        updateColumn(
            conversationId = conversationId,
            transform = { it.copy(title = title) },
            persist = { conversationRepo.updateTitle(conversationId, title) },
        )
    }

    /**
     * 用户在聊天页改的单列字段：已落库的会话只写这一列，生成中也不会顺带写入还没生成完的回复；
     * 还没落过库的新会话整份落库，留下记录（没有内容的新会话仍然不写）。
     */
    private suspend fun updateColumn(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend () -> Unit,
    ) {
        withLoadedSession(conversationId) { session ->
            session.updateMetadata(transform, persist = { persist() })
            if (!session.existsInDatabase) session.flush()
        }
    }

    // ---- 会话元数据 ----

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        transform: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        withLoadedSession(conversationId, createIfMissing = false) { session ->
            session.updateMetadata(transform, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            transform = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     * 有 session 的会话先改内存，免得之后整份落库时写回旧的文件夹；没有的只改这一列，不必加载整个会话。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) == null) {
            conversationRepo.updateConversationFolderId(conversationId, folderId)
            // 写库期间页面可能刚好打开了这个会话，加载时读到的可能是旧值，再同步一次内存
            if (sessionManager.get(conversationId) == null) return
        }
        updateConversationMetadata(
            conversationId = conversationId,
            transform = { it.copy(folderId = folderId) },
            persist = { conversationRepo.updateConversationFolderId(conversationId, folderId) },
        )
    }

    /**
     * 还没开始的新会话跟随抽屉里选中的文件夹，首次落库时归入；已经落库的会话不受影响，移动走 [moveConversationToFolder]。
     */
    suspend fun updateDraftConversationFolder(conversationId: Uuid, folderId: Uuid?) {
        val folder = folderId?.let { folderRepository.getFolderById(it) }
        withLoadedSession(conversationId) { session ->
            session.updateDraft { latest ->
                // 文件夹属于助手：已被删除或不属于会话的助手时不归入
                val target = folder?.takeIf { it.assistantId == latest.assistantId }?.id
                if (latest.folderId == target) latest else latest.copy(folderId = target)
            }
        }
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     * 已加载的会话先改内存再删库，之后落库不会写回已删除的文件夹。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.folderId == folderId }
            .forEach { session ->
                // 这一列由下面删文件夹时统一清空，这里只改内存，不让会话因此变成有未落库的修改
                session.updateMetadata(
                    transform = { if (it.folderId == folderId) it.copy(folderId = null) else it },
                    persist = {},
                )
            }
        folderRepository.deleteFolder(folderId)
    }

    // ---- 删除会话 ----

    /**
     * 删除会话：停掉生成后在会话的写锁内删库，内存换成一个还没落过库的新会话。
     * 还停在这个会话上的页面看到的是空会话，之后再发消息会新建记录，不会写回已删除的那条。
     */
    suspend fun deleteConversation(conversationId: Uuid) {
        groupRounds.remove(conversationId)
        val session = sessionManager.getOrCreate(conversationId)
        discardQueuedMessages(session)
        session.replace(newConversation(conversationId), existsInDatabase = false) { previous ->
            conversationRepo.getConversationById(conversationId)?.let { conversationRepo.deleteConversation(it) }
            // 数据库那份不知道只在内存里出现过的附件
            previous?.let { filesManager.deleteChatFiles(it.files) }
        }
        sessionManager.removeIfIdle(session)
        // 后台命令随会话一起终止, 撤销删除也不会恢复; 终止要等宽限期, 不阻塞删除
        appScope.launch { backgroundShellManager.killConversation(conversationId) }
    }

    suspend fun deleteConversationsOfAssistant(assistantId: Uuid) {
        val persisted = conversationRepo.getConversationsOfAssistant(assistantId).first().map { it.id }
        val loaded = sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.assistantId == assistantId }
            .map { it.id }
        (persisted + loaded).distinct().forEach { deleteConversation(it) }
    }

    private fun discardQueuedMessages(session: ConversationSession) {
        session.inbox.pause()
        session.inbox.state.value.messages.forEach { queued ->
            (session.inbox.remove(queued.id) as? QueuedMessage)?.let(::cleanupQueuedAttachments)
        }
        session.inbox.takeNotices()
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            sessionManager.withSession<Unit>(conversationId) { session ->
                try {
                    ensureInitialized(session)
                    val settings = settingsStore.awaitLoaded()

                    val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                        .joinToString("\n\n") { it.text }
                        .trim()

                    if (messageText.isBlank()) return@withSession

                    // Set loading state for translation
                    val loadingText = context.getString(R.string.translating)
                    session.update { it.withTranslation(message.id, loadingText) }

                    translationHandler.translateText(
                        settings = settings,
                        sourceText = messageText,
                        targetLanguage = targetLanguage
                    ) { translatedText ->
                        // Update translation field in real-time
                        session.update { it.withTranslation(message.id, translatedText) }
                    }.collect { /* Final translation already handled in onStreamUpdate */ }

                    session.flush()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Clear translation field on error
                    if (session.isInitialized) session.update { it.withTranslation(message.id, null) }
                    addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
                }
            }
        }
    }

    suspend fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        mutate(conversationId) { it.withTranslation(messageId, null) }
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return
        val settings = settingsStore.awaitLoaded()

        mutate(conversationId) { conversation ->
            conversation.editMessage(messageId, parts.replaceUserRegexes(settings.getStoredAssistantOf(conversation)))
        }
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val source = withLoadedSession(conversationId) { it.state.value }
        val existingTitles = conversationRepo.getTitlesOfAssistant(source.assistantId)
        val fork = source.fork(messageId, existingTitles, copyFile = ::copyChatFile)
            ?: throw NotFoundException("Message not found")
        // 新会话直接落库，打开时再从数据库加载
        val forkConversation = conversationStore.prepareWrite()(fork)
        conversationRepo.insertConversation(forkConversation)
        if (!forkConversation.isGroupChat()) memoryConsolidationScheduler.onConversationForked(forkConversation)
        return forkConversation
    }

    internal fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart = mapLocalFileUrls(::copyChatFile)

    private fun copyChatFile(url: String): String =
        filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()?.toString() ?: url

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        mutate(conversationId) { conversation ->
            val targetNode = conversation.messageNodes.firstOrNull { it.id == nodeId }
                ?: throw NotFoundException("Message node not found")
            if (selectIndex !in targetNode.messages.indices) {
                throw BadRequestException("Invalid selectIndex")
            }
            if (targetNode.selectIndex == selectIndex) return@mutate conversation
            conversation.selectBranch(nodeId, selectIndex)
        }
    }

    // 收藏记录在单独的表里，节点上的标记只用于显示，不需要落库
    suspend fun setNodeFavorite(conversationId: Uuid, nodeId: Uuid, favorite: Boolean) {
        val session = sessionManager.get(conversationId)?.takeIf { it.isInitialized } ?: return
        session.updateMetadata(transform = { it.withNodeFavorite(nodeId, favorite) }, persist = {})
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        mutate(conversationId) { conversation ->
            conversation.deleteMessage(messageId)
                ?: if (failIfMissing) throw NotFoundException("Message not found") else conversation
        }
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        groupRounds.remove(conversationId)
        val session = sessionManager.get(conversationId) ?: return
        val jobs = synchronized(session) {
            session.inbox.pause()
            session.cancelJobs()
        }
        if (jobs.isEmpty()) return
        jobs.forEach { it.join() }
        if (session.isInitialized) finishInterruptedPendingTools(session)
    }
}
