package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import me.rerere.ui.webview.isTextualMime
import me.rerere.ui.webview.mimeTypeOf

internal class WorkspacePreviewInterceptor(root: File?) {
    private val resources = WorkspacePreviewResources(root)

    /** Always returns a response: an unrecognised URL must never reach the network. */
    fun intercept(request: WebResourceRequest): WebResourceResponse {
        val file = resources.resolve(request.url.toString(), request.method) ?: return notFound()
        return runCatching {
            val mime = mimeTypeOf(file.name)
            WebResourceResponse(mime, if (isTextualMime(mime)) "UTF-8" else null,
                if (request.method == "HEAD") ByteArrayInputStream(byteArrayOf()) else file.inputStream())
        }.getOrElse { notFound() }
    }

    private fun notFound() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
        emptyMap(), ByteArrayInputStream(byteArrayOf()))
}
