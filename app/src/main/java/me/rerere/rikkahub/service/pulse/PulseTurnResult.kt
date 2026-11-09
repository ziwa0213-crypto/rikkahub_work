package me.rerere.rikkahub.service.pulse

import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

data class PulseTurnResult(
    val conversationId: Uuid,
    val incarnation: Int,
    val response: UIMessage,
) {
    val text: String get() = response.toText()
}
