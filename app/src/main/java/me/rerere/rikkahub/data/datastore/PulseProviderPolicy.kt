package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting

internal fun Model.isAllowedForPulse(providers: List<ProviderSetting>): Boolean =
    findProvider(providers, checkOverwrite = false) !is ProviderSetting.DeepSeekWeb &&
        findProvider(providers) !is ProviderSetting.DeepSeekWeb

internal fun Settings.pulseUsesWebProvider(): Boolean =
    findModelById(pulse.chatModelId ?: chatModelId)?.isAllowedForPulse(providers) == false

internal class PulseWebProviderBlockedException : IllegalStateException("Pulse cannot run with a web version provider")
