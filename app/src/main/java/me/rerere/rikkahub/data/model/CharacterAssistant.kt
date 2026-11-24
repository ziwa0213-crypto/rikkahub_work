package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.uuid.Uuid

fun Character.toAssistant(settings: Settings): Assistant = Assistant(
    id = id, name = name, avatar = avatar, chatModelId = chatModelId,
    useAssistantAvatar = useAssistantAvatar, systemPrompt = buildPromptModules(
        persona = settings.characterPersonas.firstOrNull { it.id == personaId }
            ?.takeIf { it.description.isNotBlank() }?.let { "## ${it.name}\n${it.description.trim()}" }.orEmpty(),
    ),
    temperature = temperature, topP = topP, contextMessageLimit = contextMessageLimit,
    streamOutput = streamOutput, enableMemory = enableMemory, autoConsolidateMemory = autoConsolidateMemory,
    enableRecentChatsReference = enableRecentChatsReference, messageTemplate = messageTemplate,
    presetMessages = presetMessages.ifEmpty { firstMessage.takeIf(String::isNotBlank)?.let { listOf(UIMessage.assistant(it)) }.orEmpty() },
    quickMessageIds = quickMessageIds, regexes = regexes, reasoningLevel = reasoningLevel,
    toolApprovalMode = toolApprovalMode, maxTokens = maxTokens, customHeaders = customHeaders,
    customBodies = customBodies, mcpServers = mcpServers, localTools = localTools,
    enableWebSearch = enableWebSearch, workspaceId = workspaceId, background = background,
    backgroundOpacity = backgroundOpacity, useGradientBackground = useGradientBackground,
    modeInjectionIds = modeInjectionIds, lorebookIds = lorebookIds, enabledSkills = enabledSkills,
    enableTimeReminder = enableTimeReminder, timeReminderIntervalMinutes = timeReminderIntervalMinutes,
    allowConversationSystemPrompt = allowConversationSystemPrompt,
)

/** Apply editing fields while keeping the original card, persona and storage identity. */
internal fun Character.withAssistantSettings(value: Assistant) = copy(
    name = value.name, avatar = value.avatar, chatModelId = value.chatModelId,
    useAssistantAvatar = value.useAssistantAvatar, temperature = value.temperature, topP = value.topP,
    contextMessageLimit = value.contextMessageLimit, streamOutput = value.streamOutput,
    enableMemory = value.enableMemory, autoConsolidateMemory = value.autoConsolidateMemory,
    enableRecentChatsReference = value.enableRecentChatsReference, messageTemplate = value.messageTemplate,
    quickMessageIds = value.quickMessageIds, regexes = value.regexes, reasoningLevel = value.reasoningLevel,
    toolApprovalMode = value.toolApprovalMode, maxTokens = value.maxTokens, customHeaders = value.customHeaders,
    customBodies = value.customBodies, mcpServers = value.mcpServers, localTools = value.localTools,
    enableWebSearch = value.enableWebSearch, workspaceId = value.workspaceId, background = value.background,
    backgroundOpacity = value.backgroundOpacity, useGradientBackground = value.useGradientBackground,
    modeInjectionIds = value.modeInjectionIds, lorebookIds = value.lorebookIds, enabledSkills = value.enabledSkills,
    enableTimeReminder = value.enableTimeReminder, timeReminderIntervalMinutes = value.timeReminderIntervalMinutes,
    allowConversationSystemPrompt = value.allowConversationSystemPrompt,
)

internal fun Assistant.toCharacter(newId: Uuid, tagNames: List<String>, now: Long) =
    Character(id = newId, characterSystemPrompt = systemPrompt, presetMessages = presetMessages,
        tags = tagNames, createdAt = now, updatedAt = now).withAssistantSettings(copy(workspaceId = null))
