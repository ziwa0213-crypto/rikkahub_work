package me.rerere.workspace.rootfs

enum class RootfsTier(val token: String) {
    MIN("min"), BASE("base"), DEV("dev");

    companion object {
        fun of(token: String): RootfsTier? = entries.firstOrNull { it.token == token }
        val preference = listOf(BASE, DEV, MIN)
    }
}
