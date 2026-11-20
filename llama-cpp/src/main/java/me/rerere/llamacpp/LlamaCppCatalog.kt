package me.rerere.llamacpp

import me.rerere.ai.provider.Modality

enum class FileRole { WEIGHTS, MMPROJ }
data class CatalogFile(val file: String, val sizeBytes: Long, val role: FileRole)
data class LlamaCppCatalogEntry(
    val id: String, val resource: String, val repo: String, val minMemGb: Int,
    val quant: String, val params: String, val license: String,
    val files: List<CatalogFile>, val modalities: List<Modality>,
) {
    val filename get() = files.first().file
}

object LlamaCppCatalog {
    private val text = listOf(Modality.TEXT)
    private val vision = text + listOf(Modality.IMAGE, Modality.VIDEO)
    val ENTRIES = listOf(
        LlamaCppCatalogEntry("lfm3-1.7b-unc-adq6k", "lfm3_17b", "AtomicChat/LFM3-1.7B-Uncensored-GGUF", 6, "AD-Q6_K", "1.7B", "LFM Open License 1.0",
            listOf(CatalogFile("LFM3-1.7B-Uncensored-AD-Q6_K.gguf", 1_590_000_000, FileRole.WEIGHTS)), text),
        LlamaCppCatalogEntry("phi4-mini-abl-q5km", "phi4mini", "Melvin56/Phi-4-mini-instruct-abliterated-GGUF", 8, "Q5_K_M", "3.8B", "MIT",
            listOf(CatalogFile("phi-4-mini-instruct-abliterated-Q5_K_M.gguf", 2_850_000_000, FileRole.WEIGHTS)), text),
        LlamaCppCatalogEntry("qwen35-4b-unc-q6k", "qwen35_4b", "HauhauCS/Qwen3.5-4B-Uncensored-HauhauCS-Aggressive", 8, "Q6_K", "4B", "Apache-2.0",
            listOf(CatalogFile("Qwen3.5-4B-Uncensored-HauhauCS-Aggressive-Q6_K.gguf", 3_460_000_000, FileRole.WEIGHTS),
                CatalogFile("mmproj-Qwen3.5-4B-Uncensored-HauhauCS-Aggressive-BF16.gguf", 680_000_000, FileRole.MMPROJ)), vision),
        LlamaCppCatalogEntry("gemma4-e2b-unc-q6kp", "gemma4_e2b", "HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive", 12, "Q6_K_P", "E2B", "Gemma Terms of Use",
            listOf(CatalogFile("Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q6_K_P.gguf", 3_870_000_000, FileRole.WEIGHTS),
                CatalogFile("mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf", 990_000_000, FileRole.MMPROJ)), vision + Modality.AUDIO),
        LlamaCppCatalogEntry("qwen35-9b-abl-q6k", "qwen35_9b", "mradermacher/Huihui-Qwen3.5-9B-abliterated-GGUF", 12, "Q6_K", "9B", "Apache-2.0",
            listOf(CatalogFile("Huihui-Qwen3.5-9B-abliterated.Q6_K.gguf", 7_360_000_000, FileRole.WEIGHTS),
                CatalogFile("Huihui-Qwen3.5-9B-abliterated.mmproj-Q8_0.gguf", 620_000_000, FileRole.MMPROJ)), vision),
    )
}
