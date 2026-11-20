package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.Tool
import me.rerere.ai.provider.LocalPromptDrop
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.llamacpp.ChatRequestMapper
import me.rerere.llamacpp.ContextPlanner
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.prompts.buildRowMemoryPrompt

internal data class BudgetedSystemPrompt(
    val system: String,
    val tools: List<Tool>,
    val dropped: List<LocalPromptDrop> = emptyList(),
)

/** Only local requests may omit optional prompt layers. Character estimates decide
 * what to include, never trim text or become token usage. Provider counts the template. */
internal fun buildBudgetedSystemPrompt(
    turn: ChatTurnContext,
    messages: List<UIMessage>,
    contextSize: Int = turn.model.contextLength,
): BudgetedSystemPrompt {
    val assistant = turn.assistant
    val base = buildString {
        val effectiveSystemPrompt =
            if (assistant.allowConversationSystemPrompt && !turn.conversationSystemPrompt.isNullOrBlank()) {
                turn.conversationSystemPrompt
            } else assistant.systemPrompt
        if (effectiveSystemPrompt.isNotBlank()) append(effectiveSystemPrompt)
        appendPulseInstruction(this, turn.pulseInstruction)
    }
    val memory = if (assistant.enableMemory) buildString {
        appendLine()
        append(buildRowMemoryPrompt(turn.rowMemories))
        append(buildMemoryPrompt(turn.memories))
    } else ""
    val toolPrompts = buildString {
        turn.tools.forEach { tool ->
            appendLine()
            append(tool.systemPrompt(turn.model, messages))
        }
    }
    if (turn.providerSetting !is ProviderSetting.LlamaCppLocal) {
        return BudgetedSystemPrompt(base + memory + toolPrompts, turn.tools)
    }

    val inputBudget = contextSize - ContextPlanner.outputBudget(contextSize, assistant.maxTokens)
    var useTools = turn.tools.isNotEmpty()
    var useMemory = assistant.enableMemory
    val schemaChars = if (useTools) ChatRequestMapper.toolsJson(turn.tools).length else 0
    fun estimate(): Double = (base.length.toLong() +
        (if (useMemory) memory.length else 0) +
        (if (useTools) toolPrompts.length.toLong() + schemaChars else 0L)) / 3.0
    val dropped = buildList {
        if (useTools && estimate() > inputBudget * .55) {
            useTools = false
            add(LocalPromptDrop.TOOLS)
        }
        if (useMemory && estimate() > inputBudget * .75) {
            useMemory = false
            add(LocalPromptDrop.MEMORY)
        }
    }
    return BudgetedSystemPrompt(
        system = base + (if (useMemory) memory else "") + (if (useTools) toolPrompts else ""),
        tools = if (useTools) turn.tools else emptyList(),
        dropped = dropped,
    )
}
