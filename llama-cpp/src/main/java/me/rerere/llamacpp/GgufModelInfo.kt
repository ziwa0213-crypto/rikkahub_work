package me.rerere.llamacpp

import java.io.File
import java.io.RandomAccessFile

/** Reads bounded metadata, never tensors or the whole multi-GB model. Unknown
 * architecture is advisory; only the engine decides what it can load. */
object GgufModelInfo {
    val verifiedArchitectures = setOf("qwen3", "qwen35", "qwen3moe", "qwen35moe", "gemma3", "gemma4", "lfm2", "lfm2moe", "lfm3", "phi3", "llama")
    fun hasMagic(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(4)
            input.read(bytes) == 4 && bytes.contentEquals(byteArrayOf(71, 71, 85, 70))
        }
    }.getOrDefault(false)

    fun architecture(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { input ->
            require(input.readInt() == 0x47475546)
            val version = input.u32()
            require(version in 2..3)
            input.u64() // tensors
            val entries = input.u64().also { require(it in 0..1_000_000) }
            repeat(entries.toInt()) {
                val key = input.string(4096)
                val type = input.u32()
                if (key == "general.architecture" && type == 8) return@use input.string(4096)
                input.skipValue(type, 0)
            }
            null
        }
    }.getOrNull()
    private fun RandomAccessFile.u32(): Int = Integer.reverseBytes(readInt())
    /** The public llama model-info API reports only layer zero and omits array
     * metadata. Hybrid models may have zero KV heads there. Read just this key,
     * bounded and without mapping any tensor data. */
    fun kvHeads(file: File): List<Int>? = runCatching {
        RandomAccessFile(file, "r").use { input ->
            require(input.readInt() == 0x47475546)
            require(input.u32() in 2..3)
            input.u64()
            val entries = input.u64().also { require(it in 0..1_000_000) }
            repeat(entries.toInt()) {
                val key = input.string(4096)
                val type = input.u32()
                if (key.endsWith(".attention.head_count_kv")) {
                    if (type in 4..5) return@use listOf(input.u32())
                    require(type == 9)
                    require(input.u32() in 4..5)
                    val count = input.u64().also { require(it in 1..100_000) }.toInt()
                    return@use List(count) { input.u32() }
                }
                input.skipValue(type, 0)
            }
            null
        }
    }.getOrNull()
    private fun RandomAccessFile.u64(): Long = java.lang.Long.reverseBytes(readLong())
    private fun RandomAccessFile.string(max: Int): String {
        val count = u64().also { require(it in 0..max.toLong()) }.toInt()
        return ByteArray(count).also(::readFully).toString(Charsets.UTF_8)
    }
    private fun RandomAccessFile.skipValue(type: Int, depth: Int) {
        require(depth < 8)
        val count = when (type) {
            0, 1, 7 -> 1L
            2, 3 -> 2L
            4, 5, 6 -> 4L
            10, 11, 12 -> 8L
            8 -> u64()
            9 -> {
                val element = u32(); val length = u64().also { require(it in 0..10_000_000) }
                repeat(length.toInt()) { skipValue(element, depth + 1) }
                return
            }
            else -> error("Unknown GGUF type")
        }
        require(count >= 0 && count <= length() - filePointer)
        seek(filePointer + count)
    }
}
