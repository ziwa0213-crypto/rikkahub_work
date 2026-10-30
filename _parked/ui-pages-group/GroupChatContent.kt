package me.rerere.rikkahub.ui.pages.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.group.components.GroupAvatar
import me.rerere.rikkahub.ui.pages.group.components.GroupEmptyState
import me.rerere.rikkahub.ui.pages.group.components.NewGroupDialog
import me.rerere.rikkahub.ui.pages.group.components.GroupBackground
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.pages.chat.ChatList
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.model.compressionThreshold
import kotlin.math.roundToInt
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.ErrorCardsDisplay
import me.rerere.hugeicons.stroke.Delete01

@Composable
fun GroupChatContent(
    drawerState: DrawerState, bigScreen: Boolean, settings: Settings, hazeState: HazeState,
    useLiquidHost: Boolean, inputHeightPx: Int, onDrawInput: @Composable (Boolean) -> Unit,
    blocked: Boolean, onBlockedChange: (Boolean) -> Unit, vm: GroupVM = rememberGroupVM(),
    onQuote: (UIMessageAnnotation.GroupQuote) -> Unit = {},
    onMention: (String) -> Unit = {},
) {
    val selectedId by vm.selectedGroupId.collectAsStateWithLifecycle()
    val group = settings.selectedGroup(selectedId)
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val conversationId by vm.selectedConversationId.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val compressionPromptId by vm.compressionPromptConversationId.collectAsStateWithLifecycle()
    val contextUsage by vm.contextUsage.collectAsStateWithLifecycle()
    val compressedCount by vm.compressedMessageCount.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    var creating by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(compressedCount) {
        compressedCount?.let {
            toaster.show(resources.getString(R.string.group_compress_done, it), type = ToastType.Success)
            vm.dismissCompressionDone()
        }
    }
    if (group != null && compressionPromptId == conversationId && conversationId != null && contextUsage != null && loadingJob == null) {
        AlertDialog(onDismissRequest = vm::dismissCompressionPrompt,
            title = { Text(stringResource(R.string.group_compress_prompt_title)) },
            text = { Text(stringResource(R.string.group_compress_prompt_desc,
                (contextUsage!!.ratio * 100).roundToInt(), (group.compressionThreshold() * 100).roundToInt())) },
            confirmButton = { TextButton(onClick = { vm.handleAutoCompress() }) { Text(stringResource(R.string.group_compress_prompt_now)) } },
            dismissButton = { TextButton(onClick = vm::dismissCompressionPrompt) { Text(stringResource(R.string.group_compress_prompt_later)) } })
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GroupBackground(group, Modifier.fillMaxSize().hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(group?.name ?: stringResource(R.string.group_mode_group), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        if (!bigScreen) IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(HugeIcons.Menu03, stringResource(R.string.menu))
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(group?.let { Screen.GroupConfig(it.id.toString()) } ?: Screen.Groups) }) {
                            Icon(HugeIcons.Settings03, stringResource(R.string.group_settings_title))
                        }
                        IconButton(onClick = {
                            group?.let { vm.newConversation(it.id) }
                        }) {
                            Icon(HugeIcons.MessageAdd01, stringResource(R.string.group_new_chat))
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(HugeIcons.MoreVertical, stringResource(R.string.group_chat_options))
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.assistant_page_tab_memory)) },
                                    leadingIcon = { Icon(HugeIcons.Brain02, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        group?.let { current -> conversationId?.let {
                                            navController.navigate(Screen.GroupMemory(current.id.toString(), it.toString()))
                                        } }
                                    },
                                    enabled = conversationId != null,
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useLiquidHost) onDrawInput(false)
            },
            containerColor = Color.Transparent,
        ) { padding ->
            val contentPadding = if (useLiquidHost) PaddingValues(
                start = padding.calculateStartPadding(layoutDirection), top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = maxOf(padding.calculateBottomPadding(), with(density) { inputHeightPx.toDp() }),
            ) else padding
            if (group != null && conversation != null && conversation?.id == conversationId) {
                key(conversationId) {
                    ChatList(innerPadding = contentPadding, conversation = conversation!!,
                        state = listState, loading = loadingJob != null, processingStatus = processingStatus,
                        previewMode = false, settings = settings, hazeState = hazeState,
                        errors = errors.filter { it.conversationId == conversationId },
                        onDismissError = vm::dismissError, onClearAllErrors = vm::clearErrors,
                        onRegenerate = vm::regenerate, onDelete = vm::deleteMessage,
                        onEdit = { if (loadingJob == null) { vm.inputState.editingMessage = it.id; vm.inputState.setContents(it.parts) } },
                        onForkMessage = { vm.forkMessage(group.id, it) }, onUpdateMessage = vm::updateNode,
                        onToolApproval = vm::handleToolApproval, onToolAnswer = vm::handleToolAnswer,
                        onClickSuggestion = { vm.inputState.setMessageText(it) },
                        groupMode = true, onQuote = onQuote, onMention = onMention,
                    )
                }
            } else Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                if (group == null) GroupEmptyState(stringResource(R.string.group_empty_groups), stringResource(R.string.group_empty_groups_hint)) {
                    TextButton(onClick = { creating = true }) { Text(stringResource(R.string.group_new)) }
                } else Column(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GroupAvatar(group, settings, Modifier.size(74.dp))
                    Text(stringResource(R.string.group_member_count, group.members.size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.group_chat_empty), style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center)
                }
                ErrorCardsDisplay(errors.filter { it.conversationId == conversationId }, vm::dismissError,
                    vm::clearErrors, Modifier.align(Alignment.BottomCenter).heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()))
            }
        }
    }
    RikkaConfirmDialog(blocked, stringResource(R.string.group_blocked_title), stringResource(R.string.group_go_create),
        stringResource(R.string.cancel), onConfirm = {
            onBlockedChange(false)
            if (settings.groupMembers.isEmpty()) {
                toaster.show(resources.getString(R.string.group_create_member_first), type = ToastType.Info)
                navController.navigate(Screen.GroupMembers)
            } else creating = true
        }, onDismiss = { onBlockedChange(false) }) { Text(stringResource(R.string.group_blocked_text)) }
    if (creating) NewGroupDialog(settings, { creating = false }, onCreate = { created ->
        vm.saveGroup(created) {
            vm.selectGroup(created.id)
            creating = false
            navController.navigate(Screen.GroupConfig(created.id.toString()))
        }
    }, onCreateMember = { navController.navigate(Screen.GroupMembers) }, onUpdateTags = vm::updateGroupTags)
}
