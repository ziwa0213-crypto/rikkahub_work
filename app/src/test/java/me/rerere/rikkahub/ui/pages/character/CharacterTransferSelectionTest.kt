package me.rerere.rikkahub.ui.pages.character

import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.RowMemory
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class CharacterTransferSelectionTest {
    private val transfer = TransferMemory(
        scope = "source",
        files = listOf(file("/profile.md"), file("/topics/work.md")),
        rows = listOf(RowMemory(1, "one"), RowMemory(2, "two")),
    )

    @Test
    fun selectAllIncludesFilesAndRowMemories() {
        assertEquals(
            TransferMemorySelection(setOf("/profile.md", "/topics/work.md"), setOf(1, 2)),
            transfer.selectAll(),
        )
    }

    @Test
    fun invertSelectionTogglesEachAvailableFileAndRow() {
        assertEquals(
            TransferMemorySelection(setOf("/topics/work.md"), setOf(2)),
            transfer.invertSelection(TransferMemorySelection(setOf("/profile.md"), setOf(1))),
        )
    }

    @Test
    fun invertFullSelectionClearsIt() {
        assertEquals(
            TransferMemorySelection(emptySet(), emptySet()),
            transfer.invertSelection(transfer.selectAll()),
        )
    }

    @Test
    fun privateMemoryCanMoveButSharedMemoryRemainsProtected() {
        val privateOwner = Assistant()
        val privateSettings = Settings(assistants = listOf(privateOwner))
        assertEquals(true, privateSettings.canMoveMemory(privateOwner.id, privateOwner.id.toString()))

        val groupId = Uuid.random()
        val sharedOwner = Assistant(memoryGroupId = groupId)
        val otherOwner = Assistant(memoryGroupId = groupId)
        val sharedSettings = Settings(assistants = listOf(sharedOwner, otherOwner))
        assertEquals(false, sharedSettings.canMoveMemory(sharedOwner.id, "g:$groupId"))

        val globalOwner = Assistant(useGlobalMemory = true)
        assertEquals(
            false,
            Settings(assistants = listOf(globalOwner))
                .canMoveMemory(globalOwner.id, MemoryRepository.GLOBAL_MEMORY_ID),
        )
    }

    private fun file(path: String) = MemoryFile(path = path, content = "content", updatedAt = 0)
}
