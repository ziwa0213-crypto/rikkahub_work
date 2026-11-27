package me.rerere.rikkahub.data.st.regex

import kotlinx.serialization.json.*

/** Character scripts precede global scripts; preset integration is intentionally absent. */
fun mergeActiveRegexScripts(characterScripts: List<RegexScript>, globalScripts: List<RegexScript>): List<RegexScript> =
    (characterScripts + globalScripts).filter { !it.disabled && it.findRegex.isNotBlank() }

fun parseCardRegexScripts(extensions: JsonObject?): List<RegexScript> =
    ((extensions?.get("regex_scripts") ?: extensions?.get("regexScripts")) as? JsonArray)
        ?.mapNotNull(::parseRegexScript).orEmpty().filter { it.findRegex.isNotBlank() }

// 单条内嵌正则的宽容解析；解析失败返回 null
internal fun parseRegexScript(element: JsonElement): RegexScript? {
    val obj = element as? JsonObject ?: return null

    fun raw(key: String, alt: String? = null): JsonElement? = obj[key] ?: alt?.let { obj[it] }

    fun primitive(key: String, alt: String? = null): JsonPrimitive? = raw(key, alt) as? JsonPrimitive

    fun stringOf(key: String, alt: String? = null, default: String = ""): String =
        primitive(key, alt)?.contentOrNull ?: default

    fun intOf(key: String, alt: String? = null): Int? {
        val text = primitive(key, alt)?.contentOrNull?.trim() ?: return null
        if (text.isEmpty()) return null
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt()
    }

    fun intOrBoolOf(key: String, alt: String? = null): Int? {
        intOf(key, alt)?.let { return it }
        return when (primitive(key, alt)?.contentOrNull?.trim()?.lowercase()) {
            "true" -> 1
            "false" -> 0
            else -> null
        }
    }

    fun boolOf(key: String, alt: String? = null, default: Boolean = false): Boolean {
        val text = primitive(key, alt)?.contentOrNull?.trim()?.lowercase() ?: return default
        return text == "true" || text == "1" || text == "yes" || text == "on"
    }

    fun intListOf(key: String): List<Int> = when (val el = obj[key]) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull() }
        is JsonPrimitive -> listOfNotNull(el.contentOrNull?.trim()?.toIntOrNull())
        else -> emptyList()
    }

    fun stringListOf(key: String, alt: String? = null): List<String> = when (val el = raw(key, alt)) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(el.contentOrNull?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }

    val disabled = when {
        primitive("disabled") != null -> boolOf("disabled")
        primitive("enabled") != null -> !boolOf("enabled", default = true)
        else -> false
    }

    return RegexScript(
        id = stringOf("id"),
        scriptName = stringOf("scriptName", "script_name", "name"),
        findRegex = stringOf("findRegex", "find_regex"),
        replaceString = stringOf("replaceString", "replace_string"),
        trimStrings = stringListOf("trimStrings", "trim_strings"),
        placement = intListOf("placement"),
        disabled = disabled,
        markdownOnly = boolOf("markdownOnly", "markdown_only"),
        promptOnly = boolOf("promptOnly", "prompt_only"),
        runOnEdit = boolOf("runOnEdit", "run_on_edit"),
        substituteRegex = intOrBoolOf("substituteRegex", "substitute_regex") ?: 0,
        minDepth = intOf("minDepth", "min_depth"),
        maxDepth = intOf("maxDepth", "max_depth"),
    )
}

