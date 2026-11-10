package me.rerere.rikkahub.service.pulse

import me.rerere.rikkahub.data.datastore.PulseConfig

internal fun PulseConfig.smsPollingEnabled(): Boolean = enableSmsReading && smsPollEnabled

/** A stable anchor: unrelated dispatches must not push the poll indefinitely into the future. */
internal fun smsNextPollAt(config: PulseConfig, lastPollAt: Long, now: Long): Long = when {
    !config.smsPollingEnabled() -> Long.MAX_VALUE
    lastPollAt <= 0L || now < lastPollAt -> now
    else -> lastPollAt + config.smsPollMinutes.coerceIn(5, 240) * 60_000L
}

/** Every live claim blocks the shared slot, even kinds that must not replay after a crash. */
internal fun pulseCandidateAfterLease(pending: PulseClaim?, now: Long, nextDue: Long): Long =
    pending?.takeIf { !it.done && it.until > now }?.until ?: nextDue
