package me.rerere.rikkahub.service.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig
import me.rerere.rikkahub.data.datastore.SettingsStore

internal data class PulseNotification(
    val packageName: String,
    val appLabel: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val redacted: Boolean,
)

/** Process memory only. Revoking a whitelist entry also discards previously captured content. */
internal class PulseNotificationBuffer(private val now: () -> Long = System::currentTimeMillis) {
    private val queue = ArrayDeque<PulseNotification>()

    @Synchronized
    fun offer(
        config: PulseConfig, selfPackage: String, packageName: String, appLabel: String,
        postedAt: Long, title: String?, text: String?, ongoing: Boolean = false, groupSummary: Boolean = false,
    ) {
        synchronize(config)
        if (!config.enableNotificationReading || packageName == selfPackage ||
            packageName in setOf("android", "com.android.systemui") || ongoing || groupSummary) return
        if (title.isNullOrBlank() && text.isNullOrBlank()) return
        val redacted = packageName !in config.notificationWhitelist
        queue.addLast(PulseNotification(
            packageName, appLabel.take(80), postedAt,
            if (redacted) null else title?.take(80),
            if (redacted) null else text?.take(200), redacted,
        ))
        synchronize(config)
    }

    @Synchronized
    fun synchronize(config: PulseConfig) {
        if (!config.enableNotificationReading) { queue.clear(); return }
        val cutoff = now() - config.notificationRetentionHours.coerceIn(1, 72) * 3_600_000L
        val retained = queue.filter { it.postedAt >= cutoff }.map { item ->
            if (!item.redacted && item.packageName !in config.notificationWhitelist)
                item.copy(title = null, text = null, redacted = true) else item
        }
        queue.clear()
        queue.addAll(retained.takeLast(config.notificationBufferLimit.coerceIn(20, 500)))
    }

    @Synchronized
    fun snapshot(config: PulseConfig): List<PulseNotification> {
        synchronize(config)
        return queue.toList()
    }

    @Synchronized
    fun clear() = queue.clear()
}

internal object PulseNotificationInbox {
    @Volatile var settingsProvider: (() -> SettingsStore)? = null
    @Volatile var connected: Boolean = false
    val buffer = PulseNotificationBuffer()
}
