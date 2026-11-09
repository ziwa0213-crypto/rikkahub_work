package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.provider.DeepSeekWebUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebUnlockTest {
    @Test
    fun disabledUnlockKeepsTheReadOnlySet() {
        val allOn = DeepSeekWebUnlock(
            calendarCreate = true,
            calendarDelete = true,
            memoryWrite = true,
            workspaceWrite = true,
            workspaceShell = true,
            evalJavascript = true,
            useSkill = true,
            mcp = true,
            clipboardWrite = true,
        )
        assertEquals(DeepSeekWebToolPolicy.baseAllowedNames, DeepSeekWebToolPolicy.allowedNames())
        assertEquals(DeepSeekWebToolPolicy.baseAllowedNames, DeepSeekWebToolPolicy.allowedNames(allOn))
        for (name in listOf(
            "calendar_create", "calendar_delete", "memory_tool", "workspace_write_file", "workspace_shell",
            "eval_javascript", "use_skill", "mcp__server__tool",
        )) {
            assertFalse("$name must remain locked while the master switch is off", DeepSeekWebToolPolicy.isUnlocked(name, allOn))
        }
        assertFalse(DeepSeekWebToolPolicy.allows("clipboard_tool", buildJsonObject { put("action", "write") }, allOn))
    }

    @Test
    fun workspaceWriteDoesNotUnlockShell() {
        val unlock = DeepSeekWebUnlock(customEnabled = true, workspaceWrite = true)
        val allowed = DeepSeekWebToolPolicy.allowedNames(unlock)
        assertTrue("workspace_write_file" in allowed)
        assertTrue("workspace_edit_file" in allowed)
        assertFalse("workspace_shell" in allowed)
    }

    @Test
    fun eachWriteCapabilityRemainsIndependent() {
        val createOnly = DeepSeekWebUnlock(customEnabled = true, calendarCreate = true)
        assertTrue(DeepSeekWebToolPolicy.allows("calendar_create", buildJsonObject { }, createOnly))
        assertFalse(DeepSeekWebToolPolicy.allows("calendar_delete", buildJsonObject { }, createOnly))

        val memoryOnly = DeepSeekWebUnlock(customEnabled = true, memoryWrite = true)
        for (name in listOf("memory_tool", "memory_write", "memory_append", "memory_str_replace", "memory_delete")) {
            assertTrue("$name", DeepSeekWebToolPolicy.isUnlocked(name, memoryOnly))
        }
        assertFalse(DeepSeekWebToolPolicy.isUnlocked("workspace_write_file", memoryOnly))

        val shellOnly = DeepSeekWebUnlock(customEnabled = true, workspaceShell = true)
        assertTrue(DeepSeekWebToolPolicy.isUnlocked("workspace_shell", shellOnly))
        assertFalse(DeepSeekWebToolPolicy.isUnlocked("workspace_write_file", shellOnly))
    }

    @Test
    fun mcpUsesPrefixMatching() {
        assertFalse(DeepSeekWebToolPolicy.isUnlocked("mcp__server__tool", DeepSeekWebUnlock(customEnabled = true)))
        assertTrue(DeepSeekWebToolPolicy.isUnlocked("mcp__server__tool", DeepSeekWebUnlock(customEnabled = true, mcp = true)))
    }

    @Test
    fun clipboardWriteRequiresItsOwnUnlock() {
        val args = buildJsonObject { put("action", "write") }
        assertFalse(DeepSeekWebToolPolicy.allows("clipboard_tool", args))
        assertTrue(DeepSeekWebToolPolicy.allows(
            "clipboard_tool", args, DeepSeekWebUnlock(customEnabled = true, clipboardWrite = true),
        ))
    }

    @Test
    fun customPromptDoesNotRepeatReadonlyInstructions() {
        val unlock = DeepSeekWebUnlock(customEnabled = true, workspaceWrite = true)
        val prompt = DeepSeekWebTools.prompt(
            messages = emptyList(),
            tools = listOf(me.rerere.ai.core.Tool("workspace_write_file", "write", execute = { emptyList() })),
            model = DeepSeekWebModels.defaults().first(),
            unlock = unlock,
        )
        assertTrue(prompt.contains("workspace_write_file"))
        assertTrue(prompt.contains("自定义工具"))
        assertFalse(prompt.contains("你只能读取工作区里的文件，不能写入或修改任何文件"))
    }
}
