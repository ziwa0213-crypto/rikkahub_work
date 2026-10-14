package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import me.rerere.rikkahub.data.model.GroupMember

class GroupMentionCompletionProvider(private val members: List<GroupMember>) : ChatCompletionProvider {
    override val id = "group_mentions"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection || context.cursor <= 0 || context.cursor > context.text.length ||
            context.text[context.cursor - 1] != '@') return null
        val start = context.cursor - 1
        if (start > 0 && context.text[start - 1].let { it.isLetterOrDigit() || it == '_' }) return null
        return ChatCompletionList(id, TextRange(start, context.cursor), members.map {
            ChatCompletionItem(label = it.name, insertText = "@${it.name} ", mentionName = it.name)
        })
    }
}
