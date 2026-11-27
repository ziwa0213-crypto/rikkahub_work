package me.rerere.rikkahub.service.pulse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Gates are evaluated on execution, including after an asynchronous foreground callback. */
internal class PulseShizukuCommands(
    private val ready: () -> Boolean,
    private val execute: suspend (List<String>) -> Boolean,
) {
    private val dozeAttempted = AtomicBoolean(false)

    suspend fun ensureDozeWhitelist(
        packageName: String,
        enabled: () -> Boolean,
        isExempt: () -> Boolean,
    ): Boolean {
        if (!enabled() || !ready() || !dozeAttempted.compareAndSet(false, true)) return false
        if (isExempt()) return true
        if (!enabled() || !ready()) return false
        return execute(listOf("cmd", "deviceidle", "whitelist", "+$packageName"))
    }
}

/** Bounded to this shell command only; unrelated to chat/heartbeat generation limits. */
internal suspend fun runPulseShizukuProcess(
    process: Process,
    timeoutMillis: Long = 10_000,
    isAlive: () -> Boolean = { process.isAlive },
): Boolean =
    withContext(Dispatchers.IO) {
        var stdout: InputStream? = null
        var stderr: InputStream? = null
        try {
            val outputStream = process.inputStream.also { stdout = it }
            val errorStream = process.errorStream.also { stderr = it }
            process.outputStream.close()
            withTimeoutOrNull(timeoutMillis) {
                coroutineScope {
                    val output = async { outputStream.drainCommandOutput() }
                    val errors = async { errorStream.drainCommandOutput() }
                    try {
                        // Remote waitFor() is a blocking Binder transaction and cannot be interrupted.
                        // Use Shizuku's explicit alive() rather than transporting exitValue exceptions.
                        while (isAlive()) delay(50)
                        output.await()
                        errors.await()
                        process.exitValue() == 0
                    } finally {
                        // Unblock pipe readers BEFORE coroutineScope waits for its cancelled children.
                        runCatching { process.destroy() }
                        runCatching { outputStream.close() }
                        runCatching { errorStream.close() }
                        output.cancel()
                        errors.cancel()
                    }
                }
            } ?: false
        } finally {
            // Also covers failures while opening the streams or entering the coroutine scope.
            runCatching { process.destroy() }
            runCatching { stdout?.close() }
            runCatching { stderr?.close() }
        }
    }

private fun InputStream.discard() {
    val buffer = ByteArray(8 * 1024)
    while (read(buffer) != -1) { /* Drain without retaining unbounded command output. */ }
}

private suspend fun InputStream.drainCommandOutput() {
    try {
        runInterruptible { discard() }
    } catch (error: Exception) {
        // Closing/interruption can throw IOException instead of InterruptedException.
        // A cancelled reader must not replace its parent's cancellation with an IO failure.
        currentCoroutineContext().ensureActive()
        throw error
    }
}
