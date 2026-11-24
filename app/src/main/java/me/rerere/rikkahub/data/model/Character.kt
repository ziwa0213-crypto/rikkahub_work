package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import kotlin.uuid.Uuid

@Serializable
data class Character(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val exampleMessages: String = "",
    val characterSystemPrompt: String = "",
    val promptModules: List<PromptModule> = emptyList(),
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val tags: List<String> = emptyList(),
    val nickname: String = "",
    val characterVersion: String = "",
    val rawExtensions: String = "{}",
    val chatModelId: Uuid? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val contextMessageLimit: Int = 0,
    val streamOutput: Boolean = true,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val regexes: List<AssistantRegex> = emptyList(),
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val useAssistantAvatar: Boolean = true,
    val enableWebSearch: Boolean = false,
    val enableRecentChatsReference: Boolean = false,
    val background: String? = null,
    val backgroundOpacity: Float = 1f,
    val useGradientBackground: Boolean = false,
    val enableTimeReminder: Boolean = false,
    val timeReminderIntervalMinutes: Int = 60,
    val allowConversationSystemPrompt: Boolean = false,
    val lorebookIds: Set<Uuid> = emptySet(),
    val modeInjectionIds: Set<Uuid> = emptySet(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val enabledSkills: Set<String> = emptySet(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo, LocalToolOption.ClientContext),
    val toolApprovalMode: ToolApprovalMode = ToolApprovalMode.Partial,
    val personaId: Uuid? = null,
    val stylePackId: Uuid? = null,
    val enableMemory: Boolean = true,
    val autoConsolidateMemory: Boolean = true,
    val referencedMemoryScopes: Set<String> = emptySet(),
    val workspaceId: Uuid? = null,
    val workspaceInitialized: Boolean = false,
    val styleNotesEnabled: Boolean = true,
    val refineEveryTurns: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
