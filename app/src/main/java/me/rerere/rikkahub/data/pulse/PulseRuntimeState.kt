package me.rerere.rikkahub.data.pulse

import android.content.Context
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.utils.JsonInstant
import java.time.LocalDate

@Serializable
data class PulseRuntimeState(
    /** Elapsed time of the current daemon instance. It resets when the service is recreated. */
    val serviceStartedAt: Long = 0L,
    val todaySuccessCount: Int = 0,
    val todayKey: String = "",
    /** Wall-clock time calculated by the scheduler, not by the notification renderer. */
    val nextHeartbeatAt: Long = 0L,
    /** Wall-clock time when the latest heartbeat started. */
    val lastHeartbeatAt: Long = 0L,
    val lastHeartbeatOk: Boolean = true,
    val lastOfDay: Boolean = false,
    /** Non-zero while a heartbeat or task is executing. */
    val runningSince: Long = 0L,
    val runningLabel: String = "",
)

object PulseRuntimeStore {
    private const val FILE = "pulse_runtime"
    private const val STATE = "state"
    private val lock = Any()

    fun load(context: Context): PulseRuntimeState = synchronized(lock) {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = preferences.getString(STATE, null)?.let {
            runCatching { JsonInstant.decodeFromString<PulseRuntimeState>(it) }.getOrNull()
        } ?: PulseRuntimeState()
        val rolled = current.rolloverIfNeeded(LocalDate.now())
        if (rolled != current) saveLocked(preferences, rolled)
        rolled
    }

    fun save(context: Context, state: PulseRuntimeState) = synchronized(lock) {
        saveLocked(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), state.rolloverIfNeeded(LocalDate.now()))
    }

    fun update(context: Context, block: (PulseRuntimeState) -> PulseRuntimeState): PulseRuntimeState = synchronized(lock) {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = preferences.getString(STATE, null)?.let {
            runCatching { JsonInstant.decodeFromString<PulseRuntimeState>(it) }.getOrNull()
        } ?: PulseRuntimeState()
        val updated = block(current.rolloverIfNeeded(LocalDate.now())).rolloverIfNeeded(LocalDate.now())
        saveLocked(preferences, updated)
        updated
    }

    private fun saveLocked(preferences: android.content.SharedPreferences, state: PulseRuntimeState) {
        check(preferences.edit().putString(STATE, JsonInstant.encodeToString(state)).commit()) {
            "Cannot save Pulse runtime state"
        }
    }

}

internal fun PulseRuntimeState.rolloverIfNeeded(date: LocalDate): PulseRuntimeState {
    val today = date.toString()
    return if (todayKey == today) this else copy(todayKey = today, todaySuccessCount = 0, lastOfDay = false)
}
