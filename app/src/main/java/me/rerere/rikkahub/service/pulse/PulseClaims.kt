package me.rerere.rikkahub.service.pulse

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.PulseConfig
import java.util.UUID

@Serializable
data class PulseClaim(
    val key: String = "",
    val owner: String = "",
    val until: Long = 0,
    val done: Boolean = false,
)

/** The claim, not the later start timestamp, identifies an interrupted execution. */
internal fun PulseClaim.isPendingFor(config: PulseConfig): Boolean {
    if (done) return false
    val slot = key.substringAfterLast(':').toLongOrNull()?.takeIf { it > 0 } ?: return false
    return when {
        key == "heartbeat:$slot" -> config.enableHeartbeat
        else -> config.enableScheduledTasks && config.tasks.any { task ->
            task.enabled && key == "task:${task.id}:$slot" && slot > task.lastSlot
        }
    }
}

/** Fenced lease: late renewal/completion can never overwrite a successor. */
internal class PulseClaims(
    private val load: () -> PulseClaim,
    private val save: (PulseClaim) -> Unit,
    private val owner: () -> String = { UUID.randomUUID().toString() },
) {
    @Synchronized
    fun claim(key: String, now: Long, active: Boolean): PulseClaim? {
        val current = load()
        if (active || (!current.done && current.until > now) || (current.key == key && current.done)) return null
        return PulseClaim(key, owner(), now + LEASE_MS).also(save)
    }

    @Synchronized
    fun renew(claim: PulseClaim, now: Long): Boolean {
        val current = load()
        if (current.owner != claim.owner || current.key != claim.key || current.done) return false
        save(current.copy(until = now + LEASE_MS))
        return true
    }

    @Synchronized
    fun finish(claim: PulseClaim): Boolean {
        val current = load()
        if (current.owner != claim.owner || current.key != claim.key || current.done) return false
        save(current.copy(done = true, until = 0))
        return true
    }

    companion object {
        const val LEASE_MS = 15 * 60_000L
        const val RENEW_MS = 2 * 60_000L
    }
}
