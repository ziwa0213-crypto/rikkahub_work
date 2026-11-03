package me.rerere.workspace.rootfs

import java.io.File

class GenericPosixSpec(
    override val arch: RootfsArch,
    private val url: String? = null,
    private val checksum: String? = null,
    override val id: RootfsId = RootfsId("custom"),
    override val tier: RootfsTier = RootfsTier.BASE,
) : RootfsSpec {
    override val displayName get() = id.displayName()
    override fun urls() = listOfNotNull(url)
    override fun sha256() = checksum?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    override fun configure(rootfsDir: File) {
        val detected = RootfsStamp.readFrom(rootfsDir)?.distroId?.let(::RootfsId) ?: id
        configureFor(detected.distro, detected.version, rootfsDir)
    }
}
