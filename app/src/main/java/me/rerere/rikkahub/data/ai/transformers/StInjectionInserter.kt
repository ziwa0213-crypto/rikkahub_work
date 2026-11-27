package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection

/** Companion to the upstream inserter. SYSTEM stays SYSTEM for chat/depth positions. */
internal fun insertKeepingRole(messages: List<UIMessage>, byPosition: Map<InjectionPosition, List<PromptInjection>>): List<UIMessage> {
    val result = applyInjections(messages, byPosition.filterKeys {
        it == InjectionPosition.BEFORE_SYSTEM_PROMPT || it == InjectionPosition.AFTER_SYSTEM_PROMPT
    }).toMutableList()
    fun insert(rows: List<PromptInjection>, target: Int) {
        var index = findSafeInsertIndex(result, target)
        rows.forEach { row ->
            result.add(index++, when (row.role) {
                MessageRole.SYSTEM -> UIMessage.system(row.content)
                MessageRole.ASSISTANT -> UIMessage.assistant(row.content)
                else -> UIMessage.user(row.content)
            }.copy(isSynthetic = true))
        }
    }
    byPosition[InjectionPosition.TOP_OF_CHAT]?.let { insert(it, result.indexOfFirst { m -> m.role == MessageRole.USER }.takeIf { i -> i >= 0 } ?: result.size) }
    byPosition[InjectionPosition.BOTTOM_OF_CHAT]?.let { insert(it, (result.size - 1).coerceAtLeast(0)) }
    byPosition[InjectionPosition.AT_DEPTH]?.groupBy { it.injectDepth }?.toSortedMap(reverseOrder())?.forEach { (depth, rows) ->
        insert(rows, (result.size - depth.coerceAtLeast(1)).coerceIn(0, result.size))
    }
    return result
}

/** Depth is relative to the list AFTER worldbook insertion, never the original history. */
internal fun applyCharacterDepthPrompt(messages: List<UIMessage>, character: Character?): List<UIMessage> {
    if (character == null || character.depthPrompt.isBlank()) return messages
    val index = findSafeInsertIndex(messages, (messages.size - character.depthPromptDepth.coerceIn(1, 20)).coerceIn(0, messages.size))
    val message = when (character.depthPromptRole.lowercase()) {
        "assistant" -> UIMessage.assistant(character.depthPrompt)
        "user" -> UIMessage.user(character.depthPrompt)
        else -> UIMessage.system(character.depthPrompt)
    }.copy(isSynthetic = true)
    return messages.toMutableList().apply { add(index, message) }
}
