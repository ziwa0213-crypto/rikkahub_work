package me.rerere.rikkahub.service

import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebCapabilityRefusalException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebGuard
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebImageException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolProtocol
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.DeepSeekWebPseudoToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class DeepSeekWebChatErrorTest {
    private val conversationId = Uuid.random()
    private val getString: (Int, String?) -> String = { resource, tool -> "$resource:${tool.orEmpty()}" }

    @Test
    fun repeatedSkillRequestsCreateDistinctPersistentCardsWithoutLinks() {
        val cards = List(5) {
            val refusal = DeepSeekWebGuard.refusal(ProviderSetting.DeepSeekWeb(), "调用 Skill 搜索 xxx")!!
            deepSeekWebChatError(refusal, conversationId, getString)!!
        }
        assertEquals(5, cards.map { it.id }.toSet().size)
        cards.forEach { card ->
            assertNotNull(card.title)
            assertTrue(card.error.message!!.contains("use_skill"))
            assertEquals(conversationId, card.conversationId)
            assertFalse(card.autoDismiss)
            assertNull(card.solution)
        }
    }

    @Test
    fun distinguishesCapabilityExecutionAndFormatMessages() {
        val errors = listOf(
            DeepSeekWebImageException("Upload HTTP 401") to R.string.chat_error_dsweb_image_message,
            DeepSeekWebCapabilityRefusalException() to R.string.chat_error_dsweb_capability_message,
            DeepSeekWebCapabilityRefusalException(isLocalExecution = true) to R.string.chat_error_dsweb_execute_message,
            DeepSeekWebPseudoToolException("use_skill") to R.string.chat_error_dsweb_pseudo_tool_message,
            DeepSeekWebPseudoToolException("ask_user", blocked = false) to R.string.chat_error_dsweb_tool_format_message,
        )
        errors.forEach { (error, messageResource) ->
            val card = deepSeekWebChatError(error, conversationId, getString)!!
            assertTrue(card.error.message!!.startsWith("$messageResource:"))
            assertFalse(card.autoDismiss)
            assertNull(card.solution)
        }
    }

    @Test
    fun ordinaryErrorsKeepDefaultDismissalAndFastModelSolution() {
        val error = IllegalStateException("Missing fast model")
        assertNull(deepSeekWebChatError(error, conversationId, getString))
        val card = ChatError(error = error, solution = ChatErrorSolution.CheckFastModelSettings)
        assertTrue(card.autoDismiss)
        assertEquals(ChatErrorSolution.CheckFastModelSettings, card.solution)
    }

    @Test
    fun parsedForbiddenCallsProducePersistentCapabilityCardsWithoutLinks() {
        listOf("calendar_create", "calendar_delete", "use_skill", "mcp__server_delete", "workspace_write_file").forEach { name ->
            val result = DeepSeekWebToolProtocol.parse("before```json\n{\"tool\":\"$name\",\"arguments\":{}}\n```after")
            assertTrue(result.result is DeepSeekWebToolProtocol.Result.Blocked)
            val card = deepSeekWebChatError(DeepSeekWebCapabilityRefusalException(name), conversationId, getString)!!
            assertTrue(card.error.message!!.startsWith("${R.string.chat_error_dsweb_tool_blocked_message}:"))
            assertTrue(card.error.message!!.contains(name))
            assertFalse(card.autoDismiss)
            assertNull(card.solution)
        }
    }
}
