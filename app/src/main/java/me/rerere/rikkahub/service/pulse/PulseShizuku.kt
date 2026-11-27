package me.rerere.rikkahub.service.pulse

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional Doze-whitelist reinforcement, never an owner of Pulse scheduling or generation.
 * The daemon and receivers remain unexported; this bridge does not start them or revive the app.
 */
object PulseShizuku {
    enum class State { UNAVAILABLE, UNAUTHORIZED, READY }

    const val PERMISSION_CODE = 4331
    const val PLUS_PACKAGE = "af.shizuku.plus.api"
    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    private const val TAG = "PulseShizuku"
    private val started = AtomicBoolean(false)
    @Volatile private var binderAlive = false
    private val changes = MutableStateFlow(0L)
    // Only invalidates the settings page's existing revision; not persisted runtime state.
    val revisions = changes.asStateFlow()
    private val receivedListener = Shizuku.OnBinderReceivedListener {
        binderAlive = true
        changes.update { it + 1 }
    }
    private val deadListener = Shizuku.OnBinderDeadListener {
        binderAlive = false
        changes.update { it + 1 }
    }
    private val commands = PulseShizukuCommands(
        ready = { state() == State.READY },
        execute = { command ->
            // 13.0.0 exposes newProcess; keep the dependency pinned until migrating to UserService.
            val process = Shizuku.newProcess(command.toTypedArray(), null, null)
            try {
                runPulseShizukuProcess(process, isAlive = { process.alive() }).also { success ->
                    if (!success) Log.w(TAG, "Optional Shizuku command failed: ${command.first()}")
                }
            } finally {
                // Cover cancellation before the helper can enter its IO context, too.
                runCatching { process.destroy() }
            }
        },
    )

    fun start() {
        if (!started.compareAndSet(false, true)) return
        runCatching {
            Shizuku.addBinderDeadListener(deadListener)
            Shizuku.addBinderReceivedListenerSticky(receivedListener)
        }.onFailure {
            runCatching { Shizuku.removeBinderDeadListener(deadListener) }
            runCatching { Shizuku.removeBinderReceivedListener(receivedListener) }
            binderAlive = false
            started.set(false)
            Log.w(TAG, "Shizuku listeners unavailable", it)
        }
    }

    fun state(): State {
        if (!runCatching { binderAlive && Shizuku.pingBinder() }.getOrDefault(false)) {
            return State.UNAVAILABLE
        }
        return if (runCatching { Shizuku.checkSelfPermission() }
                .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED) {
            State.READY
        } else State.UNAUTHORIZED
    }

    fun managerInstalled(context: Context): Boolean = installed(context, MANAGER_PACKAGE)
    fun plusInstalled(context: Context): Boolean = installed(context, PLUS_PACKAGE)

    private fun installed(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** Only called from the explicit settings action, never from a lifecycle callback. */
    fun requestPermission() {
        if (state() != State.UNAUTHORIZED) return
        runCatching { Shizuku.requestPermission(PERMISSION_CODE) }
            .onFailure { Log.w(TAG, "Shizuku permission request failed", it) }
    }

    suspend fun ensureDozeWhitelist(context: Context, enabled: () -> Boolean): Boolean =
        attempt("Doze whitelist") {
            commands.ensureDozeWhitelist(context.packageName, enabled) {
                context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            }.also { changes.update { revision -> revision + 1 } }
        }

    private suspend fun attempt(label: String, block: suspend () -> Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "$label unavailable", error)
                false
            }
        }
}
