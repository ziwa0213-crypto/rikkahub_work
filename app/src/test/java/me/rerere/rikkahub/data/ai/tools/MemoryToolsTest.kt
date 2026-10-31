package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.FakeMemoryDAO
import me.rerere.rikkahub.data.repository.FakeMemoryEntityDAO
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryToolsTest {
    private val repository = MemoryRepository(FakeMemoryDAO(), FakeMemoryEntityDAO())
    private val tools = buildMemoryTools(repository, "assistant").associateBy { it.name }
    private val rowTools = buildRowMemoryTools(repository, "assistant").associateBy { it.name }

    private suspend fun call(name: String, args: JsonObjectBuilder.() -> Unit): JsonObject {
        val output = tools.getValue(name).execute(buildJsonObject(args))
        return Json.parseToJsonElement((output.single() as UIMessagePart.Text).text).jsonObject
    }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `itemized memory tool supports first write and never writes to the file store`() = runBlocking {
        val tool = rowTools.getValue("memory_tool")
        val output = tool.execute(buildJsonObject {
            put("action", "create")
            put("content", "Avoid cilantro")
        })
        val created = Json.parseToJsonElement((output.single() as UIMessagePart.Text).text).jsonObject
        val id = created.getValue("id").jsonPrimitive.content.toInt()

        assertEquals("Avoid cilantro", repository.getRowMemories("assistant").single().content)
        assertTrue(repository.getRowMemories("other").isEmpty())
        assertTrue(repository.getFiles("assistant").isEmpty())

        val read = tool.execute(buildJsonObject { put("action", "read"); put("id", id) })
        val result = Json.parseToJsonElement((read.single() as UIMessagePart.Text).text).jsonObject
        assertEquals("Avoid cilantro", result.getValue("memories").jsonArray.single().jsonObject.string("content"))

        tool.execute(buildJsonObject { put("action", "edit"); put("id", id); put("content", "Avoid parsley") })
        assertEquals("Avoid parsley", repository.getRowMemories("assistant").single().content)
        tool.execute(buildJsonObject { put("action", "delete"); put("id", id) })
        assertTrue(repository.getRowMemories("assistant").isEmpty())
        assertTrue(repository.getFiles("assistant").isEmpty())
    }

    @Test
    fun `file tools never insert update or delete itemized memories`() = runBlocking {
        val row = repository.addRowMemory("assistant", "Avoid cilantro")
        val written = call(MemoryToolNames.WRITE) {
            put("path", "/topics/food.md")
            put("content", "- Likes noodles\n")
            put("if_version", "new")
        }
        val appended = call(MemoryToolNames.APPEND) {
            put("path", "/topics/food.md")
            put("content", "- Likes tea")
            put("if_version", written.string("version"))
        }
        assertEquals(listOf(row), repository.getRowMemories("assistant"))
        call(MemoryToolNames.DELETE) {
            put("path", "/topics/food.md")
            put("if_version", appended.string("version"))
        }
        assertEquals(listOf(row), repository.getRowMemories("assistant"))
    }

    @Test
    fun `itemized writes cannot modify another scope even with a known id`() = runBlocking {
        val other = repository.addRowMemory("other", "Keep this")
        val tool = rowTools.getValue("memory_tool")
        for (action in listOf("edit", "delete")) {
            val result = runCatching {
                tool.execute(buildJsonObject {
                    put("action", action)
                    put("id", other.id)
                    put("content", "Changed")
                })
            }
            assertTrue(result.isFailure)
            assertEquals(listOf(other), repository.getRowMemories("other"))
        }
    }

    @Test
    fun `a written file can be read back and edited with the returned version`() = runBlocking {
        val written = call(MemoryToolNames.WRITE) {
            put("path", "/topics/food.md")
            put("content", "- [stated] Likes noodles\n")
            put("if_version", "new")
        }
        val read = call(MemoryToolNames.READ) {
            put("path", buildJsonArray { add("/topics/food.md"); add("/topics/none.md") })
        }
        val file = read.getValue("files").jsonArray.single().jsonObject

        assertEquals(written.string("version"), file.string("version"))
        assertEquals("- [stated] Likes noodles\n", file.string("content"))
        assertEquals(listOf("/topics/none.md"), read.getValue("not_found").jsonArray.map { it.jsonPrimitive.content })

        val appended = call(MemoryToolNames.APPEND) {
            put("path", "/topics/food.md")
            put("content", "- [stated] Allergic to peanuts")
            put("if_version", file.string("version"))
        }
        assertNull(appended["error"])
        assertEquals(
            "- [stated] Likes noodles\n- [stated] Allergic to peanuts\n",
            repository.getFile("assistant", "/topics/food.md")?.content,
        )
    }

    @Test
    fun `memory_read accepts a single path string`() = runBlocking {
        repository.writeFile("assistant", "/profile.md", "- [stated] Name is Lin\n")

        val read = call(MemoryToolNames.READ) { put("path", "/profile.md") }

        assertEquals("/profile.md", read.getValue("files").jsonArray.single().jsonObject.string("path"))
    }

    @Test
    fun `a version conflict returns the latest content so the edit can be retried`() = runBlocking {
        repository.writeFile("assistant", "/topics/food.md", "- [stated] Likes noodles\n")
        val current = repository.getFile("assistant", "/topics/food.md")!!

        val conflict = call(MemoryToolNames.STR_REPLACE) {
            put("path", "/topics/food.md")
            put("old_str", "noodles")
            put("new_str", "rice")
            put("if_version", "stale")
        }

        assertNotNull(conflict["error"])
        assertEquals(current.version, conflict.string("current_version"))
        assertEquals(current.content, conflict.string("current_content"))

        val retried = call(MemoryToolNames.STR_REPLACE) {
            put("path", "/topics/food.md")
            put("old_str", "noodles")
            put("new_str", "rice")
            put("if_version", conflict.string("current_version"))
        }
        assertNull(retried["error"])
        assertEquals("- [stated] Likes rice\n", repository.getFile("assistant", "/topics/food.md")?.content)
    }

    @Test
    fun `editing a missing file points to creating it`() = runBlocking {
        val result = call(MemoryToolNames.APPEND) {
            put("path", "/topics/none.md")
            put("content", "- line")
            put("if_version", "abc")
        }

        assertTrue(result.string("error")!!.contains("does not exist"))
        assertNull(result["current_content"])
    }

    @Test
    fun `card and ID numbers are refused and nothing is written`() = runBlocking {
        val card = call(MemoryToolNames.WRITE) {
            put("path", "/topics/finance.md")
            put("content", "- [stated] Card number is 4111 1111 1111 1111\n")
            put("if_version", "new")
        }
        val id = call(MemoryToolNames.WRITE) {
            put("path", "/profile.md")
            put("content", "- [stated] 身份证号11010519491231002X\n")
            put("if_version", "new")
        }

        assertTrue(card.string("error")!!.contains("bank card number"))
        assertTrue(id.string("error")!!.contains("ID number"))
        assertTrue(repository.getFiles("assistant").isEmpty())
    }

    @Test
    fun `ordinary numbers are not mistaken for card or ID numbers`() {
        assertNull(findForbiddenMemoryContent("Phone is +86 138 0013 8000, order 2026100812345678"))
        assertNull(findForbiddenMemoryContent("Born 1990-05-17, moved in 2021"))
        assertNotNull(findForbiddenMemoryContent("SSN 123-45-6789"))
    }

    @Test
    fun `memory_delete removes the file and memory_list reflects it`() = runBlocking {
        repository.writeFile("assistant", "/people/mom.md", "content")
        repository.writeFile("assistant", "/topics/food.md", "content")
        val version = repository.getFile("assistant", "/people/mom.md")!!.version

        val people = call(MemoryToolNames.LIST) { put("path_prefix", "/people/") }
        assertEquals(listOf("/people/mom.md"), people.getValue("files").jsonArray.map { it.jsonObject.string("path") })

        val deleted = call(MemoryToolNames.DELETE) {
            put("path", "/people/mom.md")
            put("if_version", version)
        }
        assertEquals("true", deleted.string("deleted"))
        assertFalse(call(MemoryToolNames.LIST) {}.toString().contains("/people/mom.md"))
    }
}
