package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.toAssistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test

class AlarmRegistrationTest {
    @Test fun `old local tool serial names and assistant defaults survive`() {
        val serializer = ListSerializer(LocalToolOption.serializer())
        val json = """[{"type":"javascript_engine"},{"type":"time_info"},{"type":"clipboard"},{"type":"tts"},{"type":"ask_user"},{"type":"screen_time"},{"type":"calendar"},{"type":"chart_display"},{"type":"browser"}]"""
        val old = JsonInstant.decodeFromString(serializer, json)
        assertEquals(9, old.size)
        assertEquals(LocalToolOption.Browser, old.last())
        assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(JsonInstant.encodeToString(serializer, old)))
        assertEquals(listOf(LocalToolOption.TimeInfo), Assistant().localTools)
        assertEquals(LocalToolOption.Alarm, JsonInstant.decodeFromString(LocalToolOption.serializer(),
            JsonInstant.encodeToString(LocalToolOption.serializer(), LocalToolOption.Alarm)))
    }

    @Test fun `pulse alarm uses its own switch without changing persisted local tool options`() {
        for (information in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                val config = PulseConfig(
                    enableInformation = information,
                    enableCalendarCreate = false,
                    enableAlarm = enabled,
                )
                assertEquals(enabled, LocalToolOption.Alarm in config.toAssistant(Settings()).localTools)
                assertTrue(config.localTools.isEmpty())
            }
        }
    }
}
