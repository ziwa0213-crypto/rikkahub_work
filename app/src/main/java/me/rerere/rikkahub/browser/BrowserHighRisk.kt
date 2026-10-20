package me.rerere.rikkahub.browser

/** Hard refusal for actions which must always be performed by the human. */
object BrowserHighRisk {
    enum class Kind {
        PAYMENT,
        DELETE,
        AUTHORIZATION,
        PUBLISH,
        TRANSFER,
        FILE,
        PASSWORD,
    }

    data class Decision(val kind: Kind, val reason: String)

    private val rules = listOf(
        Kind.PAYMENT to Regex("(?i)支付|付款|立即购买|立即購買|确认订单|確認訂單|\\bpay\\b|place order|checkout"),
        Kind.DELETE to Regex("(?i)删除|刪除|注销|註銷|解绑|解綁|撤销|撤銷|\\bdelete\\b|deactivate|remove account"),
        Kind.AUTHORIZATION to Regex("(?i)授权|授權|同意并继续|同意並繼續|\\ballow\\b|authorize|grant"),
        Kind.PUBLISH to Regex("(?i)发布|發佈|發布|发帖|發帖|提交|发送|傳送|\\bpost\\b|publish|submit|\\bsend\\b"),
        Kind.TRANSFER to Regex("(?i)转账|轉帳|提现|提現|\\btransfer\\b|\\bwithdraw\\b"),
        Kind.FILE to Regex("(?i)上传|上傳|下载|下載|\\bupload\\b|\\bdownload\\b"),
    )

    fun forClick(tag: String, inputType: String, text: String): Decision? {
        if (inputType.equals("password", true)) return Decision(Kind.PASSWORD, "password field")
        if (inputType.equals("submit", true) || inputType.equals("image", true)) return Decision(Kind.PUBLISH, "form submission")
        if (tag.equals("input", ignoreCase = true) && inputType.equals("file", ignoreCase = true)) {
            return Decision(Kind.FILE, "file upload/download")
        }
        return rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }
            ?.let { (kind, _) -> Decision(kind, text.trim().take(80)) }
    }

    fun forType(tag: String, inputType: String, label: String = ""): Decision? {
        if (inputType.equals("password", ignoreCase = true)) {
            return Decision(Kind.PASSWORD, "password field")
        }
        if (inputType.equals("file", ignoreCase = true) || tag.equals("input", ignoreCase = true) &&
            inputType.equals("file", ignoreCase = true)
        ) {
            return Decision(Kind.FILE, "file input")
        }
        return rules.firstOrNull { (_, regex) -> regex.containsMatchIn(label) }
            ?.let { (kind, _) -> Decision(kind, label.trim().take(80)) }
    }
}
