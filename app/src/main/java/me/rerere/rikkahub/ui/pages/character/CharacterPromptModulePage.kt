package me.rerere.rikkahub.ui.pages.character

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Character
import me.rerere.rikkahub.data.model.PromptModule
import me.rerere.rikkahub.data.model.PromptModuleKind
import me.rerere.rikkahub.data.model.buildPromptModules
import me.rerere.rikkahub.data.model.effectivePromptModules
import me.rerere.rikkahub.data.model.estimateTokens
import me.rerere.rikkahub.data.model.findCharacter
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun CharacterPromptModulePage(
    characterId: String,
    kindName: String,
    moduleId: String?,
    createNew: Boolean = false,
) {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val store: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val character = settings.findCharacter(Uuid.parseOrNull(characterId))
    val kind = PromptModuleKind.entries.firstOrNull { it.name == kindName } ?: PromptModuleKind.CUSTOM

    if (!settings.experimentalCharacterMode || character == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    val parsedModuleId = Uuid.parseOrNull(moduleId.orEmpty())
    val defaultTitle = stringResource(moduleTitleRes(kind))
    val source = remember(character.id, character.promptModules, character.characterSystemPrompt, kind, parsedModuleId, createNew) {
        (if (!createNew) character.promptModules.firstOrNull { it.id == parsedModuleId }
            ?: character.effectivePromptModules().firstOrNull { it.kind == kind }
        else null) ?: PromptModule(kind = kind, title = defaultTitle, content = when (kind) {
                PromptModuleKind.PERSONA -> listOf(character.description, character.personality)
                    .filter(String::isNotBlank).joinToString("\n\n")
                PromptModuleKind.SCENARIO -> character.scenario
                else -> ""
            })
    }
    var title by remember(character.id, source.id) { mutableStateOf(source.title.ifBlank { defaultTitle }) }
    var content by remember(character.id, source.id) { mutableStateOf(source.content) }
    var enabled by remember(character.id, source.id) { mutableStateOf(source.enabled) }
    val totalChars = content.length
    val tooLong = totalChars > 2000
    val readOnly = kind == PromptModuleKind.STYLE
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(moduleTitleRes(kind))) },
                navigationIcon = { BackButton() },
                actions = {
                    if (!readOnly) {
                        TextButton(onClick = {
                            scope.launch {
                                store.update { latest ->
                                    val current = latest.findCharacter(character.id) ?: return@update latest
                                    val index = current.promptModules.indexOfFirst { it.id == source.id }
                                    val saved = source.copy(
                                        kind = kind,
                                        title = title.trim(),
                                        content = content,
                                        enabled = enabled,
                                        order = if (index >= 0) source.order else (current.promptModules.maxOfOrNull { it.order } ?: -1) + 1,
                                    )
                                    val modules = if (index >= 0) current.promptModules.toMutableList().apply { set(index, saved) }
                                    else current.promptModules + saved
                                    latest.copy(characters = latest.characters.map {
                                        if (it.id == current.id) it.copy(promptModules = modules, updatedAt = System.currentTimeMillis()) else it
                                    })
                                }
                                nav.popBackStack()
                            }
                        }) { Text(stringResource(R.string.common_save)) }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !readOnly,
                singleLine = true,
                label = { Text(stringResource(R.string.prompt_page_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.prompt_page_enabled), style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !readOnly)
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                enabled = !readOnly,
                minLines = 12,
                label = { Text(stringResource(R.string.assistant_page_system_prompt)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.character_prompt_budget, totalChars, estimateTokens(content)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tooLong) {
                Text(stringResource(R.string.character_prompt_too_long), color = MaterialTheme.colorScheme.error)
            }
            if (readOnly) {
                HorizontalDivider()
                Text(stringResource(R.string.character_style), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
internal fun CharacterPromptModulesContent(
    character: Character,
    modifier: Modifier = Modifier,
) {
    val nav = LocalNavController.current
    val modules = character.effectivePromptModules()
    val stylePack = LocalSettings.current.stylePacks.firstOrNull { it.id == character.stylePackId && it.enabled }
    val byKind = modules.groupBy { it.kind }
    fun displayModules(kind: PromptModuleKind, fallback: String = ""): List<PromptModule> =
        byKind[kind].orEmpty().ifEmpty {
            listOf(PromptModule(kind = kind, title = "", content = fallback))
        }
    val displayed = buildList {
        addAll(displayModules(PromptModuleKind.PERSONA,
            listOf(character.description, character.personality).filter(String::isNotBlank).joinToString("\n\n")))
        addAll(displayModules(PromptModuleKind.SCENARIO, character.scenario))
        addAll(displayModules(PromptModuleKind.OUTPUT_RULES))
        addAll(displayModules(PromptModuleKind.GUARDRAIL))
        addAll(byKind[PromptModuleKind.CUSTOM].orEmpty())
    }
    val assembled = character.buildPromptModules()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.character_prompt_section), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.character_prompt_budget, assembled.length, estimateTokens(assembled)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (assembled.length > 2000) Text(stringResource(R.string.character_prompt_too_long), color = MaterialTheme.colorScheme.error)
        stylePack?.let { pack ->
            PromptSummaryCard(
                title = stringResource(R.string.character_style),
                content = pack.rules.joinToString("\n") { "- $it" },
                enabled = character.styleNotesEnabled,
            )
        }
        displayed.forEach { module ->
            PromptSummaryCard(
                title = module.title.ifBlank { stringResource(moduleTitleRes(module.kind)) },
                content = module.content,
                enabled = module.enabled,
                onClick = {
                    val storedId = module.id.takeIf { id -> character.promptModules.any { it.id == id } }
                    nav.navigate(Screen.CharacterPromptModule(character.id.toString(), module.kind.name, storedId?.toString()))
                },
            )
        }
        TextButton(onClick = {
            nav.navigate(Screen.CharacterPromptModule(character.id.toString(), PromptModuleKind.CUSTOM.name, null, createNew = true))
        }) { Text(stringResource(R.string.character_prompt_add_module)) }
    }
}

@Composable
private fun PromptSummaryCard(
    title: String,
    content: String,
    enabled: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val cardContent: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (!enabled) Text(stringResource(R.string.prompt_page_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(content.ifBlank { "—" }, maxLines = 5, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick == null) {
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            content = { cardContent() },
        )
    } else {
        Card(
            onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            content = { cardContent() },
        )
    }
}

@StringRes
private fun moduleTitleRes(kind: PromptModuleKind): Int = when (kind) {
    PromptModuleKind.PERSONA -> R.string.character_prompt_persona
    PromptModuleKind.SCENARIO -> R.string.character_prompt_scenario
    PromptModuleKind.OUTPUT_RULES -> R.string.character_prompt_output_rules
    PromptModuleKind.GUARDRAIL -> R.string.character_prompt_guardrail
    PromptModuleKind.STYLE -> R.string.character_style
    PromptModuleKind.CUSTOM -> R.string.character_prompt_add_module
}
