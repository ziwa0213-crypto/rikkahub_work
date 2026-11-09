package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonObject
import me.rerere.ai.provider.DeepSeekWebUnlock
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebCapabilityRefusalException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolPolicy
import me.rerere.ai.ui.UIMessage

/** Applied both before approval resumption and before AgentLoop executes generated calls. */
internal fun validateDeepSeekWebToolCalls(provider: ProviderSetting, message: UIMessage) {
    if (provider !is ProviderSetting.DeepSeekWeb) return
    val unlock = provider.unlock
    DeepSeekWebOutputGuard.sanitize(listOf(message), unlock)?.let { result ->
        val match = result.matches.firstOrNull { it.blocked } ?: result.matches.first()
        throw DeepSeekWebPseudoToolException(match.toolName, match.blocked)
    }
    message.getTools().filterNot { it.isExecuted }.forEach { tool ->
        // Test the name before decoding: a forbidden name with malformed arguments is still refused.
        val arguments = tool.inputAsJson() as? JsonObject
        val allowedNames = DeepSeekWebToolPolicy.allowedNames(unlock)
        if ((tool.toolName !in allowedNames &&
                !(unlock.active.mcp && tool.toolName.startsWith("mcp__", ignoreCase = true))) || arguments == null ||
            !DeepSeekWebToolPolicy.allows(tool.toolName, arguments, unlock)) {
            throw DeepSeekWebCapabilityRefusalException(blockedToolName = tool.toolName)
        }
    }
}
