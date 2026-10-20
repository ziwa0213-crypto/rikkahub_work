package me.rerere.rikkahub.data.ai

import android.os.SystemClock
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.common.android.RequestClockSnapshot
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class RequestLoggingInterceptor internal constructor(private val clock: () -> RequestClockSnapshot) : Interceptor {
    constructor() : this({ RequestClockSnapshot(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis()) })

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val started = clock()

        val requestHeaders = request.headers.toMap()
        val requestBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message
            val finished = clock()
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = request.url.toString(),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error,
                    durationMs = finished.awakeSince(started),
                    suspendedMs = finished.suspendedSince(started),
                )
            )
            throw e
        }

        val finished = clock()
        val responseHeaders = response.headers.toMap()

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = request.url.toString(),
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = responseHeaders,
                durationMs = finished.awakeSince(started),
                suspendedMs = finished.suspendedSince(started),
                error = error
            )
        )

        return response
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { name ->
            if (name.equals("Proxy-Authorization", ignoreCase = true)) {
                "██"
            } else {
                get(name) ?: ""
            }
        }
    }
}
