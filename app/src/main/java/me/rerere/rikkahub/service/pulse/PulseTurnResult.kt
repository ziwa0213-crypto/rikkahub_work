package me.rerere.rikkahub.service.pulse

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.agent.AgentFinishReason
import kotlin.uuid.Uuid

data class PulseTurnResult(
    val conversationId: Uuid,
    val incarnation: Int,
    val response: UIMessage,
) {
    val text: String get() = response.toText()
    val awaitingApproval: Boolean get() = response.getTools().any { it.isPending }
}

/** A pending tool is a valid, reviewable result even when the model emitted no prose. */
internal fun pulseTurnResult(conversationId: Uuid, incarnation: Int, message: UIMessage?, reason: AgentFinishReason?): PulseTurnResult {
    check(reason == AgentFinishReason.Completed || reason is AgentFinishReason.AwaitingApproval) { "Pulse generation did not complete" }
    val result = PulseTurnResult(conversationId, incarnation, checkNotNull(message) { "Empty Pulse response" })
    check(result.text.isNotBlank() || result.awaitingApproval) { "Empty Pulse response" }
    check(reason !is AgentFinishReason.AwaitingApproval || result.awaitingApproval) { "Missing Pulse approval request" }
    return result
}
