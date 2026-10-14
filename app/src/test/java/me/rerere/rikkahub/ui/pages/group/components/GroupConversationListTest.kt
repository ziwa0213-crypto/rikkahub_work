package me.rerere.rikkahub.ui.pages.group.components

import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GROUP_CHAT_ASSISTANT_ID
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GroupConversationListTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun conversation(title: String, at: String, pinned: Boolean = false) = Conversation(
        assistantId = GROUP_CHAT_ASSISTANT_ID, title = title, messageNodes = emptyList(),
        createAt = Instant.parse(at), isPinned = pinned)
    private fun List<GroupConversationListItem>.conversations() = filterIsInstance<GroupConversationListItem.Item>().map { it.conversation }

    @Test fun pinnedRowsPrecedeNewerRowsAndDateHeadersUseLocalTime() {
        val pinned = conversation("Pinned", "2026-10-01T00:00:00Z", true)
        val old = conversation("Old", "2026-10-08T15:59:59Z")
        val new = conversation("New", "2026-10-08T16:00:00Z")
        val source = listOf(old, pinned, new)
        val rows = buildGroupConversationListItems(source, zone)
        assertEquals(listOf(GroupConversationListItem.PinnedHeader, GroupConversationListItem.Item(pinned),
            GroupConversationListItem.DateHeader(LocalDate.of(2026, 10, 9)), GroupConversationListItem.Item(new),
            GroupConversationListItem.DateHeader(LocalDate.of(2026, 10, 8)), GroupConversationListItem.Item(old)), rows)
        assertEquals(listOf(old, pinned, new), source)
    }

    @Test fun pinningOnlyChangesDisplayOrderAndAllPinnedHaveOneHeader() {
        val first = conversation("A", "2026-10-01T00:00:00Z")
        val second = conversation("B", "2026-10-02T00:00:00Z")
        val source = listOf(first, second)
        assertEquals(source.reversed(), buildGroupConversationListItems(source, zone).conversations())
        val pinned = source.map { it.copy(isPinned = true) }
        val rows = buildGroupConversationListItems(pinned, zone)
        assertEquals(1, rows.count { it == GroupConversationListItem.PinnedHeader })
        assertTrue(rows.filterIsInstance<GroupConversationListItem.DateHeader>().isEmpty())
        assertEquals(pinned.reversed(), rows.conversations())
        assertEquals(listOf(first, second), source)
    }

    @Test fun yearBoundaryAndStableEqualDates() {
        val before = conversation("Before", "2026-12-31T15:59:59Z")
        val after = conversation("After", "2026-12-31T16:00:00Z")
        val source = listOf(before, after)
        assertEquals(listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)),
            buildGroupConversationListItems(source, zone).filterIsInstance<GroupConversationListItem.DateHeader>().map { it.date })
        assertEquals(1, buildGroupConversationListItems(source, ZoneId.of("UTC")).filterIsInstance<GroupConversationListItem.DateHeader>().size)
        assertEquals(source, buildGroupConversationListItems(listOf(before, after.copy(createAt = before.createAt)), zone).conversations().let { it.map { row -> source.find { c -> c.id == row.id } } })
        assertTrue(buildGroupConversationListItems(emptyList(), zone).isEmpty())
    }
}
