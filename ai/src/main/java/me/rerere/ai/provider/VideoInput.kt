package me.rerere.ai.provider

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

const val VIDEO_OMITTED_PLACEHOLDER = "[Video omitted: this request does not include video content]"

/** Only the request copy changes; stored messages and other attachment parts remain intact. */
fun List<UIMessage>.withoutVideoParts(
    placeholder: String = VIDEO_OMITTED_PLACEHOLDER,
): List<UIMessage> = map { message ->
    if (message.parts.none { it is UIMessagePart.Video }) message
    else message.copy(parts = message.parts.map { part ->
        if (part is UIMessagePart.Video) UIMessagePart.Text(placeholder) else part
    })
}

/** No attachment URL, encoded content or provider credentials are included in these errors. */
class VideoInputException(val reason: Reason) : IllegalArgumentException(
    when (reason) {
        Reason.DISABLED -> "Video input is not enabled for this model."
        Reason.UNSUPPORTED_PROTOCOL -> "This provider protocol does not support video input in this app."
        Reason.ENCODING_FAILED -> "The video could not be read or encoded. Please attach it again."
    },
) {
    enum class Reason { DISABLED, UNSUPPORTED_PROTOCOL, ENCODING_FAILED }
}

/** History is downgraded on the request copy; callers bypassing the factory must
 * not serialize video into an incompatible endpoint either. */
fun List<UIMessage>.withoutVideoPartsAsText(): List<UIMessage> = withoutVideoParts("[视频]")

fun List<UIMessage>.forProviderVideoInput(setting: ProviderSetting): List<UIMessage> =
    if (setting is ProviderSetting.Google) this
    else withoutVideoPartsAsText()

/** Google and local frame decoding are the only direct-video paths. */
fun validateVideoInput(setting: ProviderSetting, model: Model, messages: List<UIMessage>) {
    if (messages.none { message -> message.parts.any { it is UIMessagePart.Video } }) return
    if (setting is ProviderSetting.Google) return
    throw VideoInputException(VideoInputException.Reason.UNSUPPORTED_PROTOCOL)
}
