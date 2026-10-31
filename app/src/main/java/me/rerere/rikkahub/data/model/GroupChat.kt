package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.uuid.Uuid

val GROUP_CHAT_ASSISTANT_ID: Uuid = Uuid.parse("8d78b652-5242-4f1b-9c03-a7712328b5c4")

fun Conversation.isGroupChat(): Boolean = assistantId == GROUP_CHAT_ASSISTANT_ID

/** Builds the shared-settings assistant view used only by the group chat input. */
fun Group.toInputAssistant(): Assistant = Assistant(
    id = GROUP_CHAT_ASSISTANT_ID,
    name = name,
    avatar = avatar,
    chatModelId = null,
    systemPrompt = systemPrompt,
    enableMemory = enableMemory,
    reasoningLevel = reasoningLevel,
    toolApprovalMode = toolApprovalMode,
    customHeaders = customHeaders,
    customBodies = customBodies,
    mcpServers = mcpServers,
    localTools = localTools,
    enableWebSearch = enableWebSearch,
    workspaceId = workspaceId,
    background = background,
    backgroundOpacity = backgroundOpacity,
    useGradientBackground = useGradientBackground,
    modeInjectionIds = modeInjectionIds,
    lorebookIds = lorebookIds,
    enabledSkills = enabledSkills,
    enableRecentChatsReference = enableRecentChatsReference,
    enableTimeReminder = enableTimeReminder,
    timeReminderIntervalMinutes = timeReminderIntervalMinutes,
)

internal fun defaultConversationTitle(base: String, existing: List<String>): String {
    if (base !in existing) return base
    var number = 2
    while ("$base $number" in existing) number++
    return "$base $number"
}

fun Group.enabledMembers(settings: Settings, deputyFirst: Boolean = false): List<GroupMember> = members
    .filter { it.enabled }.sortedBy { it.priority }
    .mapNotNull { ref -> settings.groupMembers.find { it.id == ref.memberId } }
    .let { ordered ->
        val others = ordered.filter { it.id != deputyMemberId }
        val deputies = ordered.filter { it.id == deputyMemberId }
        if (deputyFirst) deputies + others else others + deputies
    }

fun Group.speakingMembers(settings: Settings, text: String, deputyFirst: Boolean = false): List<GroupMember> {
    val enabled = enabledMembers(settings, deputyFirst)
    val aliases = members.associate { it.memberId to it.nickname }
    val mentioned = extractMentions(text,
        settings.groupMembers.filter { member -> members.any { it.memberId == member.id } }, aliases).toSet()
    return enabled.filter { it.id in mentioned }.ifEmpty { enabled }
}

// Match long names first so @Ann Lee never also selects @Ann; plain text stays unchanged.
fun extractMentions(text: String, members: List<GroupMember>, aliases: Map<Uuid, String> = emptyMap()): List<Uuid> {
    val names = members.flatMap { member ->
        listOfNotNull(
            member.name.takeIf { it.isNotBlank() }?.let { member.id to it },
            aliases[member.id]?.takeIf { it.isNotBlank() }?.let { member.id to it },
        )
    }.groupBy({ it.second }, { it.first })
    val orderedNames = names.keys.sortedByDescending { it.length }
    val result = linkedSetOf<Uuid>()
    var index = 0
    while (index < text.length) {
        if (text[index] == '@' && (index == 0 || !text[index - 1].isLetterOrDigit() && text[index - 1] != '_')) {
            val name = orderedNames.firstOrNull { name ->
                text.startsWith(name, index + 1) && (index + 1 + name.length == text.length ||
                    text[index + 1 + name.length].let { !it.isLetterOrDigit() && it != '_' })
            }
            if (name != null) {
                result += names.getValue(name)
                index += name.length
            }
        }
        index++
    }
    return result.toList()
}

internal data class GroupDispatch(val members: List<GroupMember>, val matched: Boolean)

internal fun Group.resolveDispatch(settings: Settings, text: String): GroupDispatch {
    if (!enableDeputyDispatch) return GroupDispatch(emptyList(), false)
    val candidates = enabledMembers(settings).filter { it.id != deputyMemberId }
    val named = extractMentions(text, settings.groupMembers, members.associate { it.memberId to it.nickname })
        .mapNotNull { id -> candidates.find { it.id == id } }
    val selected = named.ifEmpty {
        settings.groupTags.filter { text.containsMention(it.name) }
            .flatMap { tag -> candidates.filter { tag.id in it.tags } }.distinctBy { it.id }
    }.sortedByDescending { member -> members.find { it.memberId == member.id }?.talkativeness ?: 0.5f }
    return GroupDispatch(selected, selected.isNotEmpty())
}

internal fun String.containsMention(name: String): Boolean = name.isNotBlank() &&
    extractMentions(this, listOf(GroupMember(id = GROUP_CHAT_ASSISTANT_ID, name = name))).isNotEmpty()

// Tool use can produce several deputy messages before DISPATCH has actually completed.
// A resumed deputy is the summary only once another member has started speaking.
internal fun List<UIMessage>.completedDispatchText(deputyId: Uuid?, resumingMemberId: String?): String {
    if (deputyId == null) return ""
    fun UIMessage.senderId() = annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()?.memberId
    val deputy = deputyId.toString()
    val firstOther = indexOfFirst { it.role == MessageRole.ASSISTANT && it.senderId()?.let { id -> id != deputy } == true }
    if (resumingMemberId == deputy && firstOther < 0) return ""
    return (if (firstOther < 0) this else take(firstOther))
        .filter { it.role == MessageRole.ASSISTANT && it.senderId() == deputy }.joinToString("\n") { it.toText() }
}

fun Conversation.copyForGroup(partCopy: (UIMessagePart) -> UIMessagePart): Conversation = copy(
    id = Uuid.random(),
    messageNodes = messageNodes.map { node -> node.copy(id = Uuid.random(),
        messages = node.messages.map { message -> message.copy(parts = message.parts.map(partCopy)) }) },
)

fun GroupMember.toAssistant(group: Group, framePrompt: String = ""): Assistant = Assistant(
    id = id, name = group.displayNameOf(this), avatar = group.displayAvatarOf(this), useAssistantAvatar = true,
    chatModelId = chatModelId, tags = tags, streamOutput = streamOutput,
    systemPrompt = listOf(group.systemPrompt, systemPrompt, framePrompt)
        .filter { it.isNotBlank() }.joinToString("\n\n"),
    temperature = temperature, topP = topP, maxTokens = maxTokens,
    contextMessageLimit = contextMessageLimit, reasoningLevel = group.reasoningLevel,
    toolApprovalMode = group.toolApprovalMode,
    enableMemory = group.enableMemory, localTools = group.localTools,
    mcpServers = group.mcpServers, enabledSkills = group.enabledSkills,
    modeInjectionIds = group.modeInjectionIds, lorebookIds = group.lorebookIds,
    customHeaders = group.customHeaders, customBodies = group.customBodies,
    background = group.background, backgroundOpacity = group.backgroundOpacity,
    useGradientBackground = group.useGradientBackground,
    workspaceId = group.workspaceId,
    enableRecentChatsReference = group.enableRecentChatsReference,
    enableWebSearch = group.enableWebSearch,
    enableTimeReminder = group.enableTimeReminder,
    timeReminderIntervalMinutes = group.timeReminderIntervalMinutes,
    allowConversationSystemPrompt = false,
    allowConversationPromptInjection = false,
)

fun UIMessage.withGroupSender(member: GroupMember, fallbackName: String): UIMessage =
    if (role != MessageRole.ASSISTANT) this else copy(
        annotations = annotations.filterNot { it is UIMessageAnnotation.GroupSender } +
            UIMessageAnnotation.GroupSender(member.id.toString(), member.name.ifBlank { fallbackName }),
    )

// Prefixes are request-only: persisted text and tool arguments remain untouched.
fun List<UIMessage>.withGroupSpeakerPrefixes(contextMessageLimit: Int = 0, replyLabel: String = "Reply"): List<UIMessage> {
    val byId = associateBy { it.id.toString() }
    val request = mapNotNull { message ->
        val sender = message.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()
        val quote = message.annotations.filterIsInstance<UIMessageAnnotation.GroupQuote>().firstOrNull()
        val prefix = buildString {
            if (sender != null && message.role == MessageRole.ASSISTANT) append("[${sender.memberName}] ")
            if (quote != null && message.role in listOf(MessageRole.USER, MessageRole.ASSISTANT)) {
                val preview = byId[quote.targetMessageId]?.toText()?.take(60) ?: quote.preview
                append("[$replyLabel ${quote.targetSenderName}:$preview] ")
            }
        }
        when {
            message.parts.isEmpty() && message.role == MessageRole.ASSISTANT && sender != null -> null
            prefix.isEmpty() -> message
            else -> message.copy(parts = message.parts.withSpeakerPrefix(prefix))
        }
    }
    return request.filter { it.role == MessageRole.SYSTEM } +
        request.filter { it.role != MessageRole.SYSTEM }.limitContext(contextMessageLimit)
}

/**
 * Annotate the first existing Text without inserting or reordering parts. Responses API
 * emits parts in order, so a new leading Text would push reasoning behind an assistant item.
 * Reasoning/tool-only messages have no text to annotate and must remain untouched.
 */
private fun List<UIMessagePart>.withSpeakerPrefix(prefix: String): List<UIMessagePart> {
    val index = indexOfFirst { it is UIMessagePart.Text }
    if (index < 0) return this
    return mapIndexed { i, part ->
        if (i == index && part is UIMessagePart.Text) part.copy(text = prefix + part.text) else part
    }
}

fun UIMessage.groupQuote(ownerName: String): UIMessageAnnotation.GroupQuote = UIMessageAnnotation.GroupQuote(
    targetMessageId = id.toString(),
    targetSenderName = annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()?.memberName ?: ownerName,
    preview = toText().replace('\n', ' ').trim().take(60),
)

fun List<UIMessage>.availableGroupQuotes(ownerName: String): List<UIMessageAnnotation.GroupQuote> = filter { message ->
    (message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT &&
        message.annotations.any { it is UIMessageAnnotation.GroupSender }) && message.toText().isNotBlank()
}.takeLast(10).map { it.groupQuote(ownerName) }

private val QUOTE_PATTERN = Regex("""[\[【]{2}\s*quote\s*[:：]\s*([A-Za-z0-9\-]+)\s*[\]】]{2}""", RegexOption.IGNORE_CASE)

fun parseGroupQuotes(text: String): Pair<String, List<String>> {
    val matches = QUOTE_PATTERN.findAll(text).toList()
    if (matches.isEmpty()) return text to emptyList()
    return text.replace(QUOTE_PATTERN, "").trimStart() to matches.map { it.groupValues[1] }
}

fun UIMessage.applyGroupQuotes(available: List<UIMessageAnnotation.GroupQuote>): UIMessage {
    if (role != MessageRole.ASSISTANT) return this
    val index = parts.indexOfFirst { it is UIMessagePart.Text }
    if (index < 0) return this
    val text = (parts[index] as UIMessagePart.Text)
    val (cleaned, ids) = parseGroupQuotes(text.text)
    val target = ids.firstNotNullOfOrNull { id -> available.find { it.targetMessageId == id } } ?: return this
    return copy(parts = parts.mapIndexed { i, part -> if (i == index) text.copy(text = cleaned) else part },
        annotations = annotations.filterNot { it is UIMessageAnnotation.GroupQuote } + target)
}

/**
 * Remove the current speaker's imitated label only at the beginning of assistant body text.
 * Do not search later Text parts: those are inside the body, even if they start with a label.
 * Reasoning, tool arguments, other speakers and user messages are never changed.
 */
fun UIMessage.stripSelfSpeakerPrefix(memberName: String): UIMessage {
    if (role != MessageRole.ASSISTANT || memberName.isBlank()) return this
    val index = parts.indexOfFirst { it is UIMessagePart.Text }
    if (index < 0) return this
    val part = parts[index] as UIMessagePart.Text
    val name = Regex.escape(memberName)
    val pattern = Regex("^\\s*(?:\\[\\s*$name\\s*]|【\\s*$name\\s*】)\\s*[：:]?\\s*")
    val text = part.text.replaceFirst(pattern, "")
    if (text == part.text) return this
    return copy(parts = parts.mapIndexed { i, original ->
        if (i == index) part.copy(text = text) else original
    })
}

fun Group.memberRefOf(memberId: Uuid): GroupMemberRef? = members.find { it.memberId == memberId }

fun Group.memberRefOf(memberId: String): GroupMemberRef? = members.find { it.memberId.toString() == memberId }

fun Group.displayNameOf(member: GroupMember): String =
    memberRefOf(member.id)?.nickname?.takeIf { it.isNotBlank() } ?: member.name

fun Group.displayAvatarOf(member: GroupMember): Avatar = memberRefOf(member.id)?.avatar ?: member.avatar

/** Preserve historical sender labels when the group/member is unavailable. */
fun Group?.displayNameOf(sender: UIMessageAnnotation.GroupSender, member: GroupMember?): String {
    val ref = this?.memberRefOf(sender.memberId) ?: return sender.memberName
    return ref.nickname.takeIf { it.isNotBlank() } ?: member?.name ?: sender.memberName
}
