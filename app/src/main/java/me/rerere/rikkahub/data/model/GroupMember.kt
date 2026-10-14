package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import kotlin.uuid.Uuid

@Serializable
data class GroupMember(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val chatModelId: Uuid? = null,
    val systemPrompt: String = "",
    val streamOutput: Boolean = true,
    // Retained for compatibility; F012 reasoning is now configured on Group.
    // This member-level value is not currently used, including imported snapshots.
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageLimit: Int = 0,
    val maxTokens: Int? = null,
    val tags: List<Uuid> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun importSnapshot(assistant: Assistant) = copy(
        // Numeric tuning remains member-specific and is deliberately not imported.
        name = assistant.name,
        chatModelId = assistant.chatModelId,
        systemPrompt = assistant.systemPrompt,
        streamOutput = assistant.streamOutput,
        reasoningLevel = assistant.reasoningLevel,
    )
}
