package me.rerere.rikkahub.ui.pages.character

import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.RowMemory
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.GroupMember
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

    @Test
    fun assistantMoveReasonsPreserveBothRestrictions() {
        val member = GroupMember()
        val settings = Settings(groupMembers = listOf(member, GroupMember(id = DEFAULT_ASSISTANT_ID)))

        assertEquals(AssistantMoveBlock.DEFAULT_ASSISTANT, settings.assistantMoveBlock(DEFAULT_ASSISTANT_ID))
        assertEquals(AssistantMoveBlock.GROUP_MEMBER, settings.assistantMoveBlock(member.id))
        assertEquals(AssistantMoveBlock.NONE, settings.assistantMoveBlock(Uuid.random()))
        assertEquals(false, settings.canMoveToCharacter(DEFAULT_ASSISTANT_ID))
        assertEquals(false, settings.canMoveToCharacter(member.id))
        assertEquals(true, settings.canMoveToCharacter(Uuid.random()))
    }

    @Test
    fun globalMemoryExplainsTheGlobalRestrictionEvenWhenAlsoShared() {
        val owner = Assistant(useGlobalMemory = true)
        val settings = Settings(
            assistants = listOf(owner, Assistant(useGlobalMemory = true)),
            characters = listOf(Character(referencedMemoryScopes = setOf(MemoryRepository.GLOBAL_MEMORY_ID))),
        )
        assertEquals(MemoryMoveBlock.GLOBAL, settings.memoryMoveBlock(owner.id, MemoryRepository.GLOBAL_MEMORY_ID))
        assertEquals(false, settings.canMoveMemory(owner.id, MemoryRepository.GLOBAL_MEMORY_ID))
    }

    @Test
    fun sharedAssistantMemoryExplainsTheSharingRestriction() {
        val groupId = Uuid.random()
        val owner = Assistant(memoryGroupId = groupId)
        val settings = Settings(
            assistants = listOf(owner, Assistant(memoryGroupId = groupId)),
            characters = listOf(Character(referencedMemoryScopes = setOf("g:$groupId"))),
        )
        assertEquals(MemoryMoveBlock.SHARED_WITH_ASSISTANT, settings.memoryMoveBlock(owner.id, "g:$groupId"))
        assertEquals(false, settings.canMoveMemory(owner.id, "g:$groupId"))
    }

    @Test
    fun characterReferenceAloneStillBlocksMovingMemory() {
        val owner = Assistant()
        val scope = owner.id.toString()
        val settings = Settings(assistants = listOf(owner), characters = listOf(Character(referencedMemoryScopes = setOf(scope))))
        assertEquals(MemoryMoveBlock.REFERENCED_BY_CHARACTER, settings.memoryMoveBlock(owner.id, scope))
        assertEquals(false, settings.canMoveMemory(owner.id, scope))
    }

    @Test
    fun privateMemoryStillAllowsMove() {
        val owner = Assistant()
        val settings = Settings(assistants = listOf(owner))
        assertEquals(MemoryMoveBlock.NONE, settings.memoryMoveBlock(owner.id, owner.id.toString()))
        assertEquals(true, settings.canMoveMemory(owner.id, owner.id.toString()))
    }

    private fun file(path: String) = MemoryFile(path = path, content = "content", updatedAt = 0)
}
