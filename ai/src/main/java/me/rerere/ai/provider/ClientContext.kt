package me.rerere.ai.provider

import kotlinx.datetime.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Request-only metadata, never a message role or a model-callable tool. */
data class ClientContext(
    val date: String,
    val weekday: String,
    val location: String = "",
    val platform: String = "App",
    val language: String,
) {
    fun toLatestReminderContent(): String = listOf(date, weekday, location, platform, language).joinToString(",")

    fun toSystemLine(): String = "Current date: $date ($weekday). Platform: $platform. Language: $language."

    fun forMessage(createdAt: LocalDateTime): ClientContext = copy(
        date = createdAt.date.toString(),
        weekday = java.time.LocalDate.parse(createdAt.date.toString()).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
    )
}
