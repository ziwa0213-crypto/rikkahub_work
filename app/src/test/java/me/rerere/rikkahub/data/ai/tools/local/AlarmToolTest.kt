package me.rerere.rikkahub.data.ai.tools.local

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

    @Test fun `background is rejected without launching anything`() {
        var launches = 0
        val result = dispatchAlarmRequest(AlarmRequest(7, 30), foreground = false) { launches++ }
        assertFalse(result.getValue("success").jsonPrimitive.boolean)
        assertTrue(result.getValue("error").jsonPrimitive.content.contains("前台"))
        assertEquals(0, launches)
    }

    @Test fun `permission and missing activity failures are not successful`() {
        listOf(SecurityException("private details"), IllegalStateException("unavailable")).forEach { failure ->
            val result = dispatchAlarmRequest(AlarmRequest(7, 30), true) { throw failure }
            assertFalse(result.getValue("success").jsonPrimitive.boolean)
            assertTrue(result.getValue("error").jsonPrimitive.content.contains(failure.javaClass.simpleName))
            assertFalse(result.toString().contains("private details"))
        }
    }

    @Test fun `successful dispatch reports request sent without claiming clock persistence`() {
        val calls = mutableListOf<AlarmRequest>()
        val alarm = dispatchAlarmRequest(AlarmRequest(7, 30, label = "Morning"), true, calls::add)
        val timer = dispatchAlarmRequest(AlarmRequest(durationSeconds = 600), true, calls::add)
        assertEquals(2, calls.size)
        assertTrue(alarm.getValue("success").jsonPrimitive.boolean)
        assertEquals("request_sent", alarm.getValue("status").jsonPrimitive.content)
        assertEquals("alarm", alarm.getValue("type").jsonPrimitive.content)
        assertEquals("timer", timer.getValue("type").jsonPrimitive.content)
        assertTrue(alarm.getValue("message").jsonPrimitive.content.contains("Check the system Clock"))
        println("ALARM_OFFLINE_FIXTURE $alarm")
        println("TIMER_OFFLINE_FIXTURE $timer")
    }
}
