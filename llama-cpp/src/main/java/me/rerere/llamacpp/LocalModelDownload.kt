package me.rerere.llamacpp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DownloadHttpException(val code: Int) : IOException("HTTP $code")

internal class LocalModelDownload(private val client: OkHttpClient, private val pause: suspend (Long) -> Unit = { delay(it) }) {
    suspend fun fetch(url: String, part: File, progress: (Long, Long) -> Unit) {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try { transfer(url, part, progress); return }
            catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (e is DownloadHttpException && e.code in 400..499 || attempt >= 2) throw e
                pause(if (attempt++ == 0) 2000 else 4000)
            }
        }
    }

    private suspend fun transfer(url: String, part: File, progress: (Long, Long) -> Unit) {
        val finished = CompletableDeferred<Unit>()
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "RikkaHub-Android/llama.cpp").build())
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                try {
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            finished.complete(Unit)
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            try {
                                response.use {
                                    if (!it.isSuccessful) throw DownloadHttpException(it.code)
                                    val body = it.body
                                    val total = body.contentLength()
                                    // Content-Length can exceed catalog metadata. Check it
                                    // before opening the output (which creates .part).
                                    progress(0, total)
                                    body.byteStream().use { input -> part.outputStream().use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        var done = 0L; var reported = 0L
                                        while (true) {
                                            if (!continuation.isActive) throw IOException("Cancelled")
                                            val size = input.read(buffer)
                                            if (size < 0) break
                                            output.write(buffer, 0, size); done += size
                                            if (done - reported >= 512 * 1024) { progress(done, total); reported = done }
                                        }
                                        if (total >= 0 && done != total) throw IOException("Incomplete download")
                                        progress(done, total)
                                    } }
                                }
                                if (continuation.isActive) continuation.resume(Unit)
                            } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                            finally { finished.complete(Unit) }
                        }
                    })
                } catch (e: Exception) { finished.complete(Unit); throw e }
            }
        } finally {
            // Cancellation may beat the socket callback. Wait for its writer to close
            // before the installer deletes .part, or a late callback can recreate it.
            withContext(NonCancellable) { finished.await() }
        }
    }
}
