package me.rerere.ai.provider

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class VideoInputTest {
    @Test fun onlyGoogleKeepsOriginalVideoAndOtherProvidersKeepMetadataInRequestCopies() {
        val original = UIMessage(role = MessageRole.USER, parts = listOf(
            UIMessagePart.Text("before"), UIMessagePart.Video("private-video"), UIMessagePart.Audio("audio")),
            translation = "retained", stepCount = 3, isSynthetic = true)
        val messages = listOf(original)
        assertSame(messages, messages.forProviderVideoInput(ProviderSetting.Google()))
        for (provider in listOf(ProviderSetting.OpenAI(), ProviderSetting.Claude(), ProviderSetting.DeepSeekWeb())) {
            val result = messages.forProviderVideoInput(provider).single()
            assertEquals(original.copy(parts = listOf(original.parts[0], UIMessagePart.Text("[视频]"), original.parts[2])), result)
        }
        assertTrue(original.parts[1] is UIMessagePart.Video)
    }
    @Test fun textOnlyMessagesRetainTheirInstances() {
        val messages = listOf(UIMessage.user("hello"), UIMessage.assistant("world"))
        val result = messages.withoutVideoParts()
        messages.indices.forEach { assertSame(messages[it], result[it]) }
    }

    @Test fun mixedPartsKeepOrderAndMessageMetadata() {
        val text = UIMessagePart.Text("summarize")
        val image = UIMessagePart.Image("image")
        val audio = UIMessagePart.Audio("audio")
        val original = UIMessage(role = MessageRole.USER, parts = listOf(text, UIMessagePart.Video("video"), image, audio),
            modelId = Uuid.random(), translation = "translation", stepCount = 2, isSynthetic = true)
        val result = listOf(original).withoutVideoParts().single()
        assertEquals(original.copy(parts = listOf(text, UIMessagePart.Text(VIDEO_OMITTED_PLACEHOLDER), image, audio)), result)
        assertSame(text, result.parts[0])
        assertSame(image, result.parts[2])
        assertSame(audio, result.parts[3])
    }

    @Test fun videoOnlyMessageBecomesNonemptyText() {
        val message = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Video("video")))
        assertEquals(listOf(UIMessagePart.Text(VIDEO_OMITTED_PLACEHOLDER)), listOf(message).withoutVideoParts().single().parts)
    }

    @Test fun mixedHistoryCopiesOnlyMessagesWithVideo() {
        val text = UIMessage.user("before")
        val video = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Video("video")))
        val result = listOf(text, video).withoutVideoParts()
        assertSame(text, result[0])
        assertNotSame(video, result[1])
        assertEquals(video.id, result[1].id)
        assertEquals(video.createdAt, result[1].createdAt)
        assertEquals(video.role, result[1].role)
    }

    @Test fun callerCanCustomizePlaceholderForEveryVideo() {
        val message = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Video("one"), UIMessagePart.Video("two")))
        assertEquals(listOf(UIMessagePart.Text("omitted"), UIMessagePart.Text("omitted")),
            listOf(message).withoutVideoParts("omitted").single().parts)
    }

    @Test fun sourceListMessagesAndAttachmentPartsAreUnmodified() {
        val video = UIMessagePart.Video("private-original-video")
        val parts = listOf(video)
        val message = UIMessage(role = MessageRole.USER, parts = parts)
        val messages = listOf(message)
        messages.withoutVideoParts()
        assertSame(message, messages.single())
        assertSame(parts, message.parts)
        assertSame(video, message.parts.single())
        assertEquals("private-original-video", video.url)
    }
}
