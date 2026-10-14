package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import kotlin.math.ceil
import kotlin.uuid.Uuid

data class ContextUsage(val currentTokens: Int, val maxTokens: Int) {
    val ratio: Float get() = if (maxTokens <= 0) 0f else (currentTokens.toFloat() / maxTokens).coerceIn(0f, 1f)
}

internal fun heuristicTokens(text: String): Int {
    val cjk = text.count { it.code in 0x4E00..0x9FFF }
    return ceil(cjk * 1.5 + (text.length - cjk) / 4.0).toInt()
}

internal fun List<UIMessage>.estimateContextTokens(): Int {
    // Compression creates a new first message but retains older recent-message IDs and usage.
    // Those usage records describe the pre-compression context and must not be reused.
    val boundary = firstOrNull()?.createdAt
    asReversed().firstNotNullOfOrNull { message ->
        message.usage?.takeIf { message.role == MessageRole.ASSISTANT &&
            (boundary == null || message.createdAt >= boundary) && (it.promptTokens > 0 || it.cachedTokens > 0) }
    }?.let { return (it.promptTokens.coerceAtLeast(0).toLong() + it.cachedTokens.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    return sumOf { message -> message.parts.filterIsInstance<UIMessagePart.Text>().sumOf { heuristicTokens(it.text).toLong() } }
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal fun Group.contextUsage(messages: List<UIMessage>) = ContextUsage(
    messages.estimateContextTokens(), compressContextLength.takeIf { it > 0 } ?: 128_000,
)

internal fun Group.compressionThreshold(): Float =
    if (compressThresholdRatio.isFinite()) compressThresholdRatio.coerceIn(0.30f, 0.95f) else 0.7f

internal class GroupCompressionGate {
    private data class Prompted(val tokens: Int, val messages: Int)
    private val lastPrompt = mutableMapOf<Uuid, Prompted>()
    fun shouldPrompt(id: Uuid, usage: ContextUsage, group: Group, messageCount: Int): Boolean {
        if (!group.enableAutoCompress || messageCount <= group.compressKeepRecent.coerceAtLeast(0)) return false
        val byTokens = usage.ratio >= group.compressionThreshold()
        val byCount = group.compressByMessageCount && messageCount >= group.compressMessageCount.coerceAtLeast(1)
        if (!byTokens && !byCount) { lastPrompt.remove(id); return false }
        val last = lastPrompt[id] ?: return true
        // Textless histories still need a finite count-based cooldown.
        return if (last.tokens > 0) usage.currentTokens >= ceil(last.tokens * 1.2).toInt()
        else messageCount >= ceil(last.messages * 1.2).toInt()
    }
    fun markPrompted(id: Uuid, usage: ContextUsage, messageCount: Int) {
        lastPrompt[id] = Prompted(usage.currentTokens, messageCount)
    }
    fun reset(id: Uuid) { lastPrompt.remove(id) }
}

internal data class TieredCompressContent(val primary: String, val others: String)

private fun String.headAndTail(limit: Int): String = if (length <= limit) this else {
    val size = limit.coerceAtLeast(1)
    take(size / 2) + "…" + takeLast((size - 1) / 2)
}

internal fun buildTieredCompressContent(
    messages: List<UIMessage>, deputyMemberId: Uuid?, ownerLabel: String,
    primaryMaxLength: Int = 4000, otherPerMemberMaxLength: Int = 300,
): TieredCompressContent {
    fun UIMessage.sender() = annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().firstOrNull()
    val (primary, others) = messages.partition {
        it.role == MessageRole.USER || (deputyMemberId != null && it.sender()?.memberId == deputyMemberId.toString())
    }
    return TieredCompressContent(
        primary.joinToString("\n") { message ->
            val name = if (message.role == MessageRole.USER) ownerLabel else message.sender()?.memberName ?: message.role.name
            "[$name]: ${message.toText().headAndTail(primaryMaxLength)}"
        },
        others.groupBy { it.sender()?.memberName ?: it.role.name }.entries.joinToString("\n") { (name, history) ->
            "- $name: ${history.joinToString(" ") { it.toText() }.headAndTail(otherPerMemberMaxLength)}"
        },
    )
}
