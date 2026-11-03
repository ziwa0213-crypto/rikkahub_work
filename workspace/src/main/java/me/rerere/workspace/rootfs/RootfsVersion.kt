package me.rerere.workspace.rootfs

/** Natural ordering keeps 3.24 after 3.9 while retaining rolling release names. */
fun compareRootfsVersions(first: String, second: String): Int {
    val pattern = Regex("[0-9]+|[^0-9]+")
    val a = pattern.findAll(first).map { it.value }.toList()
    val b = pattern.findAll(second).map { it.value }.toList()
    for (i in 0 until minOf(a.size, b.size)) {
        val numericA = a[i].toLongOrNull()
        val numericB = b[i].toLongOrNull()
        val diff = if (numericA != null && numericB != null) numericA.compareTo(numericB) else a[i].compareTo(b[i])
        if (diff != 0) return diff
    }
    return a.size.compareTo(b.size)
}
