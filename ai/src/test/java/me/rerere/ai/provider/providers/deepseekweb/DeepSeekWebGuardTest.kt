package me.rerere.ai.provider.providers.deepseekweb

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import me.rerere.ai.provider.ProviderSetting
import org.junit.Test

class DeepSeekWebGuardTest {
    @Test
    fun blocksRequestsThatNeedLocalExecutionOrPersistence() {
        assertNotNull(DeepSeekWebGuard.refusal("帮我把结果存成 report.txt"))
        assertNull(DeepSeekWebGuard.refusal("读取本地的 config.json"))
        assertNotNull(DeepSeekWebGuard.refusal("把结果写到 report.txt"))
        assertNotNull(DeepSeekWebGuard.refusal("删除 report.txt"))
        assertNotNull(DeepSeekWebGuard.refusal("帮我改一下项目里的 app.py"))
        assertNotNull(DeepSeekWebGuard.refusal("跑一下这段代码"))
        assertNotNull(DeepSeekWebGuard.refusal("帮我做个 App"))
    }

    @Test
    fun allowsCodeQuestionsWithoutExecutionIntent() {
        assertNull(DeepSeekWebGuard.refusal("二分查找怎么写"))
        assertNull(DeepSeekWebGuard.refusal("这个报错什么意思"))
        assertNull(DeepSeekWebGuard.refusal("帮我写个 Python 脚本处理 CSV"))
        assertNull(DeepSeekWebGuard.refusal("帮我设计个项目架构"))
        assertNull(DeepSeekWebGuard.refusal("帮我改写这句话"))
        assertNull(DeepSeekWebGuard.refusal("读一下 /workspace/clock.html"))
        assertNull(DeepSeekWebGuard.refusal("打开那个文件看看"))
        assertNull(DeepSeekWebGuard.refusal("怎么执行 HTML？"))
        assertNull(DeepSeekWebGuard.refusal("read file config.json"))
        assertNull(DeepSeekWebGuard.refusal("Skill 是什么？"))
        assertNull(DeepSeekWebGuard.refusal("如何使用 Skill？"))
        assertNull(DeepSeekWebGuard.refusal("如何新建日历事件？"))
        assertNull(DeepSeekWebGuard.refusal("查询日历，读取剪贴板"))
    }

    @Test
    fun refusesSkillRequestsEveryTimeWithoutCallingTheModel() {
        repeat(5) {
            assertEquals("use_skill", DeepSeekWebGuard.refusal("调用 Skill 搜索 xxx")?.blockedToolName)
        }
        assertNotNull(DeepSeekWebGuard.refusal("帮我写文件到 xx"))
        assertEquals("workspace_write_file", DeepSeekWebGuard.refusal("调用 workspace_write_file")?.blockedToolName)
        assertEquals("mcp__", DeepSeekWebGuard.refusal("调用 MCP 搜索 xxx")?.blockedToolName)
    }

    @Test
    fun rejectsWriteActionsButNotReadActions() {
        assertEquals("calendar_create", DeepSeekWebGuard.refusal("新建一个日历事件")?.blockedToolName)
        assertEquals("calendar_delete", DeepSeekWebGuard.refusal("删除日历事件")?.blockedToolName)
        assertEquals("memory_tool", DeepSeekWebGuard.refusal("修改记忆")?.blockedToolName)
        assertEquals("clipboard_tool", DeepSeekWebGuard.refusal("写入剪贴板")?.blockedToolName)
        assertNull(DeepSeekWebGuard.refusal("读取工作区文件"))
        assertNull(DeepSeekWebGuard.refusal("read file clock.html"))
    }

    @Test
    fun appliesOnlyToDeepSeekWeb() {
        assertNotNull(DeepSeekWebGuard.refusal(ProviderSetting.DeepSeekWeb(), "调用 Skill 搜索 xxx"))
        assertNull(DeepSeekWebGuard.refusal(ProviderSetting.OpenAI(), "调用 Skill 搜索 xxx"))
        assertNull(DeepSeekWebGuard.refusal(null, "帮我写文件到 xx"))
    }

    @Test
    fun knowledgeQuestionDoesNotHideASeparateExecutionRequest() {
        assertNotNull(DeepSeekWebGuard.refusal("怎么执行 HTML？请帮我执行代码"))
    }
}
