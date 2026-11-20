package me.rerere.llamacpp

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import kotlin.math.round

enum class MemoryGate { OK, WARN }
data class MemoryVerdict(val gate: MemoryGate, val deviceGb: Double, val requiredGb: Int)

/** Only physical/advertised RAM participates in the advisory load gate. */
object LocalMemoryGate {
    fun deviceTotalBytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (Build.VERSION.SDK_INT >= 34) info.advertisedMem.takeIf { it > 0 } ?: info.totalMem
        else info.totalMem
    }

    fun verdict(totalBytes: Long, minMemGb: Int): MemoryVerdict {
        val gb = round(totalBytes / (1024.0 * 1024 * 1024) * 10) / 10
        return MemoryVerdict(if (gb + .05 < minMemGb) MemoryGate.WARN else MemoryGate.OK, gb, minMemGb)
    }

    fun check(context: Context, minMemGb: Int) = verdict(deviceTotalBytes(context), minMemGb)

    internal fun procBytes(lines: Sequence<String>, key: String): Long = lines
        .firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()
        ?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()?.coerceAtLeast(0)?.times(1024) ?: 0

    fun swapTotalBytes(): Long = runCatching {
        File("/proc/meminfo").useLines { procBytes(it, "SwapTotal") }
    }.getOrDefault(0)

    fun processSwapKb(): Long = runCatching {
        File("/proc/self/status").useLines { procBytes(it, "VmSwap") / 1024 }
    }.getOrDefault(0)

    fun fusionCushionBytes(enabled: Boolean, swapBytes: Long = swapTotalBytes()): Long =
        if (!enabled) 0 else minOf(swapBytes.coerceAtLeast(0) / 8, 1024L * 1024 * 1024)
}
