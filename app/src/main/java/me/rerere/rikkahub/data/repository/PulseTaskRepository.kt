package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.datastore.PulseTask
import me.rerere.rikkahub.data.datastore.PulseTaskKind
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.pulse.PulseCron
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant

class PulseTaskRepository(private val settingsStore: SettingsStore) {
    suspend fun list(): List<PulseTask> = settingsStore.awaitLoaded().pulse.tasks

    private fun taskKind(value: String): PulseTaskKind = when (value) {
        "once" -> PulseTaskKind.ONCE
        "cron" -> PulseTaskKind.CRON
        "heartbeat" -> PulseTaskKind.ON_HEARTBEAT
        else -> error("when must be once, cron or heartbeat")
    }

    private fun resolveSchedule(taskKind: PulseTaskKind, executeAt: String?, cron: String?, now: Long, zone: ZoneId): Pair<Long, Long> {
        val due = if (taskKind == PulseTaskKind.ONCE) {
            val text = requireNotNull(executeAt) { "execute_at is required" }
            val time = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
                .getOrElse { LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }
            require(time > now + 60_000 && time <= Instant.ofEpochMilli(now).atZone(zone).plusYears(1).toInstant().toEpochMilli()) {
                "execute_at must be over one minute and at most one year in the future. Current local time: "+Instant.ofEpochMilli(now).atZone(zone)+" ("+zone+")"
            }
            time
        } else 0L
        val next = if (taskKind == PulseTaskKind.CRON) requireNotNull(PulseCron(cron.orEmpty()).nextAfter(now, zone)) { "No cron occurrence in the next year" } else due
        return due to next
    }

    suspend fun update(id: String, title: String? = null, prompt: String? = null, kind: String? = null,
        executeAt: String? = null, cron: String? = null, enabled: Boolean? = null): PulseTask {
        require(title == null || title.isNotBlank()) { "title must not be blank" }
        require(prompt == null || prompt.isNotBlank()) { "prompt must not be blank" }
        var saved: PulseTask? = null
        settingsStore.update { settings ->
            val previous = settings.pulse.tasks.find { it.id == id }
                ?: error("No task with id $id. Call action=list to see the current tasks.")
            val nextKind = kind?.let(::taskKind) ?: previous.kind
            val timingChanged = kind != null || executeAt != null || cron != null
            val zone = if (nextKind != previous.kind) ZoneId.systemDefault()
                else runCatching { ZoneId.of(previous.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
            val schedule = if (timingChanged) resolveSchedule(nextKind,
                executeAt ?: previous.dueAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() },
                cron ?: previous.cron, System.currentTimeMillis(), zone) else previous.dueAt to previous.nextAt
            val updated = previous.copy(title = title?.trim() ?: previous.title, prompt = prompt ?: previous.prompt,
                kind = nextKind, dueAt = schedule.first, nextAt = schedule.second, cron = cron ?: previous.cron,
                enabled = enabled ?: previous.enabled, lastSlot = if (timingChanged) 0 else previous.lastSlot,
                timeZoneId = if (nextKind != previous.kind) zone.id else previous.timeZoneId)
            saved = updated
            settings.copy(pulse = settings.pulse.copy(tasks = settings.pulse.tasks.map { if (it.id == id) updated else it }))
        }
        return checkNotNull(saved)
    }

    suspend fun delete(id: String): Boolean {
        var deleted = false
        settingsStore.update { settings ->
            deleted = settings.pulse.tasks.any { it.id == id }
            settings.copy(pulse = settings.pulse.copy(tasks = settings.pulse.tasks.filterNot { it.id == id }))
        }
        return deleted
    }
    suspend fun create(title: String, prompt: String, kind: String, executeAt: String?, cron: String?): PulseTask {
        require(title.isNotBlank() && prompt.isNotBlank()) { "title and prompt are required" }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val taskKind = taskKind(kind)
        val (due, next) = resolveSchedule(taskKind, executeAt, cron, now, zone)
        val task = PulseTask(title = title.trim(), prompt = prompt, kind = taskKind, dueAt = due,
            cron = cron.orEmpty(), nextAt = next, timeZoneId = zone.id)
        settingsStore.update { settings ->
            require(settings.pulse.tasks.size < 50) { "At most 50 tasks; remove an old task first" }
            settings.copy(pulse = settings.pulse.copy(tasks = settings.pulse.tasks + task))
        }
        return task
    }
}
