package me.rerere.rikkahub.ui.context

import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorGroupTest {
    @Test
    fun returnsToOriginalChatWithoutReplacingItsIdOrDraftRoute() {
        val chat = Screen.Chat(id = "original-chat", text = "draft")
        val stack = mutableListOf<NavKey>(chat, Screen.Groups, Screen.GroupConfig("group"))

        assertTrue(Navigator(stack).popBackToChat())

        assertEquals(listOf(chat), stack)
    }

    @Test
    fun returnsToMostRecentChatWithoutDroppingEarlierRoutes() {
        val first = Screen.Chat(id = "first")
        val current = Screen.Chat(id = "current")
        val stack = mutableListOf<NavKey>(first, Screen.Setting, current, Screen.GroupMembers, Screen.Groups)

        assertTrue(Navigator(stack).popBackToChat())

        assertEquals(listOf(first, Screen.Setting, current), stack)
    }

    @Test
    fun missingChatDoesNotChangeTheStack() {
        val stack = mutableListOf<NavKey>(Screen.Groups, Screen.GroupConfig("group"))
        val original = stack.toList()

        assertFalse(Navigator(stack).popBackToChat())

        assertEquals(original, stack)
    }
}
