package me.rerere.workspace.rootfs

import android.os.Build

enum class RootfsArch(val token: String, val elfMachine: Int, val abi: String) {
    ARM64("arm64", 183, "arm64-v8a"),
    AMD64("amd64", 62, "x86_64");

    companion object {
        fun of(token: String): RootfsArch? = when (token) {
            "arm64", "aarch64", "arm64-v8a" -> ARM64
            "amd64", "x86_64" -> AMD64
            else -> null
        }

        fun current(abis: Array<String> = Build.SUPPORTED_ABIS): RootfsArch? =
            abis.firstNotNullOfOrNull(::of)
    }
}
