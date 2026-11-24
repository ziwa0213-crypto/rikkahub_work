package me.rerere.rikkahub.data.model

internal fun mergeRowMemoriesToProfile(rows: List<RowMemory>, existing: String? = null, scope: String = ""): String? {
    val normalized = rows.sortedBy { it.id }.mapNotNull { row ->
        val content = row.content.lines().joinToString(" ") { it.trim() }.trim()
        if (content.isEmpty()) null else row.id to content
    }
    if (normalized.isEmpty()) return existing
    val base = existing ?: "---\nname: profile\ndescription: Who the user is\n---\n"
    val fresh = normalized.filterNot { (id, _) -> scope.isNotBlank() && "<!-- f024:$scope:$id -->" in base }
    if (fresh.isEmpty()) return base
    return buildString {
        append(base)
        if (!base.endsWith('\n')) appendLine()
        if (existing == null) appendLine()
        for ((id, content) in fresh) {
            appendLine("- [stated] $content")
            if (scope.isNotBlank()) appendLine("<!-- f024:$scope:$id -->")
        }
    }
}
