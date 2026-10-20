package me.rerere.rikkahub.browser

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryGroup
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.ui.chatMemorySources
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class MemoryImportSourcesTest {
    @Test fun sharedBucketsAppearOnceAndEmptyBucketsAreHidden() {
        val group = MemoryGroup(id = Uuid.random(), name = "group")
        val private = Assistant(name = "private")
        val assistants = listOf(Assistant(useGlobalMemory = true), Assistant(useGlobalMemory = true),
            Assistant(memoryGroupId = group.id), Assistant(memoryGroupId = group.id), private, Assistant(name = "empty"))
        val sources = chatMemorySources(assistants, listOf(group), mapOf(
            MemoryRepository.GLOBAL_MEMORY_ID to 3, MemoryRepository.scopeOf(group.id) to 2,
            private.id.toString() to 1), "global", "groups", "assistants")
        assertEquals(3, sources.size)
        assertEquals(listOf("global", "group", "private"), sources.map { it.label })
        assertEquals(listOf(3, 2, 1), sources.map { it.count })
        assertEquals(3, sources.map { it.scope }.distinct().size)
    }
    @Test fun globalAndUnassignedMemoryGroupsRemainAvailable() {
        val group = MemoryGroup(id = Uuid.random(), name = "unused")
        val sources = chatMemorySources(emptyList(), listOf(group), mapOf(
            MemoryRepository.GLOBAL_MEMORY_ID to 1, MemoryRepository.scopeOf(group.id) to 4), "global", "groups", "assistants")
        assertEquals(setOf("global", "unused"), sources.map { it.label }.toSet())
    }
    @Test fun groupConversationScopeDoesNotUseStagingBucket() {
        val group = Uuid.random()
        val conversation = Uuid.random()
        assertEquals("grp:$group:$conversation", MemoryRepository.scopeOfChatGroup(group, conversation))
        assertNotEquals(MemoryRepository.scopeOfChatGroup(group), MemoryRepository.scopeOfChatGroup(group, conversation))
    }
}
