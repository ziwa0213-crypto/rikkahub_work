package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.RowMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptTest {
    private val profile = MemoryFile(MemoryFile.PROFILE_PATH, "- [stated] Name is Lin\n", updatedAt = 0)
    private val food = MemoryFile(
        path = "/topics/food.md",
        content = """
            ---
            name: food
            description: What the user eats and avoids
            aliases: [diet, 饮食]
            ---

            - [stated] Allergic to peanuts
        """.trimIndent(),
        updatedAt = 0,
    )

    private fun String.block(tag: String) = substringAfter("<$tag").substringAfter(">").substringBefore("</$tag>").trim()

    @Test
    fun `profile is injected in full with its version and other files only appear in the listing`() {
        val prompt = buildMemoryPrompt(listOf(profile, food))

        assertTrue(prompt.contains("<profile version=\"${profile.version}\">"))
        assertEquals("- [stated] Name is Lin", prompt.block("profile"))

        val listing = prompt.block("memory_listing")
        assertTrue(listing.startsWith("/topics/food.md | What the user eats and avoids | aliases: diet, 饮食 | updated: "))
        assertFalse(listing.contains(MemoryFile.PROFILE_PATH))
        assertFalse(prompt.contains("Allergic to peanuts"))
    }

    @Test
    fun `missing files are shown as empty and creatable`() {
        val prompt = buildMemoryPrompt(emptyList())

        assertTrue(prompt.contains("<preferences version=\"new\">"))
        assertEquals("(empty)", prompt.block("preferences"))
        assertEquals("(no files yet)", prompt.block("memory_listing"))
    }

    @Test
    fun `itemized injection is absent when empty and capped without modifying the source list`() {
        assertEquals("", buildRowMemoryPrompt(emptyList()))
        val rows = (1..101).map { RowMemory(id = it, content = "fact $it") }

        val prompt = buildRowMemoryPrompt(rows)

        assertTrue(prompt.startsWith("<memory>"))
        assertTrue(prompt.contains("- [id=100] fact 100\n"))
        assertFalse(prompt.contains("- [id=101]"))
        assertTrue(prompt.contains("1 more items not shown"))
        assertEquals(101, rows.size)
    }

    @Test
    fun `migrated profile history stays in the file but is not injected a second time`() {
        val migrated = profile.copy(content = "- [migrated] Name is Lin\n- ordinary note\n")

        assertEquals("- ordinary note", buildMemoryPrompt(listOf(migrated)).block("profile"))
        assertTrue(migrated.content.contains("- [migrated] Name is Lin"))
    }

    @Test
    fun `organizer reads at most 200 itemized facts and cannot modify them`() {
        val rows = (1..201).map { RowMemory(id = it, content = "fact $it") }

        val input = buildMemoryConsolidationInput(emptyList(), emptyList(), rows)
        val block = input.block("itemized_memory")

        assertTrue(block.contains("- fact 200\n"))
        assertFalse(block.contains("- fact 201"))
        assertTrue(block.contains("and 1 more not shown"))
        assertTrue(block.contains("do not copy into files"))
        assertEquals(201, rows.size)
        assertEquals("(empty)", buildMemoryConsolidationInput(emptyList(), emptyList()).block("itemized_memory"))
        for (fullReview in listOf(false, true)) {
            val prompt = buildMemoryConsolidationPrompt(fullReview)
            assertTrue(prompt.contains("Never modify or delete this list"))
            assertTrue(prompt.contains("Do not write or rephrase them into memory files"))
        }
    }

    @Test
    fun `consolidation input carries the memory state and each conversation's new turns`() {
        val input = buildMemoryConsolidationInput(
            files = listOf(profile, food),
            rowMemories = listOf(RowMemory(id = 3, content = "Avoid cilantro")),
            conversations = listOf(
                MemoryConsolidationConversation(
                    id = "c1",
                    title = "Moving <to> \"Hangzhou\"",
                    recentContext = "",
                    newTurns = "[USER #4]: I moved to Hangzhou",
                ),
            ),
        )

        assertEquals("- [stated] Name is Lin", input.block("profile"))
        assertTrue(input.block("memory_listing").startsWith("/topics/food.md"))
        assertTrue(input.block("itemized_memory").contains("- Avoid cilantro"))
        assertTrue(buildMemoryConsolidationPrompt(fullReview = false).contains("Never modify or delete this list"))
        // 标题里的引号和尖括号不能破坏标签
        assertTrue(input.contains("<conversation id=\"c1\" title=\"Moving  to   Hangzhou\">"))
        assertEquals("(none)", input.block("recent_context"))
        assertEquals("[USER #4]: I moved to Hangzhou", input.block("new_turns"))
    }

    @Test
    fun `consolidation input says so when there is nothing new`() {
        val input = buildMemoryConsolidationInput(files = listOf(profile), conversations = emptyList())

        assertTrue(input.endsWith("(no new conversation turns)"))
        assertFalse(input.contains("<conversation"))
    }

    @Test
    fun `whole-store tidying is only asked for in a full review`() {
        assertFalse(buildMemoryConsolidationPrompt(fullReview = false).contains("## Full review"))
        assertTrue(buildMemoryConsolidationPrompt(fullReview = true).contains("## Full review"))
    }
}
