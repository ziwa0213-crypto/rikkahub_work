package me.rerere.llamacpp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.core.TokenUsage
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Blocking native boundary, injectable so protocol and cancellation are tested
 * without pretending JVM fixtures are actual model inference. */
internal interface LocalGeneration {
    fun generate(onPiece: (String) -> Boolean)
    fun parse(text: String, partial: Boolean): String
    fun cancel()
    fun usage(producedText: String): TokenUsage? = null
}

internal fun streamFromLoadedModel(generation: LocalGeneration, id: String, emptyOutputDetail: String? = null): Flow<StreamChunk> = callbackFlow {
    val finished = AtomicBoolean(false)
    val textId = "$id-text"
    val reasoningId = "$id-reasoning"
    fun send(chunk: StreamChunk): Boolean = trySend(chunk).isSuccess.also { if (!it) generation.cancel() }
    val worker = launch(Dispatchers.IO) {
        try {
            val tracker = ChatDeltaTracker()
            val text = ChannelWatermark(); val reasoning = ChannelWatermark()
            val accumulated = StringBuilder()
            var reasoningOpen = false
            var textStarted = false
            var hasToolCall = false
            fun delta(value: ChatDelta, final: Boolean): Boolean {
                val r = reasoning.advance(value.reasoningDelta, value.reasoningReset, final)
                val t = text.advance(value.textDelta, value.textReset, final)
                if (r.isNotEmpty()) {
                    if (!reasoningOpen && !send(StreamChunk.ReasoningStart(reasoningId))) return false
                    reasoningOpen = true
                    if (!send(StreamChunk.ReasoningDelta(reasoningId, r))) return false
                } else if (reasoningOpen && t.isNotEmpty()) {
                    if (!send(StreamChunk.ReasoningEnd(reasoningId))) return false
                    reasoningOpen = false
                }
                if (t.isNotEmpty()) {
                    if (!textStarted && !send(StreamChunk.TextStart(textId))) return false
                    textStarted = true
                    if (!send(StreamChunk.TextDelta(textId, t))) return false
                }
                for (call in value.completedToolCalls) {
                    if (!send(StreamChunk.ToolCallStart(call.id, call.name)) ||
                        !send(StreamChunk.ToolCallDelta(call.id, inputDelta = call.arguments)) ||
                        !send(StreamChunk.ToolCallEnd(call.id))) return false
                    hasToolCall = true
                }
                return isActive
            }
            generation.generate { piece ->
                if (!isActive) { generation.cancel(); false }
                else {
                    accumulated.append(piece)
                    delta(tracker.consume(generation.parse(accumulated.toString(), true), true), false)
                }
            }
            currentCoroutineContext().ensureActive()
            val finalParsed = generation.parse(accumulated.toString(), false)
            val finalDelta = tracker.consume(finalParsed, false)
            if (emptyOutputDetail != null) {
                val parsed = JSONObject(finalParsed)
                if (parsed.optString("content").isBlank() && !hasToolCall && finalDelta.completedToolCalls.isEmpty())
                    throw LocalModelException(LocalModelError.EMPTY_OUTPUT, emptyOutputDetail)
            }
            if (delta(finalDelta, true)) {
                if (reasoningOpen && !send(StreamChunk.ReasoningEnd(reasoningId))) return@launch
                if (textStarted && !send(StreamChunk.TextEnd(textId))) return@launch
                currentCoroutineContext().ensureActive()
                generation.usage(accumulated.toString())?.let { if (!send(StreamChunk.Usage(it))) return@launch }
                send(StreamChunk.Finish("stop"))
            }
            close()
        } catch (t: Throwable) { close(t) }
        finally { finished.set(true) }
    }
    awaitClose {
        if (!finished.get()) generation.cancel()
        worker.cancel()
    }
    // trySend + the default 64-slot buffer loses text, tool arguments or Finish.
}.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)
