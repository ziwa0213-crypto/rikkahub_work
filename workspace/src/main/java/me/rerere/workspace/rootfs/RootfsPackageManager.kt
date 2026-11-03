package me.rerere.workspace.rootfs

/** Commands for future explicit actions. The installer never invokes a package manager. */
interface RootfsPackageManager {
    fun update(): String
    fun install(packages: List<String>): String
}

object AptPackageManager : RootfsPackageManager {
    override fun update() = "apt-get update"
    override fun install(packages: List<String>) = "apt-get install -y --no-install-recommends ${packageArgs(packages)}"
}

object ApkPackageManager : RootfsPackageManager {
    override fun update() = "apk update"
    override fun install(packages: List<String>) = "apk add --no-cache ${packageArgs(packages)}"
}

private fun packageArgs(packages: List<String>): String {
    require(packages.all { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9.+_:@=-]*")) }) { "Invalid package name" }
    return packages.joinToString(" ")
}
