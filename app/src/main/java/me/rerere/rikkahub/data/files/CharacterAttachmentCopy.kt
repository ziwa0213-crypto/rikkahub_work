package me.rerere.rikkahub.data.files

import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Only unreadable sources are recoverable; destination failures must roll back the transfer. */
internal fun copyCharacterAttachmentSource(
    openSource: () -> InputStream?,
    openTarget: () -> OutputStream,
    onUnavailable: (Exception) -> Unit,
): Boolean {
    return try {
        val source = readCharacterSource { openSource() ?: throw FileNotFoundException("Attachment source unavailable") }
        val guarded = object : FilterInputStream(source) {
            override fun read(): Int = readCharacterSource { `in`.read() }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                readCharacterSource { `in`.read(bytes, offset, length) }
            override fun close() = readCharacterSource { `in`.close() }
        }
        guarded.use { input -> openTarget().use { output -> input.copyTo(output) } }
        true
    } catch (e: CharacterAttachmentSourceUnavailable) {
        onUnavailable(e.cause as Exception)
        false
    }
}

private class CharacterAttachmentSourceUnavailable(cause: Exception) : IOException(cause)

private inline fun <T> readCharacterSource(read: () -> T): T = try {
    read()
} catch (e: IOException) {
    throw CharacterAttachmentSourceUnavailable(e)
} catch (e: SecurityException) {
    throw CharacterAttachmentSourceUnavailable(e)
}
