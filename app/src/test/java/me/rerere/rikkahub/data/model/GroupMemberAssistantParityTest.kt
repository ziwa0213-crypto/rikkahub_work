package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.uuid.Uuid

class GroupMemberAssistantParityTest {
    private val intentionallyUnset = setOf(
        "useGlobalMemory", "memoryGroupId", "autoConsolidateMemory", "presetMessages", "quickMessageIds", "regexes", "messageTemplate",
    )
    private val assigned = setOf("id", "name", "avatar", "useAssistantAvatar", "chatModelId", "tags", "streamOutput",
        "systemPrompt", "temperature", "topP", "maxTokens", "contextMessageLimit", "reasoningLevel", "toolApprovalMode",
        "enableMemory", "localTools", "mcpServers", "enabledSkills", "modeInjectionIds", "lorebookIds", "customHeaders",
        "customBodies", "background", "backgroundOpacity", "useGradientBackground", "workspaceId", "enableRecentChatsReference",
        "enableWebSearch", "enableTimeReminder", "timeReminderIntervalMinutes", "allowConversationSystemPrompt", "allowConversationPromptInjection")

    @Test fun `every assistant property has an explicit group policy`() {
        val properties = Assistant::class.memberProperties.map { it.name }.toSet()
        assertEquals(emptySet<String>(), properties - assigned - intentionallyUnset)
        assertEquals(emptySet<String>(), assigned + intentionallyUnset - properties)
    }

    @Test fun `group tool settings are forwarded and legacy json retains defaults`() {
        val group = Group(workspaceId = Uuid.random(), enableTimeReminder = true, timeReminderIntervalMinutes = 12)
        val assistant = GroupMember().toAssistant(group)
        assertEquals(group.workspaceId, assistant.workspaceId)
        assertTrue(assistant.enableWebSearch && assistant.enableRecentChatsReference && assistant.enableTimeReminder)
        assertEquals(12, assistant.timeReminderIntervalMinutes)
        assertNull(assistant.memoryGroupId)
        assertFalse(assistant.allowConversationSystemPrompt || assistant.allowConversationPromptInjection)
        val legacy = JsonInstant.decodeFromString<Group>("{}")
        assertNull(legacy.workspaceId)
        assertTrue(legacy.enableWebSearch && legacy.enableRecentChatsReference)
        assertEquals(group, JsonInstant.decodeFromString<Group>(JsonInstant.encodeToString(group)))
    }

    @Test fun `ownership isolates selectors but retains unowned workspaces`() {
        fun workspace(owner: String?) = WorkspaceEntity(id = "id", name = "name", root = "root", createdAt = 0, updatedAt = 0, owner = owner)
        assertTrue(WorkspaceOwnerScope.CHAT.includes(workspace(null)))
        assertTrue(WorkspaceOwnerScope.GROUP.includes(workspace(null)))
        assertFalse(WorkspaceOwnerScope.CHAT.includes(workspace("group")))
        assertFalse(WorkspaceOwnerScope.GROUP.includes(workspace("chat")))
        val id = Uuid.random()
        val settings = Settings(groups = listOf(Group(workspaceId = id)))
        assertEquals(id, settings.normalizeGroupData().groups.single().workspaceId)
        assertEquals(id, settings.normalizeGroupData(setOf(id.toString())).groups.single().workspaceId)
        assertNull(settings.normalizeGroupData(emptySet()).groups.single().workspaceId)
    }
}
