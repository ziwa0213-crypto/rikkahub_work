package me.rerere.workspace.rootfs

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RootfsIndexClient internal constructor(
    private val cacheFile: File,
    private val fetch: suspend (String) -> String,
    private val cacheTtlMs: Long = 24 * 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(http: HttpClient, cacheFile: File, cacheTtlMs: Long = 24 * 60 * 60 * 1000L) : this(
        cacheFile = cacheFile,
        fetch = { url ->
            val response = http.get(url) { header("User-Agent", "curl/8.5.0") }
            check(response.status.value in 200..299) { "Rootfs index HTTP ${response.status.value}" }
            response.bodyAsText()
        },
        cacheTtlMs = cacheTtlMs,
    )

    data class Loaded(val index: RootfsIndex, val fromCache: Boolean, val fetchedAt: Long)
    private val mutex = Mutex()

    suspend fun readCache(): Loaded? = withContext(Dispatchers.IO) {
        runCatching {
            if (!cacheFile.isFile || cacheFile.length() > MAX_INDEX_BYTES) return@runCatching null
            Loaded(RootfsIndex.parse(cacheFile.readText()), true, cacheFile.lastModified())
        }.getOrNull()
    }

    suspend fun load(forceRefresh: Boolean): Result<Loaded> = mutex.withLock {
        val cached = readCache()
        if (!forceRefresh && cached != null && now() - cached.fetchedAt in 0 until cacheTtlMs) {
            return@withLock Result.success(cached)
        }
        var lastError: Exception? = null
        for (url in INDEX_URLS) {
            try {
                val text = withTimeout(8_000) { fetch(url) }
                require(text.length <= MAX_INDEX_BYTES) { "Rootfs index is too large" }
                val index = RootfsIndex.parse(text)
                val fetchedAt = now()
                // Cache failure (e.g. full cache volume) must not discard a valid network result.
                withContext(Dispatchers.IO) {
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        val pending = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                        pending.writeText(text)
                        Files.move(pending.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        cacheFile.setLastModified(fetchedAt)
                    }
                }
                return@withLock Result.success(Loaded(index, false, fetchedAt))
            } catch (error: TimeoutCancellationException) {
                lastError = error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        cached?.let { Result.success(it) } ?: Result.failure(lastError ?: error("No index source"))
    }

    companion object {
        private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
        internal val INDEX_URLS = listOf(
            "https://raw.githubusercontent.com/ziwa0213-crypto/android-proot-rootfs/index/index.json",
            "https://cdn.jsdelivr.net/gh/ziwa0213-crypto/android-proot-rootfs@index/index.json",
        )
    }
}
