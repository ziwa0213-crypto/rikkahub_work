package me.rerere.workspace.rootfs

@JvmInline
value class RootfsId(val value: String) {
    val distro: String get() = value.substringBeforeLast('-', value)
    val version: String get() = if ('-' in value) value.substringAfterLast('-') else ""
}
