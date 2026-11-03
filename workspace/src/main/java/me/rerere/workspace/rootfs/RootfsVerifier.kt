package me.rerere.workspace.rootfs

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

enum class RootfsError {
    UNSUPPORTED_ARCH, INVALID_EXTENSION, INVALID_FORMAT, MISSING_SHELL, ARCH_MISMATCH, DESKTOP,
    TOO_MANY_FILES, CHECKSUM, DOWNLOAD, NO_SPACE, SWITCH_FAILED,
}

class RootfsInstallException(
    val code: RootfsError,
    val arguments: List<String> = emptyList(),
    cause: Throwable? = null,
) : IOException("${code.name}: ${arguments.joinToString()}", cause)

object RootfsVerifier {
    const val MAX_ENTRIES = 50_000

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                checkInstallInterrupted()
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(root: File, arch: RootfsArch, desktopEvidence: List<String> = DEFAULT_DESKTOP_EVIDENCE) {
        val shell = rootfsPath(root, "bin/sh")
        if (!shell.isFile) throw RootfsInstallException(RootfsError.MISSING_SHELL)
        val header = ByteArray(20)
        val headerSize = shell.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) break
                offset += read
            }
            offset
        }
        if (headerSize < 20 || !header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
            throw RootfsInstallException(RootfsError.INVALID_FORMAT)
        }
        val little = header[5].toInt() == 1
        val machine = if (little) (header[18].toInt() and 255) or ((header[19].toInt() and 255) shl 8)
            else ((header[18].toInt() and 255) shl 8) or (header[19].toInt() and 255)
        if (header[4].toInt() != 2 || machine != arch.elfMachine) {
            val actual = RootfsArch.entries.firstOrNull { it.elfMachine == machine }?.token ?: "ELF-$machine"
            throw RootfsInstallException(RootfsError.ARCH_MISMATCH, listOf(actual, arch.token))
        }
        if (desktopEvidence.any {
            val path = rootfsPath(root, it, followFinal = false)
            path.exists() || Files.isSymbolicLink(path.toPath())
        }) throw RootfsInstallException(RootfsError.DESKTOP)
    }

    fun checkSpace(dir: File, size: Long, multiplier: Long = 5) {
        if (size <= 0) return
        val needed = if (size > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else size * multiplier
        val free = dir.usableSpace
        if (free < needed) throw RootfsInstallException(RootfsError.NO_SPACE, listOf(needed.toString(), free.toString()))
    }
}

internal fun checkInstallInterrupted() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException("Rootfs install cancelled")
}
