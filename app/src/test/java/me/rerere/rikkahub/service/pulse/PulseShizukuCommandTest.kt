package me.rerere.rikkahub.service.pulse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class PulseShizukuCommandTest {
    @Test fun `disabled or unavailable bridge executes no commands and preserves first attempt`() = runBlocking {
        var ready = false
        var enabled = false
        val calls = mutableListOf<List<String>>()
        val commands = PulseShizukuCommands({ ready }) { calls.add(it); true }
        assertFalse(commands.ensureDozeWhitelist("own.pkg", { enabled }) { fail("must not query power"); false })
        enabled = true
        assertFalse(commands.ensureDozeWhitelist("own.pkg", { enabled }) { fail("must not query power"); false })
        assertTrue(calls.isEmpty())
        ready = true
        assertTrue(commands.ensureDozeWhitelist("own.pkg", { enabled }) { false })
        assertEquals(listOf(listOf("cmd", "deviceidle", "whitelist", "+own.pkg")), calls)
    }

    @Test fun `both settings are rechecked before the whitelist command`() = runBlocking {
        var enabled = true
        val commands = PulseShizukuCommands({ true }) { fail("disabled before execution"); true }
        assertFalse(commands.ensureDozeWhitelist("own.pkg", { enabled }) { enabled = false; false })
    }

    @Test fun `existing exemption needs no command`() = runBlocking {
        val commands = PulseShizukuCommands({ true }) { fail("already exempt"); true }
        assertTrue(commands.ensureDozeWhitelist("own.pkg", { true }) { true })
        assertFalse(commands.ensureDozeWhitelist("own.pkg", { true }) { fail("once only"); false })
    }

    @Test fun `failed whitelist command is not retried for this process`() = runBlocking {
        var attempts = 0
        val commands = PulseShizukuCommands({ true }) { attempts++; false }
        repeat(3) { assertFalse(commands.ensureDozeWhitelist("own.pkg", { true }) { false }) }
        assertEquals(1, attempts)
    }

    @Test fun `concurrent foreground signals attempt the whitelist only once`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        val commands = PulseShizukuCommands({ true }) { attempts++; entered.complete(Unit); release.await(); true }
        val first = async { commands.ensureDozeWhitelist("own.pkg", { true }) { false } }
        entered.await()
        assertFalse(commands.ensureDozeWhitelist("own.pkg", { true }) { false })
        release.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, attempts)
    }

    @Test fun `both large output pipes are drained concurrently without blocking`() = runBlocking {
        val stdout = CountingStream(PipedInputStream(1024))
        val stderr = CountingStream(PipedInputStream(1024))
        val stdoutWriter = PipedOutputStream(stdout.pipe)
        val stderrWriter = PipedOutputStream(stderr.pipe)
        val process = TestProcess(stdout, stderr, initiallyRunning = true)
        val bytes = ByteArray(256 * 1024) { 97 }
        val writer = thread(isDaemon = true) {
            try {
                // A sequential stdout reader deadlocks here while stderr's small pipe is full.
                stderrWriter.use { it.write(bytes) }
                stdoutWriter.use { it.write(bytes) }
            } finally { process.running = false }
        }
        assertTrue(withTimeout(5_000) { runPulseShizukuProcess(process) })
        writer.join(1_000)
        assertFalse(writer.isAlive)
        assertEquals(bytes.size, stdout.bytes.get())
        assertEquals(bytes.size, stderr.bytes.get())
        assertTrue(process.destroyed)
        assertTrue(stdout.closed)
        assertTrue(stderr.closed)
    }

    @Test fun `nonzero command exit is failure and destroys the process`() = runBlocking {
        val process = TestProcess(exitCode = 1)
        assertFalse(runPulseShizukuProcess(process))
        assertTrue(process.destroyed)
    }

    @Test fun `timeout destroys the command and returns failure`() = runBlocking {
        val process = TestProcess(initiallyRunning = true)
        assertFalse(runPulseShizukuProcess(process, timeoutMillis = 100))
        assertTrue(process.destroyed)
    }

    @Test fun `caller cancellation destroys process and remains cancellation`() = runBlocking {
        val process = TestProcess(initiallyRunning = true)
        val entered = CompletableDeferred<Unit>()
        val job = async { runPulseShizukuProcess(process, isAlive = { entered.complete(Unit); true }) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(process.destroyed)
    }

    @Test fun `stream acquisition failure still destroys the process`() = runBlocking {
        val process = object : TestProcess() {
            override fun getErrorStream(): InputStream = throw IOException("pipe unavailable")
        }
        try {
            runPulseShizukuProcess(process)
            fail("stream error must propagate to bridge")
        } catch (_: IOException) { /* Bridge converts this into an optional failure. */ }
        assertTrue(process.destroyed)
    }

    @Test fun `cancellation unblocks readers on open pipes`() = runBlocking {
        val stdout = CountingStream(PipedInputStream(1024))
        val stderr = CountingStream(PipedInputStream(1024))
        val stdoutWriter = PipedOutputStream(stdout.pipe)
        val stderrWriter = PipedOutputStream(stderr.pipe)
        val process = TestProcess(stdout, stderr, initiallyRunning = true)
        val entered = CompletableDeferred<Unit>()
        try {
            val job = async { runPulseShizukuProcess(process, isAlive = { entered.complete(Unit); true }) }
            entered.await()
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(process.destroyed)
            assertTrue(stdout.closed)
            assertTrue(stderr.closed)
        } finally {
            stdoutWriter.close()
            stderrWriter.close()
        }
    }

    @Test fun `output read failure destroys the process and preserves the error`() = runBlocking {
        val process = TestProcess(stdout = object : InputStream() {
            override fun read(): Int = throw IOException("read failed")
        })
        try {
            runPulseShizukuProcess(process)
            fail("read failure must propagate to bridge")
        } catch (error: IOException) {
            assertEquals("read failed", error.message)
        }
        assertTrue(process.destroyed)
    }

    @Test fun `timeout unblocks both open pipes and returns failure`() = runBlocking {
        val stdout = CountingStream(PipedInputStream(1024))
        val stderr = CountingStream(PipedInputStream(1024))
        val stdoutWriter = PipedOutputStream(stdout.pipe)
        val stderrWriter = PipedOutputStream(stderr.pipe)
        val process = TestProcess(stdout, stderr, initiallyRunning = true)
        try {
            assertFalse(withTimeout(5_000) { runPulseShizukuProcess(process, timeoutMillis = 100) })
            assertTrue(process.destroyed)
            assertTrue(stdout.closed)
            assertTrue(stderr.closed)
        } finally {
            stdoutWriter.close()
            stderrWriter.close()
        }
    }

    private open class TestProcess(
        private val stdout: InputStream = ByteArrayInputStream(byteArrayOf()),
        private val stderr: InputStream = ByteArrayInputStream(byteArrayOf()),
        private val exitCode: Int = 0,
        initiallyRunning: Boolean = false,
    ) : Process() {
        @Volatile var running = initiallyRunning
        @Volatile var destroyed = false
        override fun getInputStream() = stdout
        override fun getErrorStream() = stderr
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun isAlive() = running
        override fun waitFor(): Int = error("must not use blocking waitFor")
        override fun exitValue(): Int {
            if (running) throw IllegalThreadStateException()
            return exitCode
        }
        override fun destroy() { destroyed = true; running = false }
    }

    private class CountingStream(val pipe: PipedInputStream) : FilterInputStream(pipe) {
        val bytes = AtomicInteger()
        @Volatile var closed = false
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytes.addAndGet(it) }
        override fun close() { closed = true; super.close() }
    }
}
