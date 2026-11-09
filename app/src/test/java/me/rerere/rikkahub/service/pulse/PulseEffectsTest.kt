package me.rerere.rikkahub.service.pulse

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class PulseEffectsTest {
    private val saved = mutableMapOf<String, String>()
    private fun effects() = PulseEffects(saved::get) { key, value -> saved[key] = value }
    private val args = Json.parseToJsonElement("""{"a":1,"b":2}""")

    @Test fun `recreated runner reuses a confirmed write receipt`() = runBlocking {
        var writes = 0
        val result = listOf(UIMessagePart.Text("created"))
        effects().execute("task:1:100", "calendar_create", args) { writes++; result }
        assertEquals(result, effects().execute("task:1:100", "calendar_create", args) { writes++; result })
        assertEquals(1, writes)
    }
    @Test fun `argument key order does not bypass deduplication`() = runBlocking {
        var writes = 0
        effects().execute("task:1:100", "calendar_create", args) { writes++; emptyList() }
        effects().execute("task:1:100", "calendar_create", Json.parseToJsonElement("""{"b":2,"a":1}""")) { writes++; emptyList() }
        assertEquals(1, writes)
    }
    @Test fun `uncertain write is refused after process interruption`() = runBlocking {
        var writes = 0
        runCatching { effects().execute("heartbeat:100", "memory_tool", args) { writes++; error("connection lost") } }
        val retry = runCatching { effects().execute("heartbeat:100", "memory_tool", args) { writes++; emptyList() } }
        assertTrue(retry.exceptionOrNull()?.message.orEmpty().contains("unknown"))
        assertEquals(1, writes)
    }
    @Test fun `separate scheduled occurrences can perform their own write`() = runBlocking {
        var writes = 0
        effects().execute("task:1:100", "calendar_create", args) { writes++; emptyList() }
        effects().execute("task:1:200", "calendar_create", args) { writes++; emptyList() }
        assertEquals(2, writes)
    }
}
