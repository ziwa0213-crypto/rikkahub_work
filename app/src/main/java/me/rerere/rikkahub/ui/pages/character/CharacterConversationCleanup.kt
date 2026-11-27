package me.rerere.rikkahub.ui.pages.character

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.uuid.Uuid

/** Only commit the role/tombstone change after every owner has finished both cleanup steps. */
internal suspend fun cleanupCharacterConversations(
    ownerIds: Set<Uuid>,
    clearSessions: suspend (Uuid) -> Unit,
    deletePersisted: suspend (Uuid) -> Unit,
    onComplete: suspend () -> Unit,
) {
    for (owner in ownerIds.toSet()) {
        currentCoroutineContext().ensureActive()
        // Stop live generations and their final writes before checking persisted history again.
        clearSessions(owner)
        currentCoroutineContext().ensureActive()
        deletePersisted(owner)
    }
    currentCoroutineContext().ensureActive()
    onComplete()
}
