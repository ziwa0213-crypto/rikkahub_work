package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
