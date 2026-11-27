package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebModels
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.browser.normalizeBrowserVisitedHost
import me.rerere.rikkahub.data.model.normalizeGroupData
import me.rerere.rikkahub.data.model.DEFAULT_STYLE_PACKS
import me.rerere.rikkahub.data.model.MAX_STYLE_PACK_RULES
import kotlin.uuid.Uuid

/** 读盘后和每次修改后都要过一遍，保证内存里的设置始终是规整的。 */
internal fun Settings.normalized(): Settings =
    withBuiltInDefaults().withoutInvalidReferences().withoutHiddenDeepSeekWeb()
        .withValuesInRange().normalizeGroupData()

/** Hide DeepSeek Web without deleting its login state, and keep settings references usable. */
internal fun Settings.withoutHiddenDeepSeekWeb(): Settings {
    if (experimentalDeepSeekWeb) return this
    val hiddenIds = providers.asSequence().filterIsInstance<ProviderSetting.DeepSeekWeb>()
        .flatMap { it.models.asSequence() }.map { it.id }.toSet()
    if (hiddenIds.isEmpty()) return this
    val fallback = visibleChatFallback()
    fun Uuid?.replaceHidden(): Uuid? = if (this in hiddenIds) fallback ?: this else this
    return copy(
        chatModelId = chatModelId.replaceHidden() ?: chatModelId,
        fastModelId = fastModelId.replaceHidden() ?: fastModelId,
        memoryModelId = memoryModelId.replaceHidden(),
        imageGenerationModelId = imageGenerationModelId.replaceHidden() ?: imageGenerationModelId,
        translateModeId = translateModeId.replaceHidden() ?: translateModeId,
        ocrModelId = ocrModelId.replaceHidden() ?: ocrModelId,
        compressModelId = compressModelId.replaceHidden() ?: compressModelId,
        assistants = assistants.map { it.copy(chatModelId = it.chatModelId.replaceHidden()) },
        characters = characters.map { it.copy(chatModelId = it.chatModelId.replaceHidden()) },
        groupMembers = groupMembers.map { it.copy(chatModelId = it.chatModelId.replaceHidden() ?: it.chatModelId) },
        pulse = pulse.copy(chatModelId = pulse.chatModelId.replaceHidden()),
    )
}

// 补齐缺失的内置提供商/助手/TTS，内置提供商和推荐提供商的描述信息始终以代码里的为准
internal fun Settings.withBuiltInDefaults(): Settings {
    var providers = this.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
    DEFAULT_PROVIDERS.forEach { defaultProvider ->
        if (providers.none { it.id == defaultProvider.id }) {
            providers.add(defaultProvider.copyProvider())
        }
    }
    providers = providers.map { provider ->
        val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
        val recommendedProvider = RECOMMENDED_PROVIDERS.find { it.id == provider.id }
        when {
            defaultProvider != null -> provider.copyProvider(
                builtIn = defaultProvider.builtIn,
                description = defaultProvider.description,
                shortDescription = defaultProvider.shortDescription,
            )

            // 描述不落盘，从推荐列表添加的提供商读盘后按 id 补回
            recommendedProvider != null -> provider.copyProvider(
                description = recommendedProvider.description,
                shortDescription = recommendedProvider.shortDescription,
            )

            else -> provider
        }
    }.toMutableList()
    // 内置助手可以删除，只在一个都不剩时补回，不按 id 逐个补齐
    val assistants = this.assistants.ifEmpty { DEFAULT_ASSISTANTS }
    val ttsProviders = this.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
    DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
        if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
            ttsProviders.add(defaultTTSProvider.copyProvider())
        }
    }
    return copy(
        providers = providers,
        assistants = assistants,
        ttsProviders = ttsProviders,
    )
}

// 去重并清理无效引用
internal fun Settings.withoutInvalidReferences(): Settings {
    val settings = this
    val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
    val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validMemoryGroupIds = settings.memoryGroups.map { it.id }.toSet()
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    val validModelIds by lazy { settings.providers.flatMap { it.models }.mapTo(HashSet()) { it.id } }
    val asrProviders = settings.asrProviders.distinctBy { it.id }
    val stylePacks = (DEFAULT_STYLE_PACKS + settings.stylePacks.filterNot { it.builtIn }).distinctBy { it.id }
        .map { it.copy(rules = it.rules.take(MAX_STYLE_PACK_RULES), injectDepth = it.injectDepth.coerceAtLeast(0)) }
    val personas = settings.characterPersonas.distinctBy { it.id }
    val reservedIds = (settings.assistants + DEFAULT_ASSISTANTS).mapTo(mutableSetOf()) { it.id } +
        setOf(me.rerere.rikkahub.data.model.PULSE_ASSISTANT_ID, me.rerere.rikkahub.data.model.GROUP_CHAT_ASSISTANT_ID)
    val characters = settings.characters.distinctBy { it.id }.filterNot { it.id in reservedIds }.map { character ->
        character.copy(
            chatModelId = character.chatModelId?.takeIf { it in validModelIds },
            personaId = character.personaId?.takeIf { id -> personas.any { it.id == id } },
            stylePackId = character.stylePackId?.takeIf { id -> stylePacks.any { it.id == id } },
            mcpServers = character.mcpServers.intersect(validMcpServerIds),
            modeInjectionIds = character.modeInjectionIds.intersect(validModeInjectionIds),
            lorebookIds = character.lorebookIds.intersect(validLorebookIds),
            quickMessageIds = character.quickMessageIds.intersect(validQuickMessageIds),
        )
    }
    return settings.copy(
        assistantId = settings.assistantId
            .takeIf { id -> settings.assistants.any { it.id == id } || characters.any { it.id == id } }
            ?: settings.assistants.firstOrNull()?.id
            ?: settings.assistantId,
        characters = characters,
        characterId = settings.characterId?.takeIf { id -> characters.any { it.id == id } },
        deletedCharacterIds = settings.deletedCharacterIds - reservedIds - characters.map { it.id }.toSet(),
        characterPersonas = personas,
        stylePacks = stylePacks,
        chatModelId = settings.chatModelId.takeIf { it in validModelIds }
            ?: settings.providers.firstNotNullOfOrNull { it.models.firstOrNull()?.id }
            ?: settings.chatModelId,
        providers = settings.providers.distinctBy { it.id }.map { provider ->
            if (provider is ProviderSetting.DeepSeekWeb) provider.copy(
                models = DeepSeekWebModels.withImageInput(provider.models.distinctBy { it.id }),
                throttleMinMs = provider.throttleMinMs.coerceIn(1000, 15000),
                throttleMaxMs = provider.throttleMaxMs.coerceIn(1000, 15000),
            ) else provider.copyProvider(models = provider.models.distinctBy { model -> model.id })
        },
        assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
            assistant.copy(
                chatModelId = assistant.chatModelId?.takeIf { it in validModelIds },
                memoryGroupId = assistant.memoryGroupId?.takeIf { it in validMemoryGroupIds },
                // 过滤掉不存在的 MCP 服务器 ID
                mcpServers = assistant.mcpServers.filter { serverId ->
                    serverId in validMcpServerIds
                }.toSet(),
                // 过滤掉不存在的模式注入 ID
                modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                    id in validModeInjectionIds
                }.toSet(),
                // 过滤掉不存在的 Lorebook ID
                lorebookIds = assistant.lorebookIds.filter { id ->
                    id in validLorebookIds
                }.toSet(),
                // 过滤掉不存在的快捷消息 ID
                quickMessageIds = assistant.quickMessageIds.filter { id ->
                    id in validQuickMessageIds
                }.toSet()
            )
        },
        ttsProviders = settings.ttsProviders.distinctBy { it.id },
        asrProviders = asrProviders,
        selectedASRProviderId = settings.selectedASRProviderId
            ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
            ?: asrProviders.firstOrNull()?.id,
        favoriteModels = settings.favoriteModels.filter { it in validModelIds }.distinct(),
        modeInjections = settings.modeInjections.distinctBy { it.id },
        lorebooks = settings.lorebooks.distinctBy { it.id },
        quickMessages = settings.quickMessages.distinctBy { it.id },
    )
}

// 把取值收回合法范围，比如删掉搜索服务后选中的下标可能越界
private fun Settings.withValuesInRange(): Settings = copy(
    defaultMode = defaultMode.takeIf { it == UiMode.CHAT || it == UiMode.ROLE } ?: UiMode.CHAT,
    stVariables = stVariables.filterKeys { it.isNotBlank() }
        .mapValues { (_, values) -> values.filterKeys { it.isNotBlank() } },
    stRegexScripts = stRegexScripts.map { if (it.id.isBlank()) it.copy(id = Uuid.random().toString()) else it }.distinctBy { it.id },
    stWorldbookBudgetChars = stWorldbookBudgetChars.takeIf { it >= 200 } ?: 0,
    characters = characters.map { character -> character.copy(
        stRegexScripts = character.stRegexScripts.map { if (it.id.isBlank()) it.copy(id = Uuid.random().toString()) else it }.distinctBy { it.id },
        stWorldbookBudgetChars = character.stWorldbookBudgetChars?.takeIf { it >= 200 },
        depthPromptDepth = character.depthPromptDepth.coerceIn(1, 20),
        depthPromptRole = character.depthPromptRole.lowercase().takeIf { it in setOf("system", "user", "assistant") } ?: "system",
    ) },
    pulse = pulse.copy(
        chatModelId = pulse.chatModelId?.takeIf { id ->
            findModelById(id)?.isAllowedForPulse(providers) == true
        },
        activeStartMinute = if (pulse.activeStartMinute < 0) -1 else pulse.activeStartMinute.coerceIn(0, 1439),
        activeEndMinute = if (pulse.activeEndMinute < 0) -1 else pulse.activeEndMinute.coerceIn(0, 1439),
        notificationRetentionHours = pulse.notificationRetentionHours.coerceIn(1, 72),
        notificationBufferLimit = pulse.notificationBufferLimit.coerceIn(20, 500),
        notificationWhitelist = pulse.notificationWhitelist.map(String::trim).filter(String::isNotEmpty).distinct(),
        smsPollMinutes = pulse.smsPollMinutes.coerceIn(5, 240),
        smsWatermarkId = pulse.smsWatermarkId.coerceAtLeast(0),
        smsSenderWhitelist = pulse.smsSenderWhitelist.map(String::trim).filter(String::isNotEmpty).distinct(),
    ),
    displaySetting = displaySetting.normalizeLegacyBackgroundEffect(),
    memoryGroups = memoryGroups.distinctBy { it.id },
    browserToolsDisabled = browserToolsDisabled.distinct(),
    browserToolTimeoutMs = browserToolTimeoutMs.coerceIn(BrowserController.MIN_TOOL_TIMEOUT_MS, BrowserController.MAX_TOOL_TIMEOUT_MS),
    browserTaskTimeoutMs = browserTaskTimeoutMs.coerceIn(BrowserController.MIN_TASK_TIMEOUT_MS, BrowserController.MAX_TASK_TIMEOUT_MS),
    browserVisitedHosts = browserVisitedHosts.mapNotNull(::normalizeBrowserVisitedHost).distinct().take(200),
    searchServiceSelected = searchServiceSelected.coerceIn(0, (searchServices.size - 1).coerceAtLeast(0)),
    defaultTTSPlaybackSpeed = defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f),
    autoCompressThreshold = autoCompressThreshold.coerceIn(AUTO_COMPRESS_THRESHOLD_RANGE),
)

/** 自动压缩触发比例的可选范围，设置页的滑块也用它 */
val AUTO_COMPRESS_THRESHOLD_RANGE = 0.5f..0.95f
