package me.rerere.rikkahub.data.ai

/**
 * Extracts DeepSeek Web credentials from DevTools "Copy as cURL" output or
 * directly pasted request headers. This parser is pure text processing only.
 */
object DeepSeekWebRequestParser {

    data class Parsed(
        /** The token without the `Bearer ` prefix. */
        val token: String = "",
        val cookie: String = "",
        /** Headers other than authorization, cookie, and x-ds-pow-response. */
        val headers: Map<String, String> = emptyMap(),
        /** Whether a non-blank authorization header was present. */
        val authorizationFound: Boolean = false,
    ) {
        val isEmpty: Boolean get() = !authorizationFound && cookie.isBlank() && headers.isEmpty()
    }

    private val singleQuotedHeader = Regex("""(?:-H|--header)\s+'([^']*)'""")
    private val doubleQuotedHeader = Regex("""(?:-H|--header)\s+"([^"]*)"""")
    private val singleQuotedCookie = Regex("""(?:-b|--cookie)\s+'([^']*)'""")
    private val doubleQuotedCookie = Regex("""(?:-b|--cookie)\s+"([^"]*)"""")
    private val lineHeader = Regex("""^\s*([A-Za-z0-9!#$%&'*+.^_`|~-]+)\s*:\s*(.+?)\s*$""")
    private val bearerPrefix = Regex("""(?i)^bearer\s+""")
    private val continuation = Regex("""[\\^]\s*\n""")
    private val excluded = setOf("authorization", "cookie", "x-ds-pow-response")

    fun parse(raw: String): Parsed {
        if (raw.isBlank()) return Parsed()

        val normalized = raw.replace("\r\n", "\n").replace(continuation, " ")
        val pairs = buildList {
            singleQuotedHeader.findAll(normalized).forEach { add(it.groupValues[1]) }
            doubleQuotedHeader.findAll(normalized).forEach { add(it.groupValues[1]) }
            if (isEmpty()) {
                normalized.lines().forEach { line ->
                    lineHeader.find(line)?.let { add("${it.groupValues[1]}: ${it.groupValues[2]}") }
                }
            }
        }

        val map = LinkedHashMap<String, String>()
        pairs.forEach { pair ->
            val index = pair.indexOf(':')
            if (index <= 0) return@forEach
            val name = pair.substring(0, index).trim().lowercase()
            val value = pair.substring(index + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) map[name] = value
        }

        if (map["cookie"].isNullOrBlank()) {
            val fromFlag = singleQuotedCookie.find(normalized)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: doubleQuotedCookie.find(normalized)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            if (fromFlag != null) map["cookie"] = fromFlag.trim()
        }

        val authRaw = map["authorization"].orEmpty()
        return Parsed(
            token = authRaw.replace(bearerPrefix, "").trim(),
            cookie = map["cookie"].orEmpty(),
            headers = map.filterKeys { it !in excluded },
            authorizationFound = authRaw.isNotBlank(),
        )
    }
}
