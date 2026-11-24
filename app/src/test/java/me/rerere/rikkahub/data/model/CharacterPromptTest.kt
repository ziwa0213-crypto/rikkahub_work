package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPromptTest {
    @Test
    fun `prompt modules follow the fixed assembly order`() {
        val character = Character(
            name = "Mira",
            promptModules = listOf(
                PromptModule(kind = PromptModuleKind.CUSTOM, content = "custom", order = 0),
                PromptModule(kind = PromptModuleKind.GUARDRAIL, content = "guardrail"),
                PromptModule(kind = PromptModuleKind.OUTPUT_RULES, content = "rules"),
                PromptModule(kind = PromptModuleKind.STYLE, content = "style"),
                PromptModule(kind = PromptModuleKind.SCENARIO, content = "scenario", order = 0),
                PromptModule(kind = PromptModuleKind.PERSONA, content = "persona", order = 0),
            ),
        )

        val assembled = character.buildPromptModules(styleContent = "style", userNickname = "Kai", persona = "Reader")

        assertEquals(
            listOf("persona", "scenario", "style", "rules", "guardrail", "custom", "## User persona\nReader"),
            assembled.split("\n\n"),
        )
        assertTrue("Mira" !in assembled)
        assertTrue("Kai" !in assembled)
    }

    @Test
    fun `disabled modules are omitted and character variables are filled`() {
        val character = Character(
            name = "Mira",
            promptModules = listOf(
                PromptModule(kind = PromptModuleKind.PERSONA, content = "I am {{char}}."),
                PromptModule(kind = PromptModuleKind.OUTPUT_RULES, content = "Speak plainly."),
                PromptModule(kind = PromptModuleKind.CUSTOM, content = "User: {{user}}; persona: {{persona}}; rules: {{rules}}"),
                PromptModule(kind = PromptModuleKind.CUSTOM, content = "hidden", enabled = false),
            ),
        )

        val assembled = character.buildPromptModules(
            userNickname = "Rin",
            persona = "A careful reader",
        )

        assertTrue("I am Mira." in assembled)
        assertTrue("User: Rin; persona: A careful reader" in assembled)
        assertTrue("rules: Speak plainly." in assembled)
        assertFalse("hidden" in assembled)
        assertFalse("{{" in assembled)
    }

    @Test
    fun `legacy system prompt remains the effective persona module`() {
        val character = Character(characterSystemPrompt = "Legacy identity for {{char}}", name = "Mira")

        assertEquals("Legacy identity for Mira", character.buildPromptModules())
        assertEquals(PromptModuleKind.PERSONA, character.effectivePromptModules().single().kind)
    }
}
