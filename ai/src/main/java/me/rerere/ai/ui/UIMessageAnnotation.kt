package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UIMessageAnnotation {
    @Serializable
    @SerialName("group_sender")
    data class GroupSender(val memberId: String, val memberName: String) : UIMessageAnnotation()

    @Serializable
    @SerialName("group_quote")
    data class GroupQuote(
        val targetMessageId: String,
        val targetSenderName: String,
        val preview: String = "",
    ) : UIMessageAnnotation()

    @Serializable
    @SerialName("url_citation")
    data class UrlCitation(
        val title: String,
        val url: String
    ) : UIMessageAnnotation()
}
