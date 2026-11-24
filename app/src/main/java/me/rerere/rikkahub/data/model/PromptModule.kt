package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PromptModule(
    val id: Uuid = Uuid.random(),
    val kind: PromptModuleKind = PromptModuleKind.CUSTOM,
    val title: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    val order: Int = 0,
)

@Serializable
enum class PromptModuleKind {
    PERSONA,
    SCENARIO,
    STYLE,
    OUTPUT_RULES,
    GUARDRAIL,
    CUSTOM,
}
