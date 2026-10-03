package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.json

internal object DeepSeekWebTools {
    val allowedNames = setOf(
        "calendar_query", "calendar_create", "calendar_delete", "get_time_info", "get_screen_time",
        "recent_chats", "conversation_search", "memory_tool", "ask_user", "text_to_speech",
        "search_web", "scrape_web", "clipboard_tool",
    )

    const val policy = """
【DeepSeek 网页版使用范围】
这是网页版登录态接入。为保护账号并降低风控风险，只允许聊天、知识问答和查询。
不要要求本机读写文件、执行命令、执行代码、使用技能或调用 MCP；这类请求必须直接说明本供应商不提供该能力。
允许的工具只能按下方 JSON 协议调用，并且每轮最多调用一个工具；需要多个工具时分轮进行。
"""

    fun prompt(messages: List<UIMessage>, tools: List<Tool>, model: Model): String {
        val available = tools.filter { it.name in allowedNames }
        val text = buildString {
            append(policy)
            if (available.isNotEmpty()) {
                append("\n可用工具：\n")
                available.forEach { tool ->
                    append("- ").append(tool.name).append(": ").append(tool.description).append('\n')
                    append("  参数: ").append(tool.parameters()?.let { json.encodeToString(it) } ?: "{}").append('\n')
                }
                append("工具调用格式：单独输出 JSON {")
                append("\"tool\":\"工具名\",\"arguments\":{...}}，不要加解释。\n")
            }
            append("\n对话记录：\n")
            messages.forEach { message ->
                append(message.role.name).append(": ")
                message.parts.forEach { part ->
                    when (part) {
                        is UIMessagePart.Text -> append(part.text)
                        is UIMessagePart.Reasoning -> append(part.reasoning)
                        is UIMessagePart.Tool -> append("工具 ${part.toolName}(${part.input}) => ${part.output.joinToString { output -> (output as? UIMessagePart.Text)?.text.orEmpty() }}")
                        is UIMessagePart.Image -> append("[图片]")
                        is UIMessagePart.Document -> append("[文档]")
                        else -> Unit
                    }
                }
                append('\n')
            }
            append("\n再次提醒：不执行本机项目开发；工具调用每轮最多一个。")
        }
        return text
    }

    fun toolCall(text: String): ToolCall? {
        val candidate = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val element = runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull() ?: return null
        val name = element["tool"]?.jsonPrimitive?.contentOrNull ?: element["name"]?.jsonPrimitive?.contentOrNull ?: return null
        if (name !in allowedNames) return null
        val args = element["arguments"] ?: element["input"] ?: buildJsonObject {}
        return ToolCall(name = name, arguments = args.toString())
    }

    data class ToolCall(val name: String, val arguments: String)
}
