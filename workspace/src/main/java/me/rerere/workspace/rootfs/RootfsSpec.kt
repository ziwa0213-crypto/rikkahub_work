package me.rerere.workspace.rootfs

import java.io.File

interface RootfsSpec {
    val id: RootfsId
    val tier: RootfsTier
    val displayName: String
    val arch: RootfsArch
    fun urls(): List<String>
    fun sha256(): String?
    fun configure(rootfsDir: File)
    val desktopEvidence: List<String> get() = DEFAULT_DESKTOP_EVIDENCE
}

val DEFAULT_DESKTOP_EVIDENCE = listOf(
    "usr/bin/gnome-session", "usr/bin/startxfce4", "usr/bin/startkde", "usr/bin/startplasma-x11",
    "usr/bin/startplasma-wayland", "usr/bin/lxsession", "usr/bin/startlxqt",
    "usr/bin/cinnamon-session", "usr/bin/mate-session", "usr/bin/Xorg", "usr/share/xsessions",
)

internal val MIRROR_PREFIXES = listOf("", "https://ghproxy.net/", "https://gh-proxy.com/")

fun RootfsId.displayName(): String =
    listOf(distro.replaceFirstChar { it.uppercaseChar() }, version).filter { it.isNotBlank() }.joinToString(" ")
