package me.rerere.rikkahub.data.st.macro

import org.junit.Assert.*
import org.junit.Test
import me.rerere.rikkahub.data.st.regex.*

class CharacterMacroTest {
    @Test fun namesVariablesAndUnknownMacros() {
        MacroDefinitions.registerAll()
        val env = MacroEnv(names = MacroEnv.Names(char = "Alice", user = "Bob"))
        assertEquals("Alice/Bob", MacroEngine.evaluate("{{char}}/{{user}}", env))
        assertEquals("1", MacroEngine.evaluate("{{setvar::x::1}}{{getvar::x}}", env))
        assertEquals("{{unknown::x}}", MacroEngine.evaluate("{{unknown::x}}", env))
        assertTrue(MacroEngine.evaluate("{{random::a::b::c}}", env) in listOf("a", "b", "c"))
    }

    @Test fun regexSourceOrderFlagsTrimAndDepth() {
        val local = RegexScript(findRegex = "/cat/g", replaceString = "dog", placement = listOf(1))
        val global = RegexScript(findRegex = "/dog/g", replaceString = "fox", placement = listOf(1))
        val scripts = mergeActiveRegexScripts(listOf(local), listOf(global))
        assertEquals("fox", RegexScriptEngine.getRegexedString("cat", scripts, 1))
        assertEquals("cat", RegexScriptEngine.getRegexedString("cat", listOf(local.copy(markdownOnly = true)), 1, isPrompt = true))
        assertEquals("dog", RegexScriptEngine.getRegexedString("cat", listOf(local.copy(markdownOnly = true)), 1, isMarkdown = true))
        assertEquals("cat", RegexScriptEngine.getRegexedString("cat", listOf(local.copy(minDepth = 2)), 1, depth = 1))
        assertEquals("cat", RegexScriptEngine.getRegexedString("cat", listOf(local), 1, isEdit = true))
        assertEquals("dog", RegexScriptEngine.getRegexedString("cat", listOf(local.copy(runOnEdit = true)), 1, isEdit = true))
    }
}
