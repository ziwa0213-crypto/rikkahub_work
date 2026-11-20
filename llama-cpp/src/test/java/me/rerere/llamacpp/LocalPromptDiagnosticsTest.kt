package me.rerere.llamacpp

import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.LocalPromptDrop
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.*
import org.junit.Test

class LocalPromptDiagnosticsTest {
    @Test fun `too long counts separate raw components only when requested`() {
        val counted = mutableListOf<String>()
        val tool = Tool("lookup", "schema", execute = { emptyList() })
        val error = promptTooLong(4096, 3072, listOf(UIMessage.system("system-only"), UIMessage.user("latest")),
            TextGenerationParams(Model(), tools = listOf(tool))) { counted += it; it.length }
        assertEquals(LocalModelError.TOO_LONG, error.reason)
        assertEquals(3, counted.size)
        assertEquals("system-only", counted[0])
        assertEquals(ChatRequestMapper.toolsJson(listOf(tool)), counted[1])
        assertTrue(counted[2].contains("latest"))
        assertFalse(counted[2].contains("system-only"))
        assertFalse(counted[2].contains("schema"))
        assertEquals("nCtx=4096 input=3072 system≈11 tools≈${counted[1].length} hist=${counted[2].length}", error.detail)
    }

    @Test fun `failed diagnostic is unknown but cancellation still propagates`() {
        val error = promptTooLong(4096, 3072, listOf(UIMessage.system("rules")), TextGenerationParams(Model())) {
            error("tokenizer failed")
        }
        assertEquals("nCtx=4096 input=3072 system≈-1 tools≈0 hist=0", error.detail)
        val cancelled = runCatching {
            promptTooLong(4096, 3072, listOf(UIMessage.user("hi")), TextGenerationParams(Model())) {
                throw CancellationException("stop")
            }
        }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
    }

    @Test fun `drop notices retain order without duplicate text`() {
        assertEquals(emptyList<String>(), localPromptNotices(emptyList()) { error("no notice") })
        assertEquals(listOf("tools_dropped", "memory_dropped"), localPromptNotices(
            listOf(LocalPromptDrop.TOOLS, LocalPromptDrop.MEMORY, LocalPromptDrop.TOOLS)) { it })
    }

    @Test fun `CPU worker count reserves capacity on eight core devices`() {
        assertEquals(2, localInferenceThreads(1))
        assertEquals(4, localInferenceThreads(4))
        assertEquals(6, localInferenceThreads(8))
        assertEquals(6, localInferenceThreads(16))
    }
}
