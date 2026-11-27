package me.rerere.rikkahub.data.files

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class CharacterAttachmentCopyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingSourceDoesNotOpenDestinationOrThrow() {
        val missing = File(temporary.root, "missing.jpg")
        var targetOpened = false
        var warning: Exception? = null

        assertFalse(copyCharacterAttachmentSource(
            openSource = { missing.inputStream() },
            openTarget = { targetOpened = true; ByteArrayOutputStream() },
            onUnavailable = { warning = it },
        ))

        assertFalse(targetOpened)
        assertTrue(warning is java.io.FileNotFoundException)
    }

    @Test fun nullSourceDoesNotOpenDestination() {
        assertFalse(copyCharacterAttachmentSource(
            openSource = { null },
            openTarget = { error("No target should be opened") },
            onUnavailable = {},
        ))
    }

    @Test fun unreadableSourceIsRecoverable() {
        assertFalse(copyCharacterAttachmentSource(
            openSource = { throw SecurityException("Source permission revoked") },
            openTarget = { error("No target should be opened") },
            onUnavailable = { assertTrue(it is SecurityException) },
        ))
    }

    @Test fun readFailureClosesBothStreamsAndReportsUnavailable() {
        var sourceClosed = false
        var targetClosed = false
        val source = object : InputStream() {
            var reads = 0
            override fun read(): Int = throw IOException("Broken source")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (reads++ == 0) { bytes[offset] = 1; return 1 }
                throw IOException("Broken source")
            }
            override fun close() { sourceClosed = true }
        }
        val target = object : ByteArrayOutputStream() {
            override fun close() { targetClosed = true; super.close() }
        }

        assertFalse(copyCharacterAttachmentSource({ source }, { target }, {}))
        assertEquals(1, target.size())
        assertTrue(sourceClosed)
        assertTrue(targetClosed)
    }

    @Test fun healthyFileCopiesFullyWithoutTruncatingAtOldMemoryBudget() {
        val source = temporary.newFile("original.bin")
        val target = temporary.newFile("copy.bin")
        val bytes = ByteArray(512 * 1024 + 123) { (it % 251).toByte() }
        source.writeBytes(bytes)

        assertTrue(copyCharacterAttachmentSource({ source.inputStream() }, { target.outputStream() }) {
            fail("Healthy source must not be reported as missing")
        })
        assertArrayEquals(bytes, target.readBytes())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun destinationOpenFailureRemainsFatal() {
        val full = IOException("No space left on device")
        val thrown = assertThrows(IOException::class.java) {
            copyCharacterAttachmentSource({ ByteArrayInputStream(byteArrayOf(1)) }, { throw full }) {
                fail("Destination failure is not a missing source")
            }
        }
        assertSame(full, thrown)
    }

    @Test fun destinationWriteFailureRemainsFatalAndClosesSource() {
        var closed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() { closed = true; super.close() }
        }
        val full = IOException("No space left on device")
        val target = object : OutputStream() {
            override fun write(value: Int) { throw full }
        }

        assertSame(full, assertThrows(IOException::class.java) {
            copyCharacterAttachmentSource({ source }, { target }) { fail("Destination failure must propagate") }
        })
        assertTrue(closed)
    }

    @Test fun destinationCloseFailureRemainsFatal() {
        val failure = IOException("Flush failed")
        val target = object : ByteArrayOutputStream() {
            override fun close() { throw failure }
        }
        assertSame(failure, assertThrows(IOException::class.java) {
            copyCharacterAttachmentSource({ ByteArrayInputStream(byteArrayOf(1)) }, { target }) {
                fail("Destination close failure must propagate")
            }
        })
    }

    @Test fun cancellationIsNeverCountedAsMissing() {
        val cancellation = CancellationException("Stopped")
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            copyCharacterAttachmentSource({ throw cancellation }, { ByteArrayOutputStream() }) {
                fail("Cancellation must propagate")
            }
        })
    }
}
