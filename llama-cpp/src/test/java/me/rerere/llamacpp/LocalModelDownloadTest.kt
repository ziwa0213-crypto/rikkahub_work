package me.rerere.llamacpp

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LocalModelDownloadTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun `actual content length and user agent accompany progress`() = runBlocking {
        val bytes = ByteArray(1_200_000) { (it % 251).toByte() }
        var agent: String? = null
        val server = server { exchange ->
            agent = exchange.requestHeaders.getFirst("User-Agent")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        try {
            val part = files.newFile()
            val values = mutableListOf<Pair<Long, Long>>()
            LocalModelDownload(OkHttpClient()).fetch(url(server), part) { done, total -> values += done to total }
            assertArrayEquals(bytes, part.readBytes())
            assertTrue(agent.orEmpty().startsWith("RikkaHub-Android/"))
            assertEquals(bytes.size.toLong() to bytes.size.toLong(), values.last())
            assertTrue(values.size >= 3)
        } finally { server.stop(0) }
    }

    @Test fun `404 does not retry and 503 retries only twice with prescribed pauses`() = runBlocking {
        val count = AtomicInteger()
        var status = 404
        val server = server { exchange ->
            count.incrementAndGet()
            exchange.sendResponseHeaders(status, -1); exchange.close()
        }
        try {
            val pauses = mutableListOf<Long>()
            val downloader = LocalModelDownload(OkHttpClient()) { pauses += it }
            val failure = runCatching { downloader.fetch(url(server), files.newFile()) { _, _ -> } }.exceptionOrNull()
            assertEquals(404, (failure as DownloadHttpException).code)
            assertEquals(1, count.get()); assertTrue(pauses.isEmpty())
            count.set(0); status = 503
            runCatching { downloader.fetch(url(server), files.newFile()) { _, _ -> } }
            assertEquals(3, count.get()); assertEquals(listOf(2000L, 4000L), pauses)
        } finally { server.stop(0) }
    }

    @Test fun `unknown transfer length stays indeterminate`() = runBlocking {
        val server = server { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write("GGUFtest".toByteArray()) }
        }
        try {
            val lengths = mutableListOf<Long>()
            LocalModelDownload(OkHttpClient()).fetch(url(server), files.newFile()) { _, total -> lengths += total }
            assertTrue(lengths.isNotEmpty()); assertTrue(lengths.all { it == -1L })
        } finally { server.stop(0) }
    }

    @Test fun `cancel closes network writer before installer removes partial file`() = runBlocking {
        val started = AtomicBoolean()
        val server = server { exchange ->
            exchange.sendResponseHeaders(200, 10_000_000)
            runCatching { exchange.responseBody.use { output ->
                repeat(1000) { output.write(ByteArray(10_000)); output.flush(); Thread.sleep(5) }
            } }
        }
        try {
            val part = files.newFile()
            val job = launch(Dispatchers.IO) {
                try { LocalModelDownload(OkHttpClient()).fetch(url(server), part) { done, _ -> if (done > 0) started.set(true) } }
                finally { part.delete() }
            }
            withTimeout(3000) { while (!started.get()) delay(2) }
            withTimeout(3000) { job.cancelAndJoin() }
            delay(30)
            assertFalse(part.exists())
        } finally { server.stop(0) }
    }

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { createContext("/model", handler); start() }
    private fun url(server: HttpServer) = "http://127.0.0.1:${server.address.port}/model"
}
