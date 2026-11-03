package me.rerere.workspace.rootfs

import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceManager
import java.io.File

/** Explicit host-side archive probe; does not execute PRoot or claim Android validation. */
fun main(args: Array<String>) {
    val directory = File(args.single())
    val index = RootfsIndex.parse(File(directory, "index.json").readText())
    val manager = WorkspaceManager(File(directory, "probe-workspaces"))
    for (id in BuiltinRootfs.ids) {
        val entry = index.entries().single { it.id == id && it.arch == RootfsArch.ARM64 && it.tier == RootfsTier.BASE }
        val archive = File(directory, "${id.value}.tar.gz")
        check(RootfsVerifier.sha256(archive) == entry.sha256) { "Downloaded archive checksum mismatch: ${id.value}" }
        var stage: RootfsInstallStage? = null
        var start = System.nanoTime()
        val durations = linkedMapOf<String, Long>()
        val installer = RootfsInstaller(manager, deviceArch = { RootfsArch.ARM64 })
        installer.installLocal(id.value, archive.name, archive.length(), archive::inputStream, entry.sha256) { progress ->
            if (progress.stage != stage) {
                val now = System.nanoTime()
                stage?.let { durations[it.name] = (now - start) / 1_000_000 }
                start = now
                stage = progress.stage
            }
        }
        check(manager.hasRootfs(id.value))
        check(manager.rootfsStamp(id.value)?.distroId == id.value)
        println("${id.value}: size=${archive.length()}, stamp=${manager.rootfsStamp(id.value)}, hostStageMs=$durations")
    }
}
