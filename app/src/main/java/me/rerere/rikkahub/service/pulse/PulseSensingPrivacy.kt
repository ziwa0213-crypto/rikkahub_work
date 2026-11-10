package me.rerere.rikkahub.service.pulse

import me.rerere.ai.ui.UIMessage

private val SENSING_TOOL_NAMES = setOf("read_notifications", "read_sms", "send_sms")

/** Never duplicate sensed chat content into report files, activity notes or notification previews. */
internal fun pulseHasSensingData(history: List<UIMessage>, eventKind: String? = null): Boolean =
    eventKind == "sms" ||
        history.any { message -> message.getTools().any { it.toolName in SENSING_TOOL_NAMES } }
