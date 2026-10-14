package me.rerere.rikkahub.ui.context

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceRouteTest {
    @Test
    fun restoredRoutesPreserveChatGroupAndAllScopes() {
        for (scope in listOf(null, "chat", "group")) {
            val route = Screen.Workspaces(scope)
            val restored = Json.decodeFromString<Screen.Workspaces>(Json.encodeToString(route))
            assertEquals(scope, restored.scope)
            assertEquals(route, restored)
        }
        assertNull(Json.decodeFromString<Screen.Workspaces>("{}").scope)
    }

    @Test
    fun returningFromDetailKeepsTheSourceManagementScope() {
        for (scope in listOf(null, "chat", "group")) {
            val route = Screen.Workspaces(scope)
            val stack = mutableListOf<androidx.navigation3.runtime.NavKey>(
                Screen.Chat(id = "original-chat"), route, Screen.WorkspaceDetail("workspace")
            )
            Navigator(stack).popBackStack()
            assertEquals(route, stack.last())
        }
    }
}
