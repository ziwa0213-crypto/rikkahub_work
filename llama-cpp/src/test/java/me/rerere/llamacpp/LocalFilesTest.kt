package me.rerere.llamacpp

import me.rerere.ai.provider.Modality
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File

class LocalFilesTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun `GGUF metadata scanner skips unrelated scalar string and array fields`() {
        val file = files.newFile("model.gguf")
        DataOutputStream(file.outputStream()).use { out ->
            out.writeBytes("GGUF"); out.le32(3); out.le64(0); out.le64(3)
            out.string("general.name"); out.le32(8); out.string("世界 🌍")
            out.string("other.array"); out.le32(9); out.le32(4); out.le64(2); out.le32(7); out.le32(8)
            out.string("general.architecture"); out.le32(8); out.string("future_arch")
        }
        assertTrue(GgufModelInfo.hasMagic(file))
        assertEquals("future_arch", GgufModelInfo.architecture(file))
        assertFalse("future_arch" in GgufModelInfo.verifiedArchitectures)
        assertTrue(PendingLocalImport(file, file.name, "future_arch", false).needsWarning)
        assertFalse(PendingLocalImport(file, "mmproj.gguf", null, true).needsWarning)
    }

    @Test fun `invalid and truncated GGUF never read beyond file boundaries`() {
        val file = files.newFile("broken.gguf")
        file.writeText("nope")
        assertFalse(GgufModelInfo.hasMagic(file))
        assertNull(GgufModelInfo.architecture(file))
        file.writeText("GGUF")
        assertTrue(GgufModelInfo.hasMagic(file))
        assertNull(GgufModelInfo.architecture(file))
    }

    @Test fun `hybrid KV head metadata retains all layers and rejects truncated arrays`() {
        val file = files.newFile("hybrid.gguf")
        DataOutputStream(file.outputStream()).use { out ->
            out.writeBytes("GGUF"); out.le32(3); out.le64(0); out.le64(2)
            out.string("general.architecture"); out.le32(8); out.string("lfm3")
            out.string("lfm3.attention.head_count_kv"); out.le32(9); out.le32(4); out.le64(5)
            listOf(0, 0, 4, 0, 4).forEach { out.le32(it) }
        }
        assertEquals(listOf(0, 0, 4, 0, 4), GgufModelInfo.kvHeads(file))
        java.io.RandomAccessFile(file, "rw").use { it.setLength(it.length() - 1) }
        assertNull(GgufModelInfo.kvHeads(file))
    }

    @Test fun `import names never overwrite originals and strip external paths`() {
        val directory = files.newFolder()
        File(directory, "模型.gguf").writeText("original")
        File(directory, "模型 (2).gguf").writeText("other")
        assertEquals("模型 (3).gguf", LlamaCppInstall.uniqueFile(directory, "../模型.gguf").name)
        assertEquals("original", File(directory, "模型.gguf").readText())
    }

    @Test fun `catalog has five independent models ordered by weight with paired projectors`() {
        val entries = LlamaCppCatalog.ENTRIES
        assertEquals(5, entries.size)
        assertEquals(entries.map { it.files.first().sizeBytes }.sorted(), entries.map { it.files.first().sizeBytes })
        val files = entries.flatMap { it.files }.map { it.file }
        assertEquals(files.size, files.toSet().size)
        assertEquals(3, entries.count { it.files.any { file -> file.role == FileRole.MMPROJ } })
        assertEquals(1, entries.count { Modality.AUDIO in it.modalities })
        assertEquals(listOf(Modality.TEXT), LlamaCppInstall.expectedModalities(entries.last().filename, false))
    }

    private fun DataOutputStream.le32(value: Int) = writeInt(Integer.reverseBytes(value))
    private fun DataOutputStream.le64(value: Long) = writeLong(java.lang.Long.reverseBytes(value))
    private fun DataOutputStream.string(value: String) { val bytes = value.toByteArray(); le64(bytes.size.toLong()); write(bytes) }
}
