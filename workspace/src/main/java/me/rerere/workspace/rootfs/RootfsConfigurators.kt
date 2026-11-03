package me.rerere.workspace.rootfs

import me.rerere.workspace.RootfsPatcher
import java.io.File

internal fun configureFor(distro: String, version: String, rootfsDir: File) {
    RootfsPatcher().patch(rootfsDir)
    // Keep distro-specific configuration in this single dispatch point.
    when (distro) {
        "ubuntu", "debian" -> {
            rootfsPath(rootfsDir, "etc/dpkg/dpkg.cfg.d/force-unsafe-io").apply {
                parentFile?.mkdirs()
                writeText("force-unsafe-io\n")
            }
            rootfsPath(rootfsDir, "etc/apt/apt.conf.d/99rikkahub-nosandbox").apply {
                parentFile?.mkdirs()
                writeText("APT::Sandbox::User \"root\";\n")
            }
        }
        "alpine" -> if (version.matches(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?"))) {
            rootfsPath(rootfsDir, "etc/apk/repositories").apply {
                parentFile?.mkdirs()
                writeText(
                    "https://mirrors.tuna.tsinghua.edu.cn/alpine/v$version/main\n" +
                        "https://mirrors.tuna.tsinghua.edu.cn/alpine/v$version/community\n"
                )
            }
        }
    }
}
