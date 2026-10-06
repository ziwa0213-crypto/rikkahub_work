package me.rerere.ai.provider.providers.deepseekweb

import me.rerere.ai.provider.ProviderSetting

class DeepSeekWebCapabilityRefusalException(
    val blockedToolName: String? = null,
    val isLocalExecution: Boolean = false,
) : IllegalStateException(
    blockedToolName?.let { "DeepSeek Web blocked unsupported tool: $it" }
        ?: "DeepSeek Web does not provide local project capabilities"
)

/** Local, conservative refusal for requests that ask the app to modify or execute a project. */
object DeepSeekWebGuard {
    private val strongPhrases = listOf(
        "修改项目", "改项目", "写入文件", "写文件", "寫檔案", "保存到文件", "创建文件", "删除文件",
        "做个小程序",
        "帮我做项目", "修改代码文件", "提交代码", "push 到", "上传源码", "帮我部署",
        "write file",
        "edit file", "modify project", "build project", "create a file", "save to file"
    )

    private val actionWithTarget = listOf(
        Regex("(?:改|修改|编辑|修复).{0,32}(?:项目|文件|脚本|目录|文件夹|代码文件|\\.(?:py|kt|java|js|ts|rs|go|html|css)\\b)"),
        Regex("(?:保存|存成|存为|写入|写到|写文件|写入文件|导出).{0,40}(?:文件|目录|文件夹|/[^\\s]+|\\.[a-z0-9]{1,8}\\b)"),
        Regex("(?:删除|移除).{0,24}(?:文件|目录|文件夹|项目|代码|\\.(?:txt|csv|json|md|py|kt|java|js|ts|rs|go|html|css)\\b)"),
        Regex("(?:开发|创建|新建|做).{0,12}(?:一个|个)?\\s*(?:app|应用|小程序|项目)"),
    )

    private val executionRequest = Regex(
        "(?:运行|執行|执行|跑(?:一下|一遍)?|启动|编译|构建|打包|部署|安装|\\b(?:run|execute|build|compile)\\b).{0,28}" +
            "(?:代码|程式碼|命令|脚本|项目|程序|应用|服务器|文件|app|gradle|npm|测试|\\b(?:code|command|script|html|py)\\b)"
    )
    private val toolRequests = listOf(
        "use_skill" to Regex("(?:调用|調用|呼叫|使用|用|执行|執行|\\b(?:call|invoke|use|run)\\b).{0,24}(?:\\bskills?\\b|技能|use_skill)"),
        "mcp__" to Regex("(?:调用|調用|呼叫|使用|用|执行|執行|\\b(?:call|invoke|use|run)\\b).{0,24}(?:\\bmcp\\b|mcp__)"),
        "calendar_delete" to Regex("(?:删除|刪除|移除|取消|\\b(?:delete|remove|cancel)\\b).{0,24}(?:日历|日曆|日程|事件|\\b(?:calendar|event)\\b)"),
        "calendar_create" to Regex("(?:新建|创建|建立|新增|添加|加.{0,3}个|\\b(?:create|add|schedule)\\b).{0,24}(?:日历|日曆|日程|事件|\\b(?:calendar|event)\\b)"),
        "memory_tool" to Regex("(?:创建|新增|添加|修改|编辑|編輯|删除|刪除|记住|記住|\\b(?:create|add|edit|delete|update)\\b).{0,24}(?:记忆|記憶|\\bmemor(?:y|ies)\\b)"),
        "clipboard_tool" to Regex("(?:写入|寫入|写到|寫到|复制|複製|\\b(?:write|copy|set)\\b).{0,24}(?:剪贴板|剪貼簿|\\bclipboard\\b)"),
    )
    private val namedToolRequest = Regex(
        "(?:调用|調用|呼叫|使用|执行|執行|\\b(?:call|invoke|use|execute)\\b)\\s*" +
            "(use_skill|workspace_write_file|workspace_edit_file|workspace_shell|eval_javascript|calendar_create|calendar_delete|memory_tool|mcp__[a-z0-9_]+)\\b"
    )
    private val knowledgeQuestion = Regex(
        "(?:怎么|怎麼|如何|怎样|怎樣|什么是|什麼是|是什么意思|是什麼意思|\\bhow (?:to|do i|can i)\\b|\\bwhat is\\b)"
    )

    fun refusal(provider: ProviderSetting?, text: String): DeepSeekWebCapabilityRefusalException? =
        if (provider is ProviderSetting.DeepSeekWeb) refusal(text) else null

    internal fun refusal(text: String): DeepSeekWebCapabilityRefusalException? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null
        // A knowledge question cannot exempt a separate request to change local state.
        normalized.split(Regex("[。！？!?;；\\n]")).forEach { clause ->
            if (knowledgeQuestion.containsMatchIn(clause)) return@forEach
            namedToolRequest.find(clause)?.let {
                return DeepSeekWebCapabilityRefusalException(blockedToolName = it.groupValues[1])
            }
            toolRequests.firstOrNull { (_, pattern) -> pattern.containsMatchIn(clause) }?.let { (tool, _) ->
                return DeepSeekWebCapabilityRefusalException(blockedToolName = tool)
            }
            if (executionRequest.containsMatchIn(clause)) {
                return DeepSeekWebCapabilityRefusalException(isLocalExecution = true)
            }
            if (strongPhrases.any(clause::contains) || actionWithTarget.any { it.containsMatchIn(clause) }) {
                return DeepSeekWebCapabilityRefusalException()
            }
        }
        return null
    }
}
