package me.rerere.ai.provider.providers.deepseekweb

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.json

internal object DeepSeekWebTools {
    val allowedNames = DeepSeekWebToolPolicy.allowedNames

    const val policy = """
【DeepSeek 网页版使用范围】
这是网页版登录态接入。为保护账号并降低风控风险，只允许聊天、知识问答、查询和读取工作区文件。
只有下面列出的工具可用。不要调用未列出的工具，也不要输出未列出工具的 JSON 或 XML 工具调用格式。
你只能读取工作区里的文件，不能写入或修改任何文件。不要声称“已保存”“已写入”“已创建”或“已生成文件”，也不要给出你并未创建的路径。
你不能新建或删除日历事件，不能修改记忆，不能写入剪贴板；你只能查询和阅读。需要用户自己执行这些操作时，请明确告诉用户操作方法，不要假装已经完成。
需要交付代码或文件内容时，直接把内容贴在回复里；需要了解工作区内容时，使用可用的读取工具。
允许的工具只能按下方 JSON 协议调用，并且每轮最多调用一个工具；需要多个工具时分轮进行。
"""

    private const val callFormat = """
调用格式（严格照此输出，不要加代码围栏、不要在调用前后添加解释性文字）：

{"tool": "<工具名>", "arguments": {<参数>}}

示例（仅可调用下方清单中已启用的工具）：
{"tool": "get_time_info", "arguments": {}}
{"tool": "calendar_query", "arguments": {"range": "today"}}
{"tool": "calendar_query", "arguments": {"range": "week", "limit": 5}}

系统收到上述格式后会执行该工具并把结果返回给你，然后你再继续回答用户。
"""

    fun prompt(messages: List<UIMessage>, tools: List<Tool>, model: Model, imageReferences: Map<String, Int> = emptyMap()): String {
        val available = tools.filter { it.name in allowedNames }
        val text = buildString {
            append(policy)
            if (available.isNotEmpty()) {
                append(callFormat)
                append("\n可用工具：\n")
                available.forEach { tool ->
                    append("- ").append(tool.name).append(": ")
                    if (tool.name == "clipboard_tool") {
                        append("Read plain text from the device clipboard. Only action=read is available.\n")
                    } else {
                        append(tool.description).append('\n')
                    }
                    append("  参数: ").append(toolParameters(tool)).append('\n')
                }
            }
            append("\n对话记录：\n")
            messages.forEach { message ->
                append(message.role.name).append(": ")
                val messageText = buildString {
                    message.parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Text -> append(part.text)
                            is UIMessagePart.Reasoning -> append(part.reasoning)
                            is UIMessagePart.Tool -> {
                                append(historyToolDescription(part.toolName))
                                if (part.isExecuted && DeepSeekWebToolPolicy.allows(part.toolName,
                                        part.inputAsJson() as? JsonObject ?: JsonObject(emptyMap()))) {
                                    append("\n工具返回结果：\n")
                                    part.output.forEach { output ->
                                        when (output) {
                                            is UIMessagePart.Text -> append(output.text).append('\n')
                                            is UIMessagePart.Image -> append(imageReferences[output.url]?.let {
                                                "[Image attachment $it]"
                                            } ?: "[Image not uploaded]").append('\n')
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                            is UIMessagePart.Image -> append(imageReferences[part.url]?.let { "[Image attachment $it]" } ?: "[Image not uploaded]")
                            is UIMessagePart.Document -> append("[文档]")
                            else -> Unit
                        }
                    }
                }
                append(if (message.role == MessageRole.SYSTEM) sanitizeSystemPrompt(messageText) else messageText)
                append('\n')
            }
            append('\n').append(policy.trimIndent())
            append("\n再次提醒：只能读取工作区文件，不能写入或修改；不能新建或删除日历事件、修改记忆或写入剪贴板；工具调用每轮最多一个。")
        }
        return text
    }

    private fun historyToolDescription(toolName: String): String = if (toolName in allowedNames) {
        "（历史）助手调用了 $toolName 工具"
    } else {
        "（历史）助手调用了一个当前网页版不提供的工具"
    }

    private fun sanitizeSystemPrompt(text: String): String = text.lineSequence()
        .filterNot { line ->
            blockedToolNames.any(line::contains) ||
                line.contains("mcp__", ignoreCase = true) ||
                line.contains("MCP", ignoreCase = true) ||
                line.contains("skills directory", ignoreCase = true) ||
                line.contains("built-in skills", ignoreCase = true) ||
                line.contains("files written there persist", ignoreCase = true)
        }
        .joinToString("\n")

    private val blockedToolNames = setOf(
        "calendar_create", "calendar_delete", "memory_tool", "workspace_write_file", "workspace_edit_file",
        "workspace_shell", "eval_javascript", "use_skill",
    )

    private fun toolParameters(tool: Tool): String = if (tool.name == "clipboard_tool") {
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("read") })
                })
            })
            put("required", buildJsonArray { add("action") })
        }.toString()
    } else {
        tool.parameters()?.let { json.encodeToString(it) } ?: "{}"
    }

}
