package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.st.`import`.CharacterCardCodec
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.data.model.defaultCharacterPromptModules
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import me.rerere.ui.components.RikkaConfirmDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
internal fun CharacterPage() {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val store: SettingsStore = koinInject()
    val repo: ConversationRepository = koinInject()
    val chatService: ChatService = koinInject()
    val memory: MemoryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val files: FilesManager = koinInject()
    var adding by remember { mutableStateOf(false) }
    var greetingCharacter by remember { mutableStateOf<Character?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    // 旧版本删除角色时保留了会话；全部清理成功后才移除这一批待清理ID。
    LaunchedEffect(settings.deletedCharacterIds) {
        val pending = settings.deletedCharacterIds
        if (pending.isEmpty()) return@LaunchedEffect
        try {
            cleanupCharacterConversations(
                ownerIds = pending,
                clearSessions = { chatService.deleteConversationsOfAssistant(it) },
                deletePersisted = { repo.deleteConversationsOfOwner(it) },
                onComplete = {
                    store.update { it.copy(deletedCharacterIds = it.deletedCharacterIds - pending) }
                },
            )
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.localizedMessage ?: e.javaClass.simpleName }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTagNames by remember { mutableStateOf(emptySet<String>()) }
    var deleteTarget by remember { mutableStateOf<Character?>(null) }
    val allCharacterTags = remember(settings.characters) {
        settings.characters.flatMap { it.tags }.filter { it.isNotBlank() }.distinct()
    }
    LaunchedEffect(allCharacterTags) {
        // Deleting the last role with a selected tag must not leave an invisible, uncleared filter.
        selectedTagNames = selectedTagNames.intersect(allCharacterTags.toSet())
    }
    val filteredCharacters = remember(settings.characters, searchQuery, selectedTagNames) {
        settings.characters.filter { character ->
            val matchesSearch = searchQuery.isBlank() ||
                character.name.contains(searchQuery, ignoreCase = true)
            val matchesTags = selectedTagNames.isEmpty() ||
                character.tags.any { it in selectedTagNames }
            matchesSearch && matchesTags
        }
    }
    val isFiltering = searchQuery.isNotBlank() || selectedTagNames.isNotEmpty()

    fun startChat(character: Character, greeting: String? = null) { scope.launch {
        val chatId = Uuid.random()
        val presets = character.presetMessages.ifEmpty {
            (greeting ?: character.firstMessage).takeIf(String::isNotBlank)?.let { listOf(UIMessage.assistant(it)) }.orEmpty()
        }
        repo.insertConversation(Conversation.ofId(chatId, assistantId = character.id, newConversation = true).updateCurrentMessages(presets))
        store.update { it.copy(uiMode = UiMode.ROLE, characterId = character.id) }
        greetingCharacter = null
        navigateToChatPage(nav, chatId)
    } }
    fun createCharacter() { scope.launch {
        val now = System.currentTimeMillis()
        val fresh = Character(promptModules = defaultCharacterPromptModules(), createdAt = now, updatedAt = now)
        store.update { it.copy(characters = it.characters + fresh) }
        nav.navigate(Screen.CharacterDetail(fresh.id.toString()))
    } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else ""
                    }.orEmpty()
                    if (!name.endsWith(".png", true) && !name.endsWith(".json", true)) null else
                        context.contentResolver.openInputStream(uri)?.use { CharacterCardCodec.parse(it.readBytes(), name) }
                }
                if (parsed == null) { message = context.getString(R.string.character_import_invalid); return@launch }
                val avatar = parsed.imageBytes?.let { bytes ->
                    val file = files.saveManagedFromBytes("images", bytes, "character-${Uuid.random()}.png", "image/png")
                    Avatar.Image(files.getFile(file).toUri().toString())
                } ?: Avatar.Dummy
                val (character, books) = parsed.materialize(avatar, context.getString(R.string.character_embedded_book, parsed.card.name))
                store.update { it.copy(characters = it.characters + character, lorebooks = it.lorebooks + books) }
                android.widget.Toast.makeText(context, context.getString(R.string.character_import_complete, character.name) +
                    if (parsed.rawCardTruncated) "\n" + context.getString(R.string.character_raw_truncated) else "", android.widget.Toast.LENGTH_LONG).show()
                nav.navigate(Screen.CharacterDetail(character.id.toString()))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = context.getString(R.string.character_file_failed, e.localizedMessage.orEmpty()) }
            finally { importing = false }
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.character_settings_title)) }, navigationIcon = { BackButton() },
            colors = CustomColors.topBarColors, scrollBehavior = scroll, actions = {
            TextButton(onClick = { nav.navigate(Screen.CharacterTransfer) }) {
                Text(stringResource(R.string.character_transfer))
            }
            Box {
                IconButton(enabled = !importing, onClick = { adding = true }) {
                    Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.add))
                }
                DropdownMenu(adding, onDismissRequest = { adding = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.character_create)) }, onClick = { adding = false; createCharacter() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.character_import_card)) }, onClick = {
                        adding = false; importLauncher.launch(arrayOf("*/*"))
                    })
                }
            }
        })
    }, modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = 16.dp)
                .consumeWindowInsets(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.character_page_search_placeholder)) },
                leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            if (allCharacterTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(allCharacterTags, key = { it }) { tag ->
                        FilterChip(
                            onClick = {
                                selectedTagNames = if (tag in selectedTagNames) selectedTagNames - tag else selectedTagNames + tag
                            },
                            label = { Text(tag) },
                            selected = tag in selectedTagNames,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }

            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                if (!isFiltering && from.index in filteredCharacters.indices && to.index in filteredCharacters.indices) {
                    val fromId = from.key
                    val toId = to.key
                    scope.launch {
                        store.update { current ->
                            // Rebase on the latest settings so an import/edit/deletion is not overwritten by a drag snapshot.
                            val fromIndex = current.characters.indexOfFirst { it.id == fromId }
                            val toIndex = current.characters.indexOfFirst { it.id == toId }
                            if (fromIndex < 0 || toIndex < 0) current else current.copy(
                                characters = current.characters.toMutableList().apply {
                                    add(toIndex, removeAt(fromIndex))
                                },
                            )
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                if (filteredCharacters.isEmpty()) item {
                    Text(stringResource(R.string.character_no_selection), Modifier.padding(16.dp))
                }
                items(filteredCharacters, key = { it.id }) { character ->
                    ReorderableItem(state = reorderableState, key = character.id) { isDragging ->
                        val memoryFiles = remember(memory, character.id) { memory.getFilesFlow(character.id.toString()) }
                        val memories by memoryFiles.collectAsStateWithLifecycle(initialValue = emptyList())
                        CharacterItem(
                            character = character,
                            memories = memories,
                            onClick = { nav.navigate(Screen.CharacterDetail(character.id.toString())) },
                            onNewChat = {
                                if (character.presetMessages.isEmpty() && character.alternateGreetings.isNotEmpty()) greetingCharacter = character
                                else startChat(character)
                            },
                            onDelete = { deleteTarget = character },
                            modifier = Modifier.fillMaxWidth().animateItem()
                                .then(longPressReorder(isDragging, enabled = !isFiltering)),
                        )
                    }
                }
                if (settings.deletedCharacterIds.isNotEmpty()) item {
                    val history by produceState(emptyList<me.rerere.rikkahub.data.model.Conversation>(), settings.deletedCharacterIds) {
                        value = settings.deletedCharacterIds.flatMap { id ->
                            repo.getConversationsOfAssistant(id).first()
                        }
                    }
                    history.forEach { chat ->
                        TextButton(onClick = { navigateToChatPage(nav, chat.id) }) {
                            Text(chat.title.ifBlank { stringResource(R.string.chat_page_new_chat) } + " " + stringResource(R.string.character_deleted_suffix))
                        }
                    }
                }
            }
        }
    }
    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { target ->
                deleteTarget = null
                scope.launch {
                    try {
                        cleanupCharacterConversations(
                            ownerIds = setOf(target.id),
                            clearSessions = { chatService.deleteConversationsOfAssistant(it) },
                            deletePersisted = { repo.deleteConversationsOfOwner(it) },
                            onComplete = {
                                store.update { current ->
                                    current.copy(
                                        characters = current.characters.filterNot { it.id == target.id },
                                        characterId = current.characterId?.takeUnless { it == target.id },
                                    )
                                }
                            },
                        )
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = e.localizedMessage ?: e.javaClass.simpleName }
                }
            }
        },
        onDismiss = { deleteTarget = null },
    ) { Text(stringResource(R.string.character_delete_explanation)) }

    greetingCharacter?.let { character -> ModalBottomSheet(onDismissRequest = { greetingCharacter = null }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.character_choose_greeting), style = MaterialTheme.typography.titleLarge) }
            items((listOf(character.firstMessage) + character.alternateGreetings).distinct()) { greeting ->
                CardGroup { item(onClick = { startChat(character, greeting) }, headlineContent = { Text(greeting.ifBlank { stringResource(R.string.character_greeting) }) }) }
            }
        }
    } }
}

@Composable
private fun CharacterItem(
    character: Character,
    memories: List<MemoryFile>,
    onClick: () -> Unit,
    onNewChat: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackName = stringResource(R.string.group_mode_role)
    val tags = character.tags.filter { it.isNotBlank() }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UIAvatar(name = character.name.ifBlank { fallbackName }, value = character.avatar, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = character.name.ifBlank { fallbackName },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (character.enableMemory) {
                        Tag(type = TagType.SUCCESS) {
                            Text(stringResource(R.string.assistant_page_memory_count, memories.size))
                        }
                    }
                    tags.take(2).forEach { tag ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (tags.size > 2) {
                        Text(
                            text = "+${tags.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ItemActionMenu(actions = listOf(
                ItemAction(
                    text = stringResource(R.string.chat_page_new_chat),
                    icon = HugeIcons.MessageAdd01,
                    onClick = onNewChat,
                ),
                ItemAction(
                    text = stringResource(R.string.delete),
                    icon = HugeIcons.Delete01,
                    destructive = true,
                    onClick = onDelete,
                ),
            ))
        }
    }
}
