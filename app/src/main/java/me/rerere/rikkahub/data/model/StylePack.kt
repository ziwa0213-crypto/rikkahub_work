package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class StylePack(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val builtIn: Boolean = false,
    val rules: List<String> = emptyList(),
    val enabled: Boolean = true,
    val injectDepth: Int = 2,
)
