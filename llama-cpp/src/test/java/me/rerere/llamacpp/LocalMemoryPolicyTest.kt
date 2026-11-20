package me.rerere.llamacpp

import org.junit.Assert.*
import org.junit.Test

class LocalMemoryPolicyTest {
    @Test fun `gate uses physical total and warns without throwing`() {
        assertEquals(MemoryGate.OK, LocalMemoryGate.verdict(16L shl 30, 8).gate)
        assertEquals(MemoryGate.WARN, LocalMemoryGate.verdict(8L shl 30, 12).gate)
    }
    @Test fun `fusion is opt in bounded and handles no swap`() {
        assertEquals(0L, LocalMemoryGate.fusionCushionBytes(false, 20L shl 30))
        assertEquals(1L shl 30, LocalMemoryGate.fusionCushionBytes(true, 20L shl 30))
        assertEquals(512L shl 20, LocalMemoryGate.fusionCushionBytes(true, 4L shl 30))
        assertEquals(0L, LocalMemoryGate.fusionCushionBytes(true, 0))
        assertEquals(2048L, LocalMemoryGate.procBytes(sequenceOf("Other: 4 kB", "SwapTotal:\t  2 kB"), "SwapTotal"))
        assertEquals(0L, LocalMemoryGate.procBytes(sequenceOf("SwapTotal: denied"), "SwapTotal"))
    }
    @Test fun `space preflight fails before transfer with useful diagnostics`() {
        val error = runCatching { LlamaCppInstall.requireDownloadSpace(3100L shl 20, 900L shl 20) }.exceptionOrNull() as LocalModelException
        assertEquals(LocalModelError.NO_SPACE, error.reason)
        assertEquals("need 3100 MB, free 900 MB", error.detail)
        LlamaCppInstall.requireDownloadSpace(900L shl 20, 900L shl 20)
    }
}
