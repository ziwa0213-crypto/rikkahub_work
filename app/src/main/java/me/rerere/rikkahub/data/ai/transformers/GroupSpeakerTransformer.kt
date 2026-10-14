package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.withGroupSpeakerPrefixes

class GroupSpeakerTransformer(private val contextMessageLimit: Int, private val replyLabel: String = "Reply") : InputMessageTransformer {
    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> =
        messages.withGroupSpeakerPrefixes(contextMessageLimit, replyLabel)
}
