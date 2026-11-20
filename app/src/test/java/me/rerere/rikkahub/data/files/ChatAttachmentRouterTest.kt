package me.rerere.rikkahub.data.files

import kotlinx.coroutines.CancellationException
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ChatAttachmentRouterTest {
    @Test fun onlyGoogleAndVideoEnabledLocalModelsReceiveDirectVideo() {
        fun target(provider: ProviderSetting?, model: Model? = Model()) = attachmentTarget("clip.mp4", "video/mp4", provider, model)
        assertEquals(AttachmentTarget.VIDEO, target(ProviderSetting.Google()))
        assertEquals(AttachmentTarget.WORKSPACE, target(ProviderSetting.OpenAI()))
        assertEquals(AttachmentTarget.WORKSPACE, target(ProviderSetting.LlamaCppLocal()))
        assertEquals(AttachmentTarget.VIDEO, target(ProviderSetting.LlamaCppLocal(), Model(inputModalities = listOf(Modality.VIDEO))))
        assertEquals(AttachmentTarget.WORKSPACE, target(null, null)) // Group attachment route.
        assertEquals(AttachmentTarget.WORKSPACE, target(ProviderSetting.Google(), Model(providerOverwrite = ProviderSetting.OpenAI())))
        assertEquals(AttachmentTarget.VIDEO, target(ProviderSetting.OpenAI(), Model(providerOverwrite = ProviderSetting.Google())))
    }

    @Test fun readableAttachmentsRetainChatStorageAndBinaryFilesUseWorkspace() {
        val samples = listOf(
            Triple("photo.png", "image/png", AttachmentTarget.IMAGE),
            Triple("voice.mp3", "audio/mpeg", AttachmentTarget.AUDIO),
            Triple("notes.txt", "text/plain", AttachmentTarget.DOCUMENT),
            Triple("source.kt", "application/octet-stream", AttachmentTarget.DOCUMENT),
            Triple("paper.pdf", "application/pdf", AttachmentTarget.DOCUMENT),
            Triple("book.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", AttachmentTarget.DOCUMENT),
            Triple("archive.zip", "application/zip", AttachmentTarget.WORKSPACE),
            Triple("model.gguf", "application/octet-stream", AttachmentTarget.WORKSPACE),
            Triple("app.apk", "application/vnd.android.package-archive", AttachmentTarget.WORKSPACE),
        )
        samples.forEach { (name, mime, expected) -> assertEquals(name, expected, attachmentTarget(name, mime, null, null)) }
    }

    @Test fun actualBytesEnforceTwoGiBLimitEvenWhenSourceSizeIsUnknown() {
        // Virtual source reports bytes without allocating/writing a multi-GB fixture.
        val source = object : InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int) = len
        }
        var progress = 0L
        val input = UploadInputStream(source, {}) { progress = it }
        val buffer = ByteArray(1024 * 1024)
        repeat(2048) { input.read(buffer) }
        assertEquals(MAX_ATTACHMENT_BYTES, progress)
        assertThrows(AttachmentTooLarge::class.java) { input.read() }
    }

    @Test fun cancellationBeforeReadAndAtEofNeverLooksLikeSuccessfulCompletion() {
        var reads = 0
        val source = object : InputStream() {
            override fun read(): Int { reads++; return -1 }
        }
        var cancelled = true
        val input = UploadInputStream(source, { if (cancelled) throw CancellationException() }) {}
        assertThrows(CancellationException::class.java) { input.read() }
        assertEquals(0, reads)
        cancelled = false
        assertEquals(-1, input.read())
        var checks = 0
        val atEof = UploadInputStream(ByteArrayInputStream(byteArrayOf()), { if (++checks == 2) throw CancellationException() }) {}
        assertThrows(CancellationException::class.java) { atEof.read() }
    }
}
