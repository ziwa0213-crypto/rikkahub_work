package me.rerere.rikkahub.ui.pages.chat

import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.displayNameOf
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.MessageQueueState
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.ui.pages.group.GroupChatContent
import me.rerere.rikkahub.ui.pages.group.LocalGroupChatContext
import me.rerere.rikkahub.ui.pages.group.rememberGroupVM
import me.rerere.rikkahub.data.model.compressionThreshold
import me.rerere.rikkahub.ui.pages.group.selectedGroup
import me.rerere.rikkahub.ui.hooks.insertMention
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.ui.components.ai.completion.GroupMentionCompletionProvider
import me.rerere.rikkahub.ui.components.ai.LiquidGlassChatHost
import me.rerere.rikkahub.ui.components.ai.LiquidGlassCircuitBreaker
import me.rerere.rikkahub.ui.components.ai.LiquidGlassInputBounds
import me.rerere.rikkahub.ui.components.ai.shouldUseNativeLiquidGlass
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(setting.uiMode) {
        if (setting.uiMode == UiMode.GROUP) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        onGroupSelected = { scope.launch { drawerState.close() } },
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        onGroupSelected = { scope.launch { drawerState.close() } },
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val resources = LocalResources.current
    val groupVm = rememberGroupVM()
    val selectedGroupId by groupVm.selectedGroupId.collectAsStateWithLifecycle()
    val group = setting.selectedGroup(selectedGroupId)
    var groupBlocked by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val liquidUnavailable by LiquidGlassCircuitBreaker.unavailable.collectAsStateWithLifecycle()
    val useLiquidHost = shouldUseNativeLiquidGlass(
        apiLevel = Build.VERSION.SDK_INT,
        setting = setting.displaySetting,
        unavailable = liquidUnavailable,
    )
    var inputPanelBounds by remember { mutableStateOf<LiquidGlassInputBounds?>(null) }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val assistant = setting.getCurrentAssistant()
    val activeInputState = if (setting.uiMode == UiMode.GROUP) groupVm.inputState else inputState
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = activeInputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        setting.getCurrentChatModel()?.findProvider(setting.providers) is ProviderSetting.Google

    val groupWorkspaceConversation by groupVm.conversation.collectAsStateWithLifecycle()
    val inputWorkspaceId = if (setting.uiMode == UiMode.GROUP) group?.workspaceId else assistant.workspaceId
    val inputWorkspaceCwd = if (setting.uiMode == UiMode.GROUP) groupWorkspaceConversation?.workspaceCwd else conversation.workspaceCwd
    val completionProviders = remember(inputWorkspaceId, inputWorkspaceCwd, workspaceRepository) {
        inputWorkspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = inputWorkspaceCwd,
                )
            )
        }.orEmpty()
    }

    if (setting.uiMode == UiMode.CHAT) TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    val groupLoadingJob by groupVm.conversationJob.collectAsStateWithLifecycle()
    val groupMessageQueue by groupVm.messageQueue.collectAsStateWithLifecycle()
    val activeGroupConversationId by groupVm.selectedConversationId.collectAsStateWithLifecycle()
    var quoteTarget by remember(setting.uiMode, group?.id, activeGroupConversationId) {
        mutableStateOf<UIMessageAnnotation.GroupQuote?>(null)
    }
    val groupInputFocusRequester = remember { FocusRequester() }
    val groupCompletionProviders = remember(group, setting.groupMembers) {
        listOf(GroupMentionCompletionProvider(group?.members.orEmpty().mapNotNull { ref ->
            setting.groupMembers.find { it.id == ref.memberId }?.let { member ->
                member.copy(name = group?.displayNameOf(member) ?: member.name)
            }
        }))
    }

    fun groupSend(answer: Boolean = true) {
        if (group == null) groupBlocked = true
        else {
            if (group.members.none { it.enabled && setting.groupMembers.any { member -> member.id == it.memberId } }) {
                toaster.show(resources.getString(R.string.group_chat_no_enabled_members), type = ToastType.Error)
                return
            }
            if (groupVm.inputState.isEditing()) groupVm.editMessage(groupVm.inputState.getContents(), groupVm.inputState.editingMessage!!)
            else groupVm.sendMessage(group.id, groupVm.inputState.getContents(), answer, quoteTarget)
            groupVm.inputState.clearInput()
            quoteTarget = null
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun groupOperationUnavailable() {
        toaster.show(resources.getString(R.string.group_chat_notice), type = ToastType.Info)
    }

    val inputContent: @Composable (UiMode, Boolean) -> Unit = { mode, drawLiquidGlass ->
        val inputState = if (mode == UiMode.GROUP) groupVm.inputState else inputState
        val isGroupMode = mode == UiMode.GROUP
        val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
        val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
        ChatInput(
            onStartVoiceMode = { if (isGroupMode) groupOperationUnavailable() else onStartVoiceMode() },
            voiceState = voiceState,
            onStopVoiceMode = vm.voiceSession::stop,
            state = inputState,
            messageQueue = if (isGroupMode) groupMessageQueue else messageQueue,
            onRemoveQueuedMessage = { if (isGroupMode) groupVm.removeQueuedMessage(it) else vm.removeQueuedMessage(it) },
            onBeginEditQueuedMessage = { if (isGroupMode) groupVm.beginEditQueuedMessage(it) else vm.beginEditQueuedMessage(it) },
            onFinishEditQueuedMessage = { id, parts -> if (isGroupMode) groupVm.finishEditQueuedMessage(id, parts) else vm.finishEditQueuedMessage(id, parts) },
            onResumeMessageQueue = { if (isGroupMode) groupVm.resumeMessageQueue() else vm.resumeMessageQueue() },
            loading = if (isGroupMode) groupLoadingJob != null else loadingJob != null,
            settings = setting,
            hazeState = hazeState,
            completionProviders = if (isGroupMode) groupCompletionProviders + completionProviders else completionProviders,
            onCancelClick = { if (isGroupMode) groupVm.stopGeneration() else vm.stopGeneration() },
            enableSearch = enableWebSearch,
            onUpdateSearchMode = { mode ->
                val current = setting.getCurrentAssistant()
                val model = setting.getCurrentChatModel()
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { currentAssistant ->
                            if (currentAssistant.id == current.id) {
                                currentAssistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
                            } else currentAssistant
                        },
                        providers = if (model == null) setting.providers else setting.providers.map { provider ->
                            provider.editModel(
                                model.copy(
                                    tools = if (mode == SearchMode.BUILT_IN) {
                                        model.tools + BuiltInTools.Search
                                    } else model.tools - BuiltInTools.Search
                                )
                            )
                        },
                    )
                )
            },
            onSendClick = {
                if (isGroupMode) {
                    groupSend()
                    return@ChatInput
                }
                if (currentChatModel == null) {
                    toaster.show("请先选择模型", type = ToastType.Error)
                    return@ChatInput
                }
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                } else {
                    vm.handleMessageSend(inputState.getContents())
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                    }
                }
                inputState.clearInput()
            },
            onLongSendClick = {
                if (isGroupMode) {
                    groupSend(answer = false)
                    return@ChatInput
                }
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                } else {
                    vm.handleMessageSend(inputState.getContents(), answer = false)
                    scope.launch { chatListState.requestScrollToItem(conversation.currentMessages.size + 5) }
                }
                inputState.clearInput()
            },
            onUpdateChatModel = { vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it) },
            onUpdateAssistant = { updated ->
                vm.updateSettings(
                    setting.copy(assistants = setting.assistants.map { if (it.id == updated.id) updated else it })
                )
            },
            onUpdateSearchService = { index -> vm.updateSettings(setting.copy(searchServiceSelected = index)) },
            onMoreClick = { showFilesSheet = true },
            modifier = Modifier.fillMaxWidth(),
            forceLiquidGlass = drawLiquidGlass,
            onInputPanelBoundsChanged = if (drawLiquidGlass) {
                { x, y, size ->
                    if (mode == setting.uiMode) inputPanelBounds = LiquidGlassInputBounds(x, y, size.width, size.height)
                }
            } else null,
            onInputHeightChanged = { if (mode == setting.uiMode) inputHeightPx = it },
            groupMode = isGroupMode,
            reasoningLevelOverride = if (isGroupMode) group?.reasoningLevel else null,
            onUpdateReasoningLevelOverride = if (isGroupMode) { { level ->
                setting.selectedGroup(groupVm.selectedGroupId.value)?.let { currentGroup ->
                    groupVm.updateGroup(currentGroup.id) { it.copy(reasoningLevel = level) }
                }
            } } else null,
            toolApprovalModeOverride = if (isGroupMode) group?.toolApprovalMode else null,
            onUpdateToolApprovalModeOverride = if (isGroupMode) { { approvalMode ->
                setting.selectedGroup(groupVm.selectedGroupId.value)?.let { currentGroup ->
                    groupVm.updateGroup(currentGroup.id) { it.copy(toolApprovalMode = approvalMode) }
                }
            } } else null,
            quoteTarget = if (isGroupMode && !inputState.isEditing()) quoteTarget else null,
            onClearQuote = { quoteTarget = null },
            inputFocusRequester = if (isGroupMode) groupInputFocusRequester else null,
        )
    }

    val chatContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
            Scaffold(
                topBar = {
                    TopBar(
                        settings = setting,
                        conversation = conversation,
                        bigScreen = bigScreen,
                        drawerState = drawerState,
                        previewMode = previewMode,
                        onNewChat = {
                            navigateToChatPage(navController)
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onUpdateTitle = {
                            vm.updateTitle(it)
                        }
                    )
                },
                bottomBar = {
                    if (!useLiquidHost) {
                        inputContent(UiMode.CHAT, false)
                    }
                },
                containerColor = Color.Transparent,
            ) { innerPadding ->
                ChatList(
                    innerPadding = if (useLiquidHost) {
                        PaddingValues(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = maxOf(
                                innerPadding.calculateBottomPadding(),
                                with(density) { inputHeightPx.toDp() },
                            ),
                        )
                    } else innerPadding,
                    conversation = conversation,
                    state = chatListState,
                    loading = loadingJob != null,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    settings = setting,
                    hazeState = hazeState,
                    errors = errors,
                    onDismissError = onDismissError,
                    onClearAllErrors = onClearAllErrors,
                    onRegenerate = {
                        vm.regenerateAtMessage(it)
                    },
                    onEdit = {
                        inputState.editingMessage = it.id
                        inputState.setContents(it.parts)
                    },
                    onForkMessage = {
                        scope.launch {
                            val fork = vm.forkMessage(message = it)
                            navigateToChatPage(navController, chatId = fork.id)
                        }
                    },
                    onDelete = {
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(it)
                        }
                    },
                    onUpdateMessage = { newNode ->
                        vm.updateConversation(
                            conversation.copy(
                                messageNodes = conversation.messageNodes.map { node ->
                                    if (node.id == newNode.id) {
                                        newNode
                                    } else {
                                        node
                                    }
                                }
                            ))
                        vm.saveConversationAsync()
                    },
                    onClickSuggestion = { suggestion ->
                        inputState.editingMessage = null
                        inputState.setMessageText(suggestion)
                    },
                    onTranslate = { message, locale ->
                        vm.translateMessage(message, locale)
                    },
                    onClearTranslation = { message ->
                        vm.clearTranslationField(message.id)
                    },
                    onJumpToMessage = { index ->
                        previewMode = false
                        scope.launch {
                            chatListState.requestScrollToItem(index)
                        }
                    },
                    onToolApproval = { toolCallId, approved, reason ->
                        vm.handleToolApproval(toolCallId, approved, reason)
                    },
                    onToolAnswer = { toolCallId, answer ->
                        vm.handleToolAnswer(toolCallId, answer)
                    },
                    onToggleFavorite = { node ->
                        vm.toggleMessageFavorite(node)
                    },
                    onConversationSystemPromptChange = { newPrompt ->
                        vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                        vm.saveConversationAsync()
                    },
                )
            }
        }
    }

    AnimatedContent(
        targetState = setting.uiMode,
        label = "ChatPageMode",
        transitionSpec = {
            ((fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.8f))).using(null)
        },
        modifier = Modifier.fillMaxSize(), contentKey = { it },
    ) { mode ->
        val body: @Composable () -> Unit = {
            CompositionLocalProvider(LocalGroupChatContext provides if (mode == UiMode.GROUP) group else null) {
                if (mode == UiMode.GROUP) GroupChatContent(
                    drawerState = drawerState, bigScreen = bigScreen, settings = setting,
                    hazeState = hazeState, useLiquidHost = useLiquidHost, inputHeightPx = inputHeightPx,
                    onDrawInput = { inputContent(mode, it) },
                    blocked = groupBlocked, onBlockedChange = { groupBlocked = it }, vm = groupVm,
                    onQuote = { quoteTarget = it; groupInputFocusRequester.requestFocus() },
                    onMention = { groupVm.inputState.insertMention(it); groupInputFocusRequester.requestFocus() },
                ) else chatContent()
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            if (useLiquidHost) {
                LiquidGlassChatHost(
                    sourceContent = body,
                    inputContent = { inputContent(mode, true) },
                    inputBounds = inputPanelBounds,
                    tintColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxSize(),
                )
            } else body()
        }
    }
    if (showFilesSheet) {
        ChatFilesPickerSheet(
            inputState = activeInputState,
            setting = setting,
            conversation = conversation,
            assistant = assistant,
            vm = vm,
            groupVm = groupVm,
            attachmentPickerActions = attachmentPickerActions,
            onStartVoiceMode = { if (setting.uiMode == UiMode.GROUP) groupOperationUnavailable() else onStartVoiceMode() },
            allowConversationActions = setting.uiMode == UiMode.CHAT,
            onConversationActionBlocked = ::groupOperationUnavailable,
            onDismiss = { showFilesSheet = false },
        )
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    groupVm: me.rerere.rikkahub.ui.pages.group.GroupVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
    allowConversationActions: Boolean = true,
    onConversationActionBlocked: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    val groupConversation by groupVm.conversation.collectAsStateWithLifecycle()
    val groupUsage by groupVm.contextUsage.collectAsStateWithLifecycle()
    val selectedGroupId by groupVm.selectedGroupId.collectAsStateWithLifecycle()
    val compressionGroup = if (setting.uiMode == UiMode.GROUP) setting.selectedGroup(selectedGroupId) else null

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = if (setting.uiMode == UiMode.GROUP) groupConversation ?: conversation.copy(messageNodes = emptyList()) else conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                if (setting.uiMode == UiMode.GROUP) groupVm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
                else if (allowConversationActions) vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
                else scope.launch { onConversationActionBlocked() }
            },
            onUpdateAssistant = {
                if (!allowConversationActions) {
                    onConversationActionBlocked()
                    return@FilesPicker
                }
                vm.updateSettings(
                    setting.copy(
                        assistants = setting.assistants.map { assistant ->
                            if (assistant.id == it.id) {
                                it
                            } else {
                                assistant
                            }
                        }
                    )
                )
            },
            onUpdateConversation = {
                if (allowConversationActions) {
                    vm.updateConversation(it)
                    vm.saveConversationAsync()
                } else onConversationActionBlocked()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            groupContextUsage = if (setting.uiMode == UiMode.GROUP) groupUsage ?: me.rerere.rikkahub.data.model.ContextUsage(0, 128_000) else null,
            groupCompressThreshold = compressionGroup?.compressionThreshold(),
            onGroupCompressThresholdChange = compressionGroup?.let { group -> { threshold ->
                groupVm.updateGroup(group.id) { it.copy(compressThresholdRatio = threshold) }
            } },
            groupCompressTargetTokens = compressionGroup?.compressTargetTokens ?: 2000,
            groupCompressKeepRecent = compressionGroup?.compressKeepRecent ?: 32,
            groupMode = setting.uiMode == UiMode.GROUP,
            groupWorkspaceId = compressionGroup?.workspaceId,
            groupAllowCwd = groupConversation != null,
            onUpdateGroupWorkspace = { id -> compressionGroup?.let { group ->
                groupVm.updateGroup(group.id) { it.copy(workspaceId = id) }
            } },
            onUpdateGroupCwd = { cwd -> compressionGroup?.let { group -> groupConversation?.let { conv ->
                groupVm.updateWorkspaceCwd(group.id, conv.id, cwd)
            } } },
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(HugeIcons.Menu03, "Messages")
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    val assistant = settings.getCurrentAssistant()
                    val model = settings.getCurrentChatModel()
                    val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            IconButton(
                onClick = {
                    onNewChat()
                }
            ) {
                Icon(HugeIcons.MessageAdd01, "New Message")
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}
