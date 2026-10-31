package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.compressionThreshold
import me.rerere.rikkahub.data.model.enabledMembers
import me.rerere.rikkahub.data.model.isGroupChat
import me.rerere.rikkahub.data.model.toInputAssistant
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
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
import me.rerere.rikkahub.ui.pages.group.GroupChatContent
import me.rerere.rikkahub.ui.pages.group.GroupVM
import me.rerere.rikkahub.ui.pages.group.rememberGroupVM
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
    val conversationLoaded by vm.conversationLoaded.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    // 新会话发出第一条消息前，跟随抽屉当前选中的文件夹（#2133）
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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

    // 模式跟随当前会话类型：打开群聊即群组模式，打开聊天即聊天模式。
    // 只在类型不一致时写一次，避免恢复阶段三⑥之前的无条件常量写回。
    LaunchedEffect(conversation.id, conversation.isGroupChat(), setting.uiMode, conversationLoaded) {
        if (!conversationLoaded || setting.init) return@LaunchedEffect
        val target = if (conversation.isGroupChat()) UiMode.GROUP else UiMode.CHAT
        if (setting.uiMode != target) {
            vm.updateSettings { current ->
                if (current.uiMode == target) current else current.copy(uiMode = target)
            }
        }
    }

    if (conversation.isGroupChat()) {
        GroupChatHost(
            conversation = conversation,
            settings = setting,
            drawerState = drawerState,
            bigScreen = isBigScreen,
            chatVm = vm,
        )
        return
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
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
                    assistant = assistant,
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
                        settings = setting
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
                    assistant = assistant,
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
private fun GroupChatHost(
    conversation: Conversation,
    settings: Settings,
    drawerState: DrawerState,
    bigScreen: Boolean,
    chatVm: ChatVM,
) {
    val groupVm: GroupVM = rememberGroupVM()
    val group = settings.groups.firstOrNull { conversation.id in it.conversationIds }
    val groupAssistant = remember(group) { group?.toInputAssistant() ?: Assistant() }
    val groupConversation by groupVm.conversation.collectAsStateWithLifecycle()
    val conversationJob by groupVm.conversationJob.collectAsStateWithLifecycle()
    val messageQueue by groupVm.messageQueue.collectAsStateWithLifecycle()
    val contextUsage by groupVm.contextUsage.collectAsStateWithLifecycle()
    val activeConversation = groupConversation ?: conversation
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val mcpManager: McpManager = koinInject()
    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()

    var showFilesSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var pendingQuote by remember { mutableStateOf<UIMessageAnnotation.GroupQuote?>(null) }
    var inputPanelBounds by remember { mutableStateOf<LiquidGlassInputBounds?>(null) }
    var inputHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(group?.id, conversation.id) {
        group?.let {
            groupVm.selectGroup(it.id)
            groupVm.selectConversation(it.id, conversation.id)
        }
    }

    val completionProviders = remember(group?.workspaceId, activeConversation.workspaceCwd, workspaceRepository) {
        group?.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = activeConversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = groupVm.inputState,
        setting = settings,
        onAttachmentAdded = { showFilesSheet = false },
    )

    val liquidUnavailable by LiquidGlassCircuitBreaker.unavailable.collectAsStateWithLifecycle()
    val useLiquidHost = shouldUseNativeLiquidGlass(
        apiLevel = Build.VERSION.SDK_INT,
        setting = settings.displaySetting,
        unavailable = liquidUnavailable,
    )

    val inputContent: @Composable (Boolean) -> Unit = { drawLiquidGlass ->
        ChatInput(
            state = groupVm.inputState,
            loading = conversationJob != null,
            settings = settings,
            assistant = groupAssistant,
            chatModel = null,
            groupMode = true,
            reasoningAvailable = true,
            hazeState = hazeState,
            completionProviders = completionProviders,
            onUpdateChatModel = {},
            onUpdateAssistant = { updated ->
                group?.let { current ->
                    groupVm.updateGroup(current.id) {
                        it.copy(
                            reasoningLevel = updated.reasoningLevel,
                            toolApprovalMode = updated.toolApprovalMode,
                        )
                    }
                }
            },
            onUpdateSearchService = {},
            onUpdateSearchMode = { mode ->
                group?.let { current ->
                    groupVm.updateGroup(current.id) { it.copy(enableWebSearch = mode != SearchMode.OFF) }
                }
            },
            onMoreClick = { showFilesSheet = true },
            onCancelClick = { groupVm.stopGeneration() },
            onSendClick = {
                val parts = groupVm.inputState.getContents()
                if (parts.isEmptyInputMessage()) return@ChatInput
                if (groupVm.inputState.isEditing()) {
                    groupVm.inputState.editingMessage?.let { groupVm.editMessage(parts, it) }
                } else {
                    if (group == null || group.enabledMembers(settings).isEmpty()) {
                        blocked = true
                        return@ChatInput
                    }
                    groupVm.sendMessage(group.id, parts, quote = pendingQuote)
                    pendingQuote = null
                }
                groupVm.inputState.clearInput()
            },
            onLongSendClick = {
                val parts = groupVm.inputState.getContents()
                if (parts.isEmptyInputMessage()) return@ChatInput
                if (groupVm.inputState.isEditing()) {
                    groupVm.inputState.editingMessage?.let { groupVm.editMessage(parts, it) }
                } else {
                    if (group == null || group.enabledMembers(settings).isEmpty()) {
                        blocked = true
                        return@ChatInput
                    }
                    groupVm.sendMessage(group.id, parts, answer = false, quote = pendingQuote)
                    pendingQuote = null
                }
                groupVm.inputState.clearInput()
            },
            messageQueue = messageQueue,
            onRemoveQueuedMessage = { groupVm.removeQueuedMessage(it) },
            onBeginEditQueuedMessage = { groupVm.beginEditQueuedMessage(it) },
            onFinishEditQueuedMessage = { id, parts -> groupVm.finishEditQueuedMessage(id, parts) },
            onResumeMessageQueue = { groupVm.resumeMessageQueue() },
            forceLiquidGlass = drawLiquidGlass,
            onInputHeightChanged = { height -> inputHeightPx = height },
            onInputPanelBoundsChanged = if (drawLiquidGlass) { { x, y, size ->
                inputPanelBounds = LiquidGlassInputBounds(x, y, size.width, size.height)
            } } else null,
        )
    }

    val groupPage: @Composable () -> Unit = {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            if (useLiquidHost) {
                LiquidGlassChatHost(
                    sourceContent = {
                        GroupChatContent(
                            drawerState = drawerState,
                            bigScreen = bigScreen,
                            settings = settings,
                            hazeState = hazeState,
                            useLiquidHost = true,
                            inputHeightPx = inputHeightPx,
                            onDrawInput = {},
                            blocked = blocked,
                            onBlockedChange = { blocked = it },
                            vm = groupVm,
                            onQuote = { pendingQuote = it },
                            onMention = { groupVm.inputState.appendText("@$it ") },
                        )
                    },
                    inputContent = { inputContent(true) },
                    inputBounds = inputPanelBounds,
                    tintColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GroupChatContent(
                    drawerState = drawerState,
                    bigScreen = bigScreen,
                    settings = settings,
                    hazeState = hazeState,
                    useLiquidHost = false,
                    inputHeightPx = 0,
                    onDrawInput = { inputContent(false) },
                    blocked = blocked,
                    onBlockedChange = { blocked = it },
                    vm = groupVm,
                    onQuote = { pendingQuote = it },
                    onMention = { groupVm.inputState.appendText("@$it ") },
                )
            }

            if (showFilesSheet && group != null) {
                GroupChatFilesPickerSheet(
                    conversation = activeConversation,
                    group = group,
                    assistant = groupAssistant,
                    settings = settings,
                    groupVm = groupVm,
                    mcpManager = mcpManager,
                    contextUsage = contextUsage,
                    inputState = groupVm.inputState,
                    attachmentPickerActions = attachmentPickerActions,
                    showCompressDialog = showCompressDialog,
                    onShowCompressDialogChange = { showCompressDialog = it },
                    onDismiss = {
                        showCompressDialog = false
                        showFilesSheet = false
                    },
                )
            }
        }
    }

    if (bigScreen) {
        androidx.compose.material3.PermanentNavigationDrawer(
            drawerContent = {
                ChatDrawerContent(
                    navController = navController,
                    current = conversation,
                    vm = chatVm,
                    settings = settings,
                )
            },
        ) {
            groupPage()
        }
    } else {
        androidx.compose.material3.ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ChatDrawerContent(
                    navController = navController,
                    current = conversation,
                    vm = chatVm,
                    settings = settings,
                )
            },
        ) {
            groupPage()
        }
        BackHandler(drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}

@Composable
private fun GroupChatFilesPickerSheet(
    conversation: Conversation,
    group: Group,
    assistant: Assistant,
    settings: Settings,
    groupVm: GroupVM,
    mcpManager: McpManager,
    contextUsage: me.rerere.rikkahub.data.model.ContextUsage?,
    inputState: ChatInputState,
    attachmentPickerActions: ChatAttachmentPickerActions,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = null,
            mcpManager = mcpManager,
            onCompressContext = { prompt, keep ->
                groupVm.handleCompressContext(prompt, group.compressTargetTokens, keep)
            },
            onGroupCompressContext = { prompt, target, keep ->
                groupVm.handleCompressContext(prompt, target, keep)
            },
            onUpdateAssistant = {},
            onUpdateWorkspaceCwd = {},
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = onShowCompressDialogChange,
            onDismiss = onDismiss,
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            groupContextUsage = contextUsage,
            groupCompressThreshold = group.compressionThreshold(),
            onGroupCompressThresholdChange = { threshold ->
                groupVm.updateGroup(group.id) { it.copy(compressThresholdRatio = threshold) }
            },
            groupCompressTargetTokens = group.compressTargetTokens,
            groupCompressKeepRecent = group.compressKeepRecent,
            groupMode = true,
            groupWorkspaceId = group.workspaceId,
            onUpdateGroupWorkspace = { workspaceId ->
                groupVm.updateGroup(group.id) { it.copy(workspaceId = workspaceId) }
            },
            onUpdateGroupCwd = { cwd ->
                groupVm.updateWorkspaceCwd(group.id, conversation.id, cwd)
            },
            groupMcpServerIds = group.mcpServers,
            onUpdateGroupMcpServers = { ids ->
                groupVm.updateGroup(group.id) { it.copy(mcpServers = ids) }
            },
            groupModeInjectionIds = group.modeInjectionIds,
            onUpdateGroupModeInjectionIds = { ids ->
                groupVm.updateGroup(group.id) { it.copy(modeInjectionIds = ids) }
            },
            groupLorebookIds = group.lorebookIds,
            onUpdateGroupLorebookIds = { ids ->
                groupVm.updateGroup(group.id) { it.copy(lorebookIds = ids) }
            },
            groupEnabledSkills = group.enabledSkills,
            onUpdateGroupEnabledSkills = { skills ->
                groupVm.updateGroup(group.id) { it.copy(enabledSkills = skills) }
            },
        )
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
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    val liquidUnavailable by LiquidGlassCircuitBreaker.unavailable.collectAsStateWithLifecycle()
    val useLiquidHost = shouldUseNativeLiquidGlass(
        apiLevel = Build.VERSION.SDK_INT,
        setting = setting.displaySetting,
        unavailable = liquidUnavailable,
    )
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var inputPanelBounds by remember { mutableStateOf<LiquidGlassInputBounds?>(null) }
    var inputHeightPx by remember { mutableStateOf(0) }

    val inputContent: @Composable (Boolean) -> Unit = { drawLiquidGlass ->
        val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
        val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
        ChatInput(
            onStartVoiceMode = onStartVoiceMode,
            voiceState = voiceState,
            onStopVoiceMode = vm.voiceSession::stop,
            state = inputState,
            messageQueue = messageQueue,
            onRemoveQueuedMessage = vm::removeQueuedMessage,
            onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
            onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
            onResumeMessageQueue = vm::resumeMessageQueue,
            loading = loadingJob != null,
            settings = setting,
            assistant = assistant,
            chatModel = currentChatModel,
            hazeState = hazeState,
            completionProviders = completionProviders,
            onCancelClick = {
                vm.stopGeneration()
            },
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onSendClick = {
                if (currentChatModel == null) {
                    toaster.show("请先选择模型", type = ToastType.Error)
                    return@ChatInput
                }
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(
                        parts = inputState.getContents(),
                        messageId = inputState.editingMessage!!,
                    )
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
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(
                        parts = inputState.getContents(),
                        messageId = inputState.editingMessage!!,
                    )
                } else {
                    vm.handleMessageSend(content = inputState.getContents(), answer = false)
                    scope.launch {
                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                    }
                }
                inputState.clearInput()
            },
            onUpdateChatModel = vm::setChatModel,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateSearchService = { index ->
                vm.updateSettings { it.copy(searchServiceSelected = index) }
            },
            forceLiquidGlass = drawLiquidGlass,
            onInputHeightChanged = { height ->
                if (inputHeightPx != height) inputHeightPx = height
            },
            onInputPanelBoundsChanged = if (drawLiquidGlass) { { x, y, size ->
                val bounds = LiquidGlassInputBounds(x, y, size.width, size.height)
                if (inputPanelBounds != bounds) inputPanelBounds = bounds
            } } else null,
            onMoreClick = {
                showFilesSheet = true
            },
        )
    }

    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    assistant = assistant,
                    chatModel = currentChatModel,
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
                if (!useLiquidHost) inputContent(false)
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = if (useLiquidHost) PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = maxOf(innerPadding.calculateBottomPadding(), with(density) { inputHeightPx.toDp() }),
                ) else innerPadding,
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
                    vm.selectMessageNode(newNode.id, newNode.selectIndex)
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
                    vm.updateConversationSystemPrompt(newPrompt)
                },
            )
        }

        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (useLiquidHost) {
            LiquidGlassChatHost(
                sourceContent = body,
                inputContent = { inputContent(true) },
                inputBounds = inputPanelBounds,
                tintColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxSize(),
            )
        } else body()
        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
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
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
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
    assistant: Assistant,
    chatModel: Model?,
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
                    val provider = chatModel?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chatModel != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${chatModel.displayName} (${provider.name})",
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
