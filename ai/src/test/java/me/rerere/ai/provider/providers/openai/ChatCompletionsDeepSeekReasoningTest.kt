package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatCompletionsDeepSeekReasoningTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `deepseek host forces reasoning history when tools are sent`() {
        val body = buildRequest(
            baseUrl = "https://api.deepseek.com/v1",
            modelId = "thinking-model",
            includeHistoryReasoning = false,
        )

        assertTrue(assistantMessages(body).single().jsonObject.containsKey("reasoning_content"))
    }

    @Test
    fun `deepseek model forces reasoning history through a third party host`() {
        val body = buildRequest(
            baseUrl = "https://proxy.example.com/v1",
            modelId = "deepseek-v4-flash",
            includeHistoryReasoning = false,
        )

        assertTrue(assistantMessages(body).single().jsonObject.containsKey("reasoning_content"))
    }

    @Test
    fun `non deepseek providers keep the history reasoning switch behavior`() {
        val body = buildRequest(
            baseUrl = "https://api.example.com/v1",
            modelId = "thinking-model",
            includeHistoryReasoning = false,
        )

        assertFalse(assistantMessages(body).single().jsonObject.containsKey("reasoning_content"))
    }

    @Test
    fun `deepseek without tools does not force reasoning history`() {
        val body = buildRequest(
            baseUrl = "https://api.deepseek.com/v1",
            modelId = "deepseek-v4-flash",
            includeHistoryReasoning = false,
            withTools = false,
        )

        assertFalse(assistantMessages(body).single().jsonObject.containsKey("reasoning_content"))
    }

    private fun buildRequest(
        baseUrl: String,
        modelId: String,
        includeHistoryReasoning: Boolean,
        withTools: Boolean = true,
        parts: List<UIMessagePart>? = null,
        reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
        stream: Boolean = false,
        messages: List<UIMessage>? = null,
        customBody: List<CustomBody> = emptyList(),
        reasoningAbility: Boolean = true,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true

        return method.invoke(
            api,
            messages ?: listOf(
                UIMessage.user("Use a tool."),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = parts ?: listOf(
                        UIMessagePart.Reasoning(reasoning = "Historical reasoning"),
                        UIMessagePart.Text("I will use a tool."),
                    ),
                ),
            ),
            TextGenerationParams(
                model = Model(
                    modelId = modelId,
                    abilities = (if (withTools) listOf(ModelAbility.TOOL) else emptyList()) +
                        (if (reasoningAbility) listOf(ModelAbility.REASONING) else emptyList()),
                ),
                tools = if (withTools) listOf(testTool()) else emptyList(),
                reasoningLevel = reasoningLevel,
                customBody = customBody,
            ),
            ProviderSetting.OpenAI(
                baseUrl = baseUrl,
                includeHistoryReasoning = includeHistoryReasoning,
            ),
            stream,
        ) as JsonObject
    }

    private fun assistantMessages(body: JsonObject) = body["messages"]!!
        .jsonArray
        .filter { it.jsonObject["role"]?.jsonPrimitive?.content == "assistant" }

    private fun testTool() = Tool(
        name = "test_tool",
        description = "A test tool.",
        execute = { emptyList() },
    )

    @Test fun `deepseek tool continuation replays empty and missing reasoning`() {
        for (emptyPart in listOf(emptyList(), listOf(UIMessagePart.Reasoning(reasoning = "")))) {
            val body = buildRequest("https://api.deepseek.com/v1", "deepseek-reasoner", false,
                parts = listOf(UIMessagePart.Reasoning(reasoning = "first"),
                    UIMessagePart.Tool(toolCallId = "call_1", toolName = "test_tool", input = "{}",
                        output = listOf(UIMessagePart.Text("result")))) + emptyPart + UIMessagePart.Text("done"))
            val assistants = assistantMessages(body)
            assertEquals(2, assistants.size)
            assertEquals("first", assistants.first().jsonObject["reasoning_content"]!!.jsonPrimitive.content)
            assertEquals("", assistants.last().jsonObject["reasoning_content"]!!.jsonPrimitive.content)
        }
    }

    @Test fun `empty field is not forced for other providers no tools or thinking off`() {
        for ((host, tools, level) in listOf(Triple("https://api.openai.com/v1", true, ReasoningLevel.AUTO),
            Triple("https://api.deepseek.com/v1", false, ReasoningLevel.AUTO),
            Triple("https://api.deepseek.com/v1", true, ReasoningLevel.OFF))) {
            val model = if (host.contains("openai")) "gpt-5" else "deepseek-chat"
            val body = buildRequest(host, model, false, withTools = tools,
                parts = listOf(UIMessagePart.Text("done")), reasoningLevel = level)
            assertFalse(assistantMessages(body).single().jsonObject.containsKey("reasoning_content"))
        }
    }

    @Test fun `sequential and parallel calls preserve reasoning and results for single and group streams`() {
        for (stream in listOf(false, true)) for (group in listOf(false, true)) {
            fun tool(id: String) = UIMessagePart.Tool(toolCallId = id, toolName = "test_tool", input = "{}",
                output = listOf(UIMessagePart.Text("result-$id")))
            val prefix = if (group) "[Alice] " else ""
            val messages = listOf(UIMessage.user("question"), UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
                UIMessagePart.Reasoning(reasoning = "first"), UIMessagePart.Text(prefix + "search"), tool("one"), tool("two"),
                UIMessagePart.Text(prefix + "again"), tool("three"), UIMessagePart.Reasoning(reasoning = " "),
                UIMessagePart.Text(prefix + "done"))))
            val body = buildRequest("https://relay.example/v1", "deepseek-reasoner", false, stream = stream, messages = messages)
            val assistants = assistantMessages(body).map { it.jsonObject }
            assertEquals(listOf("first", "", " "), assistants.map { it["reasoning_content"]!!.jsonPrimitive.content })
            assertEquals(listOf(prefix + "search", prefix + "again", prefix + "done"), assistants.map { it["content"]!!.jsonPrimitive.content })
            assertEquals(2, assistants.first()["tool_calls"]!!.jsonArray.size)
            assertEquals(listOf("one", "two", "three"), body["messages"]!!.jsonArray.map { it.jsonObject }
                .filter { it["role"]!!.jsonPrimitive.content == "tool" }.map { it["tool_call_id"]!!.jsonPrimitive.content })
        }
    }

    @Test fun `custom thinking parameters determine empty reasoning presence without capability flag`() {
        for (enabled in listOf(false, true)) {
            val body = buildRequest("https://api.deepseek.com/v1", "deepseek-chat", false,
                parts = listOf(UIMessagePart.Text("answer")), reasoningLevel = ReasoningLevel.OFF,
                reasoningAbility = false, customBody = listOf(CustomBody("thinking",
                    buildJsonObject { put("type", if (enabled) "enabled" else "disabled") })))
            assertEquals(enabled, assistantMessages(body).single().jsonObject.containsKey("reasoning_content"))
        }
    }
}
