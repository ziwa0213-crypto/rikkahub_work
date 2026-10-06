package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolPolicy
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebToolProtocol
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant

data class DeepSeekWebPseudoToolMatch(
    val toolName: String,
    val blockLength: Int,
    val blocked: Boolean = true,
)

class DeepSeekWebPseudoToolException(
    val toolName: String,
    val blocked: Boolean = true,
) : IllegalStateException("DeepSeek Web pseudo tool call blocked: $toolName")

data class DeepSeekWebSanitizedMessages(
    val messages: List<UIMessage>,
    val matches: List<DeepSeekWebPseudoToolMatch>,
)

/** Removes forbidden calls; unsupported DSML for read-only tools is reported without execution. */
object DeepSeekWebOutputGuard {
    private val fencedJson = Regex("(?is)```json\\s*(.*?)```")
    private val dsmlCalls = Regex("(?s)<\\|\\|DSML\\|\\|calls>.*?(?:</\\|\\|DSML\\|\\|calls>|$)")
    private val dsmlInvoke = Regex("(?s)<\\|\\|DSML\\|\\|invoke\\b([^>]*)>.*?(?:</\\|\\|DSML\\|\\|invoke>|$)")
    private val dsmlParameter = Regex("(?s)<\\|\\|DSML\\|\\|parameter\\b([^>]*)>(.*?)</\\|\\|DSML\\|\\|parameter>")
    private val dsmlAttribute = Regex("""\b(name|string)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun sanitize(messages: List<UIMessage>): DeepSeekWebSanitizedMessages? {
        val matches = mutableListOf<DeepSeekWebPseudoToolMatch>()
        val sanitized = messages.mapIndexed { index, message ->
            // Historical API responses can contain examples that must not trigger a new error.
            if (index != messages.lastIndex || message.role != MessageRole.ASSISTANT) return@mapIndexed message
            val parts = buildList {
                var partIndex = 0
                while (partIndex < message.parts.size) {
                    val part = message.parts[partIndex++]
                    if (part !is UIMessagePart.Text) {
                        add(part)
                        continue
                    }
                    val textParts = mutableListOf(part)
                    while (message.parts.getOrNull(partIndex) is UIMessagePart.Text) {
                        textParts += message.parts[partIndex++] as UIMessagePart.Text
                    }
                    val result = sanitizeText(textParts.joinToString("") { it.text })
                    matches += result.matches
                    if (result.matches.isEmpty()) addAll(textParts)
                    else result.text.takeIf(String::isNotBlank)?.let { add(part.copy(text = it)) }
                }
            }
            message.copy(parts = parts)
        }
        return matches.takeIf(List<DeepSeekWebPseudoToolMatch>::isNotEmpty)?.let {
            DeepSeekWebSanitizedMessages(sanitized, it)
        }
    }

    private fun sanitizeText(text: String): SanitizedText {
        val candidates = mutableListOf<Candidate>()
        dsmlCalls.findAll(text).forEach { block ->
            candidates += dsmlCandidates(block.value, block.range)
        }
        dsmlInvoke.findAll(text).forEach { block ->
            if (candidates.none { block.range.first >= it.range.first && block.range.last <= it.range.last }) {
                candidates += dsmlCandidates(block.value, block.range)
            }
        }
        DeepSeekWebToolProtocol.scan(text).forEach { call ->
            if (candidates.any { call.range.first >= it.range.first && call.range.last <= it.range.last }) return@forEach
            val candidate = when (val result = call.result) {
                is DeepSeekWebToolProtocol.Result.Blocked -> {
                    val fence = fencedJson.findAll(text).firstOrNull {
                        call.range.first >= it.range.first && call.range.last <= it.range.last &&
                            it.groupValues[1].trim() == text.substring(call.range).trim()
                    }
                    Candidate(fence?.range ?: call.range, result.name)
                }
                is DeepSeekWebToolProtocol.Result.Unknown -> Candidate(call.range, result.name, blocked = false)
                is DeepSeekWebToolProtocol.Result.Invalid -> Candidate(call.range, result.name, blocked = false)
                else -> null
            }
            candidate?.let { candidates += it }
        }
        if (candidates.isEmpty()) return SanitizedText(text, emptyList())
        val merged = mergeRanges(candidates.filter(Candidate::blocked).map(Candidate::range))
        val cleaned = buildString {
            var cursor = 0
            merged.forEach { range ->
                append(text, cursor, range.first)
                cursor = range.last + 1
            }
            append(text, cursor, text.length)
        }
        return SanitizedText(
            text = cleaned,
            matches = candidates.map { DeepSeekWebPseudoToolMatch(it.toolName, it.range.last - it.range.first + 1, it.blocked) },
        )
    }

    private fun dsmlCandidates(block: String, range: IntRange): List<Candidate> {
        val calls = dsmlInvoke.findAll(block).map { invocation ->
            val name = attributes(invocation.groupValues[1])["name"] ?: "DSML"
            val action = dsmlParameter.findAll(invocation.value).firstOrNull {
                attributes(it.groupValues[1])["name"] == "action"
            }?.let { parameter ->
                val value = parameter.groupValues[2]
                if (attributes(parameter.groupValues[1])["string"] == "true") JsonPrimitive(value.trim())
                else runCatching { JsonInstant.parseToJsonElement(value) }.getOrNull()
            }
            val arguments = JsonObject(action?.let { mapOf("action" to it) }.orEmpty())
            Candidate(
                range = (range.first + invocation.range.first)..(range.first + invocation.range.last),
                toolName = name,
                blocked = !DeepSeekWebToolPolicy.allows(name, arguments),
            )
        }.toList()
        return when {
            calls.isEmpty() -> listOf(Candidate(range, "DSML", blocked = false))
            // Keep the wrapper when it still contains an allowlisted invocation.
            calls.all(Candidate::blocked) -> calls.map { it.copy(range = range) }
            else -> calls
        }
    }

    private fun attributes(header: String): Map<String, String> = dsmlAttribute.findAll(header)
        .associate { it.groupValues[1] to (it.groups[2]?.value ?: it.groupValues[3]) }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> = ranges
        .sortedBy(IntRange::first)
        .fold(mutableListOf()) { merged, range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.last + 1) {
                merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
            } else {
                merged += range
            }
            merged
        }

    private data class Candidate(val range: IntRange, val toolName: String, val blocked: Boolean = true)
    private data class SanitizedText(val text: String, val matches: List<DeepSeekWebPseudoToolMatch>)
}
