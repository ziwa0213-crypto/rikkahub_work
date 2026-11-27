package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.data.repository.*
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.McpPicker
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CharacterExtensionTabs
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.assistant.detail.*
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
internal fun CharacterDetailPage(id: String) {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val context = LocalContext.current
    val store: SettingsStore = koinInject()
    val conversations: ConversationRepository = koinInject()
    val chatService: ChatService = koinInject()
    val workspaces: WorkspaceRepository = koinInject()
    val memory: MemoryRepository = koinInject()
    val consolidation: MemoryConsolidationScheduler = koinInject()
    val scope = rememberCoroutineScope()
    val owner = Uuid.parse(id)
    val character = settings.findCharacter(owner)
    val allWorkspaces by remember(workspaces) { workspaces.listFlow() }.collectAsState(initial = emptyList())
    val files by remember(memory, id) { memory.getFilesFlow(id) }.collectAsState(initial = emptyList())
    val memoryStatus by remember(consolidation, id) { consolidation.statusOf(id) }.collectAsState(MemoryConsolidationStatus())
    val workspace = allWorkspaces.firstOrNull { it.id == character?.workspaceId?.toString() && WorkspaceOwnerScope.CHARACTER.includes(it) }
    var tab by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    fun update(block: (Character) -> Character) {
        scope.launch { store.update { latest -> latest.copy(characters = latest.characters.map {
            if (it.id == owner) block(it).copy(updatedAt = System.currentTimeMillis()) else it
        }) } }
    }
    LaunchedEffect(owner, character?.workspaceId, character?.workspaceInitialized) {
        if (character != null && !character.workspaceInitialized) {
            try {
                val prepared = CharacterWorkspaceProvisioner.ensureWorkspace(workspaces, owner, character.workspaceId)
                CharacterWorkspaceProvisioner.provision(context, workspaces, prepared)
                update { if (it.workspaceId == character.workspaceId) it.copy(
                    workspaceId = Uuid.parse(prepared.id), workspaceInitialized = true,
                ) else it }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: e.javaClass.simpleName }
        }
    }
    val sections = listOf(R.string.character_card, R.string.assistant_page_tab_basic, R.string.assistant_page_tab_prompt,
        R.string.assistant_page_tab_request, R.string.assistant_page_tab_local_tools, R.string.assistant_page_tab_mcp,
        R.string.assistant_page_tab_extensions, R.string.assistant_page_tab_memory)
    Scaffold(topBar = { TopAppBar(title = { Text(character?.name?.ifBlank { stringResource(R.string.group_mode_role) }
        ?: stringResource(R.string.character_deleted)) }, navigationIcon = { BackButton() }) }) { padding ->
        if (character == null) return@Scaffold
        val assistant = character.toAssistant(settings)
        val editor = assistant.copy(systemPrompt = character.characterSystemPrompt, presetMessages = character.presetMessages)
        fun applyAssistant(new: Assistant) = update { current ->
            current.withAssistantSettings(new).copy(
                characterSystemPrompt = if (editor.systemPrompt != new.systemPrompt) new.systemPrompt else current.characterSystemPrompt,
                presetMessages = if (editor.presetMessages != new.presetMessages) new.presetMessages else current.presetMessages,
                workspaceInitialized = current.workspaceInitialized && new.workspaceId == current.workspaceId,
            )
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab) {
                sections.forEachIndexed { i, res -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) }) }
            }
            error?.let { message -> Text(message, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error) }
            when (tab) {
                0 -> CharacterCardTab(character, ::update,
                    installEnabled = workspace?.shellStatus == WorkspaceShellStatus.READY.name,
                    installing = installing, onInstall = {
                        val workspaceId = workspace?.id ?: return@CharacterCardTab
                        scope.launch {
                            installing = true
                            try {
                                CharacterWorkspaceProvisioner.prepareInstall(context, workspaces, workspaceId)
                                nav.navigate(Screen.WorkspaceTerminal(workspaceId, CharacterWorkspaceProvisioner.COMMAND))
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.localizedMessage ?: e.javaClass.simpleName }
                            finally { installing = false }
                        }
                    }, onDelete = { deleting = true })
                1 -> AssistantBasicContent(PaddingValues(), editor, settings.providers, emptyList(), allWorkspaces, ::applyAssistant,
                    ownerScope = WorkspaceOwnerScope.CHARACTER)
                2 -> CharacterPromptModulesContent(character, Modifier.fillMaxSize())
                3 -> AssistantRequestContent(PaddingValues(), editor, ::applyAssistant)
                4 -> AssistantLocalToolContent(PaddingValues(), editor, onUpdate = ::applyAssistant)
                5 -> McpPicker(assistant = editor, servers = settings.mcpServers, onUpdateAssistant = ::applyAssistant)
                6 -> ExtensionSelector(modifier = Modifier.weight(1f).padding(12.dp), assistant = editor, settings = settings,
                    allowedTabs = CharacterExtensionTabs, onUpdate = ::applyAssistant,
                    onNavigateToQuickMessages = { nav.navigate(Screen.QuickMessages) }, onNavigateToPrompts = { nav.navigate(Screen.Prompts) },
                    onNavigateToSkills = { nav.navigate(Screen.Skills) }, onNavigateToLorebooks = { nav.navigate(Screen.CharacterLorebooks) })
                7 -> {
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = character.enableMemory, onCheckedChange = { v -> update { it.copy(enableMemory = v) } })
                            Text(stringResource(R.string.assistant_page_tab_memory))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = character.autoConsolidateMemory, enabled = character.enableMemory,
                                onCheckedChange = { v -> update { it.copy(autoConsolidateMemory = v) } })
                            Text(stringResource(R.string.character_memory_auto_organize))
                        }
                        Text(stringResource(R.string.character_reference_memory))
                        val buckets = (settings.assistants.map { it.memoryId to it.name } +
                            settings.memoryGroups.map { MemoryRepository.scopeOf(it.id) to it.name }).distinctBy { it.first }
                        for ((bucket, label) in buckets) {
                            Row {
                                Checkbox(bucket in character.referencedMemoryScopes, onCheckedChange = { selected ->
                                    update { it.copy(referencedMemoryScopes = if (selected) it.referencedMemoryScopes + bucket else it.referencedMemoryScopes - bucket) }
                                })
                                Text(label.ifBlank { bucket }, Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    TextButton(onClick = { nav.navigate(Screen.MemoryFile(id)) }) { Text(stringResource(R.string.character_create)) }
                    Box(Modifier.weight(1f)) {
                        MemoryFilesTab(files, memoryStatus.takeIf { character.enableMemory }, { consolidation.organize(id) },
                            { nav.navigate(Screen.MemoryFile(id, it)) }, { file ->
                            scope.launch { memory.deleteFile(id, file.path, file.version) }
                        }, { tab = 7 })
                    }
                }
            }
        }
    }
    RikkaConfirmDialog(show = deleting && character != null, onDismiss = { deleting = false },
        title = stringResource(R.string.confirm_delete), confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel), onConfirm = {
            deleting = false
            scope.launch {
                try {
                    cleanupCharacterConversations(
                        ownerIds = setOf(owner),
                        clearSessions = { chatService.deleteConversationsOfAssistant(it) },
                        deletePersisted = { conversations.deleteConversationsOfOwner(it) },
                        onComplete = {
                            store.update { it.copy(characters = it.characters.filterNot { c -> c.id == owner },
                                deletedCharacterIds = it.deletedCharacterIds + owner, characterId = it.characterId?.takeUnless { it == owner }) }
                        },
                    )
                    nav.popBackStack()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage ?: e.javaClass.simpleName }
            }
        }) { Text(stringResource(R.string.character_delete_explanation)) }
}

@Composable
internal fun CharacterField(label: Int, value: String, update: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = update, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) })
}
