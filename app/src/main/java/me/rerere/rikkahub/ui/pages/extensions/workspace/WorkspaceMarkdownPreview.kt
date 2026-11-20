package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.ui.context.LocalWorkspaceBaseDir
import me.rerere.rikkahub.ui.context.LocalWorkspaceStorageArea
import me.rerere.workspace.WorkspaceStorageArea

@Composable
internal fun WorkspaceMarkdownPreview(
    content: String,
    workspaceId: String,
    area: WorkspaceStorageArea,
    path: String,
    modifier: Modifier = Modifier,
) {
    val baseDir = remember(path) { path.substringBeforeLast('/', "") }
    CompositionLocalProvider(
        LocalWorkspaceId provides workspaceId,
        LocalWorkspaceBaseDir provides baseDir,
        LocalWorkspaceStorageArea provides area,
    ) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            MarkdownBlock(content)
        }
    }
}
