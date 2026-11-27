package me.rerere.rikkahub.data.st

import kotlinx.coroutines.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.transformers.*
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.normalized
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.CharacterPersona
import me.rerere.rikkahub.data.st.macro.*
import me.rerere.rikkahub.data.st.regex.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class StPipelineTest {
    @Test fun toolStepsDoNotRepeatVariableMacrosAndPromptRegexDoesNotAlterStorage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val registry = MacroStoreRegistry(ScopeVariableWriter(scope) { _, _ -> })
            val general = RegexScript(findRegex = "/x/g", replaceString = "xx", placement = listOf(1, 2))
            val prompt = RegexScript(findRegex = "/xx/g", replaceString = "prompt", placement = listOf(1), promptOnly = true)
            val char = Character(name = "Alice", nickname = "Ali", stRegexScripts = listOf(general, prompt))
            val settings = Settings(characters = listOf(char))
            val original = UIMessage.user("x")
            val stored = applyCharacterMessageRegex(original, char, settings)
            assertEquals("xx", stored.toText())
            val conversationId = Uuid.random()
            val turn = CharacterStTurn(char, settings, conversationId, registry, listOf(stored))
            turn.captureHistory(listOf(stored))
            repeat(3) { step ->
                val sys = UIMessage.system("{{incvar::n}} {{char}} dynamic=$step").copy(isSynthetic = true)
                val sent = turn.transformMessages(listOf(sys, stored))
                assertEquals("prompt", sent.last().toText())
                assertEquals("1", registry.store("conversation:$conversationId") { emptyMap() }.get("n"))
                assertTrue(sent.first().toText().contains("Ali"))
                assertTrue(sent.first().toText().startsWith("1"))
            }
            assertEquals("xx", stored.toText())
            assertEquals("xx", turn.transformOutput(UIMessage.assistant("x")).toText())
        } finally { scope.cancel() }
    }

    @Test fun regexMacrosUsePersonaAndDisplayDoesNotPersistVariables() {
        val persona = CharacterPersona(name = "Bob")
        val regex = RegexScript(findRegex = "/{{char}}/g", replaceString = "{{user}}", placement = listOf(1, 2), substituteRegex = 2)
        val char = Character(name = "Alias.+", personaId = persona.id, stRegexScripts = listOf(regex))
        val settings = Settings(characters = listOf(char), characterPersonas = listOf(persona))
        assertEquals("Bob", applyCharacterMessageRegex(UIMessage.user("Alias.+"), char, settings).toText())
        assertEquals("AliasXX", applyCharacterMessageRegex(UIMessage.user("AliasXX"), char, settings).toText())
        val display = char.copy(stRegexScripts = listOf(regex.copy(markdownOnly = true, minDepth = 2)))
        assertEquals("Alias.+", stDisplayText("Alias.+", display, settings, MessageRole.ASSISTANT, false, depth = 1))
        assertEquals("Bob", stDisplayText("Alias.+", display, settings, MessageRole.ASSISTANT, false, depth = 2))
    }

    @Test fun incompleteRegexDraftsSurviveSettingsNormalizationAndGetStableIds() {
        val char = Character(stRegexScripts = listOf(RegexScript()))
        val first = Settings(characters = listOf(char), stRegexScripts = listOf(RegexScript())).normalized()
        assertEquals(1, first.characters.single().stRegexScripts.size)
        assertEquals(1, first.stRegexScripts.size)
        assertTrue(first.stRegexScripts.single().id.isNotBlank())
        assertEquals(first.stRegexScripts, first.normalized().stRegexScripts)
    }

}
