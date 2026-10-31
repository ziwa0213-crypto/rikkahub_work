package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.abs
import me.rerere.rikkahub.data.model.groupQuote
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.memberRefOf
import me.rerere.rikkahub.data.model.displayNameOf
import me.rerere.rikkahub.ui.pages.group.LocalGroupChatContext
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.urlDecode
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec
import me.rerere.ui.webview.WebViewContentCache
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn

@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    groupMode: Boolean = false,
    onQuote: (UIMessageAnnotation.GroupQuote) -> Unit = {},
    onMention: (String) -> Unit = {},
) {
    val message = node.messages[node.selectIndex]
    if (message.isContextCheckpoint) {
        ChatMessageContextCheckpoint(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    val settings = LocalSettings.current.displaySetting
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(settings)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * settings.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * settings.fontSizeRatio,
        fontFamily = chatFontFamily
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val groupSender = message.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()
    val isGroupAssistant = message.role == MessageRole.ASSISTANT && groupSender != null
    val ownerName = settings.userNickname.ifBlank { stringResource(R.string.group_owner_name) }
    val quote by rememberUpdatedState(message.groupQuote(ownerName))
    val onQuoteState by rememberUpdatedState(onQuote)
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val maxOffset = with(density) { 110.dp.toPx() }
    val edge = with(density) { 24.dp.toPx() }
    val windowWidth by rememberUpdatedState(LocalWindowInfo.current.containerSize.width)
    var bubbleLeft by remember { mutableFloatStateOf(0f) }
    val outward = if (message.role == MessageRole.USER) 1f else -1f
    var dragOffset by remember(message.id, groupMode) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, spring(), label = "groupQuoteSwipe")
    val swipeModifier = if (groupMode && (isGroupAssistant || message.role == MessageRole.USER)) {
        Modifier.onGloballyPositioned { bubbleLeft = it.positionInWindow().x - animatedOffset }
            .graphicsLayer { translationX = animatedOffset }.pointerInput(message.id, groupMode) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Leave drawer-edge gestures alone, including outward drags on right-side user bubbles.
                val windowX = bubbleLeft + down.position.x
                if (windowX < edge || windowX > windowWidth - edge) return@awaitEachGesture
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    if (overSlop * outward > 0) {
                        change.consume()
                        dragOffset = (overSlop * 0.7f * outward).coerceIn(0f, maxOffset) * outward
                    }
                }
                if (drag != null) {
                    val finished = horizontalDrag(drag.id) { change ->
                        dragOffset = ((dragOffset + (change.position.x - change.previousPosition.x) * 0.7f) * outward)
                            .coerceIn(0f, maxOffset) * outward
                        change.consume()
                    }
                    if (finished && abs(dragOffset) >= threshold) onQuoteState(quote)
                }
                dragOffset = 0f
            }
        }
    } else Modifier
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!message.parts.isEmptyUIMessage()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (message.role == MessageRole.ASSISTANT && groupSender != null) {
                    val group = LocalGroupChatContext.current
                    val member = LocalSettings.current.groupMembers.find { it.id.toString() == groupSender.memberId }
                    val ref = group?.memberRefOf(groupSender.memberId)
                    val displayName = group.displayNameOf(groupSender, member)
                    val displayAvatar = ref?.avatar ?: member?.avatar ?: Avatar.Dummy
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UIAvatar(name = displayName, value = displayAvatar,
                            modifier = Modifier.size(28.dp), loading = loading,
                            onLongClick = if (groupMode) ({ onMention(displayName) }) else null)
                        Text(displayName, style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                } else ChatMessageAssistantAvatar(
                    message = message,
                    model = model,
                    assistant = assistant,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                ChatMessageUserAvatar(
                    message = message,
                    avatar = settings.userAvatar,
                    nickname = settings.userNickname,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ProvideTextStyle(textStyle) {
            Column(modifier = if (groupMode) swipeModifier.fillMaxWidth() else swipeModifier,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start) {
            MessagePartsBlock(
                assistant = assistant,
                role = message.role,
                parts = message.parts,
                annotations = message.annotations,
                loading = loading,
                model = model,
                isGroupAssistant = isGroupAssistant,
                groupMode = groupMode,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
            )
            }

            message.translation?.let { translation ->
                CollapsibleTranslationText(
                    content = translation,
                    onClickCitation = {}
                )
            }
        }

        val showActions = if (lastMessage) {
            !loading
        } else {
            message.parts.isEmptyUIMessage().not()
        }

        val motionScheme = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 } +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 } +
                fadeOut(motionScheme.fastEffectsSpec())
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    node = node,
                    onUpdate = onUpdate,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    onTranslate = onTranslate,
                    onClearTranslation = onClearTranslation
                )
            }
        }

        EditedFilesList(
            parts = message.parts,
            assistant = assistant,
        )

        ProvideTextStyle(textStyle) {
            ChatMessageNerdLine(message = message)
        }

    }
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onFork = onFork,
            model = model,
            onSelectAndCopy = {
                showSelectCopySheet = true
            },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onWebViewPreview = {
                val textContent = message.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()
                if (textContent.isNotBlank()) {
                    val htmlContent = buildMarkdownPreviewHtml(
                        context = context,
                        markdown = textContent,
                        colorScheme = colorScheme
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    isGroupAssistant: Boolean = false,
    groupMode: Boolean = false,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val partsState by rememberUpdatedState(parts)
    val groupQuote = if (groupMode) annotations.filterIsInstance<UIMessageAnnotation.GroupQuote>().firstOrNull() else null
    val firstTextIndex = parts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex < 0 && groupQuote != null) GroupQuoteBlock(groupQuote)

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(settings.displaySetting) {
        snapshotFlow { partsState }
            .debounce(50.milliseconds)
            .collect { parts ->
                if (parts.isNotEmpty() && loading && settings.displaySetting.enableMessageGenerationHapticEffect) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    val groupedParts = remember(parts) { parts.groupMessageParts() }
    groupedParts.fastForEach { block ->
        when (block) {
            is MessagePartBlock.ThinkingBlock -> {
                if (block.steps.isNotEmpty()) {
                    val isReasoningOnlyBlock = block.steps.fastAll { it is ThinkingStep.ReasoningStep }
                    ChainOfThought(
                        modifier = Modifier.animateContentSize(),
                        steps = block.steps,
                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                        cardColors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        assistant = assistant,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                        requestingMemberName = annotations.filterIsInstance<UIMessageAnnotation.GroupSender>()
                                            .firstOrNull()?.let { sender ->
                                                LocalGroupChatContext.current.displayNameOf(sender,
                                                    settings.groupMembers.find { it.id.toString() == sender.memberId })
                                            },
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }
                        }
                    }
                }
            }

            is MessagePartBlock.ChartBlock -> key(block.index) {
                val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                spec?.let { ChartCard(spec = it) }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        val textContent = @Composable {
                            if (role == MessageRole.USER) {
                                ChatMessageBubble(
                                    role = role,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = settings.displaySetting.bubbleOpacity),
                                    onClick = { onUserMessageClick?.invoke() },
                                    // 用户气泡不占满整行，和助手的回复错开
                                    modifier = Modifier
                                        .padding(start = 24.dp)
                                        .animateContentSize(),
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        if (block.index == firstTextIndex && groupQuote != null) GroupQuoteBlock(groupQuote)
                                        MarkdownBlock(
                                            content = part.text.replaceRegexes(
                                                assistant = assistant,
                                                scope = AssistantAffectScope.USER,
                                                visual = true,
                                            ),
                                            onClickCitation = handleClickCitation
                                        )
                                    }
                                }
                            } else {
                                if (settings.displaySetting.showAssistantBubble || isGroupAssistant) {
                                    Surface(
                                        modifier = Modifier.animateContentSize(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            if (block.index == firstTextIndex && groupQuote != null) GroupQuoteBlock(groupQuote)
                                            MarkdownBlock(
                                                content = part.text.replaceRegexes(
                                                    assistant = assistant,
                                                    scope = AssistantAffectScope.ASSISTANT,
                                                    visual = true,
                                                ),
                                                onClickCitation = handleClickCitation,
                                            )
                                        }
                                    }
                                } else {
                                    MarkdownBlock(
                                        content = part.text.replaceRegexes(
                                            assistant = assistant,
                                            scope = AssistantAffectScope.ASSISTANT,
                                            visual = true,
                                        ),
                                        onClickCitation = handleClickCitation,
                                        modifier = Modifier
                                            .animateContentSize()
                                    )
                                }
                            }
                        }

                        // 流式生成期间不启用 SelectionContainer：Markdown 在不断重渲染，
                        // 内部可选择的 Text 会频繁注册/注销，与 Compose 选择工具栏在绘制阶段
                        // 对 selectable 列表的排序产生并发修改，导致 ConcurrentModificationException。
                        // 生成结束后内容稳定，再启用文本选择。
                        if (loading) {
                            textContent()
                        } else {
                            SelectionContainer {
                                textContent()
                            }
                        }
                    }

                    is UIMessagePart.Video -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.Video01,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Audio -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.MusicNote03,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Image -> {
                        val isImageLoading =
                            part.url.isBlank() || part.url.matches(Regex("^data:image/[^;]*;base64,\\s*$"))
                        if (isImageLoading) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .shimmer(isLoading = true)
                            )
                        } else {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .height(72.dp)
                            )
                        }
                    }

                    is UIMessagePart.Document -> {
                        ChatMessageFileChip(
                            text = part.fileName,
                            onClick = { context.openLocalFile(part.url) },
                            icon = {
                                when (part.mime) {
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.docx),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    "application/pdf" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.pdf),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = HugeIcons.File02,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                        )
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.any { it is UIMessageAnnotation.UrlCitation }) {
        ChatMessageCitations(annotations = annotations)
    }
}

private val BubbleCorner = 24.dp
private val BubbleTailCorner = 6.dp
private val BubblePressedCorner = 12.dp

/**
 * 消息气泡：大圆角，靠头像一侧的顶角收紧；可点击时按下其余圆角也跟着收紧
 */
@Composable
private fun ChatMessageBubble(
    role: MessageRole,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressProgress by animatePressProgress(interactionSource)
    val corner = lerp(BubbleCorner, BubblePressedCorner, pressProgress)
    val shape = if (role == MessageRole.USER) {
        RoundedCornerShape(topStart = corner, topEnd = BubbleTailCorner, bottomEnd = corner, bottomStart = corner)
    } else {
        RoundedCornerShape(topStart = BubbleTailCorner, topEnd = corner, bottomEnd = corner, bottomStart = corner)
    }
    val contentColor = contentColorFor(color)
    Box(
        modifier = modifier
            .clipDrawing(shape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
                } else {
                    Modifier
                },
            )
            // MarkdownBlock 自带 4dp 的水平内边距
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/**
 * 只裁剪绘制内容，不像 [clip] 那样把形状设成图层轮廓。
 *
 * 四个角半径不同时图层轮廓是 Path，Compose 判断触摸点是否落在轮廓内时用 Path 求交，
 * 坐标超过 8192px 后 float 精度不够，求交结果为空，长消息下半部分（例如展开的长代码块）就收不到触摸了。
 */
private fun Modifier.clipDrawing(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

@Composable
private fun ChatMessageCitations(annotations: List<UIMessageAnnotation>) {
    var expand by remember { mutableStateOf(false) }
    val urls = remember(annotations) {
        annotations.mapNotNull { annotation ->
            when (annotation) {
                is UIMessageAnnotation.UrlCitation -> annotation.url
                else -> null
            }
        }
    }
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatMessageFileChip(
            text = stringResource(R.string.citations_count, annotations.count { it is UIMessageAnnotation.UrlCitation }),
            onClick = { expand = !expand },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { FaviconRow(urls = urls, size = 18.dp) },
        )
        if (expand) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        annotations.filterIsInstance<UIMessageAnnotation.UrlCitation>().fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupQuoteBlock(quote: UIMessageAnnotation.GroupQuote) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(8.dp).widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${stringResource(R.string.group_quote_reply_prefix)} ${quote.targetSenderName}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(quote.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
