package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.model.GroupMember
import org.junit.Assert.*
import org.junit.Test

class GroupMentionCompletionProviderTest {
    private val provider = GroupMentionCompletionProvider(listOf(GroupMember(name = "Ann Lee"), GroupMember(name = "B")))

    @Test fun atCursorOffersMembersAndReplacesOnlyAtSign() = runBlocking {
        val result = requireNotNull(provider.complete(ChatCompletionContext("Hello @ world", TextRange(7))))
        assertEquals(TextRange(6, 7), result.replacementRange)
        assertEquals(listOf("Ann Lee", "B"), result.items.map { it.label })
        assertEquals(listOf("@Ann Lee ", "@B "), result.items.map { it.insertText })
        assertEquals(listOf("Ann Lee", "B"), result.items.map { it.mentionName })
    }

    @Test fun emailPartialNameSelectionAndEmptyInputDoNotTrigger() = runBlocking {
        assertNull(provider.complete(ChatCompletionContext("a@", TextRange(2))))
        assertNull(provider.complete(ChatCompletionContext("@x", TextRange(2))))
        assertNull(provider.complete(ChatCompletionContext("@", TextRange(0, 1))))
        assertNull(provider.complete(ChatCompletionContext("", TextRange(0))))
    }
}
