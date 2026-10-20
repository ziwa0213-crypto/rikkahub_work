package me.rerere.rikkahub.browser

/**
 * Best-effort filtering applied before text read from the built-in browser is
 * returned to an AI provider. This is deliberately a small, deterministic
 * filter: it does not claim to identify every secret and it does not inspect
 * password input values (the DOM reader omits those values entirely).
 */
object CredentialFilter {
    data class FilteredResult(
        val text: String,
        val hitCount: Int,
    )

    private val jwtRegex = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")
    private val prefixedKeyRegex = Regex(
        "(?i)\\b(?:sk-[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}|AKIA[A-Z0-9]{16})\\b"
    )
    private val cardCandidateRegex = Regex("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)")
    private val longTokenRegex = Regex("(?<![A-Za-z0-9_\\-.])[A-Za-z0-9_\\-.]{32,}(?![A-Za-z0-9_\\-.])")

    fun filter(raw: String): FilteredResult {
        var hits = 0
        var filtered = raw

        fun replace(regex: Regex, category: String, predicate: (MatchResult) -> Boolean = { true }) {
            filtered = regex.replace(filtered) { match ->
                if (!predicate(match)) {
                    match.value
                } else {
                    hits++
                    "[已过滤:$category(${match.value.length}字)]"
                }
            }
        }

        replace(jwtRegex, "疑似JWT")
        replace(prefixedKeyRegex, "疑似token")
        replace(cardCandidateRegex, "疑似卡号") { match ->
            val digits = match.value.filter(Char::isDigit)
            digits.length in 13..19 && passesLuhn(digits)
        }
        replace(longTokenRegex, "疑似token") { match ->
            // A long alphabetic word can be legitimate article text. Require a
            // secret-like shape: punctuation, digits, or a mixed-case boundary.
            val value = match.value
            value.any { it.isDigit() || it == '_' || it == '-' || it == '.' } ||
                (value.any(Char::isUpperCase) && value.any(Char::isLowerCase))
        }

        return FilteredResult(filtered, hits)
    }

    internal fun passesLuhn(digits: String): Boolean {
        if (digits.length !in 13..19 || digits.any { !it.isDigit() }) return false
        var sum = 0
        var double = false
        for (index in digits.lastIndex downTo 0) {
            var digit = digits[index] - '0'
            if (double) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            double = !double
        }
        return sum % 10 == 0
    }
}
