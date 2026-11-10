package me.rerere.rikkahub.service.pulse

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID
import me.rerere.rikkahub.service.ConversationSession
import me.rerere.rikkahub.service.ConversationStore
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.uuid.Uuid

class PulseMemoryCompletionTest {
    private class Store(var persisted: Conversation) : ConversationStore {
        var fail = false
        override suspend fun prepareWrite(): (Conversation) -> Conversation = { it }
        override suspend fun write(previous: Conversation?, current: Conversation) {
            if (fail) throw IOException("offline disk fixture")
            persisted = current
        }
    }

    @Test
    fun `only durable accepted replies notify and retry never duplicates concurrent user content`() = runBlocking {
        val initial = Conversation(assistantId = PULSE_ASSISTANT_ID, messageNodes = emptyList())
        val store = Store(initial)
        val session = ConversationSession(initial.id, initial, this, store, {})
        session.initialize(load = { initial })
        val user = UIMessage.user("arrived while the automatic turn was running")
        session.update { it.updateCurrentMessages(listOf(user)) }
        val response = UIMessage.assistant("report")
        val result = PulseTurnResult(initial.id, session.incarnation, response)
        val notified = mutableListOf<Conversation>()
        store.fail = true
        try {
            session.persistPulseResponse(result) { notified += it }
            fail("Failed persistence must throw")
        } catch (_: IOException) { }
        assertTrue(notified.isEmpty())
        assertTrue(store.persisted.messageNodes.isEmpty())
        store.fail = false
        session.persistPulseResponse(result) { saved ->
            assertSame(store.persisted, saved)
            notified += saved
        }
        session.persistPulseResponse(result) { notified += it }
        assertEquals(listOf(user.id, response.id), store.persisted.currentMessages.map { it.id })
        assertEquals(2, notified.size)
        assertSame(user, store.persisted.currentMessages.first())
        assertFalse(store.persisted.currentMessages.any { it.isSynthetic })
    }

    @Test
    fun `pending approval is durable but does not start dreaming`() = runBlocking {
        val initial = Conversation(assistantId = PULSE_ASSISTANT_ID, messageNodes = emptyList())
        val store = Store(initial)
        val session = ConversationSession(initial.id, initial, this, store, {})
        session.initialize(load = { initial })
        val pending = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
            UIMessagePart.Tool(toolCallId = "fixture", toolName = "send_sms", input = "{}"),
        ))
        session.persistPulseResponse(PulseTurnResult(initial.id, 0, pending)) {
            fail("Approval is not a completed turn")
        }
        assertEquals(pending, store.persisted.currentMessages.single())
    }

    @Test
    fun `replaced session and wrong role cannot append or notify`() = runBlocking {
        val initial = Conversation(assistantId = PULSE_ASSISTANT_ID, messageNodes = emptyList())
        val store = Store(initial)
        val session = ConversationSession(initial.id, initial, this, store, {})
        session.initialize(load = { initial })
        for (result in listOf(
            PulseTurnResult(initial.id, 1, UIMessage.assistant("stale")),
            PulseTurnResult(initial.id, 0, UIMessage.user("wrong role")),
        )) {
            try {
                session.persistPulseResponse(result) { fail("Rejected output must not notify") }
                fail("Invalid result must throw")
            } catch (_: IllegalStateException) { }
        }
        assertTrue(store.persisted.messageNodes.isEmpty())
        assertTrue(session.state.value.messageNodes.isEmpty())
    }
}
