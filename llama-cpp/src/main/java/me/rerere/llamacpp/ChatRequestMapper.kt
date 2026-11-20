package me.rerere.llamacpp

import kotlinx.serialization.json.Json
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.PartGroup
import me.rerere.ai.provider.providers.groupPartsByToolBoundary
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI-shaped messages are interpreted by the GGUF's own Jinja template. */
object ChatRequestMapper {
    fun toRequestJson(messages: List<UIMessage>, params: TextGenerationParams): String {
        val array = JSONArray()
        for (message in messages) {
            if (message.role != MessageRole.ASSISTANT) {
                array.put(JSONObject().put("role", message.role.name.lowercase()).put("content", text(message.parts)))
                continue
            }
            val content = mutableListOf<UIMessagePart>()
            val tools = mutableListOf<UIMessagePart.Tool>()
            fun flush() {
                val body = text(content)
                if (body.isNotEmpty() || tools.isNotEmpty()) {
                    val obj = JSONObject().put("role", "assistant").put("content", body)
                    val reasoning = content.filterIsInstance<UIMessagePart.Reasoning>().joinToString("") { it.reasoning }
                    if (reasoning.isNotEmpty()) obj.put("reasoning_content", reasoning)
                    if (tools.isNotEmpty()) obj.put("tool_calls", JSONArray().apply {
                        tools.forEach { tool -> put(JSONObject().put("id", tool.toolCallId).put("type", "function")
                            .put("function", JSONObject().put("name", tool.toolName).put("arguments", tool.inputAsJson().toString()))) }
                    })
                    array.put(obj)
                    tools.forEach { tool -> array.put(JSONObject().put("role", "tool").put("name", tool.toolName)
                        .put("tool_call_id", tool.toolCallId).put("content", text(tool.output))) }
                }
                content.clear(); tools.clear()
            }
            groupPartsByToolBoundary(message.parts).forEach { group ->
                when (group) {
                    is PartGroup.Tools -> tools += group.tools
                    is PartGroup.Content -> { if (tools.isNotEmpty()) flush(); content += group.parts }
                }
            }
            flush()
        }
        return JSONObject().apply {
            put("messages", array)
            put("temperature", params.temperature ?: 0.7f)
            put("top_p", params.topP ?: 0.95f)
            put("enable_thinking", params.reasoningLevel != me.rerere.ai.core.ReasoningLevel.OFF)
            if (params.tools.isNotEmpty()) put("tools", toolDefinitions(params.tools))
        }.toString()
    }

    /** Same definitions used in the request; estimates never replace template counting. */
    fun toolsJson(tools: List<Tool>): String = toolDefinitions(tools).toString()

    private fun toolDefinitions(tools: List<Tool>) = JSONArray().apply {
        tools.forEach { tool -> put(JSONObject().put("type", "function").put("function", JSONObject()
            .put("name", tool.name).put("description", tool.description).put("parameters",
                tool.parameters()?.let { JSONObject(Json.encodeToString(InputSchema.serializer(), it)) }
                    ?: JSONObject().put("type", "object").put("properties", JSONObject())))) }
    }

    private fun text(parts: List<UIMessagePart>) = parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
}
