package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.ui.webview.WebView
import me.rerere.ui.webview.rememberWebViewState
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

private const val MAX_WORKSPACE_PREVIEW_BYTES = 1024L * 1024

/**
 * 工作区文本文件编辑/预览页.
 *
 * FILES 区文件可编辑并保存; LINUX (rootfs) 区文件仅只读预览 (readOnly), 避免误改系统文件.
 */
@Composable
fun WorkspaceFileEditorPage(
    id: String,
    area: WorkspaceStorageArea,
    path: String,
) {
    val repository = koinInject<WorkspaceRepository>()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val editable = area == WorkspaceStorageArea.FILES
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val isMarkdown = extension in setOf("md", "markdown")
    val isHtml = extension in setOf("html", "htm")
    val supportSvg = extension == "svg"
    val supportsPreview = isMarkdown || isHtml || supportSvg
    var showPreview by rememberSaveable(id, area, path) { mutableStateOf(supportsPreview) }
    var tooLarge by remember(id, area, path) { mutableStateOf(false) }

    // 不能用 rememberTextFieldState: 它会把全文存进 saved state Bundle, 大文件切后台时触发 TransactionTooLargeException (#1953).
    // 内容本就由下方 LaunchedEffect 从磁盘加载, 无需 saveable.
    val textState = remember(id, area, path) { TextFieldState() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id, area, path) {
        loading = true
        loadError = null
        runCatching {
            if (isMarkdown) {
                tooLarge = repository.fileSize(id, area, path) > MAX_WORKSPACE_PREVIEW_BYTES
                if (tooLarge) showPreview = false
            }
            repository.readTextForPreview(id, area, path)
        }.onSuccess { content ->
            textState.setTextAndPlaceCursorAtEnd(content)
            loading = false
        }.onFailure {
            if (it is CancellationException) throw it
            loadError = it.message ?: "读取文件失败"
            loading = false
        }
    }

    LaunchedEffect(tooLarge) { if (tooLarge) showPreview = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (supportsPreview && !loading && loadError == null && !(isMarkdown && tooLarge)) {
                        TextButton(onClick = {
                            if (isMarkdown) {
                                tooLarge = textState.text.toString().toByteArray(Charsets.UTF_8).size > MAX_WORKSPACE_PREVIEW_BYTES
                            }
                            if (!(isMarkdown && tooLarge)) showPreview = !showPreview
                        }) {
                            Text(stringResource(if (showPreview) R.string.workspace_preview_source else R.string.workspace_preview_preview))
                        }
                    }
                    if (editable && !loading && loadError == null) {
                        TextButton(
                            onClick = {
                                if (saving) return@TextButton
                                saving = true
                                scope.launch {
                                    runCatching {
                                        repository.writeText(
                                            id = id,
                                            path = path,
                                            text = textState.text.toString(),
                                            overwrite = true,
                                        )
                                    }.onSuccess {
                                        if (isMarkdown) {
                                            tooLarge = textState.text.toString().toByteArray(Charsets.UTF_8).size > MAX_WORKSPACE_PREVIEW_BYTES
                                        }
                                        toaster.show("已保存", type = ToastType.Success)
                                    }.onFailure {
                                        toaster.show(it.message ?: "保存失败", type = ToastType.Error)
                                    }
                                    saving = false
                                }
                            },
                            enabled = !saving,
                        ) {
                            Text("Save")
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (isMarkdown && tooLarge) Text(
                stringResource(R.string.workspace_preview_too_large),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                loadError != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp),
                ) {
                    Text(
                        text = loadError ?: "",
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                showPreview && !tooLarge && isMarkdown -> WorkspaceMarkdownPreview(
                    content = textState.text.toString(), workspaceId = id, area = area, path = path,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )

                showPreview -> WorkspaceWebPreview(
                    content = textState.text.toString(),
                    isSvg = supportSvg,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )

                else -> TextField(
                    state = textState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .imePadding(),
                    readOnly = !editable,
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = JetbrainsMono,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceWebPreview(
    content: String,
    isSvg: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(
        data = content,
        baseUrl = "https://workspace-preview.invalid/",
        mimeType = if (isSvg) "image/svg+xml" else "text/html",
        settings = {
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )
    WebView(state = state, modifier = modifier)
}
