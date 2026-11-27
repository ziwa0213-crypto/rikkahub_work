package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.UiMode

fun UiMode.isSelectableDefault(): Boolean = this == UiMode.CHAT || this == UiMode.ROLE

fun Settings.launchMode(): UiMode = defaultMode.takeIf { it.isSelectableDefault() } ?: UiMode.CHAT

fun Settings.orderedModes(): List<UiMode> = listOf(launchMode()) + UiMode.entries.filter { it != launchMode() }
