package me.rerere.rikkahub.data.ai.tools.local

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class AlarmToolTest {
    private fun parse(text: String) = parseAlarmRequest(Json.parseToJsonElement(text))

    @Test fun `alarm requires both fields and accepts day boundaries`() {
        assertEquals(AlarmRequest(0, 0), parse("""{"hour":0,"minute":0}"""))
        assertEquals(AlarmRequest(23, 59, label = "Wake up"), parse("""{"hour":23,"minute":59,"label":"Wake up"}"""))
        listOf("{}", """{"hour":7}""", """{"minute":30}""", "[]", "null").forEach {
            assertTrue(it, runCatching { parse(it) }.isFailure)
        }
    }

    @Test fun `invalid numeric types and ranges never guess or truncate`() {
        listOf(
            """{"hour":-1,"minute":30}""", """{"hour":24,"minute":30}""",
            """{"hour":7,"minute":-1}""", """{"hour":7,"minute":60}""",
            """{"hour":7.5,"minute":30}""", """{"hour":"7","minute":30}""",
            """{"duration_seconds":0}""", """{"duration_seconds":-1}""",
            """{"duration_seconds":86401}""", """{"duration_seconds":2147483648}""",
            """{"duration_seconds":{}}""", """{"duration_seconds":[]}""",
            """{"duration_seconds":true}""", """{"duration_seconds":"600"}""",
            """{"duration_seconds":600,"label":123}""",
        ).forEach { assertTrue(it, runCatching { parse(it) }.isFailure) }
    }

    @Test fun `timer wins over clock fields including malformed unused fields`() {
        assertEquals(AlarmRequest(durationSeconds = 600, label = "Tea"),
            parse("""{"duration_seconds":600,"hour":{},"minute":90,"label":"Tea"}"""))
        assertEquals(1, parse("""{"duration_seconds":1}""").durationSeconds)
        assertEquals(86400, parse("""{"duration_seconds":86400}""").durationSeconds)
        assertEquals(AlarmRequest(7, 30), parse("""{"hour":7,"minute":30,"duration_seconds":null,"label":null}"""))
    }

    private fun triggerAt(hour: Int, minute: Int): Long = LocalDate.of(2030, 1, 1)
        .atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun `restricted background attempts launch but never claims unconfirmed success`() = runTest {
        var launches = 0
        val result = dispatchAlarmRequest(AlarmRequest(7, 30), backgroundRestricted = true) { launches++ }
        assertFalse(result.getValue("success").jsonPrimitive.boolean)
        assertFalse(result.getValue("verified").jsonPrimitive.boolean)
        assertEquals("background_blocked", result.getValue("error_code").jsonPrimitive.content)
        assertTrue(result.getValue("error").jsonPrimitive.content.contains("后台弹出界面"))
        assertFalse(result.containsKey("status"))
        assertEquals(1, launches)
    }

    @Test fun `permission and missing activity failures are not successful`() = runTest {
        listOf(SecurityException("private details"), IllegalStateException("unavailable")).forEach { failure ->
            val result = dispatchAlarmRequest(AlarmRequest(7, 30)) { throw failure }
            assertFalse(result.getValue("success").jsonPrimitive.boolean)
            assertTrue(result.getValue("error").jsonPrimitive.content.contains(failure.javaClass.simpleName))
            assertFalse(result.toString().contains("private details"))
        }
    }

    @Test fun `successful dispatch reports request sent without claiming clock persistence`() = runTest {
        val calls = mutableListOf<AlarmRequest>()
        val alarm = dispatchAlarmRequest(AlarmRequest(7, 30, label = "Morning"), launch = calls::add)
        val timer = dispatchAlarmRequest(AlarmRequest(durationSeconds = 600), launch = calls::add)
        assertEquals(2, calls.size)
        assertTrue(alarm.getValue("success").jsonPrimitive.boolean)
        assertEquals("request_sent", alarm.getValue("status").jsonPrimitive.content)
        assertEquals("alarm", alarm.getValue("type").jsonPrimitive.content)
        assertEquals("timer", timer.getValue("type").jsonPrimitive.content)
        assertEquals(setOf("success", "status", "type", "hour", "minute", "message", "verified"), alarm.keys)
        assertEquals(setOf("success", "status", "type", "duration_seconds", "message", "verified"), timer.keys)
        assertTrue(alarm.getValue("message").jsonPrimitive.content.contains("Check the system Clock"))
        println("ALARM_OFFLINE_FIXTURE $alarm")
        println("TIMER_OFFLINE_FIXTURE $timer")
    }

    @Test fun `changed matching next alarm provides verification even without known exemption`() = runTest {
        var reads = 0
        val result = dispatchAlarmRequest(
            AlarmRequest(7, 30),
            backgroundRestricted = true,
            readNextAlarm = { if (reads++ == 0) null else triggerAt(7, 30) },
        ) {}
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        assertTrue(result.getValue("verified").jsonPrimitive.boolean)
        assertEquals(2, reads)
        assertEquals("request_sent", result.getValue("status").jsonPrimitive.content)
    }

    @Test fun `unchanged earlier or identical alarms are inconclusive rather than background failures`() = runTest {
        for (existing in listOf(null, triggerAt(6, 0), triggerAt(7, 30))) {
            val result = dispatchAlarmRequest(AlarmRequest(7, 30), readNextAlarm = { existing }) {}
            assertTrue(result.getValue("success").jsonPrimitive.boolean)
            assertFalse(result.getValue("verified").jsonPrimitive.boolean)
            assertFalse(result.containsKey("error_code"))
            assertTrue(result.getValue("message").jsonPrimitive.content.contains("无法确认"))
        }
    }

    @Test fun `unrelated next alarm change does not verify requested time`() = runTest {
        var launched = false
        val result = dispatchAlarmRequest(
            AlarmRequest(7, 30), readNextAlarm = { if (launched) triggerAt(8, 0) else null },
        ) { launched = true }
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        assertFalse(result.getValue("verified").jsonPrimitive.boolean)
    }

    @Test fun `timers do not use another alarm as evidence`() = runTest {
        var launched = false
        var reads = 0
        val result = dispatchAlarmRequest(
            AlarmRequest(durationSeconds = 600),
            readNextAlarm = { reads++; triggerAt(7, 30) },
        ) { launched = true }
        assertTrue(launched)
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        assertFalse(result.getValue("verified").jsonPrimitive.boolean)
        assertEquals(0, reads)
    }

    @Test fun `restricted timer and denied background start return actionable failure`() = runTest {
        val timer = dispatchAlarmRequest(AlarmRequest(durationSeconds = 60), backgroundRestricted = true) {}
        val denied = dispatchAlarmRequest(AlarmRequest(7, 30), backgroundRestricted = true) {
            throw SecurityException("private details")
        }
        for (result in listOf(timer, denied)) {
            assertFalse(result.getValue("success").jsonPrimitive.boolean)
            assertEquals("background_blocked", result.getValue("error_code").jsonPrimitive.content)
            assertFalse(result.toString().contains("private details"))
        }
    }

    @Test fun `explicit system denial stays blocked even with overlay permission`() = runTest {
        val result = dispatchAlarmRequest(AlarmRequest(7, 30), backgroundLaunch = true) {
            throw SecurityException("vendor denied with private details")
        }
        assertFalse(result.getValue("success").jsonPrimitive.boolean)
        assertEquals("background_blocked", result.getValue("error_code").jsonPrimitive.content)
        assertFalse(result.toString().contains("private details"))
    }

    @Test fun `verification failure cannot erase a dispatched request`() = runTest {
        var launched = false
        val result = dispatchAlarmRequest(AlarmRequest(7, 30), readNextAlarm = { error("unavailable") }) {
            launched = true
        }
        assertTrue(launched)
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        assertFalse(result.getValue("verified").jsonPrimitive.boolean)
    }

    @Test fun `unreadable before snapshot cannot verify an existing alarm`() = runTest {
        var reads = 0
        val result = dispatchAlarmRequest(AlarmRequest(7, 30), readNextAlarm = {
            if (reads++ == 0) error("unavailable") else triggerAt(7, 30)
        }) {}
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        assertFalse(result.getValue("verified").jsonPrimitive.boolean)
    }

    @Test fun `cancellation is propagated instead of being reported as an alarm error`() = runTest {
        val cancellation = CancellationException("stopped")
        try {
            dispatchAlarmRequest(AlarmRequest(7, 30)) { throw cancellation }
            fail("Cancellation should propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
