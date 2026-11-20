package me.rerere.ai.provider.providers.openai

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.*
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.provider.stream.SseEvent
import me.rerere.ai.ui.*
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DeepSeekConversationToolsTest {
    private val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    private val params = TextGenerationParams(Model(modelId = "deepseek-chat", tools = setOf(BuiltInTools.Search, BuiltInTools.ReadUrl)))
    private val context = ClientContext("2026-11-20", "Friday", language = "English")
    private val signed = json.parseToJsonElement("""{"index":0,"id":"signed-1","type":"deepseek_read_url","kind":"read_link","action":{"type":"read_link","url":"https://example.com"},"status":"completed","page":{"title":"Example","url":"https://example.com"},"signature":"keep-me","future_field":42}""").jsonObject
    private fun steps(call: JsonObject = signed) = buildJsonArray { add(buildJsonObject {
        put("index", 2); put("reasoning_content", "step thinking"); putJsonArray("calls") { add(call) }
    }) }

    private fun request(
        provider: ProviderSetting.OpenAI = ProviderSetting.OpenAI(baseUrl = "https://api.deepseek.com/v1"),
        requestParams: TextGenerationParams = params,
        messages: List<UIMessage> = listOf(UIMessage.user("hello")),
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod("buildChatCompletionRequest",
            List::class.java, TextGenerationParams::class.java, ProviderSetting.OpenAI::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(api, messages, requestParams, provider, false) as JsonObject
    }

    @Test fun `chat tools declare type only without function capability`() {
        val definitions = request()["tools"]!!.jsonArray
        assertEquals(listOf("deepseek_web_search", "deepseek_read_url"), definitions.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertTrue(definitions.all { it.jsonObject.keys == setOf("type") })
    }

    @Test fun `function tools and server tools share a single array`() {
        val tool = Tool("test", "test", parameters = { null }, execute = { emptyList() })
        val body = request(requestParams = params.copy(model = params.model.copy(abilities = listOf(ModelAbility.TOOL)), tools = listOf(tool)))
        assertEquals(listOf("function", "deepseek_web_search", "deepseek_read_url"), body["tools"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    }

    @Test fun `read url alone is independent of search`() {
        val body = request(requestParams = params.copy(model = params.model.copy(tools = setOf(BuiltInTools.ReadUrl))))
        assertEquals("deepseek_read_url", body["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun `other chat providers neither declare deepseek tools nor replay signed calls`() {
        val body = request(ProviderSetting.OpenAI(), params.copy(model = params.model.copy(modelId = "gpt-4o"), clientContext = context),
            listOf(UIMessage.user("question"), UIMessage(role = MessageRole.ASSISTANT, parts = listOf(signed.toDeepSeekServerTool(2)))))
        assertFalse("tools" in body)
        assertFalse(body.toString().contains("latest_reminder"))
        assertFalse(body.toString().contains("keep-me"))
    }

    @Test fun `relay model name activates all three protocol declarations`() {
        val relay = "https://relay.example/v1"
        assertEquals(2, request(ProviderSetting.OpenAI(baseUrl = relay))["tools"]!!.jsonArray.size)
        val response = ResponseAPI(OkHttpClient()).buildRequestBody(ProviderSetting.OpenAI(baseUrl = relay), listOf(UIMessage.user("q")), params, false)
        assertEquals(setOf("deepseek_web_search", "deepseek_read_url"), response["tools"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }.toSet())
        val method = ClaudeProvider::class.java.getDeclaredMethod("buildMessageRequest", ProviderSetting.Claude::class.java,
            List::class.java, TextGenerationParams::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val claude = method.invoke(ClaudeProvider(OkHttpClient()), ProviderSetting.Claude(baseUrl = relay), listOf(UIMessage.user("q")), params, false) as JsonObject
        claude["tools"]!!.jsonArray.forEach { assertEquals(it.jsonObject["type"], it.jsonObject["name"]) }
    }

    @Test fun `responses official host is detected even for an alias model`() {
        val body = ResponseAPI(OkHttpClient()).buildRequestBody(ProviderSetting.OpenAI(baseUrl = "https://api.deepseek.com"),
            listOf(UIMessage.user("q")), params.copy(model = params.model.copy(modelId = "alias")), false)
        assertEquals("deepseek_web_search", body["tools"]!!.jsonArray.first().jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun `non deepseek responses and messages keep their own search tools`() {
        val ordinary = params.copy(model = params.model.copy(modelId = "normal"))
        val response = ResponseAPI(OkHttpClient()).buildRequestBody(ProviderSetting.OpenAI(), listOf(UIMessage.user("q")), ordinary, false)
        assertEquals("web_search", response["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
        val method = ClaudeProvider::class.java.getDeclaredMethod("buildMessageRequest", ProviderSetting.Claude::class.java,
            List::class.java, TextGenerationParams::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val body = method.invoke(ClaudeProvider(OkHttpClient()), ProviderSetting.Claude(), listOf(UIMessage.user("q")), ordinary, false) as JsonObject
        assertEquals("web_search_20250305", body["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun `non streaming response preserves signed calls and citations for replay`() {
        val wire = buildJsonObject {
            put("role", "assistant"); put("content", "Answer"); put("server_tool_calls", steps())
            put("annotations", json.parseToJsonElement("""[{"type":"url_citation","url_citation":{"url":"https://example.com","title":"Example"}}]"""))
        }
        val parse = ChatCompletionsAPI::class.java.getDeclaredMethod("parseMessage", JsonObject::class.java).apply { isAccessible = true }
        val message = parse.invoke(api, wire) as UIMessage
        val tool = message.parts.filterIsInstance<UIMessagePart.ServerTool>().single()
        assertEquals(signed, tool.output)
        assertEquals(signed, tool.metadataAs<ServerToolMetadata>()!!.call)
        assertEquals(1, message.annotations.size)
        val replay = request(messages = listOf(UIMessage.user("q"), message))["messages"]!!.jsonArray.last().jsonObject
        assertEquals(signed, replay["server_tool_calls"]!!.jsonArray.single().jsonObject["calls"]!!.jsonArray.single())
        assertEquals("", replay["server_tool_calls"]!!.jsonArray.single().jsonObject["reasoning_content"]!!.jsonPrimitive.content)
    }

    @Test fun `server only assistant is retained with empty content string`() {
        val message = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(signed.toDeepSeekServerTool(2)))
        val replay = request(messages = listOf(message))["messages"]!!.jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(""), replay["content"])
        assertEquals(2, replay["server_tool_calls"]!!.jsonArray.single().jsonObject["index"]!!.jsonPrimitive.int)
    }

    @Test fun `stream displays progress then patches signature once without duplicating the call`() {
        val decoder = ChatCompletionsStreamDecoder()
        fun event(type: String, call: JsonObject) = SseEvent(data = buildJsonObject {
            putJsonArray("choices") { add(buildJsonObject { put("delta", buildJsonObject {})
                put("server_tool_event", buildJsonObject { put("type", type); put("step", 2); put("call", call) }) }) }
        }.toString())
        val unsigned = JsonObject(signed - "signature")
        val start = decoder.accept(event("call_started", unsigned)).chunks
        val complete = decoder.accept(event("call_completed", unsigned)).chunks
        assertEquals(1, start.filterIsInstance<StreamChunk.ServerToolStart>().size)
        assertEquals(1, complete.filterIsInstance<StreamChunk.ServerToolEnd>().size)
        val final = decoder.accept(SseEvent(data = buildJsonObject {
            putJsonArray("choices") { add(buildJsonObject {
                put("finish_reason", "stop"); put("delta", buildJsonObject { put("server_tool_calls", steps()) })
            }) }
        }.toString())).chunks + decoder.onClosed()
        val handler = StreamChunkHandler(params.model)
        var messages = listOf(UIMessage.user("q"))
        (start + complete + final).forEach { messages = handler.handle(messages, it) }
        val tool = messages.last().parts.filterIsInstance<UIMessagePart.ServerTool>().single()
        assertEquals("deepseek_read_url", tool.toolName)
        assertEquals(signed, tool.metadataAs<ServerToolMetadata>()!!.call)
        assertEquals(ServerToolStatus.COMPLETED, tool.status)
        assertTrue(decoder.onClosed().isEmpty())
    }

    @Test fun `final snapshot without progress events supplies name and failed status`() {
        val decoder = ChatCompletionsStreamDecoder()
        val failed = JsonObject(signed + ("status" to JsonPrimitive("failed")))
        decoder.accept(SseEvent(data = """{"choices":[{"delta":{"server_tool_calls":${steps(failed)}},"finish_reason":"stop"}]}"""))
        val chunks = decoder.onClosed()
        assertEquals("deepseek_read_url", chunks.filterIsInstance<StreamChunk.ServerToolStart>().single().toolName)
        assertEquals(ServerToolStatus.FAILED, chunks.filterIsInstance<StreamChunk.ServerToolEnd>().single().status)
    }

    @Test fun `historical reminders use each user date and never change source messages`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val messages = listOf(UIMessage.user("past").copy(createdAt = LocalDateTime(2026, 11, 19, 12, 0)),
                UIMessage.assistant("reply"), UIMessage.user("now").copy(createdAt = LocalDateTime(2026, 11, 20, 12, 0)))
            val body = request(requestParams = params.copy(clientContext = context), messages = messages)
            val wire = body["messages"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("latest_reminder", "user", "assistant", "latest_reminder", "user"), wire.map { it["role"]!!.jsonPrimitive.content })
            assertEquals("2026-11-19,Thursday,,App,English", wire[0]["content"]!!.jsonPrimitive.content)
            assertEquals("2026-11-20,Friday,,App,English", wire[3]["content"]!!.jsonPrimitive.content)
            assertEquals(3, messages.size)
            assertFalse(request().toString().contains("latest_reminder"))
        } finally { Locale.setDefault(originalLocale) }
    }
}
