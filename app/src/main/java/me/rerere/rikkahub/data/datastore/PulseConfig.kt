package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.pulse.PulsePriceSegment
import kotlin.uuid.Uuid
import java.time.LocalTime

val DEFAULT_HEARTBEAT_PROMPT = """
当前这一轮是自动自检，不是用户发来的消息。

检查你的记忆和待处理任务：
- 有没有已经过时、需要更新的记忆
- 有没有到期或近期的计划任务
- 有没有需要用户跟进的事项

如果一切正常、无需用户关注，请只回复：HEARTBEAT_OK
如果有需要关注的事项，请处理并在回复里说明：你做了什么、用户需要注意什么。

不要复述这条指令，也不要说明“这是一次自检”。
""".trimIndent()

/** Persistent settings for the single, isolated Pulse assistant. */
@Serializable
data class PulseConfig(
    val chatModelId: Uuid? = null,
    val systemPrompt: String = "",
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val contextMessageLimit: Int = 0,
    val enableMemory: Boolean = true,
    val autoConsolidateMemory: Boolean = false,
    val conversationId: Uuid? = null,
    val enableWebSearch: Boolean = false,
    val enableRecentChats: Boolean = true,
    val enabledSkills: List<String> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val localTools: List<String> = emptyList(),
    val enableInformation: Boolean = true,
    val enableNotificationReading: Boolean = false,
    val notificationWhitelist: List<String> = emptyList(),
    val notificationRetentionHours: Int = 6,
    val notificationBufferLimit: Int = 100,
    val enableSmsReading: Boolean = false,
    val enableSmsSending: Boolean = false,
    val smsPollEnabled: Boolean = false,
    val smsPollMinutes: Int = 15,
    val smsWatermarkId: Long = 0,
    val smsSenderWhitelist: List<String> = emptyList(),
    val enableCalendarCreate: Boolean = true,
    val enableAlarm: Boolean = false,
    val enableFileWrite: Boolean = false,
    val enableShell: Boolean = false,
    val enableMcp: Boolean = false,
    val enableHeartbeat: Boolean = false,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatPrompt: String = DEFAULT_HEARTBEAT_PROMPT,
    val activeHoursStart: Int = 8,
    val activeHoursEnd: Int = 23,
    val heartbeatSilentWhenNoContent: Boolean = true,
    val enableScheduledTasks: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val lastHeartbeatAt: Long = 0,
    val heartbeatLog: List<HeartbeatLogEntry> = emptyList(),
    val tasks: List<PulseTask> = emptyList(),
    val workspaceId: Uuid? = null,
    val workspaceInitialized: Boolean = false,
    val enableWatchers: Boolean = false,
    val watchers: List<PulseWatcher> = emptyList(),
    val notifyOnReport: Boolean = true,
    val activityLog: List<PulseActivityEntry> = emptyList(),
    val priceInputPerMillion: Double = 0.0,
    val priceOutputPerMillion: Double = 0.0,
    val priceCachedPerMillion: Double = 0.0,
    val priceMultiplier: Double = 1.0,
    val peakPricingEnabled: Boolean = false,
    val priceSegments: List<PulsePriceSegment> = emptyList(),
    val runLogs: List<PulseRunLog> = emptyList(),
)

@Serializable
data class HeartbeatLogEntry(val at: Long = 0, val result: String = "", val summary: String = "")

@Serializable
enum class WatcherTarget { BATTERY, URL, COMMAND }

@Serializable
data class PulseWatcher(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val everyMinutes: Int = 15,
    val target: WatcherTarget = WatcherTarget.URL,
    val source: String = "",
    val threshold: Double = 5.0,
    val prompt: String = "",
    val lastFingerprint: String = "",
    val baselineBattery: Double = -1.0,
    val lastCheckedAt: Long = 0L,
    val lastChangedAt: Long = 0L,
    val lastResult: String = "",
)

@Serializable
data class PulseActivityEntry(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val at: Long = System.currentTimeMillis(),
    val kind: String = "heartbeat",
    val refId: String = "",
    val result: String = "OK",
    val summary: String = "",
    val reportPath: String = "",
)

@Serializable
data class PulseRunLog(
    val at: Long = System.currentTimeMillis(),
    val kind: String = "heartbeat",
    val refId: String = "",
    val result: String = "OK",
    val durationMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val note: String = "",
)

/** Shared half-open active-hours semantics used by scheduling and the daemon notification. */
fun PulseConfig.isInActiveHours(now: LocalTime): Boolean {
    val start = LocalTime.of(activeHoursStart.coerceIn(0, 23), 0)
    val end = LocalTime.of(activeHoursEnd.coerceIn(0, 23), 0)
    return when {
        start == end -> true
        start < end -> !now.isBefore(start) && now.isBefore(end)
        else -> !now.isBefore(start) || now.isBefore(end)
    }
}
