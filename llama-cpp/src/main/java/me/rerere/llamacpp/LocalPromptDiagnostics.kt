package me.rerere.llamacpp

import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.LocalPromptDrop
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage

/** Failure-only diagnostic counts. These raw-text counts are approximate components,
 * not the authoritative template/media count, and never enter usage or clipping. */
internal fun promptTooLong(
    contextSize: Int,
    inputBudget: Int,
    messages: List<UIMessage>,
    params: TextGenerationParams,
    countTokens: (String) -> Int,
): LocalModelException {
    fun count(text: () -> String): Int = try {
        text().let { if (it.isEmpty()) 0 else countTokens(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) { -1 }
    val system = count { messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n") { it.toText() } }
    val tools = count { if (params.tools.isEmpty()) "" else ChatRequestMapper.toolsJson(params.tools) }
    val history = count {
        messages.filter { it.role != MessageRole.SYSTEM }.takeIf { it.isNotEmpty() }
            ?.let { ChatRequestMapper.toRequestJson(it, params.copy(tools = emptyList())) }.orEmpty()
    }
    return LocalModelException(LocalModelError.TOO_LONG,
        "nCtx=$contextSize input=$inputBudget system≈$system tools≈$tools hist=$history")
}

internal fun localPromptNotices(dropped: List<LocalPromptDrop>, notice: (String) -> String): List<String> =
    dropped.distinct().map { notice(when (it) {
        LocalPromptDrop.TOOLS -> "tools_dropped"
        LocalPromptDrop.MEMORY -> "memory_dropped"
    }) }
