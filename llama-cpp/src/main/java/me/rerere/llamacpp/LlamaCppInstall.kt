package me.rerere.llamacpp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Modality
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

data class InstalledLocalModel(val name: String, val path: String, val projector: String?, val projectorAdded: Boolean = false)
data class InstallProgress(val done: Long, val total: Long, val projector: Boolean = false)
data class PendingLocalImport(val file: File, val name: String, val architecture: String?, val projector: Boolean) {
    val needsWarning get() = !projector && architecture !in GgufModelInfo.verifiedArchitectures
}

/** File mutations are serialized; inference and deletion share the runtime lock.
 * Nothing enters the installed index until every file is complete and validated. */
class LlamaCppInstall(
    private val context: Context,
    private val client: OkHttpClient,
    private val store: LocalModelStore,
    private val runtime: LlamaCppRuntime,
    private val changed: suspend (List<InstalledLocalModel>) -> Unit,
) {
    private val mutex = Mutex()
    private var repaired = false
    private val directory get() = File(context.filesDir, "models/llamacpp").apply { mkdirs() }
    private val _models = MutableStateFlow<List<InstalledLocalModel>>(emptyList())
    val models = _models.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    val progress = _progress.asStateFlow()
    private val _interrupted = MutableStateFlow<String?>(null)
    val interrupted = _interrupted.asStateFlow()

    fun memoryVerdict(entry: LlamaCppCatalogEntry) = LocalMemoryGate.check(context, entry.minMemGb)

    suspend fun repair() = withContext(Dispatchers.IO) { mutex.withLock {
        if (repaired) return@withLock
        _interrupted.value = store.interrupted()
        store.models().forEach { (name, path) ->
            if (!File(path).isFile) store.remove(name)
        }
        // This private directory only contains our transactions, never user folders.
        val referenced = (store.models().values + store.projectors().values).toSet()
        directory.listFiles()?.filter { file ->
            file.name.endsWith(".part") || (file.extension.equals("gguf", true) && file.absolutePath !in referenced)
        }?.forEach { it.delete() }
        publish()
        repaired = true
    } }

    suspend fun download(entry: LlamaCppCatalogEntry, base: String, confirmArchitecture: suspend (String?) -> Boolean) = withContext(Dispatchers.IO) {
        repair()
        mutex.withLock {
        if (store.models().containsKey(entry.filename)) return@withLock
        // Space is the only download gate. Check before opening any .part file.
        requireDownloadSpace(entry.files.sumOf { it.sizeBytes } + 256L * 1024 * 1024,
            android.os.StatFs(directory.absolutePath).availableBytes)
        val created = mutableListOf<File>()
        var indexed = false
        try {
            var completed = 0L
            val lengths = LongArray(entry.files.size) { -1 }
            entry.files.forEachIndexed { index, spec ->
                val target = File(directory, spec.file)
                // Never overwrite an existing import or an unindexed recovery file.
                if (target.exists()) throw LocalModelException(LocalModelError.BAD_FILE)
                val part = File(directory, spec.file + ".part")
                created += part
                val url = base.trimEnd('/') + "/${entry.repo}/resolve/main/${spec.file}"
                LocalModelDownload(client).fetch(url, part) { done, total ->
                    if (total > 0) requireDownloadSpace((total - done).coerceAtLeast(0),
                        android.os.StatFs(directory.absolutePath).availableBytes)
                    lengths[index] = total
                    val combined = if (lengths.all { it >= 0 }) lengths.sum() else -1L
                    _progress.value = mapOf(entry.filename to InstallProgress(completed + done, combined, spec.role == FileRole.MMPROJ))
                }
                if (!GgufModelInfo.hasMagic(part)) throw LocalModelException(LocalModelError.BAD_FILE)
                if (spec.role == FileRole.WEIGHTS) {
                    val architecture = GgufModelInfo.architecture(part)
                    if (architecture !in GgufModelInfo.verifiedArchitectures && !confirmArchitecture(architecture))
                        throw CancellationException("Architecture warning declined")
                }
                check(part.renameTo(target)) { "Atomic model install failed" }
                created += target
                completed += target.length()
            }
            val projector = entry.files.firstOrNull { it.role == FileRole.MMPROJ }?.let { File(directory, it.file).absolutePath }
            store.install(entry.filename, File(directory, entry.filename).absolutePath, projector)
            indexed = true
            publish()
        } catch (e: CancellationException) { throw e }
        catch (e: LocalModelException) { throw e }
        catch (e: Exception) { throw LocalModelException(LocalModelError.DOWNLOAD).also { it.initCause(e) } }
        finally {
            if (!indexed) created.forEach { it.delete() }
            _progress.value = emptyMap()
        }
    } }

    suspend fun prepareImport(uri: Uri): PendingLocalImport = withContext(Dispatchers.IO) {
        repair()
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.let { File(it).name } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
        if (!name.endsWith(".gguf", true)) throw LocalModelException(LocalModelError.BAD_FILE)
        val part = File.createTempFile("import-", ".part", directory)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> part.outputStream().use { output ->
                val bytes = ByteArray(512 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = input.read(bytes); if (size < 0) break
                    output.write(bytes, 0, size)
                }
            } } ?: throw LocalModelException(LocalModelError.BAD_FILE)
            if (!GgufModelInfo.hasMagic(part)) throw LocalModelException(LocalModelError.BAD_FILE)
            PendingLocalImport(part, name, GgufModelInfo.architecture(part), name.contains("mmproj", true))
        } catch (t: Throwable) { part.delete(); throw t }
    }

    suspend fun commitImport(pending: PendingLocalImport, associate: String? = null) = withContext(Dispatchers.IO) {
        repair()
        mutex.withLock {
        val target = uniqueFile(directory, pending.name)
        if (pending.projector && associate == null) throw LocalModelException(LocalModelError.BAD_FILE)
        if (associate != null && store.models()[associate] == null) throw LocalModelException(LocalModelError.NOT_INSTALLED)
        check(pending.file.renameTo(target)) { "Atomic model import failed" }
        var indexed = false
        var projectorAddedFor: String? = null
        try {
            if (associate == null) store.install(target.name, target.absolutePath, null)
            else {
                val path = requireNotNull(store.models()[associate])
                runtime.delete(path) {
                    val old = store.projectors()[associate]
                    if (old == null) projectorAddedFor = associate
                    store.install(associate, path, target.absolutePath)
                    if (old != null && old != target.absolutePath) File(old).delete()
                }
            }
            indexed = true
            publish(projectorAddedFor)
        } finally { if (!indexed) target.delete() }
    } }

    fun discard(pending: PendingLocalImport) { pending.file.delete() }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        repair()
        mutex.withLock {
        val path = store.models()[name] ?: return@withLock
        val projector = store.projectors()[name]
        runtime.delete(path) {
            try {
                if (File(path).exists() && !File(path).delete()) throw IOException("Cannot delete model")
                projector?.let { if (File(it).exists() && !File(it).delete()) throw IOException("Cannot delete projector") }
            } finally {
                // A projector deletion failure must not leave a missing main file
                // selectable until the next restart.
                if (!File(path).exists()) { store.remove(name); publish() }
            }
        }
    } }

    private suspend fun publish(projectorAddedFor: String? = null) {
        val projectors = store.projectors()
        val items = store.models().map { (name, path) -> InstalledLocalModel(name, path, projectors[name], name == projectorAddedFor) }
        changed(items)
        _models.value = items
    }

    companion object {
        fun requireDownloadSpace(required: Long, free: Long) {
            if (free < required) throw LocalModelException(LocalModelError.NO_SPACE,
                "need ${(required + 1048575) / 1048576} MB, free ${free / 1048576} MB")
        }
        fun uniqueFile(directory: File, filename: String): File {
            val safe = File(filename).name
            var result = File(directory, safe)
            var suffix = 2
            while (result.exists()) result = File(directory, "${safe.substringBeforeLast('.')} (${suffix++}).${safe.substringAfterLast('.')}")
            return result
        }
        fun expectedModalities(name: String, hasProjector: Boolean): List<Modality> =
            if (!hasProjector) listOf(Modality.TEXT)
            else LlamaCppCatalog.ENTRIES.find { it.filename == name }?.modalities
                ?: listOf(Modality.TEXT, Modality.IMAGE, Modality.VIDEO)
    }
}
