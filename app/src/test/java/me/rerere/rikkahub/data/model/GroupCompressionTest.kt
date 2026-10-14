package me.rerere.rikkahub.data.model

import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class GroupCompressionTest {
    @Test fun realUsageIncludesCacheAndFallsBackAfterCompression() {
        val user = UIMessage.user("你好 hello")
        val reply = UIMessage.assistant("reply").copy(usage = TokenUsage(promptTokens = 100, cachedTokens = 20))
        assertEquals(120, listOf(user, reply).estimateContextTokens())
        assertEquals(20, listOf(user, reply.copy(usage = TokenUsage(cachedTokens = 20))).estimateContextTokens())
        assertEquals(Int.MAX_VALUE, listOf(user, reply.copy(usage = TokenUsage(promptTokens = Int.MAX_VALUE, cachedTokens = 20))).estimateContextTokens())
        val summary = UIMessage.user("摘要").copy(createdAt = reply.createdAt.let { time ->
            kotlinx.datetime.LocalDateTime(time.year + 1, time.month, time.day, time.hour, time.minute) })
        assertEquals(3 + heuristicTokens("reply"), listOf(summary, reply).estimateContextTokens())
        assertEquals(0, emptyList<UIMessage>().estimateContextTokens())
        assertEquals(3, heuristicTokens("你好"))
        assertEquals(1, heuristicTokens("hi"))
    }

    @Test fun thresholdAndTwentyPercentHysteresisArePerConversation() {
        val group = Group(compressKeepRecent = 0, compressByMessageCount = false)
        val gate = GroupCompressionGate()
        val id = Uuid.random()
        assertFalse(gate.shouldPrompt(id, ContextUsage(69, 100), group, 10))
        assertTrue(gate.shouldPrompt(id, ContextUsage(70, 100), group, 10))
        gate.markPrompted(id, ContextUsage(70, 100), 10)
        assertFalse(gate.shouldPrompt(id, ContextUsage(83, 100), group, 11))
        assertTrue(gate.shouldPrompt(id, ContextUsage(84, 100), group, 12))
        assertTrue(gate.shouldPrompt(Uuid.random(), ContextUsage(70, 100), group, 10))
        assertFalse(gate.shouldPrompt(id, ContextUsage(1, 100), group, 10))
        assertTrue(gate.shouldPrompt(id, ContextUsage(70, 100), group, 10))
        assertFalse(gate.shouldPrompt(id, ContextUsage(90, 100), group.copy(enableAutoCompress = false), 10))
    }

    @Test fun countTriggerDoesNotRequireHighTokenRatioAndCannotCompressOnlyRecentHistory() {
        val group = Group()
        val gate = GroupCompressionGate()
        val id = Uuid.random()
        assertTrue(gate.shouldPrompt(id, ContextUsage(100, 128000), group, 40))
        gate.markPrompted(id, ContextUsage(100, 128000), 40)
        assertFalse(gate.shouldPrompt(id, ContextUsage(119, 128000), group, 41))
        assertTrue(gate.shouldPrompt(id, ContextUsage(120, 128000), group, 42))
        assertFalse(gate.shouldPrompt(id, ContextUsage(120000, 128000), group, 32))
        assertEquals(0.7f, group.copy(compressThresholdRatio = Float.NaN).compressionThreshold())
        assertEquals(0f, ContextUsage(1, 0).ratio)
        assertEquals(1f, ContextUsage(200, 100).ratio)
    }

    @Test fun tieredSummaryKeepsOwnerDeputyAndHeadTailOfOtherMembers() {
        val deputy = GroupMember(name = "Deputy")
        val other = GroupMember(name = "Coder")
        val messages = listOf(UIMessage.user("Owner decision"), UIMessage.assistant("Deputy conclusion").withGroupSender(deputy, "Deputy"),
            UIMessage.assistant("HEAD" + "x".repeat(1000) + "TAIL").withGroupSender(other, "Coder"))
        val content = buildTieredCompressContent(messages, deputy.id, "Owner", otherPerMemberMaxLength = 30)
        assertTrue(content.primary.contains("[Owner]: Owner decision"))
        assertTrue(content.primary.contains("[Deputy]: Deputy conclusion"))
        assertTrue(content.others.contains("HEAD"))
        assertTrue(content.others.endsWith("TAIL"))
        assertFalse(content.primary.contains("Coder"))
        val withoutDeputy = buildTieredCompressContent(messages, null, "Owner")
        assertFalse(withoutDeputy.primary.contains("Deputy"))
        assertTrue(withoutDeputy.others.contains("Deputy"))
        assertEquals("[Deputy]: Deputy conclusion", messages[1].summaryAsTextWithSender("Owner"))
        assertEquals("New Chat 3", defaultConversationTitle("New Chat", listOf("New Chat", "New Chat 2")))
    }
}
