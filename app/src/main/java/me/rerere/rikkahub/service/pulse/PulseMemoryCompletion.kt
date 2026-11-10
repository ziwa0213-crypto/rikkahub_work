package me.rerere.rikkahub.service.pulse

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.isPulseChat
import me.rerere.rikkahub.service.ConversationSession
import java.time.Instant

/** Notify only after accepted output is durable. Failed writes can be retried without duplicating it. */
internal suspend fun ConversationSession.persistPulseResponse(
    result: PulseTurnResult,
    onCompleted: suspend (Conversation) -> Unit,
) {
    check(id == result.conversationId) { "Pulse conversation does not match" }
    check(result.response.role == MessageRole.ASSISTANT) { "Pulse result must be an assistant message" }
    update { latest ->
        check(incarnation == result.incarnation && existsInDatabase && latest.isPulseChat()) {
            "Pulse conversation was replaced"
        }
        // Merge by response ID into the latest transcript, including any concurrent user messages.
        if (latest.currentMessages.any { it.id == result.response.id }) latest else latest.copy(
            messageNodes = latest.messageNodes + MessageNode(messages = listOf(result.response)),
            updateAt = Instant.now(),
        )
    }
    val saved = flush()
    val response = saved.currentMessages.firstOrNull { it.id == result.response.id }
    if (incarnation == result.incarnation && saved.isPulseChat() && response != null &&
        response.getTools().all { it.isExecuted }
    ) {
        // Repeated persistence is safe: the scheduler rereads timestamp-based progress, so the
        // same response never becomes a second turn. This also recovers a retry after failed flush.
        onCompleted(saved)
    }
}
