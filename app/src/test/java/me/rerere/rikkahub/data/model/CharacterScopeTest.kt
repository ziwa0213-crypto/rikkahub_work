package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.memoryConsolidationAssistants
import me.rerere.rikkahub.data.ai.resolveChatTurnRoute
import me.rerere.rikkahub.data.ai.transformers.applyCharacterInjections
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.UiMode
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.memoryId
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class CharacterScopeTest {
    private val workModel = Model(modelId = "work")
    private val roleModel = Model(modelId = "role")
    private val lorebook = Lorebook()
    private val work = Assistant(chatModelId = workModel.id, lorebookIds = setOf(lorebook.id))
    private val role = Character(chatModelId = workModel.id, characterSystemPrompt = "Role identity",
        firstMessage = "Role greeting", lorebookIds = setOf(lorebook.id))
    private val settings = Settings(uiMode = UiMode.ROLE, experimentalCharacterMode = true, characterId = role.id, characters = listOf(role),
        assistants = listOf(work), assistantId = work.id, chatModelId = workModel.id, lorebooks = listOf(lorebook),
        providers = listOf(ProviderSetting.OpenAI(models = listOf(workModel, roleModel))))

    @Test fun newChatsAndOpeningKeepSeparateOwners() {
        val id = Uuid.random()
        val created = checkNotNull(settings.newChatConversation(id))
        assertEquals(role.id, settings.initialChatConversation(id).assistantId)
        assertEquals(role.id, created.assistantId)
        assertEquals("Role greeting", created.currentMessages.single().toText())
        assertNull(created.workAssistantSelection(settings))
        assertEquals(work.id, Conversation.ofId(id, assistantId = work.id).workAssistantSelection(settings))
        assertNull(settings.copy(characters = emptyList()).newChatConversation(id))
    }

    @Test fun chatPageModeFollowsLoadedConversationIdentity() {
        val workChat = Conversation.ofId(Uuid.random(), assistantId = work.id)
        val roleChat = Conversation.ofId(Uuid.random(), assistantId = role.id)

        assertEquals(UiMode.CHAT, workChat.uiModeForChatPage(settings))
        assertEquals(UiMode.ROLE, roleChat.uiModeForChatPage(settings))
        assertEquals(UiMode.CHAT, workChat.uiModeForChatPage(settings.copy(uiMode = UiMode.ROLE)))
    }

    @Test fun roleRequestUsesBoundModelAndOwnIdentity() {
        val conv = Conversation.ofId(Uuid.random(), assistantId = role.id).bindConfig(settings)
            .copy(config = ConversationConfig(chatModelId = roleModel.id))
        val request = resolveChatTurnRoute(settings, conv, memoryScope = "wrong")
        assertEquals(role.id, request.assistant.id)
        assertEquals(roleModel.id, request.model?.id)
        assertEquals("Role identity", request.assistant.systemPrompt)
        assertEquals("Role greeting", request.assistant.presetMessages.single().toText())
        assertEquals(setOf(lorebook.id), request.assistant.lorebookIds)
        assertEquals(role.id.toString(), request.memoryScope)
        assertTrue(request.allowPhoneUse)
        assertEquals(role.id.toString(), settings.memoryConsolidationAssistants().single { it.id == role.id }.memoryId)
    }

    @Test fun filteringDoesNotEraseBindingsAndDeletedRolesCannotGenerate() {
        val workSettings = settings.copy(experimentalCharacterMode = false, uiMode = UiMode.CHAT)
        val conv = Conversation.ofId(Uuid.random(), assistantId = work.id)
            .copy(lorebookIds = setOf(lorebook.id)).bindConfig(workSettings)
        assertTrue(resolveChatTurnRoute(workSettings, conv).assistant.lorebookIds.isEmpty())
        val view = workSettings.getAssistantOf(conv)
        for (update in listOf(view.copy(chatModelId = roleModel.id), view.copy(enabledSkills = setOf("new")),
            view.copy(toolApprovalMode = ToolApprovalMode.AskAll), view.copy(workspaceId = Uuid.random()))) {
            assertEquals(setOf(lorebook.id), conv.withAssistantUpdate(update, workSettings).lorebookIds)
        }
        assertEquals(setOf(lorebook.id), workSettings.assistants.single().lorebookIds)
        val deleted = workSettings.copy(characters = emptyList(), deletedCharacterIds = setOf(role.id))
        val history = conv.copy(assistantId = role.id)
        assertNull(history.workAssistantSelection(deleted))
        assertEquals(role.id, deleted.getAssistantOf(history).id)
        assertNull(deleted.getChatModelOf(history))
        assertTrue(runCatching { resolveChatTurnRoute(deleted, history) }.isFailure)
    }

    @Test fun groupNeverReceivesLorebooksRegardlessOfCharacterExperimentGate() {
        val legacyBinding = setOf(lorebook.id)
        val conversation = Conversation.ofId(Uuid.random(), assistantId = GROUP_CHAT_ASSISTANT_ID)
            .copy(lorebookIds = legacyBinding)
        for (experimental in listOf(false, true)) {
            val currentSettings = settings.copy(experimentalCharacterMode = experimental)
            assertFalse(conversation.canUseLorebooks(currentSettings))
            val groupAssistant = Assistant(id = GROUP_CHAT_ASSISTANT_ID, lorebookIds = legacyBinding)
            val route = resolveChatTurnRoute(
                settings = currentSettings,
                conversation = conversation,
                assistantOverride = groupAssistant,
                mode = AssistantMode.GROUP,
            )
            assertTrue(route.assistant.lorebookIds.isEmpty())
        }
        // Existing binding data remains untouched for backup compatibility.
        assertEquals(legacyBinding, conversation.lorebookIds)
    }

    @Test fun workAndCharacterLorebookOwnershipSwitchesWithoutMutatingStoredData() {
        val workConversation = Conversation.ofId(Uuid.random(), assistantId = work.id)
        val roleConversation = Conversation.ofId(Uuid.random(), assistantId = role.id)
        for ((experimental, expectedWork, expectedRole) in listOf(
            Triple(false, false, true),
            Triple(true, false, true),
        )) {
            val current = settings.copy(experimentalCharacterMode = experimental)
            assertEquals(expectedWork, workConversation.canUseLorebooks(current))
            assertEquals(expectedRole, roleConversation.canUseLorebooks(current))
            assertEquals(setOf(lorebook.id), current.assistants.single().lorebookIds)
            assertEquals(setOf(lorebook.id), current.characters.single().lorebookIds)
            assertEquals(listOf(lorebook), current.lorebooks)
        }
    }

    @Test fun roleQuickMessagesAreLocalAndWorkspaceOwnersIsolated() {
        val conv = Conversation.ofId(Uuid.random(), assistantId = role.id).bindConfig(settings)
        val quick = Uuid.random()
        val updated = conv.withAssistantUpdate(settings.getAssistantOf(conv).copy(quickMessageIds = setOf(quick)), settings)
        assertEquals(setOf(quick), settings.getAssistantOf(updated).quickMessageIds)
        assertTrue(settings.findCharacter(role.id)!!.quickMessageIds.isEmpty())
        val workspace = WorkspaceEntity(id = "id", name = "name", root = "root", owner = "character", createdAt = 0, updatedAt = 0)
        assertEquals(listOf(WorkspaceOwnerScope.CHARACTER), WorkspaceOwnerScope.entries.filter { it.includes(workspace) })
        assertEquals(setOf(WorkspaceOwnerScope.CHAT, WorkspaceOwnerScope.GROUP),
            WorkspaceOwnerScope.entries.filter { it.includes(workspace.copy(owner = null)) }.toSet())
    }

    @Test fun copyReplacesNodeIdsAndNestedAttachments() {
        val local = "file:///original/image.png"
        val nested = "file:///original/tool.png"
        val image = UIMessage.user("image").copy(parts = listOf(UIMessagePart.Image(local)))
        val tool = UIMessage.assistant("tool").copy(parts = listOf(UIMessagePart.Tool(toolCallId = "id", toolName = "read",
            input = "{}", output = listOf(UIMessagePart.Image(nested)))))
        val original = Conversation.ofId(Uuid.random(), assistantId = work.id, messages = listOf(image.toMessageNode(),
            MessageNode(messages = listOf(UIMessage.assistant("old branch"), tool), selectIndex = 1)))
        val copy = original.duplicateForCharacter(role.id, Uuid.random(), emptySet()) { it.replace("/original/", "/copy/") }
        assertNotEquals(original.id, copy.id)
        assertEquals(role.id, copy.assistantId)
        assertTrue(original.messageNodes.map { it.id }.intersect(copy.messageNodes.map { it.id }.toSet()).isEmpty())
        assertEquals(1, copy.messageNodes.last().selectIndex)
        assertEquals(setOf(local, nested), original.messageNodes.flatMap { it.messages }.flatMap { it.parts }.localFileUrls())
        assertEquals(setOf("file:///copy/image.png", "file:///copy/tool.png"),
            copy.messageNodes.flatMap { it.messages }.flatMap { it.parts }.localFileUrls())
    }

    @Test fun profileConversionIsRepeatableAndStyleUsesSharedDepthRules() {
        val rows = listOf(RowMemory(2, "Second"), RowMemory(1, "First\nline"))
        val profile = checkNotNull(mergeRowMemoriesToProfile(rows, scope = "work"))
        assertEquals(listOf("- [stated] First line", "- [stated] Second"), profile.lines().filter { it.startsWith("- ") })
        assertEquals(profile, mergeRowMemoriesToProfile(rows, profile, "work"))
        val pack = DEFAULT_STYLE_PACKS.single()
        assertEquals(8, pack.rules.size)
        val messages = listOf(UIMessage.system("system"), UIMessage.user("A"), UIMessage.assistant("B"), UIMessage.user("C"))
        val styled = role.copy(stylePackId = pack.id)
        val result = applyCharacterInjections(messages, styled, listOf(pack), "Style")
        assertEquals(2, result.indexOfFirst { it.toText().startsWith("## Style") })
        assertEquals(messages, applyCharacterInjections(messages, styled.copy(styleNotesEnabled = false), listOf(pack), "Style"))
    }
}
