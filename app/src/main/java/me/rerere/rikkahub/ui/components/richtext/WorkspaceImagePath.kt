package me.rerere.rikkahub.ui.components.richtext

/** Null means the existing image loader should keep the original URL. */
internal fun relativeWorkspaceImagePath(url: String, baseDir: String?): String? {
    if (baseDir == null || url.startsWith('/') || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(url)) return null
    return baseDir.trim('/').let { if (it.isEmpty()) "" else "$it/" } + url.removePrefix("./")
}
