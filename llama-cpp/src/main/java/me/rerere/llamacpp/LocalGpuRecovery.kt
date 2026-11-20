package me.rerere.llamacpp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Collections
import java.util.IdentityHashMap

private val deviceLostMarker = Regex("\\b(?:VK_ERROR_DEVICE_LOST|ERROR_DEVICE_LOST|DEVICE_LOST|(?:ERROR)?DEVICELOST)\\b", RegexOption.IGNORE_CASE)

internal fun isVulkanDeviceLost(error: Throwable): Boolean {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = error
    var matches = false
    while (current != null && seen.add(current)) {
        if (current is CancellationException || current is OutOfMemoryError) return false
        if (deviceLostMarker.containsMatchIn(current.message.orEmpty())) matches = true
        current = current.cause
    }
    return matches
}

/** The failed withReady scope has already relinquished the native lock here. */
internal suspend fun withLocalGpuRecovery(
    runtime: LlamaCppRuntime,
    hasOutput: () -> Boolean,
    generate: suspend () -> Unit,
) {
    try {
        generate()
    } catch (loss: LocalModelException) {
        if (loss.reason != LocalModelError.GPU_LOST || hasOutput()) throw loss
        currentCoroutineContext().ensureActive()
        if (!runtime.reloadOnCpu(loss)) throw loss
        try {
            generate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure !== loss) loss.addSuppressed(failure)
            throw loss
        }
    }
}
