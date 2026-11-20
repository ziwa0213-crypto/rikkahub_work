package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.workspace.WorkspaceStorageArea

/**
 * 当前内容所属的工作区 ID, 用于把 Markdown 里引用的 Rootfs 绝对路径 (如 /workspace/out.png) 解析成宿主机文件
 */
val LocalWorkspaceId = compositionLocalOf<String?> { null }

/** Relative to the selected workspace area; null retains chat's existing URL behaviour. */
val LocalWorkspaceBaseDir = compositionLocalOf<String?> { null }
val LocalWorkspaceStorageArea = compositionLocalOf { WorkspaceStorageArea.FILES }
