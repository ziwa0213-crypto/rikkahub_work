package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext

data class CompressionBudget(val inputTokens: Int, val outputTokens: Int?, val threshold: Float?)

object CompressionBudgetRules {
    fun forLocal(contextLength: Int): CompressionBudget = when {
        contextLength <= 4096 -> CompressionBudget(1500, 300, .65f)
        contextLength <= 8192 -> CompressionBudget(3000, 500, .70f)
        contextLength <= 16384 -> CompressionBudget(6000, 800, .75f)
        else -> CompressionBudget(12000, 1200, .80f)
    }

    fun of(contextLength: Int, isLocal: Boolean): CompressionBudget =
        if (isLocal) forLocal(contextLength) else CompressionBudget(Int.MAX_VALUE, null, null)
}

/** Estimate only: never persisted as usage or displayed as a native count. */
internal fun estimateLocalTokens(messages: List<UIMessage>): Int = messages.sumOf { message ->
    val text = message.parts.joinToString(" ", transform = ::textForLocalEstimate)
    val cjk = text.count { it.code > 0x2E80 }
    (cjk / 1.6 + (text.length - cjk) / 3.5).toInt() + 4
}

private fun textForLocalEstimate(part: UIMessagePart): String = when (part) {
    is UIMessagePart.Text -> part.text
    is UIMessagePart.Reasoning -> part.reasoning
    is UIMessagePart.Tool -> part.toolName + part.input + part.output.joinToString(" ", transform = ::textForLocalEstimate)
    is UIMessagePart.Notice -> part.text
    is UIMessagePart.Document -> part.fileName
    else -> ""
}

/** Clip only the request copy. Keep tool IDs, inputs and non-text attachments. */
internal fun prepareLocalCompressionMessages(messages: List<UIMessage>): List<UIMessage> =
    messages.limitContext(0).mapNotNull { message ->
        val parts = message.parts.map { part ->
            if (part !is UIMessagePart.Tool) part else {
                val text = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
                if (text.length <= 2000) part else {
                    val clipped = text.take(800) + "…[truncated ${text.length - 1200} characters]…" + text.takeLast(400)
                    var inserted = false
                    part.copy(output = part.output.mapNotNull { output ->
                        if (output !is UIMessagePart.Text) output
                        else if (inserted) null
                        else { inserted = true; output.copy(text = clipped) }
                    })
                }
            }
        }
        if (message.role == MessageRole.ASSISTANT && parts.isEmpty()) null else message.copy(parts = parts)
    }

/** A boundary includes a complete user turn; a tool and its output share one part. */
internal fun compressionBoundaries(messages: List<UIMessage>, startExclusive: Int, endExclusive: Int): List<Int> =
    (startExclusive + 2..endExclusive).filter { it == endExclusive || messages[it].role == MessageRole.USER }

internal fun selectCompressionWindow(messages: List<UIMessage>, startExclusive: Int, endExclusive: Int, budget: Int): Int {
    val boundaries = compressionBoundaries(messages, startExclusive, endExclusive)
    var selected = boundaries.first()
    for (candidate in boundaries) {
        if (estimateLocalTokens(prepareLocalCompressionMessages(messages.take(candidate))) > budget) break
        selected = candidate
    }
    return selected
}

/** Native validation can shrink an estimated window, but never drop its first turn. */
internal suspend fun fitCompressionWindow(boundaries: List<Int>, candidate: Int, fits: suspend (Int) -> Boolean): Int? {
    for (end in boundaries.asReversed().filter { it <= candidate }) if (fits(end)) return end
    return null
}
