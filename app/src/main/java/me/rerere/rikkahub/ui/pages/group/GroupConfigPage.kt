package me.rerere.rikkahub.ui.pages.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import me.rerere.ui.components.FormItem
import me.rerere.ui.components.OutlinedNumberInput
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.DragDropVertical
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tag01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.GroupMemberRef
import me.rerere.rikkahub.data.model.displayNameOf
import me.rerere.rikkahub.data.model.displayAvatarOf
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.WorkspaceOwnerScope
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ai.WorkspaceSelectSheet
import org.koin.compose.koinInject
import me.rerere.rikkahub.data.model.moveMember
import me.rerere.rikkahub.data.model.withOrderedMembers
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.group.components.*
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.pages.assistant.detail.BackgroundPicker
import me.rerere.rikkahub.utils.toFixed
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.navigateToChatPage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.DateFormat
import java.util.Date
import kotlin.uuid.Uuid
import kotlin.math.roundToInt

@Composable
fun GroupConfigPage(groupId: String, vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val group = settings.groups.find { it.id.toString() == groupId }
    val navController = LocalNavController.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    var editInfo by remember { mutableStateOf(false) }
    var addMembers by remember { mutableStateOf(false) }
    val workspaceRepository: WorkspaceRepository = koinInject()
    val workspaces by workspaceRepository.listFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var showWorkspace by remember(groupId) { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<Uuid?>(null) }
    var promptDraft by remember(groupId) { mutableStateOf<String?>(null) }
    val currentGroup by rememberUpdatedState(group)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Headers are not members; use stable keys rather than lazy-list indices.
        val fromId = from.key as? Uuid ?: return@rememberReorderableLazyListState
        val toId = to.key as? Uuid ?: return@rememberReorderableLazyListState
        currentGroup?.let { vm.updateGroup(it.id) { latest -> latest.moveMember(fromId, toId) } }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(group?.name ?: stringResource(R.string.group_settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton() },
                actions = { TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.group_save)) } },
                scrollBehavior = scrollBehavior, colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(), state = listState,
            contentPadding = padding + PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (group == null) item { Text(stringResource(R.string.group_missing), Modifier.padding(16.dp)) }
            else {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GroupAvatar(group, settings, Modifier.size(74.dp), onClick = { editInfo = true })
                        Text(group.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.group_created_at, DateFormat.getDateInstance().format(Date(group.createdAt))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.group_member_count, group.members.size), style = MaterialTheme.typography.labelSmall)
                    }
                }
                item {
                    CardGroup(Modifier.padding(horizontal = 8.dp)) {
                        item(onClick = { editInfo = true }, leadingContent = { Icon(HugeIcons.PencilEdit01, null) },
                            headlineContent = { Text(stringResource(R.string.group_info)) }, trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        item(onClick = { editInfo = true }, leadingContent = { Icon(HugeIcons.Tag01, null) },
                            headlineContent = { Text(stringResource(R.string.group_tags)) },
                            supportingContent = { GroupTags(settings, group.tags) }, trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        switchItem(
                            checked = group.enableDeputyDispatch,
                            onCheckedChange = { enabled ->
                                vm.updateGroup(group.id) { it.copy(enableDeputyDispatch = enabled) }
                            },
                            leadingContent = { CardGroupIcon(HugeIcons.Message02) },
                            headlineContent = { Text(stringResource(R.string.group_strategy)) },
                            supportingContent = { Text(stringResource(R.string.group_strategy_desc)) },
                        )
                    }
                }
                item {
                    Card(Modifier.padding(horizontal = 8.dp), colors = CustomColors.cardColorsOnSurfaceContainer) {
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = { Text(stringResource(R.string.assistant_page_gradient_background)) },
                            description = { Text(stringResource(R.string.assistant_page_gradient_background_desc)) },
                            tail = { Switch(group.useGradientBackground, { enabled ->
                                vm.updateGroup(group.id) { it.copy(useGradientBackground = enabled) }
                            }) },
                        )
                        if (!group.useGradientBackground) {
                            HorizontalDivider()
                            BackgroundPicker(
                                modifier = Modifier.padding(8.dp), background = group.background,
                                backgroundOpacity = group.backgroundOpacity,
                                description = { Text(stringResource(R.string.group_chat_background_desc)) },
                                onUpdate = { background -> vm.updateGroup(group.id) { it.copy(background = background) } },
                            )
                        }
                        if (!group.useGradientBackground && group.background != null) {
                            val opacity = group.backgroundOpacity.coerceIn(0f, 1f)
                            HorizontalDivider()
                            FormItem(
                                modifier = Modifier.padding(8.dp),
                                label = { Text(stringResource(R.string.assistant_page_background_opacity)) },
                                description = { Text(stringResource(R.string.assistant_page_background_opacity_desc)) },
                            ) {
                                Slider(value = opacity, onValueChange = { value ->
                                    vm.updateGroup(group.id) { it.copy(backgroundOpacity =
                                        value.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f) }
                                }, valueRange = 0f..1f, steps = 19, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.assistant_page_background_opacity_value, (opacity * 100).roundToInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
                item {
                    CardGroup(Modifier.padding(horizontal = 8.dp)) {
                        item(onClick = { navController.navigate(Screen.GroupMcp(group.id.toString())) },
                            leadingContent = { Icon(HugeIcons.Wrench01, null) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_tab_mcp)) },
                            supportingContent = { Text(stringResource(R.string.group_detail_mcp_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        item(onClick = { navController.navigate(Screen.GroupLocalTool(group.id.toString())) },
                            leadingContent = { Icon(HugeIcons.BookOpen01, null) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_tab_local_tools)) },
                            supportingContent = { Text(stringResource(R.string.assistant_detail_local_tools_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        item(onClick = { navController.navigate(Screen.GroupExtensions(group.id.toString())) },
                            leadingContent = { Icon(HugeIcons.Puzzle, null) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_tab_extensions)) },
                            supportingContent = { Text(listOf(
                                stringResource(R.string.extension_selector_tab_mode_injections),
                                stringResource(R.string.extension_selector_tab_lorebooks),
                                stringResource(R.string.extension_selector_tab_skills),
                            ).joinToString(" / ")) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        item(onClick = { promptDraft = group.systemPrompt },
                            leadingContent = { Icon(HugeIcons.Message02, null) },
                            headlineContent = { Text(stringResource(R.string.group_system_prompt)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        item(onClick = { showWorkspace = true }, leadingContent = { Icon(HugeIcons.Codesandbox, null) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_workspace)) },
                            supportingContent = { Text(workspaces.find { it.id == group.workspaceId?.toString() }?.name
                                ?: stringResource(R.string.workspace_no_binding)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                        switchItem(
                            checked = group.enableWebSearch,
                            onCheckedChange = { enabled -> vm.updateGroup(group.id) { it.copy(enableWebSearch = enabled) } },
                            leadingContent = { CardGroupIcon(HugeIcons.Search01) },
                            headlineContent = { Text(stringResource(R.string.use_web_search)) },
                        )
                        switchItem(
                            checked = group.enableRecentChatsReference,
                            onCheckedChange = { enabled -> vm.updateGroup(group.id) { it.copy(enableRecentChatsReference = enabled) } },
                            leadingContent = { CardGroupIcon(HugeIcons.Message02) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_recent_chats)) },
                        )
                        switchItem(
                            checked = group.enableTimeReminder,
                            onCheckedChange = { enabled -> vm.updateGroup(group.id) { it.copy(enableTimeReminder = enabled) } },
                            leadingContent = { CardGroupIcon(HugeIcons.Clock01) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder)) },
                        )
                        if (group.enableTimeReminder) item(headlineContent = {
                            OutlinedNumberInput(group.timeReminderIntervalMinutes,
                                { minutes -> if (minutes > 0) vm.updateGroup(group.id) { it.copy(timeReminderIntervalMinutes = minutes) } },
                                label = stringResource(R.string.assistant_page_time_reminder_interval_label), modifier = Modifier.fillMaxWidth())
                        })
                        switchItem(
                            checked = group.enableMemory,
                            onCheckedChange = { enabled -> vm.updateGroup(group.id) { it.copy(enableMemory = enabled) } },
                            leadingContent = { CardGroupIcon(HugeIcons.Brain02) },
                            headlineContent = { Text(stringResource(R.string.group_memory_enabled)) },
                        )
                        item(onClick = { navController.navigate(Screen.GroupRequest(group.id.toString())) },
                            leadingContent = { Icon(HugeIcons.Code, null) },
                            headlineContent = { Text(stringResource(R.string.assistant_page_tab_request)) },
                            supportingContent = { Text(stringResource(R.string.assistant_detail_request_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) })
                    }
                }
                item {
                    GroupCompressionSettings(group, settings.groupCompressPrompt,
                        onUpdate = { change -> vm.updateGroup(group.id, change) }, onUpdatePrompt = vm::updateGroupCompressPrompt)
                }
                item { Text(stringResource(R.string.group_member_count, group.members.size), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(group.members, key = { it.memberId }) { ref ->
                    val member = settings.groupMembers.find { it.id == ref.memberId }
                    if (member != null) ReorderableItem(reorderState, key = ref.memberId) { dragging ->
                        val displayName = group.displayNameOf(member)
                        val displayAvatar = group.displayAvatarOf(member)
                        val dragModifier = longPressReorder(dragging)
                        CardGroup(Modifier.padding(horizontal = 8.dp).animateItem()) {
                            item(
                                modifier = dragModifier,
                                onClick = { selectedMember = member.id },
                                leadingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(HugeIcons.DragDropVertical, stringResource(R.string.group_drag), Modifier.size(24.dp))
                                        UIAvatar(displayName, displayAvatar, modifier = Modifier.size(48.dp))
                                    }
                                },
                                headlineContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (member.id == group.deputyMemberId) Tag(type = TagType.SUCCESS) {
                                            Text(stringResource(R.string.group_deputy))
                                        }
                                    }
                                },
                                supportingContent = { Text(if (ref.enabled) stringResource(R.string.group_member_params,
                                    ref.priority + 1, (ref.talkativeness * 100).toInt()) else stringResource(R.string.group_disabled)) },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(ref.enabled, { enabled ->
                                            vm.updateGroup(group.id) { latest -> latest.copy(members = latest.members.map {
                                                if (it.memberId == member.id) it.copy(enabled = enabled) else it
                                            }) }
                                        })
                                        ItemActionMenu(listOf(ItemAction(
                                            stringResource(if (member.id == group.deputyMemberId) R.string.group_deputy_remove else R.string.group_deputy_set),
                                            HugeIcons.Brain02, onClick = { vm.updateGroup(group.id) {
                                                it.copy(deputyMemberId = if (it.deputyMemberId == member.id) null else member.id)
                                            } },
                                        )))
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    CardGroup(Modifier.padding(horizontal = 8.dp)) {
                        item(onClick = { addMembers = true }, leadingContent = { Icon(HugeIcons.Add01, null) },
                            headlineContent = { Text(stringResource(R.string.group_add_members)) })
                        item(onClick = {
                            vm.selectGroup(group.id)
                            if (!navController.popBackToChat()) navigateToChatPage(navController)
                        },
                            leadingContent = { Icon(HugeIcons.MessageAdd01, null) },
                            headlineContent = { Text(stringResource(R.string.group_start)) })
                    }
                }
            }
        }
    }
    if (group != null && showWorkspace) WorkspaceSelectSheet(
        assistant = Assistant(workspaceId = group.workspaceId), workspaces = workspaces, scope = WorkspaceOwnerScope.GROUP,
        onSelect = { id -> vm.updateGroup(group.id) { it.copy(workspaceId = id?.let(Uuid::parse)) }; showWorkspace = false },
        onManage = { showWorkspace = false; navController.navigate(Screen.Workspaces(WorkspaceOwnerScope.GROUP.value)) },
        onDismiss = { showWorkspace = false },
    )
    if (group != null && editInfo) GroupInfoDialog(group, settings, { editInfo = false }, vm::updateGroupTags) { updated ->
        vm.updateGroup(group.id) { it.copy(name = updated.name, avatar = updated.avatar, tags = updated.tags) }
        editInfo = false
    }
    if (group != null) promptDraft?.let { draft ->
        AlertDialog(onDismissRequest = { promptDraft = null },
            title = { Text(stringResource(R.string.group_system_prompt)) },
            text = { TextField(draft, { promptDraft = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 12) },
            confirmButton = { TextButton(onClick = {
                vm.updateGroup(group.id) { it.copy(systemPrompt = draft) }; promptDraft = null
            }) { Text(stringResource(R.string.group_save)) } },
            dismissButton = { TextButton(onClick = { promptDraft = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (group != null && addMembers) AddGroupMembersDialog(group, settings, { addMembers = false },
        onCreateMember = { addMembers = false; navController.navigate(Screen.GroupMembers) }) { ids ->
        vm.updateGroup(group.id) { latest -> latest.withOrderedMembers(latest.members + ids.map { GroupMemberRef(it) }) }
        addMembers = false
    }
    if (group != null) selectedMember?.let { id ->
        val member = settings.groupMembers.find { it.id == id }
        val ref = group.members.find { it.memberId == id }
        if (member != null && ref != null) MemberConfigSheet(group, member, ref, { selectedMember = null },
            onUpdate = { updated, priority ->
                vm.updateGroup(group.id) { latest ->
                    val refs = latest.members.filterNot { it.memberId == id }.toMutableList()
                    refs.add(priority.coerceIn(0, refs.size), updated)
                    latest.withOrderedMembers(refs)
                }
            }, onRemove = {
                vm.updateGroup(group.id) { it.withOrderedMembers(it.members.filterNot { ref -> ref.memberId == id }) }
                selectedMember = null
            })
    }
}
