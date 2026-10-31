package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class GroupMemoryCompatibilityTest {
    private val repository = MemoryRepository(FakeMemoryDAO(), FakeMemoryEntityDAO())
    private val groupId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val legacyScope = MemoryRepository.scopeOfChatGroup(groupId)
    private val conversationScope = MemoryRepository.scopeOfChatGroup(groupId, conversationId)

    @Test
    fun `group scopes match generation and remain separate from memory groups`() {
        assertEquals("grp:$groupId", legacyScope)
        assertEquals("grp:$groupId:$conversationId", conversationScope)
        assertEquals("g:$groupId", MemoryRepository.scopeOf(groupId))
        assertEquals(Assistant(memoryGroupId = groupId).memoryId, MemoryRepository.scopeOf(groupId))
    }

    @Test
    fun `moving a whole legacy scope transfers every row and preserves unrelated data`() = runBlocking {
        repository.addRowMemory(legacyScope, "Synthetic first legacy row")
        repository.addRowMemory(legacyScope, "Synthetic second legacy row")
        val existing = repository.addRowMemory(conversationScope, "Synthetic existing row")
        val other = repository.addRowMemory("other", "Synthetic unrelated row")
        repository.writeFile(legacyScope, "/profile.md", "Synthetic legacy file")

        repository.moveAllRowMemories(legacyScope, conversationScope)

        assertTrue(repository.getRowMemories(legacyScope).isEmpty())
        assertEquals(listOf(existing.content, "Synthetic first legacy row", "Synthetic second legacy row"),
            repository.getRowMemories(conversationScope).map { it.content })
        assertEquals(listOf(other), repository.getRowMemories("other"))
        assertTrue(repository.getFiles(legacyScope).isEmpty())
        assertEquals("Synthetic legacy file", repository.getFile(conversationScope, "/profile.md")?.content)
        repository.moveAllRowMemories(legacyScope, conversationScope)
        assertEquals(3, repository.getRowMemories(conversationScope).size)
    }

    @Test
    fun `moving an empty scope or the same scope is a no op`() = runBlocking {
        val row = repository.addRowMemory(legacyScope, "Synthetic retained row")
        repository.moveAllRowMemories("empty", conversationScope)
        repository.moveAllRowMemories(legacyScope, legacyScope)
        assertEquals(listOf(row), repository.getRowMemories(legacyScope))
        assertTrue(repository.getRowMemories(conversationScope).isEmpty())
    }

    @Test
    fun `copying and deleting both stores keeps original scopes intact`() = runBlocking {
        val original = repository.addRowMemory(legacyScope, "Synthetic source row")
        repository.writeFile(legacyScope, "/profile.md", "Synthetic source file")

        repository.copyRowMemories(legacyScope, conversationScope)
        repository.copyFiles(legacyScope, conversationScope)

        assertEquals(listOf(original.content), repository.getRowMemories(conversationScope).map { it.content })
        assertEquals("Synthetic source file", repository.getFile(conversationScope, "/profile.md")?.content)
        repository.deleteAllRowMemories(conversationScope)
        repository.deleteFilesOfAssistant(conversationScope)
        assertTrue(repository.getRowMemories(conversationScope).isEmpty())
        assertTrue(repository.getFiles(conversationScope).isEmpty())
        assertEquals(listOf(original), repository.getRowMemories(legacyScope))
        assertEquals("Synthetic source file", repository.getFile(legacyScope, "/profile.md")?.content)
    }
}
