package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class PulseTaskKind {
    @SerialName("once") ONCE,
    @SerialName("cron") CRON,
    @SerialName("heartbeat") ON_HEARTBEAT,
}

@Serializable
data class PulseTask(
    val id: String = Uuid.random().toString(),
    val title: String = "",
    val prompt: String = "",
    val kind: PulseTaskKind = PulseTaskKind.ONCE,
    val dueAt: Long = 0,
    val cron: String = "",
    val enabled: Boolean = true,
    val lastRunAt: Long = 0,
    val lastResult: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val nextAt: Long = 0,
    val lastSlot: Long = 0,
    val timeZoneId: String = "",
)
