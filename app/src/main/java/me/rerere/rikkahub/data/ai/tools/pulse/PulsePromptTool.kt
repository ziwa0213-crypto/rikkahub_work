package me.rerere.rikkahub.data.ai.tools.pulse

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore

internal fun updateHeartbeatPromptTool(settingsStore: SettingsStore) = Tool(
    name = "update_heartbeat_prompt",
    description = "Update your heartbeat instructions for the next heartbeat. action=edit (default): small adjustments; action=rewrite: replace the instructions when the user has enabled rewriting. Only heartbeat instructions can be changed; persona/systemPrompt cannot be changed.",
    parameters = { InputSchema.Obj(buildJsonObject {
        put("action", buildJsonObject { put("type", "string"); put("description", "edit | rewrite; default edit") })
        put("prompt", buildJsonObject { put("type", "string"); put("description", "Complete new heartbeat instructions, 1 to 4000 characters") })
    }, required = listOf("prompt")) },
    execute = { value ->
        val args = value.jsonObject
        val action = (args["action"] as? JsonPrimitive)?.contentOrNull ?: "edit"
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
