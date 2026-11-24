package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CharacterPersona(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val avatar: Avatar = Avatar.Dummy,
)
