package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.model.ToolApprovalMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRiskTiersTest {
    @Test fun alarmUsesExistingCreateTierAndApprovalModes() {
        val args = buildJsonObject {}
        org.junit.Assert.assertEquals(ToolTier.L2_CREATE, tierOf("set_alarm", args))
        assertTrue(applyApprovalMode(listOf(tool("set_alarm")), ToolApprovalMode.AskAll).single().needsApproval(args))
        assertFalse(applyApprovalMode(listOf(tool("set_alarm")), ToolApprovalMode.Partial).single().needsApproval(args))
        assertFalse(applyApprovalMode(listOf(tool("set_alarm")), ToolApprovalMode.AllowAll).single().needsApproval(args))
    }
    @Test fun smsAlwaysNeedsExplicitApprovalAndReadToolsRemainReadOnly() {
        ToolApprovalMode.entries.forEach { mode ->
            assertTrue(applyApprovalMode(listOf(tool("send_sms")), mode).single().needsApproval(buildJsonObject {}))
        }
        assertFalse(applyApprovalMode(listOf(tool("read_sms"), tool("read_notifications")), ToolApprovalMode.Partial)
            .any { it.needsApproval(buildJsonObject {}) })
    }
    @Test
    fun `partial mode keeps original approval and applies risk tiers`() {
        val tools = applyApprovalMode(
            listOf(
                tool("calendar_query"),
                tool("calendar_create"),
                tool("calendar_delete"),
                tool("original_gate", needsApproval = true),
            ),
            ToolApprovalMode.Partial,
        )

        assertFalse(tools[0].needsApproval(buildJsonObject {}))
        assertFalse(tools[1].needsApproval(buildJsonObject {}))
        assertTrue(tools[2].needsApproval(buildJsonObject {}))
        assertTrue(tools[3].needsApproval(buildJsonObject {}))
    }

    @Test
    fun `parameter-sensitive tools use conservative action defaults`() {
        val tools = applyApprovalMode(
            listOf(tool("clipboard_tool"), tool("memory_tool")),
            ToolApprovalMode.Partial,
        )

        assertFalse(tools[0].needsApproval(buildJsonObject { put("action", "read") }))
        assertTrue(tools[0].needsApproval(buildJsonObject { put("action", "write") }))
        assertFalse(tools[1].needsApproval(buildJsonObject { put("action", "create") }))
        assertTrue(tools[1].needsApproval(buildJsonObject { put("action", "delete") }))
        assertTrue(tools[1].needsApproval(buildJsonObject {}))
    }

    @Test
    fun `mcp and workspace writes remain gated but workspace reads are allowed`() {
        val tools = applyApprovalMode(
            listOf(tool("mcp__server__tool"), tool("workspace_write_file"), tool("workspace_edit_file"), tool("workspace_shell"), tool("workspace_shell_kill")),
            ToolApprovalMode.Partial,
        )

        assertTrue(tools.all { it.needsApproval(buildJsonObject {}) })
        val reads = applyApprovalMode(listOf(tool("workspace_read_file"), tool("workspace_shell_output")), ToolApprovalMode.Partial)
        assertTrue(reads.none { it.needsApproval(buildJsonObject {}) })
        val outside = applyApprovalMode(listOf(tool("workspace_write_file", needsApproval = true)), ToolApprovalMode.Partial).single()
        assertTrue(outside.needsApproval(buildJsonObject {}))
    }

    @Test
    fun `ask all and allow all override tool decisions`() {
        val original = tool("calendar_query", needsApproval = true)

        val askAll = applyApprovalMode(listOf(original), ToolApprovalMode.AskAll).single()
        val allowAll = applyApprovalMode(listOf(original), ToolApprovalMode.AllowAll).single()

        assertTrue(askAll.needsApproval(buildJsonObject {}))
        assertFalse(allowAll.needsApproval(buildJsonObject {}))
    }

    @Test
    fun `ask user remains gated in every approval mode`() {
        val original = tool("ask_user")

        val askAll = applyApprovalMode(listOf(original), ToolApprovalMode.AskAll).single()
        val partial = applyApprovalMode(listOf(original), ToolApprovalMode.Partial).single()
        val allowAll = applyApprovalMode(listOf(original), ToolApprovalMode.AllowAll).single()

        assertTrue(askAll.needsApproval(buildJsonObject {}))
        assertTrue(partial.needsApproval(buildJsonObject {}))
        assertTrue(allowAll.needsApproval(buildJsonObject {}))
    }

    private fun tool(name: String, needsApproval: Boolean = false) = Tool(
        name = name,
        description = name,
        needsApproval = { needsApproval },
        execute = { emptyList() },
    )
}
