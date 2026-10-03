package me.rerere.ai.provider.providers.deepseekweb

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.util.json

internal class DeepSeekWebSSE(
    private val responseId: String,
    private val model: String,
) {
    private var thinking = ""
    private var text = ""
    private var finished = false
    private var emittedText = ""
    private var toolCandidate = false
    private var toolBuffer = ""
    private var finishEmitted = false

    fun accept(data: String): List<StreamChunk> {
        if (data.trim() == "[DONE]") return finish()
        val chunks = mutableListOf<StreamChunk>()
        val complete = runCatching { json.parseToJsonElement(data.trim()).jsonObject }.getOrNull()
        if (complete != null) {
            parseElement(complete, chunks)
        } else {
            data.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
                val element = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                parseElement(element, chunks)
            }
        }
        return chunks
    }

    private fun parseElement(element: JsonObject, chunks: MutableList<StreamChunk>) {
        val path = element["p"]?.jsonPrimitive?.contentOrNull
        val value = element["v"]
        if (path == "response/status" && value?.jsonPrimitive?.contentOrNull == "FINISHED") finished = true
        if (path == "response/thinking_content") appendThinking(value?.jsonPrimitive?.contentOrNull.orEmpty(), chunks)
        if (path == "response/content") appendText(value?.jsonPrimitive?.contentOrNull.orEmpty(), chunks)
        if (path == "response/fragments" && element["o"]?.jsonPrimitive?.contentOrNull == "APPEND") {
            val fragment = value?.jsonObject
            val content = fragment?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            when (fragment?.get("type")?.jsonPrimitive?.contentOrNull?.uppercase()) {
                "THINK", "THINKING" -> appendThinking(content, chunks)
                "RESPONSE", "TEXT" -> appendText(content, chunks)
            }
        }
        if (path?.contains("/content") == true && path != "response/content") {
            if (path.contains("THINK", true)) appendThinking(value?.jsonPrimitive?.contentOrNull.orEmpty(), chunks)
            else appendText(value?.jsonPrimitive?.contentOrNull.orEmpty(), chunks)
        }
        val response = (element["v"] as? JsonObject)?.get("response")?.jsonObject ?: return
        response["content"]?.jsonPrimitive?.contentOrNull?.let { replaceText(it, chunks) }
        response["thinking_content"]?.jsonPrimitive?.contentOrNull?.let { replaceThinking(it, chunks) }
        response["fragments"]?.jsonArray?.forEach { fragment ->
            val obj = fragment.jsonObject
            val content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            when (obj["type"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                "THINK", "THINKING" -> replaceThinking(content, chunks)
                "RESPONSE", "TEXT" -> replaceText(content, chunks)
            }
        }
    }

    private fun appendThinking(value: String, chunks: MutableList<StreamChunk>) {
        if (value.isEmpty()) return
        thinking += value
        chunks += StreamChunk.ReasoningDelta(responseId, value)
    }

    private fun replaceThinking(value: String, chunks: MutableList<StreamChunk>) {
        if (value.length <= thinking.length) return
        appendThinking(value.substring(thinking.length), chunks)
    }

    private fun appendText(value: String, chunks: MutableList<StreamChunk>) {
        if (value.isEmpty()) return
        text += value
        if (text.trimStart().startsWith("{") || text.trimStart().startsWith("```json")) {
            toolCandidate = true
            toolBuffer = text
            return
        }
        val delta = text.substring(emittedText.length)
        if (delta.isNotEmpty()) {
            emittedText = text
            chunks += StreamChunk.TextDelta(responseId, delta)
        }
    }

    private fun replaceText(value: String, chunks: MutableList<StreamChunk>) {
        if (value.length <= text.length) return
        appendText(value.substring(text.length), chunks)
    }

    fun finish(): List<StreamChunk> {
        if (finishEmitted) return emptyList()
        finished = true
        finishEmitted = true
        val chunks = mutableListOf<StreamChunk>()
        if (toolCandidate) {
            val call = DeepSeekWebTools.toolCall(toolBuffer)
            if (call != null) {
                val id = "$responseId:tool-0"
                chunks += StreamChunk.ToolCallStart(id = id, toolName = call.name)
                chunks += StreamChunk.ToolCallDelta(id = id, inputDelta = call.arguments)
                chunks += StreamChunk.ToolCallEnd(id)
                Log.d(TAG, "DeepSeek Web emitted one allowed tool call")
            } else {
                chunks += StreamChunk.TextDelta(responseId, text)
            }
        } else if (text.length > emittedText.length) {
            chunks += StreamChunk.TextDelta(responseId, text.substring(emittedText.length))
        }
        chunks += StreamChunk.Finish(
            finishReason = if (toolCandidate && DeepSeekWebTools.toolCall(toolBuffer) != null) "tool-calls" else "stop",
            responseId = responseId,
            model = model,
        )
        return chunks
    }

    companion object {
        private const val TAG = "DeepSeekWebSSE"
    }
}
