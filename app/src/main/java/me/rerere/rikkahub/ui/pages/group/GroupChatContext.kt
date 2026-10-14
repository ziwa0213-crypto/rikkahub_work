package me.rerere.rikkahub.ui.pages.group

import androidx.compose.runtime.staticCompositionLocalOf
import me.rerere.rikkahub.data.model.Group

/** Current group for display overrides only. Ordinary chat always provides null. */
val LocalGroupChatContext = staticCompositionLocalOf<Group?> { null }
