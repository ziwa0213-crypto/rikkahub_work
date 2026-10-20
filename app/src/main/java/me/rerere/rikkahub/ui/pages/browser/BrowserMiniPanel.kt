package me.rerere.rikkahub.ui.pages.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.rikkahub.R
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.data.ai.tools.local.filterBrowserContent
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.message.ChatMessageReasoningStep
import me.rerere.rikkahub.ui.components.message.ChatMessageToolStep
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.ErrorCardsDisplay
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@Composable
internal fun BrowserMiniPanel(
    conversationId: Uuid, controller: BrowserController, settingsStore: SettingsStore, modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    service: ChatService = koinInject(),
) {
    val conversation by remember(conversationId) { service.getConversationFlow(conversationId) }.collectAsStateWithLifecycle()
    val job by remember(conversationId) { service.getGenerationJobStateFlow(conversationId) }.collectAsStateWithLifecycle(null)
    val processing by remember(conversationId) { service.getProcessingStatusFlow(conversationId) }.collectAsStateWithLifecycle()
    val errors by service.errors.collectAsStateWithLifecycle()
    val filteredCount by controller.filteredCount.collectAsStateWithLifecycle()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val assistant = settings.assistants.find { it.id == conversation.assistantId }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var offsetY by remember(conversationId) { mutableFloatStateOf(0f) }
    var ballOffsetX by remember(conversationId) { mutableFloatStateOf(0f) }
    var ballOffsetY by remember(conversationId) { mutableFloatStateOf(0f) }
    var draft by remember(conversationId) { mutableStateOf(TextFieldValue()) }
    var busy by remember { mutableStateOf(false) }
    var initialized by remember(conversationId) { mutableStateOf(false) }
    val failure = stringResource(R.string.error_title_operation)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val maxBallOffsetX = with(density) { (screenWidthDp.dp - 96.dp).toPx().coerceAtLeast(0f) }
    val maxBallOffsetY = with(density) { ((screenHeightDp.dp - 104.dp).toPx() / 2f).coerceAtLeast(0f) }
    val maxUp = with(density) { screenHeightDp.dp.toPx() * 0.45f }
    val dragState = rememberDraggableState { delta ->
        offsetY = (offsetY + delta).coerceIn(-maxUp, 0f)
    }

    fun updateExpanded(value: Boolean) {
        if (!value) offsetY = 0f
        onExpandedChange(value)
    }

    LaunchedEffect(expanded) {
        if (!expanded) offsetY = 0f
    }
    LaunchedEffect(maxBallOffsetX, maxBallOffsetY) {
        ballOffsetX = ballOffsetX.coerceIn(-maxBallOffsetX, 0f)
        ballOffsetY = ballOffsetY.coerceIn(-maxBallOffsetY, maxBallOffsetY)
    }

    DisposableEffect(conversationId) {
        service.addConversationReference(conversationId)
        onDispose { service.removeConversationReference(conversationId) }
    }
    LaunchedEffect(conversationId) {
        try { service.initializeConversation(conversationId); initialized = true }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { toaster.show(failure, type = ToastType.Error) }
    }
    // Only the panel's rendering preferences are overridden, never persisted.
    val panelSettings = settings.copy(displaySetting = settings.displaySetting.copy(showThinkingContent = false, autoCloseThinking = true))
    val loading = job != null
    val status = when {
        filteredCount > 0 -> stringResource(R.string.browser_status_filtered, filteredCount)
        loading -> processing ?: stringResource(R.string.browser_ui_working)
        else -> stringResource(R.string.browser_ui_ready)
    }
    val lastText = conversation.currentMessages.lastOrNull()?.parts?.filterIsInstance<UIMessagePart.Text>()
        ?.joinToString("\n") { it.text }.orEmpty()
    val localErrors = errors.filter { it.conversationId == conversationId }
    // Keep a collapsed panel collapsed when an error arrives; the floating ball's
    // status dot advertises it, and the existing error card remains available on tap.
    LaunchedEffect(loading, expanded, scroll.maxValue) { if (loading && expanded) scroll.animateScrollTo(scroll.maxValue) }

    if (!expanded) {
        Box(
            modifier = modifier
                .offset { IntOffset(ballOffsetX.roundToInt(), ballOffsetY.roundToInt()) }
                .zIndex(10f)
                .size(72.dp)
                .semantics {
                    role = Role.Button
                    onClick {
                        updateExpanded(true)
                        true
                    }
                }
                .pointerInput(conversationId, maxBallOffsetX, maxBallOffsetY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var previousX = down.position.x
                        var previousY = down.position.y
                        var dragged = false
                        var pressed = true
                        val touchSlop = viewConfiguration.touchSlop
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - previousX
                            val dy = change.position.y - previousY
                            val totalX = change.position.x - down.position.x
                            val totalY = change.position.y - down.position.y
                            if (!dragged && totalX * totalX + totalY * totalY > touchSlop * touchSlop) {
                                dragged = true
                                ballOffsetX = (ballOffsetX + totalX).coerceIn(-maxBallOffsetX, 0f)
                                ballOffsetY = (ballOffsetY + totalY).coerceIn(-maxBallOffsetY, maxBallOffsetY)
                            } else if (dragged) {
                                ballOffsetX = (ballOffsetX + dx).coerceIn(-maxBallOffsetX, 0f)
                                ballOffsetY = (ballOffsetY + dy).coerceIn(-maxBallOffsetY, maxBallOffsetY)
                            }
                            if (dragged) change.consume()
                            previousX = change.position.x
                            previousY = change.position.y
                            pressed = change.pressed
                        }
                        if (!dragged) updateExpanded(true)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (assistant != null) {
                    UIAvatar(
                        name = assistant.name,
                        value = assistant.avatar,
                        modifier = Modifier.size(32.dp),
                        onClick = { updateExpanded(true) },
                    )
                } else {
                    Icon(HugeIcons.AiMagic, contentDescription = stringResource(R.string.browser_ui_expand_chat))
                }
                if (localErrors.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(7.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
        }
        return
    }

    fun feed(kind: Int) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val raw = when (kind) {
                    0 -> controller.readText(conversationId).getOrThrow()
                    1 -> controller.selectedText(conversationId).getOrThrow()
                    else -> controller.state(conversationId).getOrThrow().let { "${it.title}\n${it.url}" }
                }
                if (raw.isBlank()) {
                    toaster.show(context.getString(if (kind == 1) R.string.browser_ui_no_selection else R.string.browser_ui_unreadable))
                } else {
                    val filtered = filterBrowserContent(settingsStore, raw)
                    controller.recordFilteredCount(filtered.hitCount)
                    val note = context.getString(R.string.browser_ui_untrusted_notice)
                    val next = listOf(draft.text.takeIf { it.isNotBlank() }, "[$note]\n${filtered.text}")
                        .filterNotNull().joinToString("\n\n").take(150_000)
                    draft = TextFieldValue(next, TextRange(next.length))
                    updateExpanded(true)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { toaster.show(failure, type = ToastType.Error) }
            finally { busy = false }
        }
    }
    Surface(
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .fillMaxHeight(0.52f)
            .offset { IntOffset(0, offsetY.roundToInt()) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 6.dp, shadowElevation = 12.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        startDragImmediately = true,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                )
            }
            Row(Modifier.fillMaxWidth().clickable { updateExpanded(!expanded) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                assistant?.let { UIAvatar(it.name, it.avatar, Modifier.size(32.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(assistant?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(if (expanded) status else "$status · $lastText", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { updateExpanded(!expanded) }) {
                    Text(stringResource(if (expanded) R.string.browser_ui_collapse_chat else R.string.browser_ui_expand_chat))
                }
            }
            if (expanded) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompositionLocalProvider(LocalSettings provides panelSettings) {
                        conversation.currentMessages.takeLast(8).forEach { message -> key(message.id) {
                            Text(if (message.role == MessageRole.USER) settings.displaySetting.userNickname.ifBlank { stringResource(R.string.group_owner_name) }
                                else assistant?.name.orEmpty(), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            message.parts.forEach { part -> when (part) {
                                is UIMessagePart.Text -> MarkdownBlock(part.text)
                                is UIMessagePart.Reasoning -> ChainOfThought(steps = listOf(part)) {
                                    ChatMessageReasoningStep(it, model = null, assistant = assistant)
                                }
                                is UIMessagePart.Tool -> ChainOfThought(steps = listOf(part)) {
                                    ChatMessageToolStep(it, loading && !it.isExecuted,
                                        onToolApproval = { id, approved, reason -> service.handleToolApproval(conversationId, id, approved, reason) },
                                        onToolAnswer = { id, answer -> service.handleToolApproval(conversationId, id, true, answer = answer) })
                                }
                                else -> Unit
                            } }
                        } }
                    }
                    ErrorCardsDisplay(localErrors, service::dismissError, { localErrors.forEach { service.dismissError(it.id) } })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(R.string.browser_ui_read_page, R.string.browser_ui_send_selection, R.string.browser_ui_include_url)
                        .forEachIndexed { index, label -> TextButton(enabled = !busy, onClick = { feed(index) }) { Text(stringResource(label)) } }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), maxLines = 4,
                        placeholder = { Text(stringResource(R.string.browser_ui_input_hint)) })
                    if (loading) TextButton(onClick = { scope.launch { service.stopGeneration(conversationId) } }) { Text(stringResource(R.string.stop)) }
                    else TextButton(enabled = initialized && draft.text.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                // Re-filter edited drafts at the final send boundary as well.
                                val filtered = filterBrowserContent(settingsStore, draft.text)
                                controller.recordFilteredCount(filtered.hitCount)
                                service.sendMessage(conversationId, listOf(UIMessagePart.Text(filtered.text)))
                                draft = TextFieldValue()
                            } catch (error: CancellationException) { throw error }
                            catch (_: Exception) { toaster.show(failure, type = ToastType.Error) }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.send)) }
                }
            }
        }
    }
}
