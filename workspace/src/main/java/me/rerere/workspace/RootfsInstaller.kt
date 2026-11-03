package me.rerere.workspace

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import me.rerere.workspace.rootfs.*
import org.tukaani.xz.XZInputStream

class RootfsInstaller(
    private val manager: WorkspaceManager,
    private val patcher: RootfsPatcher = RootfsPatcher(),
    private val deviceArch: () -> RootfsArch? = { RootfsArch.current() },
    private val moveDirectory: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
) {
    fun install(
        root: String,
        url: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ) {
        installUrl(root, url, null, onProgress)
    }

    fun installUrl(root: String, url: String, sha256: String?, onProgress: (RootfsInstallProgress) -> Unit = {}) {
        install(root, GenericPosixSpec(requireArch(), url, sha256), onProgress = onProgress)
    }

    fun install(
        root: String,
        spec: RootfsSpec,
        sizeBytes: Long = 0,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ) {
        val actualArch = requireArch()
        if (spec.arch != actualArch) throw RootfsInstallException(RootfsError.ARCH_MISMATCH, listOf(spec.arch.token, actualArch.token))
        validateChecksum(spec.sha256())
        require(spec.urls().isNotEmpty()) { "Rootfs download url is required" }
        val format = ArchiveFormat.fromUrl(spec.urls().first())
        withStaging(root, format, spec, onProgress) { archive ->
            RootfsVerifier.checkSpace(archive.parentFile!!, sizeBytes)
            var lastError: Exception? = null
            var checksumFailed = false
            val urls = spec.urls()
            for ((index, url) in urls.withIndex()) {
                checkInterrupted()
                try {
                    require(URL(url).protocol in listOf("http", "https")) { "Unsupported URL scheme" }
                    download(url, archive, onProgress)
                    verifyChecksum(archive, spec.sha256(), onProgress)
                    return@withStaging
                } catch (error: InterruptedException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is RootfsInstallException && error.code == RootfsError.NO_SPACE) throw error
                    lastError = error
                    checksumFailed = checksumFailed || (error as? RootfsInstallException)?.code == RootfsError.CHECKSUM
                    archive.delete()
                    if (index == urls.lastIndex) throw RootfsInstallException(
                        if (checksumFailed) RootfsError.CHECKSUM else RootfsError.DOWNLOAD,
                        listOf(urls.size.toString()), lastError,
                    )
                }
            }
        }
    }

    fun installLocal(
        root: String,
        fileName: String,
        sizeBytes: Long?,
        openStream: () -> InputStream,
        sha256: String? = null,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ) {
        val format = ArchiveFormat.fromUrl(fileName)
        validateChecksum(sha256)
        val spec = GenericPosixSpec(requireArch(), checksum = sha256)
        withStaging(root, format, spec, onProgress) { archive ->
            RootfsVerifier.checkSpace(archive.parentFile!!, sizeBytes ?: 0)
            openStream().use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var count = 0L
                    var reported = -PROGRESS_STEP_BYTES.toLong()
                    while (true) {
                        checkInterrupted()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        count += read
                        if (count - reported >= PROGRESS_STEP_BYTES) {
                            reported = count
                            RootfsVerifier.checkSpace(archive.parentFile!!, count, 4)
                            onProgress(RootfsInstallProgress(RootfsInstallStage.DOWNLOADING, count, sizeBytes, copyingLocalFile = true))
                        }
                    }
                }
            }
            verifyChecksum(archive, sha256, onProgress)
        }
    }

    private fun requireArch() = deviceArch() ?: throw RootfsInstallException(RootfsError.UNSUPPORTED_ARCH)

    private fun validateChecksum(checksum: String?) {
        require(checksum == null || checksum.matches(Regex("(?i)[a-f0-9]{64}"))) { "Invalid SHA-256 checksum" }
    }

    private fun verifyChecksum(archive: File, checksum: String?, progress: (RootfsInstallProgress) -> Unit) {
        progress(RootfsInstallProgress(RootfsInstallStage.VERIFYING))
        if (checksum != null && !RootfsVerifier.sha256(archive).equals(checksum, ignoreCase = true)) {
            throw RootfsInstallException(RootfsError.CHECKSUM)
        }
    }

    private fun withStaging(
        root: String,
        format: ArchiveFormat,
        spec: RootfsSpec,
        onProgress: (RootfsInstallProgress) -> Unit,
        acquire: (File) -> Unit,
    ) {
        manager.ensureWorkspace(root)
        val tempDir = manager.tempDir(root)
        val archive = File(tempDir, "rootfs.${format.extension}")
        val stagingDir = File(tempDir, "rootfs-staging")
        val linuxDir = manager.linuxDir(root)
        val old = File(tempDir, "linux.old")
        val discarded = File(tempDir, "rootfs-discard")
        check(deleteTree(discarded)) { "Could not clean previous rootfs backup" }
        // Recover an interrupted swap before touching another archive. Never discard the backup first.
        if (old.exists()) {
            check(deleteTree(linuxDir) && moveDirectory(old, linuxDir)) { "Could not recover previous rootfs" }
        }
        try {
            check(deleteTree(stagingDir)) { "Could not clean rootfs staging directory" }
            check(stagingDir.mkdirs()) { "Could not create rootfs staging directory" }
            acquire(archive)
            RootfsVerifier.checkSpace(tempDir, archive.length(), 4)
            extractTar(archive, stagingDir, format, onProgress)
            checkInterrupted()
            onProgress(RootfsInstallProgress(RootfsInstallStage.CHECKING))
            RootfsVerifier.verify(stagingDir, spec.arch, spec.desktopEvidence)
            var backedUp = false
            var swapped = false
            try {
                if (linuxDir.exists()) {
                    check(moveDirectory(linuxDir, old)) { "Could not preserve previous rootfs" }
                    backedUp = true
                }
                checkInterrupted()
                check(moveDirectory(stagingDir, linuxDir)) { "Failed to move rootfs into workspace" }
                swapped = true
                onProgress(RootfsInstallProgress(RootfsInstallStage.CONFIGURING))
                spec.configure(linuxDir)
                checkInterrupted()
                onProgress(RootfsInstallProgress(stage = RootfsInstallStage.INSTALLED))
                // Rename the committed backup out of the recovery slot before best-effort cleanup.
                // A failed/partial cleanup must never restore an incomplete old tree next time.
                if (backedUp) check(moveDirectory(old, discarded)) { "Could not finish rootfs transaction" }
            } catch (error: Throwable) {
                // This also covers a failed staging rename and cancellation after the old tree moved.
                if (swapped && !deleteTree(linuxDir)) error.addSuppressed(IOException("Could not remove incomplete rootfs"))
                if (backedUp && !moveDirectory(old, linuxDir)) error.addSuppressed(IOException("Previous rootfs retained in linux.old"))
                throw error
            }
            deleteTree(discarded)
        } finally {
            archive.delete()
            deleteTree(stagingDir)
        }
    }

    /** Rootfs links are guest paths: cleanup must never traverse them on the host. */
    private fun deleteTree(file: File): Boolean = runCatching {
        if (!Files.exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return@runCatching true
        Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(path)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(path: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(path)
                return FileVisitResult.CONTINUE
            }
        })
        true
    }.getOrDefault(false)

    private fun download(
        url: String,
        target: File,
        onProgress: (RootfsInstallProgress) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Rootfs download failed: HTTP $code" }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            RootfsVerifier.checkSpace(target.parentFile!!, totalBytes ?: 0)
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead = 0L
                    var lastReportBytes = 0L
                    while (true) {
                        checkInterrupted()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (bytesRead - lastReportBytes >= PROGRESS_STEP_BYTES || bytesRead == totalBytes) {
                            lastReportBytes = bytesRead
                            onProgress(
                                RootfsInstallProgress(
                                    stage = RootfsInstallStage.DOWNLOADING,
                                    bytesRead = bytesRead,
                                    totalBytes = totalBytes,
                                )
                            )
                        }
                    }
                    if (bytesRead == 0L) {
                        onProgress(
                            RootfsInstallProgress(
                                stage = RootfsInstallStage.DOWNLOADING,
                                bytesRead = 0,
                                totalBytes = totalBytes,
                            )
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractTar(
        archive: File,
        targetDir: File,
        format: ArchiveFormat = ArchiveFormat.fromFile(archive),
        onProgress: (RootfsInstallProgress) -> Unit,
    ) {
        format.wrapStream(BufferedInputStream(archive.inputStream())).use { input ->
            var entries = 0
            var pendingName: String? = null
            var pendingLinkName: String? = null
            val pendingHardLinks = mutableListOf<Pair<File, String>>()
            while (true) {
                checkInterrupted()
                val rawHeader = input.readTarHeader() ?: break
                val header = rawHeader.copy(
                    name = pendingName ?: rawHeader.name,
                    linkName = pendingLinkName ?: rawHeader.linkName,
                )
                pendingName = null
                pendingLinkName = null
                if (header.name.isBlank()) {
                    input.skipFully(header.size.paddedTarSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_NAME) {
                    pendingName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_LINK) {
                    pendingLinkName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.PAX) {
                    val pax = parsePax(input.readExactly(header.size).toString(Charsets.UTF_8))
                    pendingName = pax["path"]
                    pendingLinkName = pax["linkpath"]
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (++entries > RootfsVerifier.MAX_ENTRIES) throw RootfsInstallException(RootfsError.TOO_MANY_FILES)
                val target = targetDir.safeResolve(header.name)
                target.parentFile?.mkdirs()
                when (header.type) {
                    TarEntryType.DIRECTORY -> rootfsPath(targetDir, header.name).mkdirs()
                    TarEntryType.SYMLINK -> createSymlink(targetDir, target, header.linkName)
                    TarEntryType.HARDLINK -> if (!createHardLink(targetDir, target, header.linkName)) pendingHardLinks += target to header.linkName
                    TarEntryType.FILE -> {
                        if (Files.isSymbolicLink(target.toPath())) Files.delete(target.toPath())
                        target.outputStream().use { output ->
                            input.copyExactly(output, header.size)
                        }
                        target.applyMode(header.mode)
                    }

                    // LONG_NAME/LONG_LINK/PAX 已在上方 continue, 这里只有 OTHER 可达;
                    // 数据区统一由下方的非 FILE skip 跳过, 这里再 skip 会双重跳过导致后续 header 错位
                    TarEntryType.LONG_NAME,
                    TarEntryType.LONG_LINK,
                    TarEntryType.PAX,
                    TarEntryType.OTHER -> Unit
                }
                if (header.type != TarEntryType.FILE) {
                    input.skipFully(header.size)
                }
                input.skipFully(header.size.paddingSize())
                if (header.modTime > 0 && header.type != TarEntryType.SYMLINK) {
                    target.setLastModified(header.modTime * 1000)
                }
                onProgress(
                    RootfsInstallProgress(
                        stage = RootfsInstallStage.EXTRACTING,
                        entriesExtracted = entries,
                        currentEntry = header.name,
                    )
                )
            }
            while (pendingHardLinks.isNotEmpty()) {
                val resolved = pendingHardLinks.removeAll { (target, source) -> createHardLink(targetDir, target, source) }
                require(resolved) { "Unresolved rootfs hard link" }
            }
        }
    }

    private fun createSymlink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val linkTarget = if (File(linkName).isAbsolute) {
            File(linkName)
        } else {
            val resolved = File(target.parentFile ?: root, linkName).absoluteFile.normalize()
            val rootFile = root.absoluteFile.normalize()
            require(resolved.path == rootFile.path || resolved.path.startsWith(rootFile.path + File.separator)) {
                "Symlink escapes rootfs: ${target.name}"
            }
            (target.parentFile ?: root).toPath().relativize(resolved.toPath()).toFile()
        }
        target.delete()
        Files.createSymbolicLink(target.toPath(), linkTarget.toPath())
    }

    private fun createHardLink(root: File, target: File, linkName: String): Boolean {
        require(linkName.isNotBlank()) { "Empty rootfs hard link" }
        val source = rootfsPath(root, normalizeTarPath(linkName))
        if (!source.exists()) return false
        target.delete()
        runCatching {
            Files.createLink(target.toPath(), source.toPath())
        }.recoverCatching { error ->
            if (error !is IOException &&
                error !is UnsupportedOperationException &&
                error !is SecurityException
            ) {
                throw error
            }
            source.copyTo(target, overwrite = true)
            target.setReadable(source.canRead(), false)
            target.setWritable(source.canWrite(), true)
            target.setExecutable(source.canExecute(), false)
        }.getOrThrow()
        return true
    }

    private fun InputStream.readTarHeader(): TarHeader? {
        val header = ByteArray(TAR_BLOCK_SIZE)
        val read = readFullyOrEnd(header)
        if (read == 0) return null
        if (read < TAR_BLOCK_SIZE) throw EOFException("Unexpected EOF while reading tar header")
        if (header.all { it == 0.toByte() }) return null

        val name = header.string(0, 100)
        val prefix = header.string(345, 155)
        val fullName = listOf(prefix, name)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return TarHeader(
            name = normalizeTarPath(fullName),
            mode = header.octal(100, 8).toInt(),
            size = header.octal(124, 12),
            modTime = header.octal(136, 12),
            type = when (header[156].toInt().toChar()) {
                '0', '\u0000' -> TarEntryType.FILE
                '5' -> TarEntryType.DIRECTORY
                '2' -> TarEntryType.SYMLINK
                '1' -> TarEntryType.HARDLINK
                'L' -> TarEntryType.LONG_NAME
                'K' -> TarEntryType.LONG_LINK
                'x' -> TarEntryType.PAX
                else -> TarEntryType.OTHER
            },
            linkName = header.string(157, 100),
        )
    }

    private fun parsePax(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < text.length) {
            val space = text.indexOf(' ', index)
            if (space < 0) break
            val length = text.substring(index, space).toIntOrNull() ?: break
            require(length > space - index + 1 && index + length <= text.length) { "Malformed tar PAX record" }
            val end = (index + length).coerceAtMost(text.length)
            val record = text.substring(space + 1, end).trimEnd('\n')
            val equals = record.indexOf('=')
            if (equals > 0) {
                result[record.substring(0, equals)] = record.substring(equals + 1)
            }
            index += length
        }
        return result
    }

    // 协程取消时调用方通过 runInterruptible 将取消转成线程中断, 这里在阻塞循环中检测并尽早退出,
    // 避免离开页面后仍继续下载/解压并向已清空的 StateFlow 推送进度
    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Rootfs install cancelled")
        }
    }

    private fun InputStream.copyExactly(output: java.io.OutputStream, bytes: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = bytes
        while (remaining > 0) {
            checkInterrupted()
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("Unexpected EOF while extracting tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.readExactly(bytes: Long): ByteArray {
        require(bytes in 0..(1024 * 1024)) { "Tar metadata is too large to buffer: $bytes" }
        val buffer = ByteArray(bytes.toInt())
        val read = readFullyOrEnd(buffer)
        if (read != buffer.size) throw EOFException("Unexpected EOF while reading tar entry")
        return buffer
    }

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            checkInterrupted()
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining--
            } else {
                throw EOFException("Unexpected EOF while skipping tar data")
            }
        }
    }

    private fun InputStream.readFullyOrEnd(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun File.safeResolve(path: String): File {
        val normalized = normalizeTarPath(path)
        return rootfsPath(this, normalized, followFinal = false)
    }

    private fun File.applyMode(mode: Int) {
        setReadable(mode and 0b100_000_000 != 0, false)
        setWritable(mode and 0b010_000_000 != 0, true)
        setExecutable(mode and 0b001_000_000 != 0, false)
    }

    // 根目录条目 (如 Alpine minirootfs 开头的 "./") 规范化后为空串, 由调用方跳过
    private fun normalizeTarPath(path: String): String {
        require(!path.contains('\u0000')) { "Rootfs entry path contains invalid character" }
        val segments = path
            .replace('\\', '/')
            .trim()
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
        require(segments.none { it == ".." }) { "Rootfs entry escapes target directory: $path" }
        return segments.joinToString("/")
    }

    private fun ByteArray.string(offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it] == 0.toByte() }
            ?: (offset + length)
        return copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun ByteArray.octal(offset: Int, length: Int): Long {
        val value = string(offset, length)
            .trim()
            .lowercase(Locale.US)
            .trimEnd('\u0000')
        return if (value.isBlank()) 0L else value.toLong(8)
    }

    private fun Long.paddingSize(): Long = (TAR_BLOCK_SIZE - (this % TAR_BLOCK_SIZE)).let {
        if (it == TAR_BLOCK_SIZE.toLong()) 0L else it
    }

    private fun Long.paddedTarSize(): Long = this + paddingSize()

    private data class TarHeader(
        val name: String,
        val mode: Int,
        val size: Long,
        val modTime: Long,
        val type: TarEntryType,
        val linkName: String,
    )

    private enum class TarEntryType {
        FILE,
        DIRECTORY,
        SYMLINK,
        HARDLINK,
        LONG_NAME,
        LONG_LINK,
        PAX,
        OTHER,
    }

    enum class ArchiveFormat(val extension: String) {
        TAR_GZ("tar.gz") {
            override fun wrapStream(input: InputStream): InputStream = GZIPInputStream(input)
        },
        TAR_XZ("tar.xz") {
            override fun wrapStream(input: InputStream): InputStream = XZInputStream(input)
        };

        abstract fun wrapStream(input: InputStream): InputStream

        companion object {
            fun fromUrl(url: String): ArchiveFormat {
                val path = url.substringBefore('?').substringBefore('#')
                return when {
                    path.endsWith(".tar.xz", true) || path.endsWith(".txz", true) -> TAR_XZ
                    path.endsWith(".tar.gz", true) || path.endsWith(".tgz", true) -> TAR_GZ
                    else -> throw RootfsInstallException(RootfsError.INVALID_EXTENSION)
                }
            }

            fun fromFile(file: File): ArchiveFormat = fromUrl(file.name)
        }
    }

    companion object {
        private const val TAR_BLOCK_SIZE = 512
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 512 * 1024
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
