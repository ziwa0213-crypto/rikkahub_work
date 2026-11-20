package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.LLAMACPP_PROVIDER_ID
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.InstalledLocalModel
import me.rerere.llamacpp.LlamaCppInstall
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.uuid.Uuid

/** Reconcile only the local provider, retaining model UUIDs and all API settings.
 * Dropped model selections follow the normal global-model fallback. */
internal fun Settings.withLocalModels(installed: List<InstalledLocalModel>): Settings {
    val provider = providers.filterIsInstance<ProviderSetting.LlamaCppLocal>().find { it.id == LLAMACPP_PROVIDER_ID }
        ?: ProviderSetting.LlamaCppLocal()
    val old = provider.models.associateBy { it.modelId }
    val models = installed.map { item ->
        old[item.name]?.let {
            // Preserve the user's modality permissions across repair/install. Only
            // removing the projector retracts unusable media permissions.
            when {
                item.projectorAdded && it.inputModalities.all { mode -> mode == Modality.TEXT } ->
                    it.copy(inputModalities = LlamaCppInstall.expectedModalities(item.name, true))
                item.projector == null && it.inputModalities.any { mode -> mode != Modality.TEXT } ->
                    it.copy(inputModalities = listOf(Modality.TEXT))
                else -> it
            }
        } ?: Model(
            modelId = item.name, displayName = item.name.removeSuffix(".gguf"), contextLength = 8192,
            abilities = listOf(ModelAbility.TOOL),
            inputModalities = LlamaCppInstall.expectedModalities(item.name, item.projector != null),
        )
    }
    val removed = provider.models.map { it.id }.toSet() - models.map { it.id }.toSet()
    val updated = providers.map { if (it.id == provider.id) provider.copy(models = models) else it }
        .let { list -> if (list.any { it.id == provider.id }) list else list + provider.copy(models = models) }
    val fallback = updated.flatMap { it.models }.firstOrNull()?.id ?: Uuid.random()
    fun Uuid.orFallback() = if (this in removed) fallback else this
    return copy(
        providers = updated,
        favoriteModels = favoriteModels.filterNot { it in removed },
        chatModelId = chatModelId.orFallback(), fastModelId = fastModelId.orFallback(),
        memoryModelId = memoryModelId?.takeUnless { it in removed },
        translateModeId = translateModeId.orFallback(), ocrModelId = ocrModelId.orFallback(),
        compressModelId = compressModelId.orFallback(), imageGenerationModelId = imageGenerationModelId.orFallback(),
        assistants = assistants.map { it.copy(chatModelId = it.chatModelId?.takeUnless { id -> id in removed }) },
        groupMembers = groupMembers.map { it.copy(chatModelId = it.chatModelId?.takeUnless { id -> id in removed }) },
        pulse = pulse.copy(chatModelId = pulse.chatModelId?.takeUnless { it in removed }),
    )
}

/**
 * Move persisted selections away from the paused local-model provider while retaining
 * the provider, its model list, and all downloaded GGUF files.
 */
internal fun Settings.migrateAwayFromLocalModels(): Settings {
    val localProviderIds = providers
        .filterIsInstance<ProviderSetting.LlamaCppLocal>()
        .mapTo(HashSet()) { it.id }
    val localModelIds = providers
        .filter { it.id in localProviderIds }
        .flatMap { it.models }
        .mapTo(HashSet()) { it.id }
    if (localModelIds.isEmpty()) return this

    val fallback = providers
        .filterNot { it.id in localProviderIds }
        .flatMap { it.models }
        .firstOrNull()?.id
        ?: return this

    fun Uuid.orFallback(): Uuid = if (this in localModelIds) fallback else this

    return copy(
        chatModelId = chatModelId.orFallback(),
        fastModelId = fastModelId.orFallback(),
        memoryModelId = memoryModelId?.takeUnless { it in localModelIds },
        translateModeId = translateModeId.orFallback(),
        ocrModelId = ocrModelId.orFallback(),
        compressModelId = compressModelId.orFallback(),
        imageGenerationModelId = imageGenerationModelId.orFallback(),
        assistants = assistants.map { it.copy(chatModelId = it.chatModelId?.takeUnless { id -> id in localModelIds }) },
        groupMembers = groupMembers.map { it.copy(chatModelId = it.chatModelId?.takeUnless { id -> id in localModelIds }) },
        pulse = pulse.copy(chatModelId = pulse.chatModelId?.takeUnless { it in localModelIds }),
    )
}
