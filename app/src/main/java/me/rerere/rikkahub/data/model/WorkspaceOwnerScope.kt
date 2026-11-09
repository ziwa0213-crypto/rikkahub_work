package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity

enum class WorkspaceOwnerScope(val value: String) {
    CHAT("chat"), GROUP("group"), PULSE("pulse");

    fun includes(workspace: WorkspaceEntity): Boolean = when (this) {
        PULSE -> workspace.owner == value
        CHAT, GROUP -> workspace.owner == null || workspace.owner == value
    }
}
