package me.rerere.llamacpp

import android.content.Context
import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
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
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Append-only UI cannot retract a speculative parser reset. Resynchronize the
 * watermark, suppress midstream resets and flush authoritative final corrections. */
internal class ChannelWatermark {
    private var shown = ""
    fun advance(value: String, reset: Boolean, final: Boolean): String {
        if (reset) { shown = value; return if (final) value else "" }
        shown += value
        return value
    }
}

class LlamaCppProvider(
    context: Context,
    private val runtime: LlamaCppRuntime,
    private val store: LocalModelStore,
    private val notice: (String) -> String,
) : Provider<ProviderSetting.LlamaCppLocal> {
    private val media = LocalMediaDecoder(context)
    override suspend fun listModels(providerSetting: ProviderSetting.LlamaCppLocal): List<Model> = providerSetting.models
    override suspend fun generateText(providerSetting: ProviderSetting.LlamaCppLocal, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult {
        return collectResult(params, streamText(providerSetting, messages, params))
    }

    private suspend fun collectResult(params: TextGenerationParams, flow: Flow<StreamChunk>): TextGenerationResult {
        var result = listOf(UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()))
        val handler = StreamChunkHandler(params.model)
        flow.collect { result = handler.handle(result, it) }
        return TextGenerationResult("llamacpp-${UUID.randomUUID()}", params.model.modelId, result.last(),
            usage = result.last().lastRequestUsage())
    }

    /** Compression must consume all selected history or fail; normal chat may trim. */
    fun compressionProvider(inputLimit: Int): Provider<ProviderSetting.LlamaCppLocal> =
        object : Provider<ProviderSetting.LlamaCppLocal> by this {
            override suspend fun generateText(providerSetting: ProviderSetting.LlamaCppLocal, messages: List<UIMessage>, params: TextGenerationParams) =
                collectResult(params, streamLoaded(messages, params, inputLimit))
            override suspend fun streamText(providerSetting: ProviderSetting.LlamaCppLocal, messages: List<UIMessage>, params: TextGenerationParams) =
                streamLoaded(messages, params, inputLimit)
        }

    suspend fun compressionInputFits(messages: List<UIMessage>, params: TextGenerationParams, inputLimit: Int): Boolean =
        runtime.withReady(params.model.modelId) {
            val prepared = media.prepare(messages, this, params.model.inputModalities)
            val blob = applyTemplate(ChatRequestMapper.toRequestJson(prepared.messages, params), params.model.chatTemplate)
            try {
                val tokens = countApplied(blob)
                val limit = minOf(inputLimit, contextSize - ContextPlanner.outputBudget(contextSize, params.maxTokens))
                Log.d("LocalModel", "Compression input: tokens=$tokens limit=$limit nCtx=$contextSize")
                tokens <= limit
            } finally { release(blob) }
        }

    override suspend fun streamText(providerSetting: ProviderSetting.LlamaCppLocal, messages: List<UIMessage>, params: TextGenerationParams): Flow<StreamChunk> =
        streamLoaded(messages, params, null)

    /** A loaded model can have a smaller training ceiling than the saved setting. */
    fun promptContextSize(model: Model): Int =
        (runtime.state.value as? LocalModelState.Ready)?.takeIf { it.modelId == model.modelId }?.contextSize
            ?: (model.contextLength.coerceIn(512, 16384) / 256 * 256)

    private fun streamLoaded(messages: List<UIMessage>, params: TextGenerationParams, strictInputLimit: Int?): Flow<StreamChunk> = callbackFlow {
        val owner = UUID.randomUUID().toString()
        val produced = AtomicBoolean(false)
        val mediaNoticesSent = mutableSetOf<Pair<String, String>>()
        var noticeIndex = 0
        fun send(chunk: StreamChunk): Boolean {
            val success = trySend(chunk).isSuccess
            if (!success) runtime.cancelGeneration(owner)
            return success
        }
        fun sendMediaNotices(kind: String, notices: List<String>): Boolean {
            val fresh = notices.filter { mediaNoticesSent.add(kind to it) }
            if (fresh.isEmpty()) return true
            val id = "$owner-$kind-${noticeIndex++}"
            return send(StreamChunk.TextStart(id)) &&
                send(StreamChunk.TextDelta(id, fresh.joinToString("\n", postfix = "\n\n"))) &&
                send(StreamChunk.TextEnd(id))
        }
        val worker = launch(Dispatchers.IO) {
            suspend fun attempt() {
                runtime.withReady(params.model.modelId, owner) {
                    currentCoroutineContext().ensureActive()
                    val outputBudget = ContextPlanner.outputBudget(contextSize, params.maxTokens)
                    val inputBudget = minOf(contextSize - outputBudget, strictInputLimit ?: Int.MAX_VALUE)
                    Log.d("LocalModel", "Budget: nCtx=$contextSize input=$inputBudget output=$outputBudget")
                    fun tooLong(history: List<UIMessage>) =
                        promptTooLong(contextSize, inputBudget, history, params, ::countTokens)
                    fun trimToBudget(history: List<UIMessage>, budget: Int): List<UIMessage> {
                        var counted = history
                        return try { ContextPlanner.trim(history, budget) {
                            counted = it
                            countTokens(ChatRequestMapper.toRequestJson(it, params))
                        } } catch (e: LocalModelException) {
                            if (e.reason != LocalModelError.TOO_LONG) throw e
                            throw tooLong(counted)
                        }
                    }
                    var trimmed = if (strictInputLimit != null) messages else trimToBudget(messages, inputBudget)
                    var applied: ByteArray? = null
                    var prepared: PreparedMedia? = null
                    var mediaFailed = false
                    fun textOnlyTemplate(): ByteArray {
                        beginMedia()
                        mediaFailed = true
                        return applyTemplate(ChatRequestMapper.toRequestJson(trimmed.map { message ->
                            message.copy(parts = message.parts.filterNot {
                                it is UIMessagePart.Image || it is UIMessagePart.Audio || it is UIMessagePart.Video
                            })
                        }, params), params.model.chatTemplate)
                    }
                    try {
                        repeat(3) { pass ->
                            if (applied == null) {
                                currentCoroutineContext().ensureActive()
                                // Settings describe user permission; runtime capabilities
                                // describe support. Decode only their intersection.
                                prepared = media.prepare(trimmed, this, params.model.inputModalities)
                                var blob = applyTemplate(ChatRequestMapper.toRequestJson(prepared!!.messages, params), params.model.chatTemplate)
                                applied = blob
                                val tokens = try { countApplied(blob) } catch (e: RuntimeException) {
                                    if (e.message != "media tokenization failed") throw e
                                    release(blob)
                                    blob = textOnlyTemplate(); applied = blob
                                    countApplied(blob)
                                }
                                if (tokens > inputBudget) {
                                    release(blob); applied = null
                                    if (strictInputLimit != null || pass == 2) throw tooLong(trimmed)
                                    val oldSize = trimmed.size
                                    trimmed = trimToBudget(trimmed, (inputBudget.toDouble() / tokens * countTokens(ChatRequestMapper.toRequestJson(trimmed, params)) * .9).toInt())
                                    if (oldSize == trimmed.size) throw tooLong(trimmed)
                                }
                            }
                        }
                        var blob = requireNotNull(applied)
                        currentCoroutineContext().ensureActive()
                        val notices = buildList {
                            if (projectorFailed) add(notice("vision_failed"))
                            if (prepared?.unsupported == true || mediaFailed) add(notice("unsupported"))
                            if (prepared?.truncated == true) add(notice("audio_truncated"))
                        }
                        localPromptNotices(params.localPromptDrops, notice).forEach { Log.d("LocalModel", "Prompt drop: $it") }
                        if (!sendMediaNotices("notice", notices)) return@withReady
                        val loadedRuntime = this
                        val turnContext = currentCoroutineContext()
                        var generationStartedAt = 0L
                        streamFromLoadedModel(object : LocalGeneration {
                            override fun generate(onPiece: (String) -> Boolean) {
                                generationStartedAt = SystemClock.uptimeMillis()
                                var emitted = false
                                val forwardPiece: (String) -> Boolean = { piece ->
                                    emitted = true
                                    if (piece.isNotEmpty()) produced.set(true)
                                    onPiece(piece)
                                }
                                try {
                                    loadedRuntime.generate(blob, outputBudget, forwardPiece)
                                } catch (e: RuntimeException) {
                                    // Encoding runs during prefill, before any output. A failed
                                    // image/audio/video must still allow the text turn to run.
                                    if (emitted || e.message != "media evaluation failed") throw e
                                    turnContext.ensureActive()
                                    release(blob)
                                    blob = textOnlyTemplate(); applied = blob
                                    if (countApplied(blob) > inputBudget) throw tooLong(trimmed)
                                    if (!sendMediaNotices("media-fallback", listOf(notice("unsupported")))) return
                                    turnContext.ensureActive()
                                    loadedRuntime.generate(blob, outputBudget, forwardPiece)
                                }
                            }
                            override fun parse(text: String, partial: Boolean) =
                                splitInlineThinking(loadedRuntime.parse(text, partial, blob), partial)
                            override fun usage(producedText: String): TokenUsage {
                                val prompt = loadedRuntime.countApplied(blob)
                                val completion = loadedRuntime.countTokens(producedText)
                                val elapsed = (SystemClock.uptimeMillis() - generationStartedAt).coerceAtLeast(1L)
                                val speed = completion * 1000.0 / elapsed
                                Log.d("LocalModel", "Usage: promptTokens=$prompt completionTokens=$completion nCtx=$contextSize VmSwap=${LocalMemoryGate.processSwapKb()}kB elapsed=${elapsed}ms speed=${speed}tok/s")
                                return TokenUsage(promptTokens = prompt, completionTokens = completion, totalTokens = prompt + completion)
                            }
                            override fun cancel() { loadedRuntime.cancelGeneration(owner) }
                        }, owner, emptyOutputDetail = "nCtx=$contextSize").collect { if (!send(it)) throw CancellationException("Local stream closed") }
                    } finally { applied?.let(::release) }
                }
            }
            try {
                withLocalGpuRecovery(runtime, produced::get, ::attempt)
                close()
            } catch (t: Throwable) { close(t) }
        }
        awaitClose { runtime.cancelGeneration(owner); worker.cancel() }
        // Native generation is blocking. Structured children must finish before the
        // runtime lock is released and another title/suggestion can swap the model.
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)
}
