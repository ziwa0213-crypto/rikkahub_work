package me.rerere.rikkahub.data.model

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.handleTextGenerationResult
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.transformers.applyMessageTemplate
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOP_FallbackServiceProvider
import java.io.Reader
import java.io.StringReader
import kotlin.uuid.Uuid

class GroupChatTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun initializeJvmLogging() {
            // Pebble must not initialize the Android logger in local JVM tests.
            val key = "slf4j.provider"
            val previous = System.getProperty(key)
            try {
                System.setProperty(key, NOP_FallbackServiceProvider::class.java.name)
                LoggerFactory.getILoggerFactory()
            } finally {
                if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
            }
        }
    }

    @Test fun legacyGroupUsesPersistedDefaults() {
        val group = JsonInstant.decodeFromString<Group>("""{"name":"legacy"}""")
        assertTrue(group.enableMemory)
        assertEquals("", group.systemPrompt)
        assertTrue(group.conversationIds.isEmpty())
        val current = group.copy(enableMemory = false, systemPrompt = "shared", conversationIds = listOf(Uuid.random()))
        assertEquals(current, JsonInstant.decodeFromString<Group>(JsonInstant.encodeToString(current)))
    }

    @Test fun normalizationKeepsConversationMembershipAndDeduplicatesIds() {
        val id = Uuid.random()
        val group = Group(conversationIds = listOf(id, id, Uuid.random()))
        val result = Settings(groups = listOf(group)).normalizeGroupData().groups.single()
        assertEquals(group.conversationIds.distinct(), result.conversationIds)
        assertEquals(result, Settings(groups = listOf(result)).normalizeGroupData().groups.single())
    }

    @Test fun enabledMembersFollowPriorityAndSkipMissingMembers() {
        val a = GroupMember(name = "A")
        val b = GroupMember(name = "B")
        val c = GroupMember(name = "C")
        val group = Group(members = listOf(GroupMemberRef(a.id, priority = 2),
            GroupMemberRef(b.id, priority = 0), GroupMemberRef(c.id, enabled = false),
            GroupMemberRef(Uuid.random(), priority = 1)))
        assertEquals(listOf(b, a), group.enabledMembers(Settings(groupMembers = listOf(a, b, c))))
    }

    @Test fun pseudoAssistantUsesMemberTuningAndSharedGroupResources() {
        val member = GroupMember(name = "A", chatModelId = Uuid.random(), systemPrompt = "member",
            streamOutput = false, reasoningLevel = ReasoningLevel.OFF, temperature = 0.3f,
            topP = 0.8f, contextMessageLimit = 12, maxTokens = 128, avatar = Avatar.Emoji("A"))
        val group = Group(systemPrompt = "group", reasoningLevel = ReasoningLevel.HIGH,
            localTools = listOf(LocalToolOption.TimeInfo), mcpServers = setOf(Uuid.random()),
            modeInjectionIds = setOf(Uuid.random()), lorebookIds = setOf(Uuid.random()),
            enabledSkills = setOf("test"), customHeaders = listOf(CustomHeader("X-Test", "value")),
            customBodies = listOf(CustomBody("test", JsonPrimitive(true))), background = "file:///test.png")
        val assistant = member.toAssistant(group, "frame")
        assertEquals(member.id, assistant.id)
        assertEquals(member.avatar, assistant.avatar)
        assertEquals(member.chatModelId, assistant.chatModelId)
        assertFalse(assistant.streamOutput)
        assertEquals("group\n\nmember\n\nframe", assistant.systemPrompt)
        assertEquals(member.temperature, assistant.temperature)
        assertEquals(member.topP, assistant.topP)
        assertEquals(member.maxTokens, assistant.maxTokens)
        assertEquals(member.contextMessageLimit, assistant.contextMessageLimit)
        assertEquals(group.reasoningLevel, assistant.reasoningLevel)
        assertEquals(group.localTools, assistant.localTools)
        assertEquals(group.mcpServers, assistant.mcpServers)
        assertEquals(group.enabledSkills, assistant.enabledSkills)
        assertEquals(group.modeInjectionIds, assistant.modeInjectionIds)
        assertEquals(group.lorebookIds, assistant.lorebookIds)
        assertEquals(group.customHeaders, assistant.customHeaders)
        assertEquals(group.customBodies, assistant.customBodies)
        assertEquals(group.background, assistant.background)
        assertTrue(assistant.enableMemory)
        assertEquals(ToolApprovalMode.Partial, assistant.toolApprovalMode)
        assertNull(assistant.memoryGroupId)
        assertFalse(assistant.useGlobalMemory)
        assertFalse(member.toAssistant(group.copy(enableMemory = false)).enableMemory)
        assertNull(member.copy(chatModelId = null).toAssistant(group).chatModelId)
    }

    @Test fun senderIsPersistentAndDoesNotReplaceCitationsOrUserMessages() {
        val member = GroupMember(name = "Alice")
        val citation = UIMessageAnnotation.UrlCitation("source", "https://example.com")
        val user = UIMessage.user("question")
        assertEquals(user, user.withGroupSender(member, "fallback"))
        val reply = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("answer")),
            annotations = listOf(citation)).withGroupSender(member, "fallback")
        assertEquals(listOf(citation), reply.annotations.filterIsInstance<UIMessageAnnotation.UrlCitation>())
        assertEquals(1, reply.withGroupSender(member, "fallback").annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().size)
        val node = MessageNode(messages = listOf(reply, reply.copy(id = Uuid.random())))
        val conversation = Conversation(assistantId = GROUP_CHAT_ASSISTANT_ID, messageNodes = listOf(node))
        val restored = JsonInstant.decodeFromString<Conversation>(JsonInstant.encodeToString(conversation))
        assertEquals(conversation, restored)
        assertTrue(restored.isGroupChat())
        assertEquals("Alice", restored.currentMessages.single().annotations
            .filterIsInstance<UIMessageAnnotation.GroupSender>().single().memberName)
        val updated = restored.updateCurrentMessages(listOf(reply.copy(parts = listOf(UIMessagePart.Text("streamed")))))
        assertEquals(2, updated.messageNodes.single().messages.size)
        assertEquals(reply.annotations, updated.currentMessages.single().annotations)
    }

    @Test fun speakerPrefixesAreRequestOnlyAndToolArgumentsAreUntouched() {
        val member = GroupMember(name = "Alice")
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "get_time_info", input = "{}")
        val reply = UIMessage(role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("answer"), tool)).withGroupSender(member, "fallback")
        val stub = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()).withGroupSender(member, "fallback")
        val user = UIMessage.user("question")
        val request = listOf(user, reply, stub).withGroupSpeakerPrefixes()
        assertEquals(2, request.size)
        assertEquals(user, request.first())
        assertTrue(request.last().toText().startsWith("[Alice] "))
        assertEquals(listOf(tool), request.last().getTools())
        assertEquals(listOf(UIMessagePart.Text("answer"), tool), reply.parts)
        assertTrue(stub.parts.isEmpty())
    }

    @Test fun unannotatedSingleChatIsUnchanged() {
        val reply = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("answer")))
        assertEquals(listOf(reply), listOf(reply).withGroupSpeakerPrefixes())
        assertFalse(Conversation(assistantId = Uuid.random(), messageNodes = emptyList()).isGroupChat())
    }

    @Test fun speakerPrefixPreservesReasoningTextAndToolOrderAndMetadata() {
        val member = GroupMember(name = "Alice")
        val reasoning = UIMessagePart.Reasoning("thinking", metadata = buildJsonObject {
            put("reasoningId", "reasoning-1")
            put("encryptedContent", "opaque")
        })
        val text = UIMessagePart.Text("answer", metadata = buildJsonObject { put("id", "text-1") })
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "get_time_info", input = "{}")
        val reply = UIMessage.assistant("").copy(parts = listOf(reasoning, text, tool))
            .withGroupSender(member, "fallback")
        val request = listOf(reply).withGroupSpeakerPrefixes().single()
        assertEquals(reply.copy(parts = listOf(reasoning, text.copy(text = "[Alice] answer"), tool)), request)
        assertSame(reasoning, request.parts.first())
        assertSame(tool, request.parts.last())
        assertEquals(listOf(reasoning, text, tool), reply.parts)
    }

    @Test fun speakerPrefixLeavesReasoningAndToolOnlyMessageUntouched() {
        val reasoning = UIMessagePart.Reasoning("thinking")
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "get_time_info", input = "{}")
        val reply = UIMessage.assistant("").copy(parts = listOf(reasoning, tool))
            .withGroupSender(GroupMember(name = "Alice"), "fallback")
        val request = listOf(reply).withGroupSpeakerPrefixes().single()
        assertEquals(reply, request)
        assertSame(reply.parts, request.parts)
    }

    @Test fun speakerPrefixAnnotatesOnlyFirstTextAcrossToolBoundaries() {
        val reasoning = UIMessagePart.Reasoning("thinking")
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "get_time_info", input = "{}")
        val reply = UIMessage.assistant("").copy(parts = listOf(reasoning,
            UIMessagePart.Text("first"), tool, UIMessagePart.Text("second")))
            .withGroupSender(GroupMember(name = "Alice"), "fallback")
        val request = listOf(reply).withGroupSpeakerPrefixes().single()
        assertEquals(reply.parts.size, request.parts.size)
        assertEquals(listOf(reasoning, UIMessagePart.Text("[Alice] first"), tool,
            UIMessagePart.Text("second")), request.parts)
    }

    @Test fun speakerPrefixDoesNotReorderPreexistingTextBeforeReasoning() {
        val reasoning = UIMessagePart.Reasoning("thinking")
        val reply = UIMessage.assistant("answer").copy(parts = listOf(UIMessagePart.Text("answer"), reasoning))
            .withGroupSender(GroupMember(name = "Alice"), "fallback")
        assertEquals(listOf(UIMessagePart.Text("[Alice] answer"), reasoning),
            listOf(reply).withGroupSpeakerPrefixes().single().parts)
    }

    @Test fun speakerPrefixDoesNotAnnotateUserWithSenderMetadata() {
        val user = UIMessage.user("[Alice] question").copy(annotations = listOf(
            UIMessageAnnotation.GroupSender("member", "Alice")))
        assertSame(user, listOf(user).withGroupSpeakerPrefixes().single())
    }

    @Test fun selfSpeakerPrefixSupportsBothBracketsWhitespaceAndOptionalColon() {
        listOf("[Alice] answer", "[Alice]: answer", "[Alice]：answer", "【Alice】 answer",
            "【Alice】: answer", "【Alice】：answer", " \n[ Alice ] \n： \nanswer").forEach { text ->
            val reply = UIMessage.assistant(text)
            val result = reply.stripSelfSpeakerPrefix("Alice")
            assertEquals(text, "answer", result.toText())
            assertEquals(reply.copy(parts = listOf(UIMessagePart.Text("answer"))), result)
            assertSame(result, result.stripSelfSpeakerPrefix("Alice"))
        }
    }

    @Test fun selfSpeakerPrefixEscapesSpecialCharactersAndKeepsNameWhitespace() {
        listOf("1.0", "a[b]", "a(b)+$?", "a\\b", " Alice ").forEach { name ->
            val reply = UIMessage.assistant("[$name] answer")
            assertEquals(name, "answer", reply.stripSelfSpeakerPrefix(name).toText())
        }
        val other = UIMessage.assistant("[1x0] answer")
        assertSame(other, other.stripSelfSpeakerPrefix("1.0"))
    }

    @Test fun selfSpeakerPrefixLeavesUserSystemAndToolMessagesUntouched() {
        listOf(MessageRole.USER, MessageRole.SYSTEM, MessageRole.TOOL).forEach { role ->
            val message = UIMessage(role = role, parts = listOf(UIMessagePart.Text("[Alice] body")))
            assertSame(message, message.stripSelfSpeakerPrefix("Alice"))
        }
    }

    @Test fun selfSpeakerPrefixLeavesOtherNamesAndMidBodyLabelsUntouched() {
        listOf("[Bob] answer", "[Alice Jr] answer", "Hello [Alice] answer", "Hello\n[Alice] answer",
            "[Alice】 answer", "【Alice] answer").forEach { text ->
            val reply = UIMessage.assistant(text)
            assertSame(text, reply, reply.stripSelfSpeakerPrefix("Alice"))
        }
    }

    @Test fun selfSpeakerPrefixDoesNotSearchLaterTextParts() {
        val reply = UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Text("opening"),
            UIMessagePart.Text("[Alice] later paragraph")))
        assertSame(reply, reply.stripSelfSpeakerPrefix("Alice"))
        val emptyFirst = reply.copy(parts = listOf(UIMessagePart.Text(""), reply.parts.last()))
        assertSame(emptyFirst, emptyFirst.stripSelfSpeakerPrefix("Alice"))
    }

    @Test fun selfSpeakerPrefixKeepsReasoningToolsAnnotationsAndOtherText() {
        val reasoning = UIMessagePart.Reasoning("[Alice] thinking")
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "ask_user", input = "{\"question\":\"[Alice]\"}")
        val text = UIMessagePart.Text("[Alice] answer", metadata = buildJsonObject { put("id", "text-1") })
        val later = UIMessagePart.Text("[Alice] later paragraph")
        val reply = UIMessage.assistant("").copy(parts = listOf(reasoning, text, tool, later),
            annotations = listOf(UIMessageAnnotation.UrlCitation("source", "https://example.com")))
            .withGroupSender(GroupMember(name = "Alice"), "fallback")
        val result = reply.stripSelfSpeakerPrefix("Alice")
        assertEquals(reply.copy(parts = listOf(reasoning, text.copy(text = "answer"), tool, later)), result)
        assertSame(reasoning, result.parts.first())
        assertSame(tool, result.parts[2])
        assertSame(later, result.parts.last())
        assertSame(result, result.stripSelfSpeakerPrefix("Alice"))
        assertEquals(reply, JsonInstant.decodeFromString<UIMessage>(JsonInstant.encodeToString(reply)))
        assertEquals(result, JsonInstant.decodeFromString<UIMessage>(JsonInstant.encodeToString(result)))
    }

    @Test fun selfSpeakerPrefixLeavesEmptyNamesAndNonTextMessagesUntouched() {
        val reply = UIMessage.assistant("[Alice] answer")
        listOf("", " ", "\n").forEach { assertSame(reply, reply.stripSelfSpeakerPrefix(it)) }
        val empty = reply.copy(parts = emptyList())
        val reasoningOnly = reply.copy(parts = listOf(UIMessagePart.Reasoning("[Alice] thinking")))
        assertSame(empty, empty.stripSelfSpeakerPrefix("Alice"))
        assertSame(reasoningOnly, reasoningOnly.stripSelfSpeakerPrefix("Alice"))
    }

    @Test fun selfSpeakerPrefixUsesResolvedFallbackForUnnamedMember() {
        val member = GroupMember(name = "")
        val reply = UIMessage.assistant("[Member] answer").withGroupSender(member, "Member")
            .stripSelfSpeakerPrefix("Member")
        assertEquals("answer", reply.toText())
        assertEquals("Member", reply.annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().single().memberName)
    }

    @Test fun selfSpeakerPrefixCleansCumulativeStreamWithoutMutatingProviderBuffer() {
        val member = GroupMember(name = "Alice")
        val stub = UIMessage.assistant("").copy(parts = emptyList()).withGroupSender(member, "fallback")
        val handler = StreamChunkHandler()
        var rawMessages = listOf(UIMessage.user("[Alice] question"), stub)
        val deltas = listOf("[Al", "ice]", ": ", "answer", " [Alice] in body")
        val expected = listOf("[Al", "", "", "answer", "answer [Alice] in body")
        deltas.forEachIndexed { index, delta ->
            rawMessages = handler.handle(rawMessages, StreamChunk.TextDelta("text", delta))
            val cleaned = rawMessages.map { it.withGroupSender(member, "fallback").stripSelfSpeakerPrefix("Alice") }
            assertEquals(deltas.take(index + 1).joinToString(""), rawMessages.last().toText())
            assertEquals(expected[index], cleaned.last().toText())
            assertEquals(rawMessages.first(), cleaned.first())
            assertEquals(stub.id, cleaned.last().id)
        }
    }

    @Test fun selfSpeakerPrefixCleansNonStreamingResultWithoutMergingEarlierSpeaker() {
        val earlier = UIMessage.assistant("[Bob] earlier speaker")
        val member = GroupMember(name = "Alice")
        val stub = UIMessage.assistant("").copy(parts = emptyList()).withGroupSender(member, "fallback")
        val result = me.rerere.ai.provider.TextGenerationResult("result", "model", UIMessage.assistant("[Alice] answer"))
        val messages = listOf(earlier, stub).handleTextGenerationResult(result)
        val cleaned = messages.last().withGroupSender(member, "fallback").stripSelfSpeakerPrefix("Alice")
        assertEquals(earlier, messages.first())
        assertEquals(stub.id, cleaned.id)
        assertEquals("answer", cleaned.toText())
        assertEquals(stub.annotations, cleaned.annotations)
        assertEquals("[Alice] answer", messages.last().toText())
    }

    @Test fun contextLimitOneRetainsQuestionInsteadOfEmptyResponseSlot() {
        val member = GroupMember(name = "A")
        val system = UIMessage.system("frame")
        val question = UIMessage.user("current question")
        val stub = UIMessage.assistant("").copy(parts = emptyList()).withGroupSender(member, "fallback")
        assertEquals(listOf(system, question), listOf(system, UIMessage.user("old"), question, stub)
            .withGroupSpeakerPrefixes(contextMessageLimit = 1))
    }

    @Test fun groupSenderIsReattachedWithoutLosingProviderCitationsDuringStreaming() {
        val member = GroupMember(name = "A")
        val stub = UIMessage.assistant("").copy(parts = emptyList()).withGroupSender(member, "fallback")
        val handler = me.rerere.ai.ui.StreamChunkHandler()
        var messages = listOf(UIMessage.user("question"), stub)
        messages = handler.handle(messages, me.rerere.ai.ui.StreamChunk.TextDelta("text", "answer"))
        val citation = UIMessageAnnotation.UrlCitation("source", "https://example.com")
        messages = handler.handle(messages, me.rerere.ai.ui.StreamChunk.Annotations(listOf(citation)))
            .map { it.withGroupSender(member, "fallback") }
        assertEquals(stub.id, messages.last().id)
        assertEquals("answer", messages.last().toText())
        assertEquals(listOf(citation), messages.last().annotations.filterIsInstance<UIMessageAnnotation.UrlCitation>())
        assertEquals(1, messages.last().annotations.filterIsInstance<UIMessageAnnotation.GroupSender>().size)
    }

    @Test fun nonStreamingReplyUsesItsOwnResponseNode() {
        val member = GroupMember(name = "B")
        val earlier = UIMessage.assistant("earlier speaker")
        val stub = UIMessage.assistant("").copy(parts = emptyList()).withGroupSender(member, "fallback")
        val result = me.rerere.ai.provider.TextGenerationResult("result", "model", UIMessage.assistant("answer"))
        val messages = listOf(earlier, stub).handleTextGenerationResult(result)
        assertEquals(earlier, messages.first())
        assertEquals(stub.id, messages.last().id)
        assertEquals("answer", messages.last().toText())
        assertEquals(stub.annotations, messages.last().annotations)
    }

    private fun templateEngine(templates: Map<String, String>) = PebbleEngine.Builder()
        .loader(object : StringLoader() {
            override fun getReader(cacheKey: String?): Reader? = templates[cacheKey]?.let(::StringReader)
            override fun resourceExists(templateName: String?): Boolean = templates.containsKey(templateName)
        })
        .autoEscaping(false)
        .build()

    @Test fun missingGroupTemplateReturnsOriginalMessagesInsteadOfReaderCloseCrash() {
        val member = GroupMember(name = "A")
        val engine = templateEngine(emptyMap())
        val originalFailure = runCatching { engine.getTemplate(member.id.toString()) }.exceptionOrNull()
        assertTrue(originalFailure is NullPointerException)
        val messages = listOf(UIMessage.user("question"),
            UIMessage.assistant("answer").withGroupSender(member, "fallback"))
        assertSame(messages, applyMessageTemplate(engine, member.id, messages))
    }

    @Test fun singleChatCustomTemplateStillRendersRolesAndPreservesMetadata() {
        val assistant = Assistant(messageTemplate = "{{ role }}: {{ message }}")
        val engine = templateEngine(mapOf(assistant.id.toString() to assistant.messageTemplate))
        val messages = listOf(UIMessage.user("question"), UIMessage.assistant("answer"))
        val rendered = applyMessageTemplate(engine, assistant.id, messages)
        assertEquals(listOf("user: question", "assistant: answer"), rendered.map { it.toText() })
        messages.zip(rendered).forEach { (before, after) ->
            assertEquals(before.copy(parts = after.parts), after)
        }
        assertEquals("question", messages.first().toText())
    }

    @Test fun messageTemplateLeavesSyntheticMessagesAndToolArgumentsUntouched() {
        val assistant = Assistant(messageTemplate = "prefix {{ message }}")
        val engine = templateEngine(mapOf(assistant.id.toString() to assistant.messageTemplate))
        val synthetic = UIMessage.user("reminder").copy(isSynthetic = true)
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "get_time_info", input = "{}")
        val reply = UIMessage.assistant("answer").copy(parts = listOf(UIMessagePart.Text("answer"), tool))
        val rendered = applyMessageTemplate(engine, assistant.id, listOf(synthetic, reply))
        assertSame(synthetic, rendered.first())
        assertEquals("prefix answer", rendered.last().parts.filterIsInstance<UIMessagePart.Text>().single().text)
        assertEquals(listOf(tool), rendered.last().getTools())
    }

    @Test fun defaultMessageTemplateKeepsSingleChatContentUnchanged() {
        val assistant = Assistant()
        val engine = templateEngine(mapOf(assistant.id.toString() to assistant.messageTemplate))
        val messages = listOf(UIMessage.user("<html> & {{ literal }}"), UIMessage.assistant("answer"))
        assertEquals(messages, applyMessageTemplate(engine, assistant.id, messages))
    }
}
