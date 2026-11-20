package me.rerere.ui.webview

import java.util.Locale

/** Shared by bundled assets and offline workspace previews. */
fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "js", "mjs" -> "text/javascript"
    "css" -> "text/css"
    "html", "htm" -> "text/html"
    "json" -> "application/json"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "woff2" -> "font/woff2"
    "woff" -> "font/woff"
    "ttf" -> "font/ttf"
    "ico" -> "image/x-icon"
    "md", "markdown", "txt", "xml", "csv" -> "text/plain"
    else -> "application/octet-stream"
}

fun isTextualMime(mime: String): Boolean =
    mime.startsWith("text/") || mime.endsWith("json") || mime.endsWith("xml")
