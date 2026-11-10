package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GROUP_CHAT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID
import me.rerere.rikkahub.data.model.isGroupChat
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.service.pulse.PULSE_TURN_CONSTRAINT
import me.rerere.rikkahub.service.pulse.pulseTurnInput
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestFactoryPulseInstructionTest {
    @Test
    fun nullPulseInstructionAddsNothing() {
        val target = StringBuilder("persona")
        appendPulseInstruction(target, null)
        assertEquals("persona", target.toString())
        assertFalse(target.contains("HEARTBEAT_OK"))
    }

    @Test
    fun pulseInstructionIsSeparatedAfterPersona() {
        val target = StringBuilder("persona")
        appendPulseInstruction(target, "HEARTBEAT_OK")
        assertEquals("persona\n\nHEARTBEAT_OK", target.toString())
        assertTrue(target.indexOf("persona") < target.indexOf("HEARTBEAT_OK"))
    }

    @Test
    fun manualPulseMessageGetsFrameworkConstraintWithoutChangingHistory() {
        val conversation = Conversation(
            assistantId = PULSE_ASSISTANT_ID,
            messageNodes = listOf(UIMessage.user("17:00 写个文件").toMessageNode()),
        )
        val original = conversation.currentMessages
        val target = StringBuilder("Custom persona\n<workspace>existing instructions</workspace>")
        appendPulseInstruction(target, resolvePulseInstruction(conversation, null))
        assertEquals("Custom persona\n<workspace>existing instructions</workspace>\n\n$PULSE_TURN_CONSTRAINT", target.toString())
        assertEquals(original, conversation.currentMessages)
        assertFalse(original.single().isSynthetic)
    }

    @Test
    fun automaticPulseMessageGetsExactlyOneConstraintAndKeepsItsUserTrigger() {
        val conversation = Conversation(assistantId = PULSE_ASSISTANT_ID, messageNodes = emptyList())
        val input = pulseTurnInput(conversation.currentMessages, "Custom heartbeat prompt")
        val target = StringBuilder("Custom persona")
        appendPulseInstruction(target, resolvePulseInstruction(conversation, input.systemInstruction))
        assertEquals("Custom persona\n\n$PULSE_TURN_CONSTRAINT", target.toString())
        assertEquals("Custom heartbeat prompt", input.messages.last().toText())
        assertTrue(input.messages.last().isSynthetic)
        assertFalse(target.contains("Custom heartbeat prompt"))
    }

    @Test
    fun ordinaryAndGroupRequestsKeepTheirSystemTextByteForByte() {
        val ordinary = Conversation.ofId(kotlin.uuid.Uuid.random())
        val group = Conversation.ofId(kotlin.uuid.Uuid.random(), GROUP_CHAT_ASSISTANT_ID)
        assertTrue(group.isGroupChat())
        val original = "Persona\n<memory>unchanged\u0000中文</memory>\n\n"
        for (conversation in listOf(ordinary, group)) {
            val resolved = resolvePulseInstruction(conversation, null)
            assertNull(resolved)
            val target = StringBuilder(original)
            appendPulseInstruction(target, resolved)
            assertTrue(original.toByteArray().contentEquals(target.toString().toByteArray()))
        }
    }

    @Test
    fun explicitInstructionRetainsTheExistingAppendContract() {
        val conversation = Conversation(assistantId = PULSE_ASSISTANT_ID, messageNodes = emptyList())
        assertEquals("Explicit instruction", resolvePulseInstruction(conversation, "Explicit instruction"))
    }
}
