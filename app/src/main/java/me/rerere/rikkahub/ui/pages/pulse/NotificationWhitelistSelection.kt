package me.rerere.rikkahub.ui.pages.pulse

internal fun selectAllNotificationApps(current: List<String>, scope: List<String>): List<String> =
    (current + scope).distinct()

/** Only flip visible applications; retain previously selected packages outside the list. */
internal fun invertNotificationApps(current: List<String>, scope: List<String>): List<String> {
    val visible = scope.toSet()
    val selected = current.toSet()
    return (current.filter { it !in visible } + scope.filter { it !in selected }).distinct()
}
