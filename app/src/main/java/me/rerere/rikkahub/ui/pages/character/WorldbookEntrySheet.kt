package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.PromptInjection.RegexInjection
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
internal fun WorldbookEntrySheet(entry: RegexInjection, onUpdate: (RegexInjection) -> Unit, onDismiss: () -> Unit, onDelete: () -> Unit) {
    // Fast edits use this latest draft, rather than a stale Settings snapshot while persistence suspends.
    var draft by remember(entry.id) { mutableStateOf(entry) }
    var deleting by remember { mutableStateOf(false) }
    fun change(block: (RegexInjection) -> RegexInjection) { draft = block(draft); onUpdate(draft) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CardGroup(title = { Text(stringResource(R.string.character_entry_content)) }) {
                characterTextField(R.string.character_entry_comment, draft.stComment) { v -> change { it.copy(stComment = v, name = v) } }
                formItem { CharacterChips(R.string.character_entry_keys, draft.keywords) { v -> change { it.copy(keywords = v) } } }
                formItem { CharacterChips(R.string.character_entry_secondary, draft.stSecondaryKeys) { v -> change { it.copy(stSecondaryKeys = v) } } }
                characterTextField(R.string.character_entry_content, draft.content, 8) { v -> change { it.copy(content = v) } }
            } }
            item { CardGroup(title = { Text(stringResource(R.string.character_entry_trigger)) }) {
                toggle(R.string.character_entry_constant, draft.constantActive) { v -> change { it.copy(constantActive = v) } }
                toggle(R.string.character_entry_selective, draft.stSelective) { v -> change { it.copy(stSelective = v) } }
                characterChoice(R.string.character_entry_logic, draft.stSelectiveLogic, listOf(R.string.character_logic_any,
                    R.string.character_logic_not_all, R.string.character_logic_none, R.string.character_logic_all)) { v -> change { it.copy(stSelectiveLogic = v) } }
                toggle(R.string.character_entry_whole, draft.stMatchWholeWords) { v -> change { it.copy(stMatchWholeWords = v) } }
                toggle(R.string.character_entry_regex, draft.useRegex) { v -> change { it.copy(useRegex = v) } }
                toggle(R.string.character_entry_case, draft.caseSensitive) { v -> change { it.copy(caseSensitive = v) } }
                characterNumber(R.string.character_entry_scan, draft.scanDepth, 1..20) { v -> change { it.copy(scanDepth = v ?: 4) } }
                toggle(R.string.character_match_persona, draft.stMatchPersonaDescription) { v -> change { it.copy(stMatchPersonaDescription = v) } }
                toggle(R.string.character_match_description, draft.stMatchCharacterDescription) { v -> change { it.copy(stMatchCharacterDescription = v) } }
                toggle(R.string.character_match_personality, draft.stMatchCharacterPersonality) { v -> change { it.copy(stMatchCharacterPersonality = v) } }
                toggle(R.string.character_match_depth, draft.stMatchCharacterDepthPrompt) { v -> change { it.copy(stMatchCharacterDepthPrompt = v) } }
                toggle(R.string.character_match_scenario, draft.stMatchScenario) { v -> change { it.copy(stMatchScenario = v) } }
                toggle(R.string.character_match_notes, draft.stMatchCreatorNotes) { v -> change { it.copy(stMatchCreatorNotes = v) } }
            } }
            item { CardGroup(title = { Text(stringResource(R.string.character_entry_injection)) }) {
                characterChoice(R.string.character_entry_position, draft.stPosition.coerceAtLeast(0), characterPositionLabels) { v -> change { it.copy(stPosition = v) } }
                if (draft.stPosition == 4) characterNumber(R.string.character_depth, draft.injectDepth, 1..20) { v -> change { it.copy(injectDepth = v ?: 4) } }
                characterChoice(R.string.character_message_role, characterRoleValues.indexOf(draft.role.name.lowercase()), characterRoleLabels) { v -> change { it.copy(role = MessageRole.valueOf(characterRoleValues[v].uppercase())) } }
                characterNumber(R.string.character_entry_order, draft.priority, Int.MIN_VALUE..Int.MAX_VALUE) { v -> change { it.copy(priority = v ?: 0) } }
                toggle(R.string.character_entry_probability_enabled, draft.stUseProbability) { v -> change { it.copy(stUseProbability = v) } }
                characterNumber(R.string.character_entry_probability, draft.stProbability, 0..100) { v -> change { it.copy(stProbability = v ?: 100) } }
                characterNumber(R.string.character_entry_sticky, draft.stSticky) { v -> change { it.copy(stSticky = v ?: 0) } }
                characterNumber(R.string.character_entry_cooldown, draft.stCooldown) { v -> change { it.copy(stCooldown = v ?: 0) } }
                characterNumber(R.string.character_entry_delay, draft.stDelay) { v -> change { it.copy(stDelay = v ?: 0) } }
            } }
            item { CardGroup(title = { Text(stringResource(R.string.character_entry_recursion)) }) {
                toggle(R.string.character_entry_recurse, !draft.stExcludeRecursion) { v -> change { it.copy(stExcludeRecursion = !v) } }
                toggle(R.string.character_entry_prevent_recursion, draft.stPreventRecursion) { v -> change { it.copy(stPreventRecursion = v) } }
                characterNumber(R.string.character_entry_recursion_delay, draft.stDelayUntilRecursion, 0..3) { v -> change { it.copy(stDelayUntilRecursion = v ?: 0) } }
                toggle(R.string.character_entry_ignore_budget, draft.stIgnoreBudget) { v -> change { it.copy(stIgnoreBudget = v) } }
            } }
            item { CardGroup(title = { Text(stringResource(R.string.character_entry_group)) }) {
                characterTextField(R.string.character_entry_group_name, draft.stGroup) { v -> change { it.copy(stGroup = v) } }
                characterNumber(R.string.character_entry_weight, draft.stGroupWeight) { v -> change { it.copy(stGroupWeight = v ?: 100) } }
                toggle(R.string.character_entry_override, draft.stGroupOverride) { v -> change { it.copy(stGroupOverride = v) } }
                if (draft.stPosition == 7) characterTextField(R.string.character_entry_outlet, draft.stOutletName) { v -> change { it.copy(stOutletName = v) } }
                item(onClick = { deleting = true }, headlineContent = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) })
            } }
        }
    }
    RikkaConfirmDialog(show = deleting, title = stringResource(R.string.confirm_delete), confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel), onDismiss = { deleting = false }, onConfirm = onDelete) { Text(stringResource(R.string.character_entry_delete)) }
}

internal fun CardGroupScope.toggle(label: Int, checked: Boolean, update: (Boolean) -> Unit) =
    switchItem(checked, update, headlineContent = { Text(stringResource(label)) })
