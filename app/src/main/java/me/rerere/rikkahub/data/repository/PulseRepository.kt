package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID
import me.rerere.rikkahub.data.model.isPulseChat
import kotlin.uuid.Uuid

class PulseRepository(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val conversations: ConversationRepository,
) {
    private val mutex = Mutex()

    suspend fun ensurePulseConversation(): Uuid = mutex.withLock {
        val settings = settingsStore.awaitLoaded()
        settings.pulse.conversationId?.let { id ->
            if (conversations.getConversationById(id)?.isPulseChat() == true) return@withLock id
        }
        // Recover a conversation inserted before a process death interrupted the settings write.
        conversations.getConversationsOfAssistant(PULSE_ASSISTANT_ID).first().firstOrNull()?.let { existing ->
            settingsStore.update { it.copy(pulse = it.pulse.copy(conversationId = existing.id)) }
            return@withLock existing.id
        }
        // Keep creation and recording the ID together even if navigation is cancelled.
        withContext(NonCancellable) {
            val conversation = Conversation(
                assistantId = PULSE_ASSISTANT_ID,
                messageNodes = emptyList(),
                title = context.getString(R.string.pulse_conversation_title),
            )
            conversations.insertConversation(conversation)
            settingsStore.update { it.copy(pulse = it.pulse.copy(conversationId = conversation.id)) }
            conversation.id
        }
    }
}
