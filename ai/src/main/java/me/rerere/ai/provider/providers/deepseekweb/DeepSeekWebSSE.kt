package me.rerere.ai.provider.providers.deepseekweb

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.util.json

/** Decodes both DeepSeek Web fragment snapshots and direct streaming patches. */
internal class DeepSeekWebSSE(
    private val responseId: String,
    private val model: String,
    private val thinkingEnabled: Boolean = false,
    private val availableToolNames: Set<String> = DeepSeekWebToolPolicy.allowedNames,
) {
    private enum class Sink { FRAGMENTS, THINKING, CONTENT }

    private data class Fragment(
        val type: String,
        var content: String,
    )

    private val fragments = mutableListOf<Fragment>()
    private var fragmentsText = ""
    private var fragmentsThinking = ""
    private var text = ""
    private var thinking = ""
    private var emittedText = ""
    private var emittedThinking = ""
    private var sink: Sink? = null
    private var orphanBuffer = ""
    private var pendingFinish: String? = null
    private var sawData = false
    private var toolCandidate = false
    private var finishEmitted = false
    var refusal: DeepSeekWebCapabilityRefusalException? = null
        private set

    fun accept(data: String): List<StreamChunk> {
        if (data.trim() == "[DONE]") return finish()
        val chunks = mutableListOf<StreamChunk>()
        val parsed = runCatching { json.parseToJsonElement(data.trim()) }.getOrNull()
        if (parsed != null) {
            parseElement(parsed, chunks)
        } else {
            data.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { line ->
                    runCatching { json.parseToJsonElement(line) }
                        .getOrNull()
                        ?.let { parseElement(it, chunks) }
                }
        }
        return chunks
    }

    private fun parseElement(element: JsonElement, chunks: MutableList<StreamChunk>) {
        when (element) {
            is JsonArray -> element.forEach { parseElement(it, chunks) }
            !is JsonObject -> return
            else -> parseObject(element, chunks)
        }
    }

    private fun parseObject(element: JsonObject, chunks: MutableList<StreamChunk>) {
        sawData = true
        val value = element["v"]
        val snapshot = (value as? JsonObject)?.get("response") as? JsonObject
        if (snapshot != null) {
            (snapshot["fragments"] as? JsonArray)?.let { replaceFragments(it, chunks) }
            snapshot.stringValue("thinking_content")?.let { replaceThinking(it, chunks) }
            snapshot.stringValue("content")?.let {
                if (fragments.isEmpty()) {
                    sink = Sink.CONTENT
                    replaceText(it, chunks)
                }
            }
            snapshot.stringValue("finish_reason")?.let { pendingFinish = it }
            return
        }

        if (element.stringValue("type") == "error") return
        val path = element.stringValue("p")
        if (path == null) {
            value.stringValue()?.takeIf(String::isNotEmpty)?.let { appendSink(it, chunks) }
            return
        }

        when (path) {
            "response/fragments" -> appendFragments(value, chunks)
            "response/fragments/-1/content" -> value.stringValue()?.let {
                appendToLastFragment(it, chunks)
                if (!(fragments.isEmpty() && (sink == Sink.THINKING || sink == Sink.CONTENT))) {
                    sink = Sink.FRAGMENTS
                }
            }
            "response/fragments/-1/elapsed_secs" -> {
                if ((value.numberValue()?.takeIf { it > 0.0 }) != null) settleOrphans(chunks)
            }
            "response/thinking_content" -> value.stringValue()?.let {
                if (it.isNotEmpty()) {
                    sink = Sink.THINKING
                    appendThinking(it, chunks)
                }
            }
            "response/content" -> value.stringValue()?.let {
                if (it.isNotEmpty()) {
                    sink = Sink.CONTENT
                    appendText(it, chunks)
                }
            }
            "response/finish_reason" -> value.stringValue()?.let { pendingFinish = it }
            "response/status" -> value.stringValue()?.let { status ->
                if (status == "FINISHED") pendingFinish = pendingFinish ?: status
            }
            "response" -> (value as? JsonArray)?.forEach { operation ->
                val operationObject = operation as? JsonObject ?: return@forEach
                if (operationObject.stringValue("p") == "fragments") {
                    appendFragments(operationObject["v"], chunks)
                }
            }
            else -> Unit
        }
    }

    private fun replaceFragments(list: JsonArray, chunks: MutableList<StreamChunk>) {
        fragments.clear()
        list.forEach { addFragment(it) }
        rebuildFragmentText()
        sink = fragments.takeIf { it.isNotEmpty() }?.let { Sink.FRAGMENTS }
        fragments.firstOrNull()?.let { settleOrphans(chunks) }
        if (fragments.isNotEmpty()) {
            reconcileThinking(fragmentsThinking, chunks)
            reconcileText(fragmentsText, chunks)
        }
    }

    private fun appendFragments(value: JsonElement?, chunks: MutableList<StreamChunk>) {
        val incoming = when (value) {
            is JsonArray -> value
            null -> return
            else -> JsonArray(listOf(value))
        }
        var settled = false
        incoming.forEach { element ->
            val fragment = fragmentOf(element) ?: return@forEach
            if (!settled) {
                settled = true
                settleOrphans(chunks)
            }
            fragments += fragment
            if (isThinking(fragment.type)) {
                fragmentsThinking += fragment.content
                appendThinking(fragment.content, chunks)
            } else {
                fragmentsText += fragment.content
                appendText(fragment.content, chunks)
            }
        }
        if (fragments.isNotEmpty()) sink = Sink.FRAGMENTS
    }

    private fun appendToLastFragment(value: String, chunks: MutableList<StreamChunk>) {
        val fragment = fragments.lastOrNull()
        if (fragment == null) {
            when (sink) {
                Sink.THINKING -> appendThinking(value, chunks)
                Sink.CONTENT -> appendText(value, chunks)
                Sink.FRAGMENTS, null -> if (thinkingEnabled) orphanBuffer += value else appendText(value, chunks)
            }
            return
        }
        fragment.content += value
        if (isThinking(fragment.type)) {
            fragmentsThinking += value
            appendThinking(value, chunks)
        } else {
            fragmentsText += value
            appendText(value, chunks)
        }
    }

    private fun appendSink(value: String, chunks: MutableList<StreamChunk>) {
        when (sink) {
            Sink.THINKING -> appendThinking(value, chunks)
            Sink.CONTENT -> appendText(value, chunks)
            Sink.FRAGMENTS -> appendToLastFragment(value, chunks)
            null -> appendText(value, chunks)
        }
    }

    private fun settleOrphans(chunks: MutableList<StreamChunk>) {
        if (orphanBuffer.isEmpty()) return
        val value = orphanBuffer
        orphanBuffer = ""
        // Before the first fragment is known, DeepSeek sends thinking text first.
        appendThinking(value, chunks)
    }

    private fun reconcileThinking(candidate: String, chunks: MutableList<StreamChunk>) {
        if (candidate.isEmpty() || candidate == emittedThinking) return
        if (candidate.startsWith(emittedThinking)) {
            appendThinking(candidate.substring(emittedThinking.length), chunks)
        }
    }

    private fun reconcileText(candidate: String, chunks: MutableList<StreamChunk>) {
        if (candidate.isEmpty() || candidate == text) return
        if (candidate.startsWith(text)) {
            appendText(candidate.substring(text.length), chunks)
        }
    }

    private fun appendThinking(value: String, chunks: MutableList<StreamChunk>) {
        if (value.isEmpty()) return
        thinking += value
        emittedThinking += value
        chunks += StreamChunk.ReasoningDelta(responseId, value)
    }

    private fun replaceThinking(value: String, chunks: MutableList<StreamChunk>) {
        if (value.length <= thinking.length) return
        appendThinking(value.substring(thinking.length), chunks)
    }

    private fun appendText(value: String, chunks: MutableList<StreamChunk>) {
        if (value.isEmpty()) return
        text += value
        if (!toolCandidate && (text.contains('{') || text.contains("```") || text.contains(DSML_PREFIX))) {
            toolCandidate = true
        }
        // Buffer from the first possible call; keep its preceding prose streaming.
        val candidateStart = listOf(text.indexOf('{'), text.indexOf("```"), text.indexOf(DSML_PREFIX))
            .filter { it >= 0 }.minOrNull() ?: text.length
        val prefix = if (toolCandidate) text.take(candidateStart) else text
        val suffixLength = listOf(DSML_PREFIX, "```").maxOf { marker ->
            (1 until marker.length).lastOrNull { prefix.endsWith(marker.take(it)) } ?: 0
        }
        val safeText = prefix.dropLast(suffixLength)
        val delta = safeText.substring(emittedText.length)
        if (delta.isNotEmpty()) {
            emittedText = safeText
            chunks += StreamChunk.TextDelta(responseId, delta)
        }
    }

    private fun replaceText(value: String, chunks: MutableList<StreamChunk>) {
        if (value.length <= text.length) return
        appendText(value.substring(text.length), chunks)
    }

    fun finish(): List<StreamChunk> {
        if (finishEmitted) return emptyList()
        finishEmitted = true
        val chunks = mutableListOf<StreamChunk>()
        if (orphanBuffer.isNotEmpty()) {
            val value = orphanBuffer
            orphanBuffer = ""
            if (thinkingEnabled && looksLikeThinking(value)) appendThinking(value, chunks) else appendText(value, chunks)
        }
        if (!sawData) return chunks

        val parsed = DeepSeekWebToolProtocol.parse(text)
        val parsedTool = parsed.result
        if (parsedTool is DeepSeekWebToolProtocol.Result.Blocked) {
            runCatching { Log.w(TAG, "DeepSeek Web blocked unsupported tool call: ${parsedTool.name}") }
            refusal = DeepSeekWebCapabilityRefusalException(parsedTool.name)
        }
        var call = (parsedTool as? DeepSeekWebToolProtocol.Result.Allowed)?.call
        if (call != null && call.name !in availableToolNames) {
            refusal = DeepSeekWebCapabilityRefusalException(call.name)
            call = null
        }
        val remaining = parsed.text.substring(emittedText.length)
        if (remaining.isNotEmpty()) chunks += StreamChunk.TextDelta(responseId, remaining)
        emittedText = parsed.text
        if (call != null) {
            val id = "$responseId:tool-0"
            chunks += StreamChunk.ToolCallStart(id = id, toolName = call.name)
            chunks += StreamChunk.ToolCallDelta(id = id, inputDelta = call.arguments)
            chunks += StreamChunk.ToolCallEnd(id)
            runCatching { Log.d(TAG, "DeepSeek Web emitted one allowed tool call") }
        }
        chunks += StreamChunk.Finish(
            finishReason = if (call != null) "tool_calls" else pendingFinish ?: "stop",
            responseId = responseId,
            model = model,
        )
        return chunks
    }

    private fun addFragment(element: JsonElement) {
        fragmentOf(element)?.let { fragments += it }
    }

    private fun fragmentOf(element: JsonElement): Fragment? {
        val objectValue = element as? JsonObject ?: return null
        val content = objectValue.stringValue("content") ?: return null
        return Fragment(objectValue.stringValue("type") ?: "RESPONSE", content)
    }

    private fun rebuildFragmentText() {
        fragmentsText = fragments.filterNot { isThinking(it.type) }.joinToString("") { it.content }
        fragmentsThinking = fragments.filter { isThinking(it.type) }.joinToString("") { it.content }
    }

    private fun isThinking(type: String): Boolean = when (type.uppercase()) {
        "THINK", "THINKING", "REASONING" -> true
        else -> false
    }

    private fun looksLikeThinking(value: String): Boolean =
        value.trimStart().startsWith("<analysis", ignoreCase = true) ||
            value.trimStart().startsWith("<thinking", ignoreCase = true) ||
            value.trimStart().startsWith("<summary", ignoreCase = true)

    private fun JsonObject.stringValue(key: String): String? = this[key].stringValue()

    private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.numberValue(): Double? = stringValue()?.toDoubleOrNull()

    companion object {
        private const val TAG = "DeepSeekWebSSE"
        private const val DSML_PREFIX = "<||DSML||"
    }
}
