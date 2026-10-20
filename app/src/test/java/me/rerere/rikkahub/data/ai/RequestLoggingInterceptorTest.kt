package me.rerere.rikkahub.data.ai

import me.rerere.common.android.Logging
import me.rerere.common.android.RequestClockSnapshot
import me.rerere.rikkahub.ui.pages.log.requestActiveDuration
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RequestLoggingInterceptorTest {
    @Before fun enableLogging() { Logging.clear(); Logging.setRequestLoggingEnabled(true) }
    @After fun cleanup() { Logging.clear(); Logging.setRequestLoggingEnabled(false) }

    @Test fun successSeparatesAwakeDurationFromTwoHourSleep() {
        client().newCall(Request.Builder().url("https://example.test").build()).execute().close()
        val log = Logging.getRequestLogs().single()
        assertEquals(30_000L, log.durationMs)
        assertEquals(7_200_000L, log.suspendedMs)
        assertEquals(200, log.responseCode)
    }

    @Test fun failureAlsoRecordsBothClocksAndRethrowsOriginalError() {
        val error = assertThrows(IOException::class.java) {
            client(fail = true).newCall(Request.Builder().url("https://example.test").build()).execute()
        }
        assertEquals("offline fixture", error.message)
        val log = Logging.getRequestLogs().single()
        assertEquals(30_000L, log.durationMs)
        assertEquals(7_200_000L, log.suspendedMs)
        assertEquals("offline fixture", log.error)
    }

    @Test fun disabledLoggingDoesNotReadClockOrCreateEntry() {
        Logging.setRequestLoggingEnabled(false)
        val client = OkHttpClient.Builder()
            .addInterceptor(RequestLoggingInterceptor { error("Clock should not be read") })
            .addInterceptor { response(it.request()) }.build()
        client.newCall(Request.Builder().url("https://example.test").build()).execute().close()
        assertTrue(Logging.getRequestLogs().isEmpty())
    }

    @Test fun shortDurationsKeepMillisecondsLongDurationsUseSeconds() {
        assertEquals("30000ms", requestActiveDuration(30_000))
        assertEquals("59999ms", requestActiveDuration(59_999))
        assertEquals("60s", requestActiveDuration(60_000))
    }

    private fun client(fail: Boolean = false): OkHttpClient {
        val times = ArrayDeque(listOf(RequestClockSnapshot(10_000, 9000), RequestClockSnapshot(7_240_000, 39_000)))
        return OkHttpClient.Builder().retryOnConnectionFailure(false)
            .addInterceptor(RequestLoggingInterceptor { times.removeFirst() })
            .addInterceptor { if (fail) throw IOException("offline fixture") else response(it.request()) }.build()
    }

    private fun response(request: Request): Response = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("fixture").body("OK".toResponseBody()).build()
}
