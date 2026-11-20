package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.ai.provider.LocalPromptDrop
import kotlin.uuid.Uuid

/** Process-only UI state. An empty entry is a dismissed conversation, not an absent one. */
internal class LocalPromptNoticeStore {
    private val notices = MutableStateFlow<Map<Uuid, Set<LocalPromptDrop>>>(emptyMap())
    val state = notices.asStateFlow()

    fun note(conversationId: Uuid, drops: List<LocalPromptDrop>) {
        if (drops.isEmpty()) return
        notices.update { current ->
            if (conversationId in current && current[conversationId].isNullOrEmpty()) current
            else current + (conversationId to (current[conversationId].orEmpty() + drops))
        }
    }

    fun dismiss(conversationId: Uuid) {
        notices.update { it + (conversationId to emptySet()) }
    }
}
