package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.ai.tools.applyApprovalMode
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupToolApprovalTest {
    @Test
    fun legacyGroupDefaultsToPartialApproval() {
        val legacy = JsonInstant.decodeFromString<Group>("""{"name":"legacy"}""")
        assertEquals(ToolApprovalMode.Partial, legacy.toolApprovalMode)
        val settings = JsonInstant.decodeFromString<Settings>("""{"groups":[{"name":"legacy"}]}""")
        assertEquals(ToolApprovalMode.Partial, settings.normalizeGroupData().groups.single().toolApprovalMode)
    }

    @Test
    fun allApprovalModesPersistAndReachEveryMember() {
        val members = listOf(GroupMember(name = "A"), GroupMember(name = "B"))
        ToolApprovalMode.entries.forEach { mode ->
            val group = Group(toolApprovalMode = mode,
                members = members.mapIndexed { index, member -> GroupMemberRef(member.id, priority = index) })
            val settings = Settings(groups = listOf(group), groupMembers = members)
            val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings))
                .normalizeGroupData()
            assertEquals(group, restored.groups.single())
            restored.groups.single().enabledMembers(restored).forEach { member ->
                assertEquals(mode, member.toAssistant(restored.groups.single()).toolApprovalMode)
            }
        }
    }

    @Test
    fun changingOneGroupPreservesOtherGroupsAndChatAssistant() {
        val chatAssistant = Assistant(toolApprovalMode = ToolApprovalMode.AllowAll)
        val first = Group(toolApprovalMode = ToolApprovalMode.Partial)
        val second = Group(toolApprovalMode = ToolApprovalMode.AskAll)
        val before = Settings(assistants = listOf(chatAssistant), groups = listOf(first, second))
        val after = before.copy(groups = before.groups.map {
            if (it.id == first.id) it.copy(toolApprovalMode = ToolApprovalMode.AllowAll) else it
        }).normalizeGroupData()
        assertEquals(ToolApprovalMode.AllowAll, after.groups.first().toolApprovalMode)
        assertEquals(second, after.groups.last())
        assertEquals(before.assistants, after.assistants)
        assertEquals(first.toolApprovalMode, before.groups.first().toolApprovalMode)
        val chatChanged = after.copy(assistants = listOf(chatAssistant.copy(toolApprovalMode = ToolApprovalMode.AskAll)))
        assertEquals(after.groups, chatChanged.normalizeGroupData().groups)
        assertEquals(ToolApprovalMode.Partial, GroupMember().toAssistant(Group()).toolApprovalMode)
    }

    @Test
    fun groupModeUsesExistingApprovalPolicyAndKeepsHitlGated() {
        val tools = listOf(tool("calendar_query"), tool("calendar_delete"),
            tool("unknown_tool"), tool("get_time_info", originalApproval = true), tool("ask_user"))
        val arguments = buildJsonObject {}
        ToolApprovalMode.entries.forEach { mode ->
            val assistant = GroupMember().toAssistant(Group(toolApprovalMode = mode))
            val gated = applyApprovalMode(tools, assistant.toolApprovalMode)
            assertTrue(gated.last().needsApproval(arguments))
            when (mode) {
                ToolApprovalMode.AskAll -> assertTrue(gated.all { it.needsApproval(arguments) })
                ToolApprovalMode.Partial -> {
                    assertFalse(gated.first().needsApproval(arguments))
                    assertTrue(gated.drop(1).all { it.needsApproval(arguments) })
                }
                ToolApprovalMode.AllowAll -> assertTrue(gated.dropLast(1).none { it.needsApproval(arguments) })
            }
        }
    }

    private fun tool(name: String, originalApproval: Boolean = false) = Tool(
        name = name,
        description = name,
        needsApproval = { originalApproval },
        execute = { emptyList() },
    )
}
