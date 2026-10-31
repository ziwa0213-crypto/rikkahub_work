package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.RowMemory
import me.rerere.rikkahub.data.repository.MemoryRepository

/** Itemized writes deliberately never call any of the file-store methods. */
internal fun buildRowMemoryTools(repository: MemoryRepository, scope: String): List<Tool> = listOf(
    Tool(
        name = "memory_tool",
        description = """
            Manage the user's itemized memories in <memory>, separately from the Markdown knowledge store.
            Use only when the user explicitly asks to remember, update or forget an item.
            create: content; edit: id and content; delete: id; read: optional id (otherwise all rows).
            Read returns stable IDs, including rows omitted from the prompt. Never guess IDs.
            Never claim success unless the tool succeeds. Do not copy these items into memory files.
        """.trimIndent(),
        systemPrompt = { _, _ ->
            "For explicit remember/update/forget requests use memory_tool, including when <memory> is empty. " +
                "Never write those requests to the separate <memory_system> file store."
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { listOf("create", "edit", "delete", "read").forEach { add(it) } })
                    })
                    put("id", buildJsonObject { put("type", "integer"); put("description", "Item ID for edit/delete, optional for read") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "Content for create/edit") })
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val params = args.jsonObject
            fun string(name: String) = (params[name] as? JsonPrimitive)?.contentOrNull ?: error("$name is required")
            fun id() = (params["id"] as? JsonPrimitive)?.intOrNull ?: error("id is required")
            val action = string("action")
            val result = when (action) {
                "create", "edit" -> {
                    val content = string("content")
                    val refusal = findForbiddenMemoryContent(content)
                    if (refusal != null) buildJsonObject { put("error", refusal) } else {
                        val row = if (action == "create") repository.addRowMemory(scope, content)
                        else repository.updateRowMemory(scope, id(), content)
                        row.toToolJson()
                    }
                }
                "delete" -> {
                    val id = id()
                    repository.deleteRowMemory(scope, id)
                    buildJsonObject { put("success", true); put("id", id) }
                }
                "read" -> {
                    val id = if ("id" in params) id() else null
                    val rows = repository.getRowMemories(scope).filter { id == null || it.id == id }
                    buildJsonObject { put("memories", buildJsonArray { rows.forEach { add(it.toToolJson()) } }) }
                }
                else -> error("Unknown action: $action")
            }
            listOf(UIMessagePart.Text(result.toString()))
        },
    )
)

private fun RowMemory.toToolJson(): JsonObject = buildJsonObject { put("id", id); put("content", content) }
