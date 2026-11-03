package me.rerere.workspace.rootfs

import java.io.File

data class RootfsStamp(val distroId: String, val arch: String, val tier: String, val built: String) {
    companion object {
        fun readFrom(linuxDir: File): RootfsStamp? = runCatching {
            val file = rootfsPath(linuxDir, "etc/android-proot-release")
            if (!file.isFile || file.length() > 16 * 1024) return null
            val fields = file.readLines().mapNotNull { line ->
                val key = line.substringBefore('=', "").trim()
                if (key.isBlank()) null else key to line.substringAfter('=').trim().trim('"', '\'')
            }.toMap()
            val distro = fields["distro"]?.takeIf { it.isNotBlank() } ?: return null
            RootfsStamp(distro, fields["arch"].orEmpty(), fields["tier"].orEmpty(), fields["built"].orEmpty())
        }.getOrNull()
    }
}
