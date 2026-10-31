package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonObject
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebCapabilityRefusalException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolPolicy
import me.rerere.ai.ui.UIMessage

/** Applied both before approval resumption and before AgentLoop executes generated calls. */
internal fun validateDeepSeekWebToolCalls(provider: ProviderSetting, message: UIMessage) {
    if (provider !is ProviderSetting.DeepSeekWeb) return
    DeepSeekWebOutputGuard.sanitize(listOf(message))?.let { result ->
        val match = result.matches.firstOrNull { it.blocked } ?: result.matches.first()
        throw DeepSeekWebPseudoToolException(match.toolName, match.blocked)
    }
    message.getTools().filterNot { it.isExecuted }.forEach { tool ->
        // Test the name before decoding: a forbidden name with malformed arguments is still refused.
        val arguments = tool.inputAsJson() as? JsonObject
        if (tool.toolName !in DeepSeekWebToolPolicy.allowedNames || arguments == null ||
            !DeepSeekWebToolPolicy.allows(tool.toolName, arguments)) {
            throw DeepSeekWebCapabilityRefusalException(blockedToolName = tool.toolName)
        }
    }
}
