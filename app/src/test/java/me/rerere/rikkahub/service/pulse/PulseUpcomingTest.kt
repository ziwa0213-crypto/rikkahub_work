package me.rerere.rikkahub.service.pulse

import me.rerere.rikkahub.data.datastore.PulseTask
import me.rerere.rikkahub.data.datastore.PulseTaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseUpcomingTest {
    @Test
    fun `only enabled tasks appear without modifying the input`() {
        val tasks = listOf(
            PulseTask(title = "enabled", dueAt = 20),
            PulseTask(title = "disabled", dueAt = 10, enabled = false),
        )
        assertEquals(listOf(PulseUpcoming("enabled", 20)), pulseUpcomingTasks(tasks, 0))
        assertEquals(listOf("enabled", "disabled"), tasks.map { it.title })
        assertTrue(pulseUpcomingTasks(tasks.map { it.copy(enabled = false) }, 0).isEmpty())
    }

    @Test
    fun `once and cron use their own stored times in ascending order`() {
        val tasks = listOf(
            PulseTask(title = "later", kind = PulseTaskKind.ONCE, dueAt = 300, nextAt = 1),
            PulseTask(title = "cron", kind = PulseTaskKind.CRON, nextAt = 100, dueAt = 999,
                cron = "not a cron expression"),
            PulseTask(title = "earlier", kind = PulseTaskKind.ONCE, dueAt = 200),
        )
        assertEquals(listOf("cron", "earlier", "later"), pulseUpcomingTasks(tasks, 0).map { it.title })
        assertEquals(listOf(100L, 200L, 300L), pulseUpcomingTasks(tasks, 0).map { it.at })
    }

    @Test
    fun `heartbeat has no concrete time and follows timed tasks`() {
        val tasks = listOf(
            PulseTask(title = "heartbeat", kind = PulseTaskKind.ON_HEARTBEAT, dueAt = 1, nextAt = 1),
            PulseTask(title = "timed", dueAt = 100),
        )
        assertEquals(listOf(PulseUpcoming("timed", 100), PulseUpcoming("heartbeat", null, true)),
            pulseUpcomingTasks(tasks, 0))
    }

    @Test
    fun `blank titles fall back to at most twenty prompt characters`() {
        val tasks = listOf(
            PulseTask(title = "  ", prompt = "这是一个超过二十个字符的任务提示词用于验证空标题回退", dueAt = 10),
            PulseTask(title = "", prompt = "short", dueAt = 20),
            PulseTask(title = "explicit", prompt = "unused", dueAt = 30),
        )
        assertEquals(listOf(tasks[0].prompt.take(20), "short", "explicit"),
            pulseUpcomingTasks(tasks, 0).map { it.title })
    }

    @Test
    fun `overdue tasks remain visible and missing times are unknown instead of epoch dates`() {
        val tasks = listOf(
            PulseTask(title = "heartbeat", kind = PulseTaskKind.ON_HEARTBEAT),
            PulseTask(title = "unknown once", dueAt = 0),
            PulseTask(title = "unknown cron", kind = PulseTaskKind.CRON, nextAt = -1),
            PulseTask(title = "overdue", dueAt = 10),
        )
        val result = pulseUpcomingTasks(tasks, now = 100)
        assertEquals(listOf(PulseUpcoming("overdue", 10), PulseUpcoming("unknown once", null),
            PulseUpcoming("unknown cron", null), PulseUpcoming("heartbeat", null, true)), result)
        assertFalse(result.dropLast(1).any { it.onHeartbeat })
    }
}
