package me.rerere.rikkahub.data.model

internal val DEFAULT_OUTPUT_RULES = """
【输出规则】
- 只输出角色自己的言行，不要以旁白或作者口吻总结，不要解释你在扮演。
- 角色说的话用「」括起来；动作、神态、环境描写用 *…* 括起来。
- 一次回复 1~3 段，总长度控制在 300 字以内；需要展开时等用户接话。
- 不要替用户决定他说什么、做什么、想什么。
- 不要复述用户刚说过的话。
""".trim()

internal fun defaultCharacterPromptModules(): List<PromptModule> = listOf(
    PromptModule(kind = PromptModuleKind.OUTPUT_RULES, title = "输出规则", content = DEFAULT_OUTPUT_RULES),
)

/** Preserve legacy prompt text in memory when upgrading old character records. */
internal fun Character.effectivePromptModules(): List<PromptModule> = when {
    promptModules.isNotEmpty() -> promptModules
    characterSystemPrompt.isNotBlank() -> listOf(
        PromptModule(kind = PromptModuleKind.PERSONA, title = "人设", content = characterSystemPrompt),
    )
    else -> emptyList()
}

internal fun Character.buildPromptModules(
    styleContent: String = "",
    userNickname: String = "",
    persona: String = "",
): String {
    val modules = effectivePromptModules().filter { it.enabled }
    fun of(kind: PromptModuleKind) = modules.filter { it.kind == kind }.sortedBy { it.order }
    val personaModules = of(PromptModuleKind.PERSONA)
    val scenarioModules = of(PromptModuleKind.SCENARIO)
    val ruleModules = of(PromptModuleKind.OUTPUT_RULES)
    val blocks = buildList {
        personaModules.forEach { add(it.content) }
        listOf(description, personality).filter(String::isNotBlank).joinToString("\n\n")
            .takeIf(String::isNotBlank)?.let(::add)
        if (scenarioModules.isEmpty()) scenario.takeIf(String::isNotBlank)?.let(::add)
        scenarioModules.forEach { add(it.content) }
        if (styleContent.isNotBlank()) add(styleContent)
        ruleModules.forEach { add(it.content) }
        of(PromptModuleKind.GUARDRAIL).forEach { add(it.content) }
        of(PromptModuleKind.CUSTOM).forEach { add(it.content) }
        if (persona.isNotBlank()) add("## User persona\n$persona")
        if (exampleMessages.isNotBlank()) add("## Example dialogue\n${exampleMessages.trim()}")
    }
    val rules = ruleModules.joinToString("\n\n") { it.content }
    return blocks.filter(String::isNotBlank).joinToString("\n\n")
        .fillCharacterVariables(this, userNickname, persona, rules)
}

internal fun String.fillCharacterVariables(
    character: Character,
    userNickname: String,
    persona: String,
    rules: String,
): String = replace("{{char}}", character.nickname.ifBlank { character.name })
    .replace("{{user}}", userNickname)
    .replace("{{persona}}", persona)
    .replace("{{rules}}", rules)

internal fun estimateTokens(text: String): Int {
    val cjk = text.count { it.code in 0x4E00..0x9FFF }
    val other = text.length - cjk
    return (cjk * 1.5).toInt() + other / 4
}
