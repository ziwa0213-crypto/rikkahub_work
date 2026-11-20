package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Model
import me.rerere.llamacpp.LocalModelState
import me.rerere.rikkahub.R
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.rikkahub.ui.pages.setting.locallm.SettingLocalLlmViewModel
import me.rerere.rikkahub.ui.pages.setting.locallm.localLoadFailure
import me.rerere.rikkahub.ui.pages.setting.locallm.localLoadingText

/** Page content: the drawer and navigation destinations must cover this control. */
@Composable
fun LocalModelCapsule(model: Model, vm: SettingLocalLlmViewModel, modifier: Modifier = Modifier) {
    val state by vm.runtimeState.collectAsStateWithLifecycle()
    val visible = state !is LocalModelState.Ready || (state as LocalModelState.Ready).modelId != model.modelId
    val visibility = remember(model.modelId) { MutableTransitionState(visible) }
    visibility.targetState = visible
    if (!visibility.currentState && !visibility.targetState) return
    val loading = (state as? LocalModelState.Loading)?.takeIf { it.modelId == model.modelId }
    val failure = (state as? LocalModelState.Failed)?.takeIf { it.modelId == model.modelId }
    var showFailure by remember(model.modelId) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
        AnimatedVisibility(visibility, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(shape = RoundedCornerShape(18.dp),
            color = if (failure == null) colors.primaryContainer else colors.errorContainer,
            contentColor = if (failure == null) colors.onPrimaryContainer else colors.onErrorContainer,
            modifier = Modifier.height(36.dp).clickable(enabled = loading == null && state !is LocalModelState.Loading) {
                if (failure != null) showFailure = true else vm.load(model.modelId)
            }) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading != null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimaryContainer)
                else Icon(if (failure != null) HugeIcons.Alert01 else HugeIcons.Package01, null, Modifier.size(16.dp))
                Text(when {
                    loading != null -> localLoadingText(loading)
                    failure != null -> stringResource(R.string.local_llm_load_failed_view)
                    else -> stringResource(R.string.local_llm_not_loaded_load)
                }, style = MaterialTheme.typography.labelLarge)
            }
        }
        }
    if (showFailure && failure != null) ModalBottomSheet(onDismissRequest = { showFailure = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localLoadFailure(failure))
            Text(stringResource(R.string.local_llm_load_advice))
            TextButton(onClick = { showFailure = false; vm.load(model.modelId) }) { Text(stringResource(R.string.local_llm_retry_load)) }
        }
    }
}
