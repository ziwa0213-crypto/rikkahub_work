package me.rerere.rikkahub.data.event

import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data object OpenUsageAccessSettings : AppEvent()
    data class OpenBrowser(val conversationId: Uuid, val url: String) : AppEvent()

    /** 聊天生成过程中的流式更新，由 ChatNotificationManager 消费用于 Live Update 通知。 */
    data class ChatGenerationUpdate(
        val conversationId: Uuid,
        val lastMessage: UIMessage,
        val senderName: String,
    ) : AppEvent()

    /** Chat generation finished with an explicit outcome for notification policy. */
    data class ChatGenerationEnded(
        val conversationId: Uuid,
        val senderName: String,
        val contentPreview: String?,
        val outcome: GenerationOutcome = GenerationOutcome.SUCCESS,
        val failureMessage: String? = null,
    ) : AppEvent()
}

enum class GenerationOutcome { SUCCESS, CANCELLED, FAILED }
