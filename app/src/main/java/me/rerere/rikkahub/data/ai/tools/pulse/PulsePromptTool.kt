package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DEFAULT_HEARTBEAT_PROMPT
import me.rerere.rikkahub.data.datastore.SettingsStore

internal fun updateHeartbeatPromptTool(settingsStore: SettingsStore) = Tool(
    name = "update_heartbeat_prompt",
    description = """
        Read or update your heartbeat instructions — the instructions you follow on every automatic self-check.

        - action=read: returns the current heartbeat instructions.
        - action=edit (default): replace them. Small adjustments.
        - action=rewrite: replace them wholesale; only when the user has enabled rewriting.

        Always read before writing. Do not guess the current text from the conversation:
        this turn's instruction may be a scheduled task or a watcher prompt, not the heartbeat prompt.

        Only heartbeat instructions can be changed; persona/systemPrompt cannot be changed.
    """.trimIndent(),
    parameters = { InputSchema.Obj(buildJsonObject {
        put("action", buildJsonObject { put("type", "string"); put("description", "read | edit | rewrite; default edit") })
        put("prompt", buildJsonObject { put("type", "string"); put("description", "Complete new heartbeat instructions, 1 to 4000 characters. Not needed for action=read.") })
    }, required = emptyList()) },
    execute = { value ->
        val args = value.jsonObject
        val action = (args["action"] as? JsonPrimitive)?.contentOrNull ?: "edit"
        if (action == "read") {
            val pulse = settingsStore.settingsFlow.value.pulse
            require(pulse.enableSelfPromptEdit) { "Action is unavailable in Pulse mode." }
            val stored = pulse.heartbeatPrompt
            val content = stored.ifBlank { DEFAULT_HEARTBEAT_PROMPT }
            return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("content", content)
                put("is_default", stored.isBlank())
                put("length", content.length)
            }.toString()))
        }
        val prompt = (args["prompt"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        require(prompt.isNotEmpty()) { "prompt is empty" }
        require(prompt.length <= 4000) { "prompt is too long (max 4000 chars)" }
        settingsStore.update { current ->
            require(current.pulse.enableSelfPromptEdit && (action == "edit" ||
                action == "rewrite" && current.pulse.enableSelfPromptRewrite)) { "Action is unavailable in Pulse mode." }
            current.copy(pulse = current.pulse.copy(heartbeatPrompt = prompt))
        }
        listOf(UIMessagePart.Text(buildJsonObject { put("updated", true); put("action", action); put("length", prompt.length) }.toString()))
    },
)
