package me.rerere.rikkahub.ui.pages.character

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.duplicateForCharacter
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.uuid.Uuid

class CharacterTransferAttachmentsTest {
    @Test fun missingAttachmentKeepsOriginalUrlAndHealthyAttachmentsStillCopy() {
        val missing = "file:///source/missing.jpg"
        val healthy = "file:///source/healthy.jpg"
        val nested = "file:///source/nested.jpg"
        val original = Conversation.ofId(Uuid.random(), messages = listOf(
            UIMessage.user("Keep the conversation text").copy(parts = listOf(
                UIMessagePart.Text("Keep the conversation text"), UIMessagePart.Image(missing),
                UIMessagePart.Image(healthy), UIMessagePart.Image(healthy),
                UIMessagePart.Tool(toolCallId = "tool", toolName = "read", input = "{}", output = listOf(
                    UIMessagePart.Image(missing), UIMessagePart.Image(nested),
                )),
            )).toMessageNode(),
        ))
        val attempts = mutableListOf<String>()
        val attachments = CharacterTransferAttachments { url, id ->
            attempts += url
            if (url == missing) null else "file:///copy/$id/${url.substringAfterLast('/')}"
        }
        val target = Uuid.random()
        val copyId = Uuid.random()
        val copy = original.duplicateForCharacter(target, copyId, emptySet(), attachments.forConversation(copyId))

        assertEquals(1, attachments.missing)
        assertEquals(listOf(missing, healthy, nested), attempts)
        assertEquals(listOf("file:///copy/$copyId/healthy.jpg", "file:///copy/$copyId/nested.jpg"), attachments.copiedUrls)
        assertEquals(setOf(missing) + attachments.copiedUrls, copy.messageNodes.flatMap { it.messages }.flatMap { it.parts }.localFileUrls())
        assertEquals(setOf(missing, healthy, nested), original.messageNodes.flatMap { it.messages }.flatMap { it.parts }.localFileUrls())
        assertEquals("Keep the conversation text", (copy.currentMessages.single().parts.first() as UIMessagePart.Text).text)
        assertEquals(target, copy.assistantId)
        assertNotEquals(original.messageNodes.single().id, copy.messageNodes.single().id)
    }

    @Test fun cacheIsLocalToEachConversationSoCopiesHaveSeparateDestinations() {
        val first = Uuid.random()
        val second = Uuid.random()
        val attachments = CharacterTransferAttachments { _, id -> "file:///copy/$id/image.jpg" }
        assertEquals("file:///copy/$first/image.jpg", attachments.forConversation(first)("file:///source/image.jpg"))
        assertEquals("file:///copy/$second/image.jpg", attachments.forConversation(second)("file:///source/image.jpg"))
        assertEquals(2, attachments.copiedUrls.size)
        assertEquals(0, attachments.missing)
    }

    @Test fun cleanupListNeverContainsMissingOrOriginalFilesWhenALaterCopyFails() {
        val failure = IOException("No space left on device")
        val attachments = CharacterTransferAttachments { url, _ -> when {
            url.endsWith("missing.jpg") -> null
            url.endsWith("full.jpg") -> throw failure
            else -> "file:///copy/good.jpg"
        } }
        val rewrite = attachments.forConversation(Uuid.random())

        rewrite("file:///source/good.jpg")
        rewrite("file:///source/missing.jpg")
        assertSame(failure, assertThrows(IOException::class.java) { rewrite("file:///source/full.jpg") })
        assertEquals(listOf("file:///copy/good.jpg"), attachments.copiedUrls)
        assertEquals(1, attachments.missing)
    }
}
