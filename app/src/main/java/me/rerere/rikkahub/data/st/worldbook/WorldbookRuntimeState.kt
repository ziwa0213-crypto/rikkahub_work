package me.rerere.rikkahub.data.st.worldbook

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

internal class TurnTracker {
    private var lastId: Uuid? = null
    var ordinal = 0
        private set

    fun observe(messages: List<UIMessage>): Int {
        val id = messages.lastOrNull { it.role == MessageRole.USER && !it.isSynthetic && !it.isContextCheckpoint }?.id
        if (id != null && id != lastId) { lastId = id; ordinal++ }
        return ordinal
    }
}

internal class WorldbookConversationState {
    val tracker = TurnTracker()
    var characterId: Uuid? = null
    val stickyUntil = mutableMapOf<String, Int>()
    val cooldownUntil = mutableMapOf<String, Int>()
    var frozenTurn = -1
    var frozen: ScanResult? = null
}

/** Only this conversation is cleared on deletion or owner change; switching pages cannot affect another chat. */
internal object WorldbookRuntimeState {
    private val conversations = mutableMapOf<Uuid, WorldbookConversationState>()
    @Synchronized fun conversation(id: Uuid) = conversations.getOrPut(id) { WorldbookConversationState() }
    @Synchronized fun clear(id: Uuid) { conversations.remove(id) }
}
