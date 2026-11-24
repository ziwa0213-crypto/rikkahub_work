package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

internal fun characterCopyId(source: Uuid, owner: Uuid): Uuid = Uuid.parse(
    java.util.UUID.nameUUIDFromBytes("f024:$source:$owner".encodeToByteArray()).toString(),
)

internal fun Conversation.duplicateForCharacter(
    owner: Uuid, newId: Uuid, existingTitles: Set<String>, copyFile: (String) -> String,
): Conversation = copy(
    id = newId,
    assistantId = owner,
    title = forkConversationTitle(title, existingTitles),
    messageNodes = messageNodes.map { node ->
        node.copy(id = Uuid.random(), messages = node.messages.map { message ->
            message.copy(parts = message.parts.map { it.mapLocalFileUrls(copyFile) })
        })
    },
    folderId = null,
    workspaceCwd = null,
    config = config?.copy(workspaceId = null),
    newConversation = false,
)
