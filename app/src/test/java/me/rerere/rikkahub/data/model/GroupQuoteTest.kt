package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test

class GroupQuoteTest {
    private val target = UIMessage.user("question")
    private val quote = target.groupQuote("Owner")
    private val sender = GroupMember(name = "A")
    private val available = listOf(quote)

    @Test fun quoteAnnotationRoundTripsWithoutChangingExistingFields() {
        val reply = UIMessage.assistant("body").withGroupSender(sender, "member").copy(annotations = listOf(
            UIMessageAnnotation.GroupSender(sender.id.toString(), sender.name), quote,
            UIMessageAnnotation.UrlCitation("site", "https://example.com")))
        assertEquals(reply, JsonInstant.decodeFromString<UIMessage>(JsonInstant.encodeToString(reply)))
        val payload = JsonInstant.encodeToString(reply)
        assertTrue(payload.contains("group_quote"))
        assertEquals("", JsonInstant.decodeFromString<UIMessageAnnotation>(
            """{"type":"group_quote","targetMessageId":"id","targetSenderName":"owner"}""")
            .let { it as UIMessageAnnotation.GroupQuote }.preview)
    }

    @Test fun parserSupportsBracketsCaseColonWhitespaceAndMiddleMarkers() {
        listOf("[[quote:${target.id}]] body", "【【QUOTE： ${target.id} 】】 body", "before [[QuOtE:${target.id}]] after").forEach { text ->
            val reply = UIMessage.assistant(text).applyGroupQuotes(available)
            assertEquals(listOf(quote), reply.annotations)
            assertFalse(reply.toText().contains(target.id.toString()))
        }
    }

    @Test fun invalidOrMalformedIdsKeepTextAndNeverCreatePhantomQuotes() {
        listOf("[[quote:missing]] body", "[[quote:bad_id]] body", "[[quote:]] body", "[[quote:id] body", "normal").forEach { text ->
            val original = UIMessage.assistant(text)
            assertSame(original, original.applyGroupQuotes(available))
        }
    }

    @Test fun firstValidTargetWinsAndAnnotationsAreNotDuplicated() {
        val other = UIMessage.user("other").groupQuote("Owner")
        val raw = UIMessage.assistant("[[quote:missing]] [[quote:${target.id}]] [[quote:${other.targetMessageId}]] body")
        val reply = raw.applyGroupQuotes(available + other)
        assertEquals("body", reply.toText())
        assertEquals(listOf(quote), reply.annotations)
        assertEquals(reply, reply.applyGroupQuotes(available + other))
    }

    @Test fun onlyAssistantBodyIsParsedAndPartsMetadataAndOrderArePreserved() {
        val reasoning = UIMessagePart.Reasoning("[[quote:${target.id}]] thinking")
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "ask_user", input = "[[quote:${target.id}]]")
        val text = UIMessagePart.Text("[[quote:${target.id}]] body")
        val later = UIMessagePart.Text("[[quote:${target.id}]] later")
        val raw = UIMessage.assistant("").copy(parts = listOf(reasoning, tool, text, later))
        val parsed = raw.applyGroupQuotes(available)
        assertEquals(listOf(reasoning, tool, text.copy(text = "body"), later), parsed.parts)
        assertEquals(listOf(reasoning, tool, text, later), raw.parts)
        assertSame(reasoning, parsed.parts.first())
        assertSame(tool, parsed.parts[1])
        assertSame(raw.copy(role = MessageRole.USER).parts, raw.copy(role = MessageRole.USER).applyGroupQuotes(available).parts)
        val toolOnly = raw.copy(parts = listOf(reasoning, tool))
        assertSame(toolOnly, toolOnly.applyGroupQuotes(available))
    }

    @Test fun quoteRequestsMergeIntoFirstTextForUsersAndMembersWithoutInsertingParts() {
        val reasoning = UIMessagePart.Reasoning("thinking")
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "ask_user", input = "{}")
        val user = UIMessage.user("body").copy(annotations = listOf(quote))
        val member = UIMessage.assistant("").copy(parts = listOf(reasoning, UIMessagePart.Text("answer"), tool),
            annotations = listOf(UIMessageAnnotation.GroupSender(sender.id.toString(), sender.name), quote))
        val request = listOf(target, user, member).withGroupSpeakerPrefixes(replyLabel = "Reply")
        assertEquals("[Reply Owner:question] body", request[1].toText())
        assertEquals(listOf(reasoning, UIMessagePart.Text("[A] [Reply Owner:question] answer"), tool), request[2].parts)
        assertEquals(member.parts.size, request[2].parts.size)
        assertEquals("body", user.toText())
        assertEquals("answer", (member.parts[1] as UIMessagePart.Text).text)
    }

    @Test fun missingTargetFallsBackToPreviewAndToolOnlyRequestsAreUnchanged() {
        val message = UIMessage.assistant("body").copy(annotations = listOf(quote))
        assertEquals("[Reply Owner:question] body", listOf(message).withGroupSpeakerPrefixes().single().toText())
        val raw = message.copy(parts = listOf(UIMessagePart.Reasoning("thinking")))
        assertEquals(raw, listOf(raw).withGroupSpeakerPrefixes().single())
    }

    @Test fun quoteListIncludesOnlyRecentTenBodyMessagesAndUsesStableIds() {
        val messages = (0..11).map { UIMessage.user("question-$it" + "x".repeat(100)) }
        val reasoning = UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Reasoning("thinking")))
            .withGroupSender(sender, "member")
        val plainAssistant = UIMessage.assistant("not a group member")
        val quotes = (messages + reasoning + plainAssistant).availableGroupQuotes("Owner")
        assertEquals(messages.takeLast(10).map { it.id.toString() }, quotes.map { it.targetMessageId })
        assertTrue(quotes.all { it.preview.length == 60 && it.targetSenderName == "Owner" })
    }

    @Test fun streamingPartialMarkerKeepsRawBufferAndCompleteMarkerCanBeReprocessed() {
        val raw = UIMessage.assistant("[[quote:${target.id}")
        assertSame(raw, raw.applyGroupQuotes(available))
        val full = raw.copy(parts = listOf(UIMessagePart.Text("[[quote:${target.id}]] body")))
        assertEquals("body", full.applyGroupQuotes(available).toText())
        assertEquals("[[quote:${target.id}]] body", full.toText())
        assertEquals(full.applyGroupQuotes(available), full.applyGroupQuotes(available))
    }
}
