package me.rerere.workspace.rootfs

object BuiltinRootfs {
    private const val REPO = "ziwa0213-crypto/android-proot-rootfs"
    val ids = listOf(RootfsId("ubuntu-26.04"), RootfsId("debian-13"), RootfsId("alpine-3.24"))
    val default: RootfsId = ids.first()
    val preferredTier = RootfsTier.BASE

    fun fallbackUrl(id: RootfsId, arch: RootfsArch, tier: RootfsTier): String =
        "https://github.com/$REPO/releases/download/${id.value}/${id.value}-${arch.token}-${tier.token}.tar.gz"

    fun fallbackEntries(arch: RootfsArch): List<RootfsEntry> = ids.map {
        RootfsEntry(it, arch, preferredTier, 0, "", fallbackUrl(it, arch, preferredTier), EntrySource.BUILTIN)
    }
}
