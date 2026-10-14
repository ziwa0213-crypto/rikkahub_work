package me.rerere.rikkahub.ui.pages.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.data.model.GroupMember
import me.rerere.rikkahub.data.model.GroupMemberRef
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.group.components.GroupTags
import me.rerere.rikkahub.ui.pages.group.components.NewGroupDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class GroupLayoutTest {
    @Test
    fun newGroupUsesMemberLibraryOrderRatherThanCreationDate() {
        val early = GroupMember(name = "Early", createdAt = 1)
        val late = GroupMember(name = "Late", createdAt = 2)
        val settings = Settings(groupMembers = listOf(late, early))
        val initial = Group(name = "Group", members = listOf(GroupMemberRef(early.id), GroupMemberRef(late.id)))
        var created: Group? = null
        compose.setContent {
            MaterialTheme {
                NewGroupDialog(settings, onDismiss = {}, onCreate = { created = it }, onCreateMember = {}, initial = initial)
            }
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.group_create)
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(listOf(late.id, early.id), requireNotNull(created).members.map { it.memberId }) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardGroupMeasuresEmptyAndPopulatedTagsWithoutSubcomposition() {
        val tags = List(3) { Tag(Uuid.random(), "Tag $it") }
        val settings = Settings(groupTags = tags)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(200.dp)) {
                    CardGroup {
                        item(headlineContent = { Text("Empty tags") },
                            supportingContent = { GroupTags(settings, emptyList()) })
                        item(headlineContent = { Text("Populated tags") },
                            supportingContent = { GroupTags(settings, tags.map { it.id }) })
                    }
                }
            }
        }
        compose.onNodeWithText("Empty tags").assertIsDisplayed()
        compose.onNodeWithText("Tag 0").assertIsDisplayed()
        compose.onNodeWithText("Tag 1").assertIsDisplayed()
        compose.onNodeWithText("+1").assertIsDisplayed()
    }

    @Test
    fun creatingGroupDisplaysConfigurationWithEmptyTags() {
        val member = GroupMember(name = "Member")
        val settings = Settings(groupMembers = listOf(member))
        val initial = Group(name = "New group", members = listOf(GroupMemberRef(member.id)))
        var created: Group? = null
        compose.setContent {
            MaterialTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    NewGroupDialog(settings, onDismiss = {}, onCreate = {
                        created = it
                        showDialog = false
                    }, onCreateMember = {}, initial = initial)
                } else {
                    CardGroup {
                        item(headlineContent = { Text("Created group") },
                            supportingContent = { GroupTags(settings, created!!.tags) })
                    }
                }
            }
        }
        val createLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.group_create)
        compose.onNodeWithText(createLabel).performClick()
        compose.onNodeWithText("Created group").assertIsDisplayed()
        compose.runOnIdle {
            val saved = requireNotNull(created)
            assertEquals(initial.id, saved.id)
            assertEquals(initial.members, saved.members)
            assertEquals(emptyList<Uuid>(), saved.tags)
        }
    }
}
