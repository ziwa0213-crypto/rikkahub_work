package me.rerere.rikkahub.service

import me.rerere.ai.provider.LocalPromptDrop
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class LocalPromptNoticeStoreTest {
    @Test fun `notices merge without duplicates and conversations stay isolated`() {
        val store = LocalPromptNoticeStore()
        val a = Uuid.random(); val b = Uuid.random()
        store.note(a, listOf(LocalPromptDrop.TOOLS, LocalPromptDrop.TOOLS))
        store.note(a, listOf(LocalPromptDrop.MEMORY))
        store.note(b, listOf(LocalPromptDrop.MEMORY))
        assertEquals(LocalPromptDrop.entries.toSet(), store.state.value[a])
        assertEquals(setOf(LocalPromptDrop.MEMORY), store.state.value[b])
    }

    @Test fun `dismiss survives subsequent steps and another conversation is unaffected`() {
        val store = LocalPromptNoticeStore()
        val a = Uuid.random(); val b = Uuid.random()
        store.note(a, listOf(LocalPromptDrop.TOOLS))
        store.dismiss(a)
        store.note(a, LocalPromptDrop.entries)
        store.note(b, listOf(LocalPromptDrop.TOOLS))
        assertTrue(store.state.value.getValue(a).isEmpty())
        assertEquals(setOf(LocalPromptDrop.TOOLS), store.state.value[b])
        assertTrue(LocalPromptNoticeStore().state.value.isEmpty())
    }
}
