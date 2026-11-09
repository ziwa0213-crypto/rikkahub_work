package me.rerere.rikkahub.service.pulse

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.PulseWatcher
import me.rerere.rikkahub.data.datastore.WatcherTarget
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus
import java.net.HttpURLConnection
import java.net.URL

internal data class PulseWatcherSample(
    val fingerprint: String,
    val preview: String,
    val batteryPercent: Double? = null,
)

internal class PulseWatcherSampler(
    private val context: Context,
    private val workspaces: WorkspaceRepository,
) {
    suspend fun sample(watcher: PulseWatcher, workspaceId: String?): PulseWatcherSample = withContext(Dispatchers.IO) {
        when (watcher.target) {
            WatcherTarget.BATTERY -> {
                val battery = context.getSystemService(BatteryManager::class.java)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                require(battery in 0..100) { "Battery percentage is unavailable" }
                val content = battery.toString().toByteArray()
                PulseWatcherSample(watcherFingerprint(content), "Battery: $battery%", battery.toDouble())
            }
            WatcherTarget.URL -> sampleUrl(watcher.source)
            WatcherTarget.COMMAND -> sampleCommand(watcher.source, workspaceId)
        }
    }

    private fun sampleUrl(address: String): PulseWatcherSample {
        val url = URL(address.trim())
        require(url.protocol == "https" || url.protocol == "http") { "Only HTTP and HTTPS URLs are supported" }
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "RikkaHub-Work-Pulse/1.0")
        try {
            val status = connection.responseCode
            require(status in 200..299) { "HTTP $status" }
            val bytes = connection.inputStream.use { input -> readBounded(input, MAX_URL_BYTES) }
            val content = bytes.toString(Charsets.UTF_8)
            return PulseWatcherSample(watcherFingerprint(bytes), content.take(PREVIEW_CHARS))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun sampleCommand(command: String, workspaceId: String?): PulseWatcherSample {
        require(!workspaceId.isNullOrBlank()) { "no-workspace" }
        val workspace = workspaces.getById(workspaceId) ?: error("no-workspace")
        require(workspace.owner == "pulse" && workspace.shellStatus == WorkspaceShellStatus.READY.name) { "no-workspace" }
        require(command.isNotBlank()) { "Command is empty" }
        val result = workspaces.executeCommand(workspaceId, command, timeoutMillis = COMMAND_TIMEOUT_MS)
        require(!result.timedOut) { "Command timed out" }
        require(result.exitCode == 0) { result.stderr.ifBlank { "Command exited with ${result.exitCode}" }.take(200) }
        val bytes = result.stdout.toByteArray(Charsets.UTF_8).take(MAX_COMMAND_BYTES).toByteArray()
        val preview = bytes.toString(Charsets.UTF_8).take(PREVIEW_CHARS)
        return PulseWatcherSample(watcherFingerprint(bytes), preview)
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (output.size() < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_URL_BYTES = 256 * 1024
        const val MAX_COMMAND_BYTES = 64 * 1024
        const val PREVIEW_CHARS = 800
        const val COMMAND_TIMEOUT_MS = 60_000L
    }
}
