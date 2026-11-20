package me.rerere.rikkahub.ui.components.message

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.files.uploadSize
import me.rerere.rikkahub.data.files.uploadWorkspaceId
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.toast.ToastType
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

@Composable
internal fun WorkspaceDocumentChip(document: UIMessagePart.Document) {
    val context = LocalContext.current
    val repository: WorkspaceRepository = koinInject()
    val id = document.uploadWorkspaceId ?: LocalWorkspaceId.current
    val path = document.url.trimStart('/')
    val nav = LocalNavController.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var size by remember(document, id) { mutableStateOf(document.uploadSize) }
    var missing by remember(document, id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    suspend fun check(): Boolean {
        if (id == null || path.isBlank()) { missing = true; return false }
        return try {
            // resolveFile is local-only; fileSize also handles SSH workspaces.
            size = repository.fileSize(id, WorkspaceStorageArea.FILES, path)
            missing = false; true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { missing = true; false }
    }
    fun open() { scope.launch {
        if (check()) nav.navigate(Screen.WorkspaceFileEditor(requireNotNull(id), WorkspaceStorageArea.FILES.name, path))
        else toaster.show(context.getString(R.string.workspace_upload_missing), type = ToastType.Error)
    } }
    LaunchedEffect(document.url, id) { check() }
    Box {
        Surface(shape = MaterialTheme.shapes.large,
            color = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.combinedClickable(onClick = ::open, onLongClick = { menu = true })) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(when {
                    document.mime.startsWith("video/") -> HugeIcons.Video01
                    document.mime in setOf("application/zip", "application/x-7z-compressed", "application/x-rar-compressed", "application/vnd.rar") -> HugeIcons.Package01
                    else -> HugeIcons.File02
                }, null, Modifier.size(20.dp))
                Text(if (missing) "${document.fileName} · ${stringResource(R.string.workspace_upload_missing)}"
                    else document.fileName + (size?.let { " · ${Formatter.formatFileSize(context, it)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.workspace_upload_open)) }, onClick = { menu = false; open() })
            DropdownMenuItem(text = { Text(stringResource(R.string.workspace_upload_copy_path)) }, onClick = {
                menu = false; context.writeClipboardText(document.url)
            })
        }
    }
}
