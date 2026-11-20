package me.rerere.rikkahub.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.workspace.WorkspaceStorageArea
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.time.LocalDate

sealed interface RoutedAttachment {
    data class ChatPart(val parts: List<UIMessagePart>) : RoutedAttachment
    data class WorkspaceUpload(val path: String, val name: String, val size: Long, val mime: String) : RoutedAttachment
    data class Rejected(val reason: Reason) : RoutedAttachment
    enum class Reason { NO_WORKSPACE, TOO_LARGE, COPY_FAILED, UNREADABLE }
}

internal enum class AttachmentTarget { IMAGE, AUDIO, DOCUMENT, VIDEO, WORKSPACE }
internal fun attachmentTarget(name: String, mime: String, provider: ProviderSetting?, model: Model?): AttachmentTarget {
    val effectiveProvider = model?.providerOverwrite ?: provider
    return when {
    mime.startsWith("image/") -> AttachmentTarget.IMAGE
    mime.startsWith("audio/") -> AttachmentTarget.AUDIO
    isAllowedFileType(name, mime) -> AttachmentTarget.DOCUMENT
    mime.startsWith("video/") && effectiveProvider is ProviderSetting.Google -> AttachmentTarget.VIDEO
    else -> AttachmentTarget.WORKSPACE
    }
}

internal const val MAX_ATTACHMENT_BYTES = 2L * 1024 * 1024 * 1024
internal const val UPLOAD_PROGRESS_THRESHOLD = 200L * 1024 * 1024
internal class AttachmentTooLarge : IOException("Attachment exceeds 2 GB")

/** Counts actual bytes too: a provider may omit or lie about OpenableColumns.SIZE. */
internal class UploadInputStream(input: InputStream, private val checkActive: () -> Unit,
    private val onProgress: (Long) -> Unit) : FilterInputStream(input) {
    private var done = 0L
    private fun accepted(count: Int) {
        checkActive()
        if (count > 0) {
            done += count
            if (done > MAX_ATTACHMENT_BYTES) throw AttachmentTooLarge()
            onProgress(done)
        }
    }
    override fun read(): Int { checkActive(); return `in`.read().also { accepted(if (it < 0) -1 else 1) } }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkActive(); return `in`.read(b, off, len).also(::accepted)
    }
}

/** Workspace uploads never pass through filesDir/upload or a temporary directory. */
class ChatAttachmentRouter(private val context: Context, private val files: FilesManager,
    private val workspaces: WorkspaceRepository) {
    suspend fun route(uri: Uri, workspaceId: String?, provider: ProviderSetting?, model: Model?,
        onProgress: (name: String, done: Long, total: Long?) -> Unit = { _, _, _ -> }): RoutedAttachment = withContext(Dispatchers.IO) {
        val name = (runCatching { files.getFileNameFromUri(uri) }.getOrNull() ?: uri.lastPathSegment)?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "file"
        val mime = runCatching { files.getFileMimeType(uri) }.getOrNull()?.lowercase()
            ?.takeUnless { it.isBlank() || it == "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val size = sourceSize(uri)
        if (size != null && size > MAX_ATTACHMENT_BYTES) return@withContext RoutedAttachment.Rejected(RoutedAttachment.Reason.TOO_LARGE)
        val target = attachmentTarget(name, mime, provider, model)
        if (target != AttachmentTarget.WORKSPACE) {
            val coroutine = currentCoroutineContext()
            val local = try { files.createChatFilesByContents(listOf(uri)) { input ->
                UploadInputStream(input, { coroutine.ensureActive() }) { done -> onProgress(name, done, size) }
            }.firstOrNull() } catch (_: AttachmentTooLarge) {
                return@withContext RoutedAttachment.Rejected(RoutedAttachment.Reason.TOO_LARGE)
            }
                ?: return@withContext RoutedAttachment.Rejected(RoutedAttachment.Reason.UNREADABLE)
            return@withContext RoutedAttachment.ChatPart(listOf(when (target) {
                AttachmentTarget.IMAGE -> UIMessagePart.Image(local.toString())
                AttachmentTarget.AUDIO -> UIMessagePart.Audio(local.toString())
                AttachmentTarget.VIDEO -> UIMessagePart.Video(local.toString())
                else -> UIMessagePart.Document(local.toString(), name, mime)
            }))
        }
        val coroutine = currentCoroutineContext()
        try {
            if (workspaceId == null || workspaces.getById(workspaceId) == null)
                return@withContext RoutedAttachment.Rejected(RoutedAttachment.Reason.NO_WORKSPACE)
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext RoutedAttachment.Rejected(RoutedAttachment.Reason.UNREADABLE)
            input.use {
                val monitored = UploadInputStream(it, { coroutine.ensureActive() }) { done -> onProgress(name, done, size) }
                val entry = workspaces.importFile(workspaceId, WorkspaceStorageArea.FILES, "uploads/${LocalDate.now()}", name, monitored)
                RoutedAttachment.WorkspaceUpload("/${entry.path.trimStart('/')}", entry.name, entry.sizeBytes, mime)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: AttachmentTooLarge) { RoutedAttachment.Rejected(RoutedAttachment.Reason.TOO_LARGE) }
        catch (_: Exception) { coroutine.ensureActive(); RoutedAttachment.Rejected(RoutedAttachment.Reason.COPY_FAILED) }
    }

    private fun sourceSize(uri: Uri): Long? = runCatching {
        if (uri.scheme == "file") File(requireNotNull(uri.path)).length()
        else context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index).takeIf { it >= 0 } else null
        }
    }.getOrNull()
}
