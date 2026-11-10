package me.rerere.rikkahub.service.pulse

import kotlinx.serialization.json.*
import me.rerere.rikkahub.data.ai.tools.pulse.notificationResult
import me.rerere.rikkahub.data.datastore.PulseConfig
import org.junit.Assert.*
import org.junit.Test

class PulseNotificationBufferTest {
    private val config = PulseConfig(enableNotificationReading = true, notificationWhitelist = listOf("chat"))
    private var time = 100_000_000L
    private val buffer = PulseNotificationBuffer { time }
    private fun add(pkg: String = "chat", at: Long = time, ongoing: Boolean = false, summary: Boolean = false) =
        buffer.offer(config, "self", pkg, pkg, at, "t".repeat(100), "body".repeat(100), ongoing, summary)

    @Test fun filtersBeforeBufferingAndRetainsOnlyCountsOutsideWhitelist() {
        listOf("self", "android", "com.android.systemui").forEach { add(it) }
        add(ongoing = true)
        add(summary = true)
        add("chat")
        add("other")
        val items = buffer.snapshot(config)
        assertEquals(2, items.size)
        assertEquals(80, items.first().title!!.length)
        assertEquals(200, items.first().text!!.length)
        assertTrue(items.last().redacted)
        assertNull(items.last().title)
        assertNull(items.last().text)
    }
    @Test fun expiresByWallClockAndCapsFifoAndClearsOnDisable() {
        repeat(120) { add(at = time + it) }
        assertEquals(100, buffer.snapshot(config).size)
        assertEquals(time + 20, buffer.snapshot(config).first().postedAt)
        time += 7 * 3_600_000L
        assertTrue(buffer.snapshot(config).isEmpty())
        add()
        assertTrue(buffer.snapshot(config.copy(enableNotificationReading = false)).isEmpty())
        assertTrue(buffer.snapshot(config).isEmpty())
    }
    @Test fun removingAndReaddingWhitelistNeverRestoresOldContent() {
        add()
        buffer.synchronize(config.copy(notificationWhitelist = emptyList()))
        val item = buffer.snapshot(config).single()
        assertTrue(item.redacted)
        assertNull(item.text)
    }
    @Test fun limitsActualEscapedJsonAndNeverLeaksCountOnlyContent() {
        val entries = (0..30).map {
            PulseNotification("chat", "\"".repeat(80), time + it, "\"".repeat(80), "\n".repeat(200), false)
        } + PulseNotification("other", "Other", time, "secret", "private", false)
        val result = notificationResult(config, entries, 100, null)
        assertTrue(result.toString().length <= 4000)
        assertFalse(result.toString().contains("private"))
        assertEquals(32, result["total"]!!.jsonPrimitive.int)
        assertTrue(result["items"]!!.jsonArray.size <= 20)
        assertTrue(result.containsKey("redacted_note"))
        assertEquals(31, notificationResult(config, entries, 1, "chat")["total"]!!.jsonPrimitive.int)
        assertEquals(1, notificationResult(config, entries, 1, "chat")["items"]!!.jsonArray.size)
    }
}
