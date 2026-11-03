package me.rerere.workspace.rootfs

import java.io.File

class EntrySpec(private val entry: RootfsEntry) : RootfsSpec {
    override val id get() = entry.id
    override val tier get() = entry.tier
    override val displayName get() = id.displayName()
    override val arch get() = entry.arch
    // Unverified fallback downloads stay on the original source; proxies require a trusted hash.
    override fun urls() = if (sha256() == null) listOf(entry.url) else MIRROR_PREFIXES.map { it + entry.url }
    override fun sha256() = entry.sha256.takeIf { it.isNotBlank() }
    override fun configure(rootfsDir: File) = configureFor(id.distro, id.version, rootfsDir)
}
