package me.rerere.workspace.rootfs

data class RootfsEntry(
    val id: RootfsId,
    val arch: RootfsArch,
    val tier: RootfsTier,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val source: EntrySource,
) {
    val key: String get() = "${id.value}|${arch.token}|${tier.token}"
}

enum class EntrySource { BUILTIN, INDEX }
