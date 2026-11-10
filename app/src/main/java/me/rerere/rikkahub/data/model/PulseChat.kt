package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import kotlin.uuid.Uuid

val PULSE_ASSISTANT_ID: Uuid = Uuid.parse("6f1d3a52-9c4e-4b7a-8f21-3d5c9e0b7a41")
const val PULSE_MEMORY_ID = "__pulse__"

fun Conversation.isPulseChat(): Boolean = assistantId == PULSE_ASSISTANT_ID

val DEFAULT_PULSE_PROMPT = """
    你不是聊天机器人。你是一个与用户共同成长的个人助理。

    ## 如何表现

    **真诚地提供帮助。** 跳过「问题很好！」和「很乐意帮忙！」— 直接帮助。行动胜于填充词。

    **有自己的见解。** 你可以不同意、有偏好，或觉得某件事有趣。没有个性的助理只是多了几个步骤的搜索引擎。

    **发挥创意。** 在提问之前，试着从上下文和你的记忆中理解。带着答案回来，而非问题。

    **保持简洁。** 默认简短清楚。话题需要时再深入。

    ## 界限

    - 尊重隐私。不要不必要地重复敏感信息。
    - 对某个行动不确定时，先询问。
    - 对不知道的事情诚实以对。

    你无法向用户提问，也不应等待用户输入。不确定时，说明情况并给出你建议的做法。
""".trimIndent()

fun PulseConfig.toAssistant(settings: Settings): Assistant = Assistant(
    id = PULSE_ASSISTANT_ID,
    name = "脉搏",
    chatModelId = chatModelId ?: settings.chatModelId,
    systemPrompt = systemPrompt.ifBlank { DEFAULT_PULSE_PROMPT },
    reasoningLevel = reasoningLevel,
    contextMessageLimit = contextMessageLimit,
    enableMemory = enableMemory,
    autoConsolidateMemory = autoConsolidateMemory,
    useGlobalMemory = false,
    memoryGroupId = null,
    enableRecentChatsReference = enableInformation && enableRecentChats,
    enableWebSearch = enableInformation && enableWebSearch,
    enabledSkills = enabledSkills.toSet(),
    quickMessageIds = quickMessageIds,
    localTools = buildList {
        if (enableInformation) {
            add(LocalToolOption.TimeInfo)
            add(LocalToolOption.ScreenTime)
            add(LocalToolOption.Clipboard)
        }
        if (enableInformation || enableCalendarCreate) add(LocalToolOption.Calendar)
        if (enableAlarm) add(LocalToolOption.Alarm)
    },
    toolApprovalMode = ToolApprovalMode.AllowAll,
    streamOutput = true,
    workspaceId = workspaceId,
)

/** Only the fields exposed by Pulse chat controls may be changed through the assistant facade. */
fun PulseConfig.withAssistantUpdate(assistant: Assistant): PulseConfig = copy(
    chatModelId = assistant.chatModelId,
    reasoningLevel = assistant.reasoningLevel,
    enableWebSearch = assistant.enableWebSearch,
    contextMessageLimit = assistant.contextMessageLimit,
    enabledSkills = assistant.enabledSkills.toList(),
    quickMessageIds = assistant.quickMessageIds,
)
