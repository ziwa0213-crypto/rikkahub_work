package me.rerere.ai.provider.providers.deepseekweb

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.util.json

/** The text protocol is local to DeepSeek Web, not the shared provider tool wire format. */
object DeepSeekWebToolProtocol {
    sealed interface Result {
        data class Allowed(val call: Call) : Result
        data class Blocked(val name: String) : Result
        data class Unknown(val name: String) : Result
        data class Invalid(val name: String = "JSON") : Result
        data object NotTool : Result
    }

    data class Call(val name: String, val arguments: String)
    data class Candidate(val range: IntRange, val result: Result)
    data class Output(val result: Result, val text: String)

    private val callKeys = Regex(""""(?:tool|arguments|input|parameters)"\s*:""")
    private val fences = Regex("(?is)```(?:json)?[ \\t]*\\r?\\n?(.*?)```")
    private val dsml = Regex("(?s)<\\|\\|DSML\\|\\|(?:calls|invoke)\\b.*?(?:</\\|\\|DSML\\|\\|calls>|</\\|\\|DSML\\|\\|invoke>|$)")

    fun scan(text: String): List<Candidate> {
        val dsmlRanges = dsml.findAll(text).map { it.range }.toList()
        return objectRanges(text).mapNotNull { range ->
            if (dsmlRanges.any { range.first in it }) return@mapNotNull null
            val candidate = text.substring(range)
            val value = runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
            val result = if (value == null) {
                if (callKeys.containsMatchIn(candidate)) {
                    warn("Malformed tool JSON, length=${candidate.length}")
                    Result.Invalid()
                } else Result.NotTool
            } else classify(value)
            if (result == Result.NotTool) null else Candidate(range, result)
        }
    }

    fun parse(text: String): Output {
        val candidates = scan(text)
        val first = candidates.firstOrNull() ?: return Output(Result.NotTool, text)
        if (candidates.size > 1) warn("Discarded ${candidates.size - 1} additional tool calls")
        // Unknown examples and malformed calls stay visible; the application reports them.
        val removed = candidates.filterIndexed { index, candidate ->
            index > 0 || candidate.result is Result.Allowed || candidate.result is Result.Blocked
        }.map { it.range }.toMutableList()
        fences.findAll(text).forEach { fence ->
            val content = fence.groups[1]!!
            val inside = removed.filter { it.first >= content.range.first && it.last <= content.range.last }
            if (inside.isNotEmpty() && removeRanges(content.value, inside.map {
                    (it.first - content.range.first)..(it.last - content.range.first)
                }).isBlank()) {
                removed += fence.range
            }
        }
        return Output(first.result, removeRanges(text, removed))
    }

    private fun classify(value: JsonObject): Result {
        val argumentKey = listOf("arguments", "input", "parameters").firstOrNull(value::containsKey)
        if (!value.containsKey("tool") && !(value.containsKey("name") && argumentKey != null)) {
            if (argumentKey != null) {
                warn("Tool JSON is missing a tool name")
                return Result.Invalid()
            }
            return Result.NotTool
        }
        val name = ((value["tool"] ?: value["name"]) as? JsonPrimitive)
            ?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return Result.Invalid().also { warn("Tool JSON has an invalid tool name") }
        if (name !in DeepSeekWebToolPolicy.allowedNames) {
            return if (DeepSeekWebToolPolicy.isBlockedName(name)) Result.Blocked(name)
            else Result.Unknown(name).also { warn("Unrecognized tool name in JSON") }
        }
        val arguments = argumentKey?.let { value[it] } as? JsonObject
            ?: return Result.Invalid(name).also { warn("Tool JSON arguments are missing or not an object") }
        if (!DeepSeekWebToolPolicy.allows(name, arguments)) return Result.Blocked(name)
        return Result.Allowed(Call(name, arguments.toString()))
    }

    // Locate outer objects only. The JSON parser handles syntax; brace matching only finds boundaries.
    private fun objectRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        text.forEachIndexed { index, char ->
            if (start < 0) {
                if (char == '{') {
                    start = index
                    depth = 1
                }
            } else if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        ranges += start..index
                        start = -1
                    }
                }
            }
        }
        if (start >= 0) ranges += start..text.lastIndex
        return ranges
    }

    private fun removeRanges(text: String, ranges: List<IntRange>): String = buildString {
        var cursor = 0
        ranges.sortedBy(IntRange::first).forEach { range ->
            if (range.first > cursor) append(text, cursor, range.first)
            cursor = maxOf(cursor, range.last + 1)
        }
        append(text, cursor, text.length)
    }

    private fun warn(message: String) {
        runCatching { Log.w("DeepSeekWebTools", message) }
    }
}
