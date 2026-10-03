package me.rerere.ai.provider.providers.deepseekweb

/** Local, conservative refusal for requests that ask the app to modify or execute a project. */
internal object DeepSeekWebGuard {
    private val actionWords = listOf(
        "帮我改", "帮我修改", "修改项目", "改项目", "写入文件", "保存到文件", "创建文件",
        "删除文件", "运行代码", "执行代码", "执行命令", "跑一下", "编译项目", "打包项目",
        "开发一个", "做个小程序", "帮我做项目", "帮我开发", "修改代码文件", "提交代码",
        "push 到", "上传源码", "帮我部署", "read file", "write file", "run command",
        "execute code", "edit file", "build project", "create a file"
    )

    fun refusal(text: String): String? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank() || actionWords.none(normalized::contains)) return null
        return "这个请求需要在本机读写文件、执行命令或持续开发项目。为保护你的 DeepSeek 网页账号，本供应商只提供聊天、知识问答和查询，不执行本机项目操作。"
    }
}
