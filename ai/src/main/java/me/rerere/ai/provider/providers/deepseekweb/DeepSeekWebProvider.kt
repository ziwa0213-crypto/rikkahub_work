package me.rerere.ai.provider.providers.deepseekweb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.json
import me.rerere.common.http.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID

internal class DeepSeekWebProvider(
    private val client: OkHttpClient,
    context: Context,
) : Provider<ProviderSetting.DeepSeekWeb> {
    private val appContext = context.applicationContext

    override suspend fun listModels(providerSetting: ProviderSetting.DeepSeekWeb): List<Model> = listOf(
        Model("deepseek-web", "快速模式", type = ModelType.CHAT, abilities = listOf(ModelAbility.TOOL)),
        Model("deepseek-web-thinking", "思考模式", type = ModelType.CHAT, abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING)),
    )

    override suspend fun streamText(
        providerSetting: ProviderSetting.DeepSeekWeb,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = flow {
        require(providerSetting.token.isNotBlank()) { "DeepSeek 网页版尚未登录，请先保存 Token。" }
        val latestUser = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        DeepSeekWebGuard.refusal(latestUser)?.let { refusal ->
            emit(StreamChunk.TextDelta("local-refusal", refusal))
            emit(StreamChunk.Finish("stop", "local-refusal", params.model.modelId))
            return@flow
        }
        gate.withPermit(providerSetting.throttleMinMs, providerSetting.throttleMaxMs) {
            val requestMessages = messages.map { message ->
                if (message.role == MessageRole.SYSTEM) UIMessage.system(message.toText()) else message
            }
            val prompt = DeepSeekWebTools.prompt(requestMessages, params.tools, params.model)
            val headers = requestHeaders(providerSetting)
            val sessionId = createSession(headers)
            try {
                val pow = DeepSeekWebPoW.createHeader(client, appContext, headers)
                completion(headers, pow, sessionId, prompt, params.model.modelId == "deepseek-web-thinking")
                    .collect { emit(it) }
            } finally {
                deleteSession(headers, sessionId)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateText(
        providerSetting: ProviderSetting.DeepSeekWeb,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult {
        val chunks = streamText(providerSetting, messages, params).toList()
        val parts = mutableListOf<UIMessagePart>()
        val toolBuffers = linkedMapOf<String, Pair<String, StringBuilder>>()
        chunks.forEach { chunk ->
            when (chunk) {
                is StreamChunk.TextDelta -> parts += UIMessagePart.Text(chunk.text)
                is StreamChunk.ReasoningDelta -> parts += UIMessagePart.Reasoning(chunk.text)
                is StreamChunk.ToolCallStart -> toolBuffers[chunk.id] = chunk.toolName to StringBuilder()
                is StreamChunk.ToolCallDelta -> toolBuffers[chunk.id]?.second?.append(chunk.inputDelta)
                is StreamChunk.ToolCallEnd -> toolBuffers[chunk.id]?.let { (name, input) ->
                    parts += UIMessagePart.Tool(chunk.id, name, input.toString(), emptyList())
                }
                else -> Unit
            }
        }
        return TextGenerationResult(
            id = chunks.filterIsInstance<StreamChunk.Finish>().firstOrNull()?.responseId.orEmpty(),
            model = params.model.modelId,
            message = UIMessage(role = MessageRole.ASSISTANT, parts = parts),
            finishReason = chunks.filterIsInstance<StreamChunk.Finish>().firstOrNull()?.finishReason,
        )
    }

    private suspend fun createSession(headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url("$BASE/api/v0/chat_session/create").headers(headers.toHeaders()).post(EMPTY).build()).await()
        val body = response.body?.string().orEmpty()
        check(response.isSuccessful) { "DeepSeek session creation failed: ${response.code}" }
        json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("biz_data")?.jsonObject?.let { data ->
            (data["chat_session"]?.jsonObject?.get("id") ?: data["id"])?.toString()?.trim('"')
        }?.takeIf(String::isNotBlank) ?: error("DeepSeek session id missing")
    }

    private fun completion(
        headers: Map<String, String>,
        pow: String,
        sessionId: String,
        prompt: String,
        thinking: Boolean,
    ): Flow<StreamChunk> = callbackFlow {
        val body = buildJsonObject {
            put("chat_session_id", sessionId)
            put("parent_message_id", null as String?)
            put("prompt", prompt)
            put("ref_file_ids", kotlinx.serialization.json.JsonArray(emptyList()))
            put("thinking_enabled", thinking)
            put("search_enabled", false)
            put("model_type", "default")
            put("action", null as String?)
            put("preempt", false)
        }
        val requestHeaders = headers.toMutableMap().apply {
            put("Accept", "text/event-stream")
            put("x-ds-pow-response", pow)
        }
        val request = Request.Builder()
            .url("$BASE/api/v0/chat/completion")
            .headers(requestHeaders.toHeaders())
            .post(json.encodeToString(body).toRequestBody(JSON))
            .build()
        val decoder = DeepSeekWebSSE(UUID.randomUUID().toString(), if (thinking) "deepseek-web-thinking" else "deepseek-web")
        val listener = object : EventSourceListener() {
            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (type == "toast") error("DeepSeek 网页端：${data.take(300)}")
                    decoder.accept(data).forEach { trySend(it) }
                    if (data.contains("\"FINISHED\"") || data.trim() == "[DONE]") decoder.finish().forEach { trySend(it) }
                } catch (error: Throwable) {
                    close(error)
                }
            }

            override fun onFailure(source: EventSource, t: Throwable?, response: Response?) {
                close(t ?: error("DeepSeek completion failed: HTTP ${response?.code ?: "unknown"}"))
            }

            override fun onClosed(source: EventSource) {
                decoder.finish().forEach { trySend(it) }
                close()
            }
        }
        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private suspend fun deleteSession(headers: Map<String, String>, sessionId: String) {
        runCatching {
            client.newCall(
                Request.Builder().url("$BASE/api/v0/chat_session/delete").headers(headers.toHeaders())
                    .post(json.encodeToString(buildJsonObject { put("chat_session_id", sessionId) }).toRequestBody(JSON)).build()
            ).await().close()
        }.onFailure { Log.w(TAG, "DeepSeek temporary session cleanup failed", it) }
    }

    private fun requestHeaders(provider: ProviderSetting.DeepSeekWeb): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${provider.token.trim()}")
        if (provider.cookie.isNotBlank()) put("Cookie", provider.cookie.trim())
        put("Accept", "application/json, text/plain, */*")
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        put("Content-Type", "application/json")
        put("Origin", BASE)
        put("Referer", "$BASE/")
        put("User-Agent", provider.fingerprintHeaders.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: DEFAULT_UA)
        provider.fingerprintHeaders.forEach { (name, value) ->
            if (name.lowercase() != "authorization" && name.lowercase() != "cookie" && name.lowercase() != "x-ds-pow-response") put(name, value)
        }
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers = okhttp3.Headers.Builder().apply {
        forEach { (name, value) -> add(name, value) }
    }.build()

    companion object {
        private const val TAG = "DeepSeekWebProvider"
        private const val BASE = "https://chat.deepseek.com"
        private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY = "{}".toRequestBody(JSON)
        private val gate = DeepSeekWebGate()
    }
}
