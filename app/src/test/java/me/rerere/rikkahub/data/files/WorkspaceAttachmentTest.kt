package me.rerere.rikkahub.data.files

import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.mapLocalFileUrls
import org.junit.Assert.*
import org.junit.Test

class WorkspaceAttachmentTest {
    @Test fun workspaceAttachmentHasMetadataOnlyAndIsExcludedFromChatAttachmentBackup() {
        val document = RoutedAttachment.WorkspaceUpload("/uploads/2026-11-18/demo.zip", "demo.zip", 500_000_000, "application/zip").toDocument("workspace-a")
        assertEquals("workspace-a", document.uploadWorkspaceId)
        assertEquals(500_000_000L, document.uploadSize)
        val prompt = unopenedUploadPrompt(document, document.url)
        assertTrue(prompt.contains("<UploadFile name=\"demo.zip\" path=\"/uploads/2026-11-18/demo.zip\" size=\"500000000\">"))
        assertTrue(prompt.contains("/workspace/uploads/2026-11-18/demo.zip"))
        assertTrue(prompt.contains("content has not been expanded"))
        val local = UIMessagePart.Document("file:///data/upload/notes.txt", "notes.txt")
        assertEquals(setOf(local.url), listOf(document, local).localFileUrls())
        var copied = false
        assertEquals(document, document.mapLocalFileUrls { copied = true; "changed" })
        assertFalse(copied)
    }

    @Test fun binaryDocumentsAreNotReadableButExistingParserFormatsRemainSupported() {
        for (mime in listOf("application/zip", "video/mp4", "application/octet-stream", "application/x-executable")) {
            assertFalse(isTextReadable(mime, "unknown.bin"))
        }
        for (mime in listOf("text/plain", "application/json", "application/pdf", "application/epub+zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation")) assertTrue(isTextReadable(mime, "file"))
        assertTrue(isTextReadable("application/octet-stream", "source.kt"))
        val old = UIMessagePart.Document("file:///data/old.zip", "x\"<.zip", "application/zip")
        val prompt = unopenedUploadPrompt(old, null)
        assertTrue(prompt.contains("name=\"x&quot;&lt;.zip\""))
        assertFalse(prompt.contains("Stored in workspace"))
    }
}
