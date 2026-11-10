package me.rerere.rikkahub.service.pulse

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.util.KeyRoulette
import me.rerere.rikkahub.data.ai.appendPulseInstruction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class PulseTurnInputTest {
    @Test
    fun `empty history gets a nonpersistent trigger and fixed framework constraint`() {
        val history = emptyList<UIMessage>()
        val input = pulseTurnInput(history, "check tasks")
        assertEquals(1, input.messages.size)
        assertTrue(input.messages.single().isSynthetic)
        assertEquals("check tasks", input.messages.single().toText())
        assertEquals(PULSE_TURN_CONSTRAINT, input.systemInstruction)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `nonempty history is preserved and instruction is the final synthetic user`() {
        val history = listOf(UIMessage.user("hello"), UIMessage.assistant("HEARTBEAT_OK"))
        val input = pulseTurnInput(history, "check tasks")
        assertEquals(history.size + 1, input.messages.size)
        assertEquals(history, input.messages.dropLast(1))
        assertEquals(MessageRole.USER, input.messages.last().role)
        assertTrue(input.messages.last().isSynthetic)
        assertEquals("check tasks", input.messages.last().toText())
        assertEquals(PULSE_TURN_CONSTRAINT, input.systemInstruction)
        assertFalse(history.first().isSynthetic)
    }

    @Test
    fun `trailing user text is not overwritten by an automatic trigger`() {
        val history = listOf(UIMessage.user("keep this message"))
        val input = pulseTurnInput(history, "check tasks")
        assertSame(history.single(), input.messages.first())
        assertFalse(history.single().isSynthetic)
        assertEquals(listOf("keep this message", "check tasks"), input.messages.map { it.toText() })
        assertTrue(input.messages.last().isSynthetic)
    }

    @Test
    fun `serialized request keeps long heartbeat instruction out of system and at end of messages`() {
        val instruction = "[HEARTBEAT] Check notifications, SMS, TODO and streams.\n" +
            "Use the enabled tools before deciding whether there is anything to report.\n".repeat(25) +
            "If nothing needs attention, reply only HEARTBEAT_OK."
        val input = pulseTurnInput(listOf(UIMessage.assistant("HEARTBEAT_OK")), instruction)
        val system = StringBuilder("Persona\n<memory_system>Memory instructions</memory_system>\n<workspace>Workspace instructions</workspace>")
        appendPulseInstruction(system, input.systemInstruction)
        val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest", List::class.java, TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val body = method.invoke(api, listOf(UIMessage.system(system.toString())) + input.messages,
            TextGenerationParams(model = Model(modelId = "deepseek-fixture")),
            ProviderSetting.OpenAI(baseUrl = "https://example.test/v1"), false) as JsonObject
        val messages = body.getValue("messages").jsonArray
        fun text(element: JsonElement): String = when (element) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            else -> error("Unexpected message content")
        }
        assertEquals("system", messages.first().jsonObject.getValue("role").jsonPrimitive.content)
        val systemText = text(messages.first().jsonObject.getValue("content"))
        assertEquals(system.toString(), systemText)
        assertTrue(systemText.endsWith(PULSE_TURN_CONSTRAINT))
        assertEquals(1, Regex("## 时间与等待").findAll(systemText).count())
        assertFalse(systemText.contains(instruction))
        assertFalse(systemText.contains("[HEARTBEAT]"))
        assertEquals("user", messages.last().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(instruction, text(messages.last().jsonObject.getValue("content")))
        println("PULSE_REQUEST_FIXTURE $body")
    }
}
