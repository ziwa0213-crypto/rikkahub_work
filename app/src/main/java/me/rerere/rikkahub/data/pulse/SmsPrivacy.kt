package me.rerere.rikkahub.data.pulse

/** Whole-message redaction happens before truncation and before the result can leave the reader. */
internal fun isVerificationSms(body: String): Boolean {
    val digits = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])").containsMatchIn(body)
    if (!digits) return false
    val keywords = listOf("验证码", "驗證碼", "校验码", "校驗碼", "动态码", "動態碼", "动态密码",
        "動態密碼", "登录码", "登入碼", "确认码", "確認碼", "verification", "verify", "code", "otp", "one-time", "認証")
    return keywords.any { body.contains(it, ignoreCase = true) } ||
        (body.length < 60 && (body.startsWith("G-") || body.startsWith("【")))
}

internal fun normalizeSmsAddress(address: String): String = address.filterNot { it.isWhitespace() || it in "-()（）" }

internal fun smsSenderAllowed(address: String, whitelist: List<String>): Boolean =
    whitelist.isEmpty() || whitelist.any { normalizeSmsAddress(it) == normalizeSmsAddress(address) }

internal fun maskSmsAddress(address: String, whitelist: List<String>): String {
    val normalized = normalizeSmsAddress(address).take(80)
    if (whitelist.any { normalizeSmsAddress(it) == normalized }) return normalized
    // Service short codes remain identifiable; personal numbers are masked in both tool and poll paths.
    if (normalized.length <= 6) return normalized
    val prefix = (normalized.length - 4) / 2
    return normalized.take(prefix) + "****" + normalized.drop(prefix + 4)
}

internal data class SmsEntry(
    val id: Long, val address: String, val body: String?, val date: Long,
    val redactedKind: String? = null, val truncated: Boolean = false,
)

internal fun redactedSmsEntry(id: Long, address: String, body: String, date: Long, whitelist: List<String>): SmsEntry? {
    if (body.isBlank() || !smsSenderAllowed(address, whitelist)) return null
    val verification = isVerificationSms(body)
    return SmsEntry(id, maskSmsAddress(address, whitelist), if (verification) null else body.take(2000), date,
        if (verification) "verification_code" else null, !verification && body.length > 2000)
}
