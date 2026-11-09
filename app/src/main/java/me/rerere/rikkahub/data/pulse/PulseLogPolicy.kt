package me.rerere.rikkahub.data.pulse

import me.rerere.rikkahub.data.datastore.PulseActivityEntry
import me.rerere.rikkahub.data.datastore.PulseRunLog

internal const val MAX_PULSE_ACTIVITY_LOGS = 20
internal const val MAX_PULSE_RUN_LOGS = 200

internal fun appendPulseActivityLog(current: List<PulseActivityEntry>, entry: PulseActivityEntry): List<PulseActivityEntry> =
    (listOf(entry.copy(summary = entry.summary.take(200))) + current).take(MAX_PULSE_ACTIVITY_LOGS)

internal fun appendPulseRunLog(current: List<PulseRunLog>, entry: PulseRunLog): List<PulseRunLog> =
    (listOf(entry.copy(note = entry.note.take(200))) + current).take(MAX_PULSE_RUN_LOGS)
