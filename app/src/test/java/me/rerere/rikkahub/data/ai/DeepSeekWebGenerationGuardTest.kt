package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.DeepSeekWebUnlock
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebCapabilityRefusalException
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeepSeekWebGenerationGuardTest {
    private val web = ProviderSetting.DeepSeekWeb()

    private fun call(name: String, input: String = "{}", approved: Boolean = false) = UIMessage.assistant("").copy(
        parts = listOf(UIMessagePart.Tool("call-1", name, input,
            approvalState = if (approved) ToolApprovalState.Approved else ToolApprovalState.Auto)),
    )

    @Test fun forbiddenCallsAreRefusedEvenAfterApprovalOrWithInvalidArguments() {
        for (name in listOf("calendar_create", "calendar_delete", "memory_tool", "memory_write",
            "memory_append", "memory_str_replace", "memory_delete", "workspace_shell", "mcp__server__read")) {
            for (approved in listOf(false, true)) {
                val error = assertThrows(DeepSeekWebCapabilityRefusalException::class.java) {
                    validateDeepSeekWebToolCalls(web, call(name, "{bad", approved))
                }
                assertEquals(name, error.blockedToolName)
            }
        }
    }

    @Test fun clipboardReadIsAllowedButWriteMissingOrArrayActionIsRefused() {
        validateDeepSeekWebToolCalls(web, call("clipboard_tool", """{"action":"read"}"""))
        for (input in listOf("""{"action":"write"}""", "{}", "[]", """{"action":[]}""")) {
            assertThrows(DeepSeekWebCapabilityRefusalException::class.java) {
                validateDeepSeekWebToolCalls(web, call("clipboard_tool", input))
            }
        }
    }

    @Test fun readonlyCallsAndOtherProvidersKeepTheirExistingBehavior() {
        validateDeepSeekWebToolCalls(web, call("calendar_query"))
        validateDeepSeekWebToolCalls(web, call("workspace_read_file", """{"path":"readme.txt"}"""))
        validateDeepSeekWebToolCalls(ProviderSetting.OpenAI(), call("workspace_shell"))
    }

    @Test fun forbiddenPseudoCallIsRejectedBeforeExecutingAnyTools() {
        assertThrows(DeepSeekWebPseudoToolException::class.java) {
            validateDeepSeekWebToolCalls(web, UIMessage.assistant("""{"tool":"calendar_delete","arguments":{"id":"1"}}"""))
        }
    }

    @Test fun alreadyExecutedHistoricalToolDoesNotExecuteOrRefuseAgain() {
        val old = call("calendar_delete")
        val tool = old.getTools().single().copy(output = listOf(UIMessagePart.Text("old result")))
        validateDeepSeekWebToolCalls(web, old.copy(parts = listOf(tool)))
    }

    @Test fun localRefusalFollowsTheIndividualUnlocks() {
        val fileWriteOnly = web.copy(unlock = DeepSeekWebUnlock(customEnabled = true, workspaceWrite = true))
        assertEquals(null, me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebGuard.refusal(fileWriteOnly, "帮我写个文件"))
        assertNotNull(me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebGuard.refusal(fileWriteOnly, "帮我跑个脚本"))
    }

    @Test fun executionGuardAllowsOnlyTheIndividuallyUnlockedCapabilities() {
        val fileWriteOnly = web.copy(unlock = DeepSeekWebUnlock(customEnabled = true, workspaceWrite = true))
        validateDeepSeekWebToolCalls(fileWriteOnly, call("workspace_write_file", """{"path":"note.txt","text":"ok"}"""))
        assertThrows(DeepSeekWebCapabilityRefusalException::class.java) {
            validateDeepSeekWebToolCalls(fileWriteOnly, call("workspace_shell", """{"command":"pwd"}"""))
        }

        val clipboardWriteOnly = web.copy(unlock = DeepSeekWebUnlock(customEnabled = true, clipboardWrite = true))
        validateDeepSeekWebToolCalls(clipboardWriteOnly, call("clipboard_tool", """{"action":"write","text":"ok"}"""))
        assertThrows(DeepSeekWebCapabilityRefusalException::class.java) {
            validateDeepSeekWebToolCalls(web, call("clipboard_tool", """{"action":"write","text":"ok"}"""))
        }

        val mcp = web.copy(unlock = DeepSeekWebUnlock(customEnabled = true, mcp = true))
        validateDeepSeekWebToolCalls(mcp, call("mcp__server__read"))
        assertThrows(DeepSeekWebCapabilityRefusalException::class.java) {
            validateDeepSeekWebToolCalls(web, call("mcp__server__read"))
        }
    }

    @Test fun persistedChildFlagsStayInactiveWithoutTheMasterSwitch() {
        val persistedButDisabled = web.copy(unlock = DeepSeekWebUnlock(workspaceWrite = true, mcp = true))
        for (name in listOf("workspace_write_file", "mcp__server__read")) {
            assertThrows(DeepSeekWebCapabilityRefusalException::class.java) {
                validateDeepSeekWebToolCalls(persistedButDisabled, call(name))
            }
        }
    }
}
