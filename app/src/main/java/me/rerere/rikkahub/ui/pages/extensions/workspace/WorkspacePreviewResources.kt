package me.rerere.rikkahub.ui.pages.extensions.workspace

import java.io.File
import java.net.URI

internal const val WORKSPACE_PREVIEW_HOST = "workspace-preview.invalid"
internal const val WORKSPACE_PREVIEW_PATH = "/preview/"
internal const val WORKSPACE_PREVIEW_BASE_URL = "https://$WORKSPACE_PREVIEW_HOST$WORKSPACE_PREVIEW_PATH"
internal const val MAX_WORKSPACE_PREVIEW_BYTES = 1024L * 1024

/** The virtual directory maps once to the opened document's real parent directory. */
internal class WorkspacePreviewResources(root: File?) {
    private val root = runCatching { root?.canonicalFile?.takeIf(File::isDirectory) }.getOrNull()

    fun resolve(url: String, method: String = "GET"): File? = runCatching {
        if (method != "GET" && method != "HEAD") return null
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || !uri.host.equals(WORKSPACE_PREVIEW_HOST, true) ||
            uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
        val path = uri.path ?: return null
        if (!path.startsWith(WORKSPACE_PREVIEW_PATH) || '\u0000' in path || '\\' in path) return null
        val relative = path.removePrefix(WORKSPACE_PREVIEW_PATH)
        if (relative.isBlank() || relative.startsWith('/')) return null
        val base = root ?: return null
        val file = File(base, relative).canonicalFile
        file.takeIf { it.isFile && it.path.startsWith(base.path.trimEnd(File.separatorChar) + File.separator) }
    }.getOrNull()
}
