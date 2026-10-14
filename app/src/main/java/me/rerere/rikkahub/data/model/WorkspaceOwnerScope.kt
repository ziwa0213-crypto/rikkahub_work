package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity

enum class WorkspaceOwnerScope(val value: String) {
    CHAT("chat"), GROUP("group");

    fun includes(workspace: WorkspaceEntity): Boolean = workspace.owner == null || workspace.owner == value
}
