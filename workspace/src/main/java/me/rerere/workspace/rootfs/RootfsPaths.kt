package me.rerere.workspace.rootfs

import java.io.File
import java.nio.file.Files

/** Resolve guest absolute symlinks inside the rootfs, never against the Android/host root. */
internal fun rootfsPath(root: File, path: String, followFinal: Boolean = true): File {
    val base = root.absoluteFile.normalize()
    val pending = ArrayDeque(path.replace('\\', '/').split('/'))
    val parts = mutableListOf<String>()
    var links = 0
    while (pending.isNotEmpty()) {
        val part = pending.removeFirst()
        when (part) {
            "", "." -> continue
            ".." -> {
                require(parts.isNotEmpty()) { "Rootfs path escapes root" }
                parts.removeAt(parts.lastIndex)
                continue
            }
        }
        parts += part
        val candidate = File(base, parts.joinToString("/"))
        if ((followFinal || pending.isNotEmpty()) && Files.isSymbolicLink(candidate.toPath())) {
            require(++links <= 40) { "Rootfs symlink loop" }
            val target = Files.readSymbolicLink(candidate.toPath()).toString()
            parts.removeAt(parts.lastIndex)
            if (target.startsWith('/')) parts.clear()
            target.split('/').asReversed().forEach { pending.addFirst(it) }
        }
    }
    return File(base, parts.joinToString("/"))
}
