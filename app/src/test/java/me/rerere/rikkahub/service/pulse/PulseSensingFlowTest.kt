package me.rerere.rikkahub.service.pulse

import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.rikkahub.data.datastore.PulseConfig
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class PulseSensingFlowTest {
    private val pending = UIMessage.assistant("").copy(parts = listOf(
        UIMessagePart.Tool(toolCallId = "sms", toolName = "send_sms", input = """{"to":"95588","text":"hello"}""",
            approvalState = ToolApprovalState.Pending),
    ))

    @Test fun pendingApprovalWithoutTextIsPersistableButIncompleteTurnsAreRejected() {
        val result = pulseTurnResult(Uuid.random(), 0, pending, AgentFinishReason.AwaitingApproval(setOf("sms")))
        assertTrue(result.awaitingApproval)
        assertEquals("", result.text)
        assertTrue(runCatching { pulseTurnResult(Uuid.random(), 0, UIMessage.assistant(""), AgentFinishReason.Completed) }.isFailure)
        assertTrue(runCatching { pulseTurnResult(Uuid.random(), 0, UIMessage.assistant("hello"), null) }.isFailure)
        assertTrue(runCatching { pulseTurnResult(Uuid.random(), 0, UIMessage.assistant("hello"),
            AgentFinishReason.AwaitingApproval(setOf("sms"))) }.isFailure)
    }
    @Test fun sensingHistoryRemainsPrivateAfterSwitchesAreDisabled() {
        assertTrue(pulseHasSensingData(listOf(pending)))
        assertTrue(pulseHasSensingData(emptyList(), "sms"))
        assertFalse(pulseHasSensingData(emptyList()))
        assertFalse(pulseHasSensingData(listOf(UIMessage.assistant("hello"))))
        listOf("read_notifications", "read_sms", "send_sms").forEach { name ->
            val tool = pending.getTools().single().copy(toolName = name)
            assertTrue(pulseHasSensingData(listOf(pending.copy(parts = listOf(tool)), UIMessage.assistant("later turn"))))
        }
        assertFalse(pulseHasSensingData(listOf(pending.copy(parts = listOf(pending.getTools().single().copy(toolName = "get_time_info"))))))
    }
    @Test fun pollUsesStableAnchorAndIndependentSwitches() {
        val enabled = PulseConfig(enableSmsReading = true, smsPollEnabled = true)
        assertEquals(1000L, smsNextPollAt(enabled, 0, 1000))
        assertEquals(901000L, smsNextPollAt(enabled, 1000, 2000))
        assertEquals(901000L, smsNextPollAt(enabled, 1000, 8000))
        assertEquals(500L, smsNextPollAt(enabled, 1000, 500))
        assertEquals(Long.MAX_VALUE, smsNextPollAt(enabled.copy(enableSmsReading = false), 1000, 2000))
        assertEquals(Long.MAX_VALUE, smsNextPollAt(enabled.copy(smsPollEnabled = false), 1000, 2000))
        assertEquals(301000L, smsNextPollAt(enabled.copy(smsPollMinutes = 1), 1000, 2000))
        assertEquals(14401000L, smsNextPollAt(enabled.copy(smsPollMinutes = 999), 1000, 2000))
    }
    @Test fun smsClaimWithoutReplayStillSchedulesWakeAtLeaseExpiryAfterRestart() {
        val pending = PulseClaim(key = "sms:42:1000", owner = "previous-process", until = 901000L)
        assertFalse(pending.isPendingFor(PulseConfig(enableSmsReading = true, smsPollEnabled = true)))
        assertEquals(901000L, pulseCandidateAfterLease(pending, 2000L, 0L))
        assertEquals(0L, pulseCandidateAfterLease(pending, 901000L, 0L))
        assertEquals(5000L, pulseCandidateAfterLease(pending.copy(done = true), 2000L, 5000L))
        assertEquals(5000L, pulseCandidateAfterLease(null, 2000L, 5000L))
    }
}
