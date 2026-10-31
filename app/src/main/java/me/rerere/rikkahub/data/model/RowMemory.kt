package me.rerere.rikkahub.data.model

/** The legacy row table has no timestamps; unknown dates remain null rather than being invented. */
data class RowMemory(val id: Int, val content: String, val createdAt: Long? = null)
