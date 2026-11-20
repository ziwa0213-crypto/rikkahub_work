package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.ServerToolMetadata
import me.rerere.ai.ui.ServerToolProtocol
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull

/** Preserve the complete signed call, including fields unknown to this client. */
internal fun JsonObject.toDeepSeekServerTool(stepIndex: Int?): UIMessagePart.ServerTool =
    UIMessagePart.ServerTool(
        toolCallId = this["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        toolName = this["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        input = this["action"],
        output = this,
        status = this["status"]?.jsonPrimitive?.contentOrNull.toServerToolStatus(),
        metadata = ServerToolMetadata(
            protocol = ServerToolProtocol.DEEPSEEK_CHAT_COMPLETIONS,
            call = this,
            callIndex = stepIndex,
        ).toMetadata(),
    )

internal fun JsonArray.deepSeekServerTools(): List<UIMessagePart.ServerTool> = flatMap { element ->
    val step = element.jsonObjectOrNull ?: return@flatMap emptyList()
    val index = step["index"]?.jsonPrimitive?.intOrNull
    step["calls"]?.jsonArrayOrNull?.mapNotNull { call ->
        call.jsonObjectOrNull?.toDeepSeekServerTool(index)?.takeIf { it.toolCallId.isNotBlank() }
    }.orEmpty()
}
