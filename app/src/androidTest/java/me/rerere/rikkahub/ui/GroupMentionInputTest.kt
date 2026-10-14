package me.rerere.rikkahub.ui

import androidx.compose.ui.text.TextRange
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.insertMention
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMentionInputTest {
    @Test fun avatarMentionReplacesSelectionAndLeavesCursorAfterName() {
        val state = ChatInputState()
        state.textContent.edit { append("Hello old world"); selection = TextRange(6, 9) }
        state.insertMention("Ann Lee")
        assertEquals("Hello @Ann Lee  world", state.textContent.text.toString())
        assertEquals(TextRange(15), state.textContent.selection)
    }

    @Test fun completionMentionReplacesOnlyRequestedRange() {
        val state = ChatInputState()
        state.textContent.edit { append("Before @ after"); selection = TextRange(8) }
        state.insertMention("Chen", TextRange(7, 8))
        assertEquals("Before @Chen  after", state.textContent.text.toString())
        assertEquals(TextRange(13), state.textContent.selection)
    }
}
