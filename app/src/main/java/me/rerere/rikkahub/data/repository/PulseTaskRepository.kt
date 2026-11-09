package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.datastore.PulseTask
import me.rerere.rikkahub.data.datastore.PulseTaskKind
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.pulse.PulseCron
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PulseTaskRepository(private val settingsStore: SettingsStore) {
    suspend fun create(title: String, prompt: String, kind: String, executeAt: String?, cron: String?): PulseTask {
        require(title.isNotBlank() && prompt.isNotBlank()) { "title and prompt are required" }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val taskKind = when (kind) {
            "once" -> PulseTaskKind.ONCE
            "cron" -> PulseTaskKind.CRON
            "heartbeat" -> PulseTaskKind.ON_HEARTBEAT
            else -> error("when must be once, cron or heartbeat")
        }
        val due = if (taskKind == PulseTaskKind.ONCE) {
            val text = requireNotNull(executeAt) { "execute_at is required" }
            val time = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
                .getOrElse { LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }
            require(time > now + 60_000 && time <= ZonedDateTime.now(zone).plusYears(1).toInstant().toEpochMilli()) {
                "execute_at must be over one minute and at most one year in the future. Current local time: ${ZonedDateTime.now(zone)} ($zone)"
            }
            time
        } else 0L
        val next = if (taskKind == PulseTaskKind.CRON) requireNotNull(PulseCron(cron.orEmpty()).nextAfter(now, zone)) { "No cron occurrence in the next year" } else due
        val task = PulseTask(title = title.trim(), prompt = prompt, kind = taskKind, dueAt = due,
            cron = cron.orEmpty(), nextAt = next, timeZoneId = zone.id)
        settingsStore.update { settings ->
            require(settings.pulse.tasks.size < 50) { "At most 50 tasks; remove an old task first" }
            settings.copy(pulse = settings.pulse.copy(tasks = settings.pulse.tasks + task))
        }
        return task
    }
}
