package me.rerere.rikkahub.data.files

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart

internal fun RoutedAttachment.WorkspaceUpload.toDocument(workspaceId: String?): UIMessagePart.Document =
    UIMessagePart.Document(path, name, mime, buildJsonObject {
        put("workspace_size", size)
        workspaceId?.let { put("workspace_id", it) }
    })

internal val UIMessagePart.Document.uploadSize: Long?
    get() = runCatching { metadata?.get("workspace_size")?.jsonPrimitive?.longOrNull }.getOrNull()
internal val UIMessagePart.Document.uploadWorkspaceId: String?
    get() = runCatching { metadata?.get("workspace_id")?.jsonPrimitive?.contentOrNull }.getOrNull()

internal fun isTextReadable(mime: String, name: String): Boolean = mime.startsWith("text/") || mime in setOf(
    "application/json", "application/xml", "application/javascript", "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/epub+zip",
) || (mime == "application/octet-stream" && me.rerere.rikkahub.utils.isAllowedFileType(name, mime))

private fun attribute(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;")
    .replace("<", "&lt;").replace(">", "&gt;")

internal fun unopenedUploadPrompt(document: UIMessagePart.Document, path: String?): String {
    val location = path ?: document.url
    val shellPath = if (document.url.startsWith("/")) "/workspace$location" else location
    return """
        <UploadFile name="${attribute(document.fileName)}" path="${attribute(location)}" size="${document.uploadSize ?: -1}">
        File content has not been expanded. ${if (document.url.startsWith("/")) "Stored in workspace FILES." else "Attachment contents were not read."}
        Workspace tools can process the file if available. Shell path: $shellPath
        </UploadFile>
    """.trimIndent()
}
