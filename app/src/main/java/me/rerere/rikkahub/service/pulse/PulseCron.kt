package me.rerere.rikkahub.service.pulse

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A deliberately small five-field cron dialect, with standard day-of-month/day-of-week OR. */
class PulseCron(expression: String) {
    private val parts = expression.trim().split(Regex("\\s+"))
    private val limits = listOf(0..59, 0..23, 1..31, 1..12, 0..7)
    private val fields: List<Set<Int>>

    init {
        require(parts.size == 5) { "Use five cron fields: minute hour day month weekday" }
        fields = parts.mapIndexed { index, field ->
            field.split(',').flatMap { item ->
                val range = limits[index]
                when {
                    item == "*" -> range.toList()
                    item.startsWith("*/") -> {
                        val step = item.drop(2).toIntOrNull()
                        require(step != null && step in 1..range.last + 1) { "Invalid cron step" }
                        range.filter { (it - range.first) % step == 0 }
                    }
                    Regex("[0-9]+-[0-9]+").matches(item) -> {
                        val (first, last) = item.split('-').map(String::toInt)
                        require(first in range && last in range && first <= last) { "Invalid cron range" }
                        (first..last).toList()
                    }
                    else -> {
                        val value = item.toIntOrNull()
                        require(value != null && value in range) { "Invalid cron value" }
                        listOf(value)
                    }
                }
            }.map { if (index == 4 && it == 7) 0 else it }.toSet()
        }
    }

    fun nextAfter(from: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        var time = Instant.ofEpochMilli(from).atZone(zoneId).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        repeat(366 * 24 * 60) {
            val dayMatch = time.dayOfMonth in fields[2]
            val weekMatch = time.dayOfWeek.value % 7 in fields[4]
            val calendarMatch = when {
                parts[2] == "*" -> weekMatch
                parts[4] == "*" -> dayMatch
                else -> dayMatch || weekMatch
            }
            if (time.minute in fields[0] && time.hour in fields[1] && time.monthValue in fields[3] && calendarMatch) {
                return time.toInstant().toEpochMilli()
            }
            time = time.plusMinutes(1)
        }
        return null
    }
}
