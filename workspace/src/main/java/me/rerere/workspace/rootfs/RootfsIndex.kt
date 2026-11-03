package me.rerere.workspace.rootfs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.util.logging.Logger

@Serializable
data class RootfsIndex(
    val schemaVersion: Int = 1,
    val repo: String = "",
    val generatedAt: String = "",
    val packages: List<RootfsPackageJson> = emptyList(),
) {
    fun entries(): List<RootfsEntry> = packages.mapNotNull { it.toEntry() }.distinctBy { it.key }

    companion object {
        internal val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): RootfsIndex {
            val root = json.parseToJsonElement(text) as? JsonObject ?: error("Invalid rootfs index")
            val packages = root["packages"] as? JsonArray ?: error("Missing rootfs packages")
            // A malformed package must not hide the remaining usable entries.
            val validPackages = packages.mapNotNull { runCatching { json.decodeFromJsonElement<RootfsPackageJson>(it) }.getOrNull() }
            val version = runCatching { root["schemaVersion"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: 1
            if (version > 1) Logger.getLogger("RootfsIndex").info("Reading rootfs index schema $version")
            return json.decodeFromJsonElement<RootfsIndex>(
                JsonObject(root - "packages")
            ).copy(schemaVersion = version, packages = validPackages)
        }
    }
}

@Serializable
data class RootfsPackageJson(
    val id: String = "",
    val name: String = "",
    val distro: String = "",
    val version: String = "",
    val arch: String = "",
    val tier: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val url: String = "",
) {
    fun toRootfsId(): RootfsId = RootfsId(when {
        id.isNotBlank() -> id
        version.isNotBlank() -> "$distro-$version"
        else -> distro
    })

    fun toEntry(): RootfsEntry? {
        val rootfsId = toRootfsId()
        if (!rootfsId.value.matches(Regex("[a-z0-9][a-z0-9._-]*"))) return null
        if (!sha256.matches(Regex("(?i)[a-f0-9]{64}")) || size < 0) return null
        // Index transport is fixed. Only HTTPS assets can enter the installable catalog.
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return RootfsEntry(
            rootfsId, RootfsArch.of(arch) ?: return null, RootfsTier.of(tier) ?: return null,
            size, sha256.lowercase(), url, EntrySource.INDEX,
        )
    }
}
