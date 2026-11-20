package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.VideoInputException
import me.rerere.ai.provider.validateVideoInput
import me.rerere.ai.provider.withoutVideoParts
import me.rerere.ai.provider.forProviderVideoInput
import me.rerere.ai.provider.VIDEO_OMITTED_PLACEHOLDER
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.encodeBase64
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class ChatCompletionsVideoTest {
    private val data = "data:video/mp4;base64,AAAA"
    private val model = Model(modelId = "video-test", abilities = listOf(ModelAbility.VIDEO))

    private fun request(messages: List<UIMessage>, model: Model = this.model, stream: Boolean = false): JsonObject {
        val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest", List::class.java, TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return try {
            method.invoke(api, messages, TextGenerationParams(model = model),
                ProviderSetting.OpenAI(baseUrl = "https://video.invalid/v1"), stream) as JsonObject
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun videoMessage(url: String = data, role: MessageRole = MessageRole.USER) =
        UIMessage(role = role, parts = listOf(UIMessagePart.Video(url)))

    @Test fun requestCopyDowngradesVideoForStreamingAndNonStreaming() {
        for (stream in listOf(false, true)) {
            val original = listOf(UIMessage(role = MessageRole.USER, parts = listOf(
                UIMessagePart.Text("Describe this video"), UIMessagePart.Video(data),
            )))
            val body = request(original.forProviderVideoInput(ProviderSetting.OpenAI()), stream = stream)
            val message = body["messages"]!!.jsonArray.single().jsonObject
            assertEquals("user", message["role"]!!.jsonPrimitive.content)
            assertTrue(body.toString().contains("[视频]"))
            assertTrue(body.toString().contains("Describe this video"))
            assertFalse(body.toString().contains("video_url"))
            assertFalse(body.toString().contains(data))
            assertEquals(data, (original.single().parts[1] as UIMessagePart.Video).url)
        }
    }

    @Test fun videoOnlyUserAndAssistantMessagesAreNotDropped() {
        val body = request(listOf(videoMessage(), videoMessage(role = MessageRole.ASSISTANT))
            .forProviderVideoInput(ProviderSetting.OpenAI()))
        val messages = body["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        messages.forEach { message ->
            assertEquals("[视频]", message.jsonObject["content"]!!.jsonPrimitive.content)
        }
    }

    @Test fun bypassingFactoryCannotEnableOpenAiVideoWithAnOldAbilityFlag() {
        val error = assertThrows(VideoInputException::class.java) { request(listOf(videoMessage()), Model()) }
        assertEquals(VideoInputException.Reason.UNSUPPORTED_PROTOCOL, error.reason)
        assertFalse(error.message!!.contains(data))
    }

    @Test fun unreadableVideoIsRejectedBeforeTryingToReadIt() {
        val privateUrl = "private-invalid-video-path"
        val error = assertThrows(VideoInputException::class.java) { request(listOf(videoMessage(privateUrl))) }
        assertEquals(VideoInputException.Reason.UNSUPPORTED_PROTOCOL, error.reason)
        assertFalse(error.message!!.contains(privateUrl))
    }

    @Test fun malformedDataVideoIsRejectedBeforeTryingToEncodeIt() {
        val error = assertThrows(VideoInputException::class.java) { request(listOf(videoMessage("data:video/mp4;base64,"))) }
        assertEquals(VideoInputException.Reason.UNSUPPORTED_PROTOCOL, error.reason)
    }

    @Test fun dataVideoEncodingRetainsOrRemovesItsPrefixAsRequested() {
        assertEquals(data, UIMessagePart.Video(data).encodeBase64().getOrThrow())
        assertEquals("AAAA", UIMessagePart.Video(data).encodeBase64(withPrefix = false).getOrThrow())
    }

    @Test fun googleNativeVideoRemainsAvailableWithoutANewAbilityFlag() {
        validateVideoInput(ProviderSetting.Google(), Model(), listOf(videoMessage()))
    }

    @Test fun unimplementedProtocolsFailEvenWhenTheModelFlagIsOn() {
        val messages = listOf(videoMessage())
        val protocols = listOf(ProviderSetting.OpenAI(useResponseApi = true), ProviderSetting.Claude())
        protocols.forEach { setting ->
            val error = assertThrows(VideoInputException::class.java) { validateVideoInput(setting, model, messages) }
            assertEquals(VideoInputException.Reason.UNSUPPORTED_PROTOCOL, error.reason)
        }
    }

    @Test fun ordinaryTextDoesNotRequireVideoCapability() {
        assertEquals("hello", request(listOf(UIMessage.user("hello")), Model())["messages"]!!.jsonArray.single()
            .jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test fun compressionCopyOmitsVideoInActualSerializerEvenWhenVideoIsDisabled() {
        val original = listOf(UIMessage(role = MessageRole.USER, parts = listOf(
            UIMessagePart.Text("summarize"), UIMessagePart.Video(data),
            UIMessagePart.Image("data:image/png;base64,AAAA"),
        )))
        val body = request(original.withoutVideoParts(), Model())
        val serialized = body.toString()
        assertFalse(serialized.contains("video_url"))
        assertFalse(serialized.contains(data))
        assertTrue(serialized.contains(VIDEO_OMITTED_PLACEHOLDER))
        assertTrue(serialized.contains("image_url"))
        assertTrue(original.single().parts[1] is UIMessagePart.Video)
        assertEquals(data, (original.single().parts[1] as UIMessagePart.Video).url)
    }

    @Test fun oldModelDataAndExistingAbilityNamesRemainCompatible() {
        val old = Json.decodeFromString<Model>("""{"modelId":"old","abilities":["TOOL","REASONING"]}""")
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), old.abilities)
        assertFalse(ModelAbility.VIDEO in Json.decodeFromString<Model>("{}").abilities)
        assertEquals(ModelAbility.VIDEO, ModelAbility.entries.last())
        assertEquals(old.copy(abilities = old.abilities + ModelAbility.VIDEO),
            Json.decodeFromString<Model>(Json.encodeToString(old.copy(abilities = old.abilities + ModelAbility.VIDEO))))
    }
}
