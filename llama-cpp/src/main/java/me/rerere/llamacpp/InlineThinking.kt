package me.rerere.llamacpp

import org.json.JSONObject

private val THINK_OPEN = listOf("<think>", "<thinking>")
private val THINK_CLOSE = listOf("</think>", "</thinking>")

/** Fallback for templates that leave literal thinking tags in content. Native reasoning wins. */
internal fun splitInlineThinking(json: String, partial: Boolean = false): String {
    val message = runCatching { JSONObject(json) }.getOrNull() ?: return json
    if (!message.isNull("reasoning_content") && message.optString("reasoning_content").isNotEmpty()) return json
    val content = message.optString("content")
    val openAt = content.indices.firstOrNull { index ->
        THINK_OPEN.any { content.startsWith(it, index) } && !insideCode(content, index)
    }
    if (openAt == null) {
        val visible = if (partial) withoutPendingTag(content, THINK_OPEN) else content
        if (visible == content || insideCode(content, visible.length)) return json
        message.put("content", visible)
        return message.toString()
    }
    val open = THINK_OPEN.first { content.startsWith(it, openAt) }
    val thinkingStart = openAt + open.length
    val closeAt = THINK_CLOSE.map { content.indexOf(it, thinkingStart) }
        .filter { it >= 0 && !insideCode(content, it) }.minOrNull()
    val thinking = if (closeAt == null) {
        content.substring(thinkingStart).let { if (partial) withoutPendingTag(it, THINK_CLOSE) else it }
    } else content.substring(thinkingStart, closeAt)
    val rest = if (closeAt == null) "" else {
        val close = THINK_CLOSE.first { content.startsWith(it, closeAt) }
        content.substring(closeAt + close.length)
    }
    message.put("reasoning_content", thinking.trimStart('\n'))
    message.put("content", (content.substring(0, openAt) + rest).trimStart('\n'))
    return message.toString()
}

/** Don't leak `<thi` or `</thi` into an append-only stream before the tag is complete. */
private fun withoutPendingTag(text: String, tags: List<String>): String {
    val length = tags.flatMap { tag -> (1 until tag.length).map { tag.take(it) } }
        .filter(text::endsWith).maxOfOrNull(String::length) ?: return text
    return text.dropLast(length)
}

/** Respect fenced and inline Markdown code so examples remain ordinary text. */
private fun insideCode(text: String, end: Int): Boolean {
    var fence: Char? = null
    var fenceSize = 0
    var inlineSize = 0
    var index = 0
    while (index < end) {
        val char = text[index]
        if (char != '`' && char != '~') { index++; continue }
        var next = index
        while (next < end && text[next] == char) next++
        val count = next - index
        val lineStart = text.lastIndexOf('\n', index - 1) + 1
        val isFence = count >= 3 && text.substring(lineStart, index).all { it == ' ' } && index - lineStart <= 3
        if (isFence && inlineSize == 0) {
            if (fence == null) { fence = char; fenceSize = count }
            else if (char == fence && count >= fenceSize) fence = null
        } else if (char == '`' && fence == null) {
            if (inlineSize == 0) inlineSize = count else if (inlineSize == count) inlineSize = 0
        }
        index = next
    }
    return fence != null || inlineSize != 0
}
