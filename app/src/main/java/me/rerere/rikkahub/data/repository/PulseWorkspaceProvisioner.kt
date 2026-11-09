package me.rerere.rikkahub.data.repository

/** Adds missing Pulse workspace guidance without replacing files edited by the user. */
internal object PulseWorkspaceProvisioner {
    val templates: Map<String, String> = linkedMapOf(
        "README.md" to """# 脉搏工作区

这是脉搏专属的文件与命令工作区。用户维护的文件优先；补充模板不会覆盖已有文件。

- `IDENTITY.md`：脉搏的身份、表达方式与边界
- `USER.md`：用户主动提供的偏好
- `TODO.md`：待办与承诺
- `JOURNAL.md`：按日期记录的重要进展
- `TOOLS.md`：工具能力和使用约定
""".trimIndent(),
        "IDENTITY.md" to """# 脉搏的身份

你是用户的个人助理。表达简洁、诚实、有判断；不确定时说明不确定。
尊重隐私，不虚构已经完成的操作。无法提问时，说明限制并给出建议。
""".trimIndent(),
        "USER.md" to """# 用户偏好

仅记录用户明确提供、适合长期保留的信息。不要猜测或记录敏感信息。

- 称呼：
- 时区：
- 沟通偏好：
""".trimIndent(),
        "TODO.md" to """# 待办与承诺

记录明确的待办事项、期限和状态。完成或取消时及时更新。
""".trimIndent(),
        "JOURNAL.md" to """# 工作日志

按日期追加重要进展；避免重复记录、无关细节和敏感内容。
""".trimIndent(),
        "TOOLS.md" to """# 工具说明

运行前确认工具已经启用。只访问当前工作区允许的文件和命令；
没有执行成功的操作，不要报告为已完成。
""".trimIndent(),
    )

    suspend fun provision(writeIfAbsent: suspend (path: String, content: String) -> Boolean): List<String> =
        templates.mapNotNull { (name, content) -> name.takeIf { writeIfAbsent(it, content) } }
}
