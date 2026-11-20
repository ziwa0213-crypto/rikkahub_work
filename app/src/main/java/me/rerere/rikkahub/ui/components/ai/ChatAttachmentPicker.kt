package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.ChatAttachmentRouter
import me.rerere.rikkahub.data.files.RoutedAttachment
import me.rerere.rikkahub.data.files.UPLOAD_PROGRESS_THRESHOLD
import me.rerere.rikkahub.data.files.toDocument
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.ui.sketch.SketchDialog
import me.rerere.ui.sketch.SketchResult
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

internal data class ChatAttachmentPickerActions(
    val onTakePicture: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit,
    val onPickAudio: () -> Unit,
    val onPickFile: () -> Unit,
    val onSketch: () -> Unit,
)

@Composable
internal fun rememberChatAttachmentPickerActions(
    inputState: ChatInputState,
    setting: Settings,
    onAttachmentAdded: () -> Unit,
    workspaceId: String?,
    provider: ProviderSetting?,
    model: Model?,
    onBindWorkspace: () -> Unit,
    initialUris: List<Uri> = emptyList(),
    onInitialUrisConsumed: () -> Unit = {},
): ChatAttachmentPickerActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val workspaces: WorkspaceRepository = koinInject()
    val router = remember(context, filesManager, workspaces) { ChatAttachmentRouter(context, filesManager, workspaces) }
    val scope = rememberCoroutineScope()
    val uploadState = remember { MutableStateFlow<Triple<String, Long, Long?>?>(null) }
    val upload by uploadState.collectAsState()
    var transfer by remember { mutableStateOf<Job?>(null) }
    var needsWorkspace by remember { mutableStateOf(false) }
    DisposableEffect(inputState, workspaceId) {
        onDispose { transfer?.cancel() }
    }
    fun routeFiles(uris: List<Uri>) {
        if (transfer?.isCompleted == false) return
        transfer = scope.launch {
            try {
                for (uri in uris) {
                    val result = router.route(uri, workspaceId, provider, model) { name, done, total ->
                        if ((total ?: done) > UPLOAD_PROGRESS_THRESHOLD) uploadState.value = Triple(name, done, total)
                    }
                    when (result) {
                        is RoutedAttachment.ChatPart -> {
                            inputState.messageContent = inputState.messageContent + result.parts
                            onAttachmentAdded()
                        }
                        is RoutedAttachment.WorkspaceUpload -> {
                            inputState.addFiles(listOf(result.toDocument(workspaceId)))
                            onAttachmentAdded()
                        }
                        is RoutedAttachment.Rejected -> {
                            if (result.reason == RoutedAttachment.Reason.NO_WORKSPACE) needsWorkspace = true
                            else toaster.show(resources.getString(when (result.reason) {
                                RoutedAttachment.Reason.TOO_LARGE -> R.string.workspace_upload_too_large
                                RoutedAttachment.Reason.COPY_FAILED -> R.string.workspace_upload_failed
                                else -> R.string.workspace_upload_unreadable
                            }), type = ToastType.Error)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                toaster.show(resources.getString(R.string.workspace_upload_failed), type = ToastType.Error)
            } finally { uploadState.value = null }
        }
    }
    androidx.compose.runtime.LaunchedEffect(initialUris) { if (initialUris.isNotEmpty()) { routeFiles(initialUris); onInitialUrisConsumed() } }
    upload?.let { (name, done, total) ->
        AlertDialog(onDismissRequest = {}, title = { Text(resources.getString(R.string.workspace_upload_progress)) },
            text = { Column {
                Text(name)
                if (total != null && total > 0) LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) })
                else LinearProgressIndicator()
                Text(if (total != null && total > 0) "${(done * 100 / total).coerceIn(0, 100)}%" else "${done / 1048576} MB")
            } }, confirmButton = {}, dismissButton = {
                TextButton(onClick = { transfer?.cancel(); uploadState.value = null }) { Text(resources.getString(R.string.cancel)) }
            })
    }
    if (needsWorkspace) AlertDialog(onDismissRequest = { needsWorkspace = false },
        text = { Text(resources.getString(R.string.workspace_upload_no_workspace)) },
        confirmButton = { TextButton(onClick = { needsWorkspace = false; onBindWorkspace() }) { Text(resources.getString(R.string.workspace_upload_bind)) } },
        dismissButton = { TextButton(onClick = { needsWorkspace = false }) { Text(resources.getString(R.string.cancel)) } })
    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val launchCameraCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            onAttachmentAdded()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                inputState.addImages(filesManager.createChatFilesByContents(listOf(cameraOutputUri!!)))
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                onAttachmentAdded()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onTakePicture: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            onAttachmentAdded()
        },
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (!setting.displaySetting.skipCropImage && selectedUris.size == 1) {
                    launchImageCrop(selectedUris.first())
                } else {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
                    onAttachmentAdded()
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                routeFiles(selectedUris)
            }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addAudios(filesManager.createChatFilesByContents(selectedUris))
                onAttachmentAdded()
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                routeFiles(uris)
            }
        }

    var showSketch by remember { mutableStateOf(false) }
    if (showSketch) {
        SketchDialog(
            onDismiss = { showSketch = false },
            onConfirm = { result ->
                showSketch = false
                scope.launch {
                    inputState.addImages(listOfNotNull(filesManager.createChatFileBySketch(result)))
                    onAttachmentAdded()
                }
            },
        )
    }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onPickVideo = { videoPickerLauncher.launch("video/*") },
        onPickAudio = { audioPickerLauncher.launch("audio/*") },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        onSketch = { showSketch = true },
    )
}

/** 把画板的结果存成聊天附件。 */
internal suspend fun FilesManager.createChatFileBySketch(result: SketchResult): Uri? = withContext(Dispatchers.IO) {
    createChatFilesByByteArrays(
        byteArrays = listOf(result.encode()),
        displayName = "sketch.${result.extension}",
        mimeType = result.mimeType,
    ).firstOrNull()
}
