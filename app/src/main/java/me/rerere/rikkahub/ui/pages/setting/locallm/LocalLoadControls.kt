package me.rerere.rikkahub.ui.pages.setting.locallm

import android.os.SystemClock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.rerere.llamacpp.LoadStage
import me.rerere.llamacpp.LocalModelException
import me.rerere.llamacpp.LocalModelState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.ErrorText
import androidx.compose.ui.platform.LocalContext

@Composable
fun localLoadingText(state: LocalModelState.Loading): String {
    var seconds by remember(state.startedAt) { mutableLongStateOf(0) }
    LaunchedEffect(state.startedAt) {
        while (true) { seconds = (SystemClock.elapsedRealtime() - state.startedAt).coerceAtLeast(0) / 1000; delay(1000) }
    }
    return stringResource(when (state.stage) {
        LoadStage.WEIGHTS -> R.string.local_llm_loading_weights
        LoadStage.CONTEXT -> R.string.local_llm_loading_context
        LoadStage.PROJECTOR -> R.string.local_llm_loading_projector
    }) + stringResource(R.string.local_llm_loading_elapsed, seconds)
}

@Composable
fun localLoadFailure(state: LocalModelState.Failed): String =
    ErrorText.resolve(LocalContext.current, LocalModelException(state.reason, state.detail))
        ?: stringResource(R.string.local_llm_error_bad_file)

@Composable
fun LocalMemoryConfirmation(vm: SettingLocalLlmViewModel) {
    val verdict by vm.lowMemoryConfirm.collectAsStateWithLifecycle()
    verdict?.let { value ->
        AlertDialog(onDismissRequest = vm::dismissLowMemory,
            title = { Text(stringResource(R.string.local_llm_memory_warning_title)) },
            text = { Text(stringResource(R.string.local_llm_memory_warning_body, value.deviceGb, value.requiredGb)) },
            confirmButton = { TextButton(onClick = vm::confirmLowMemory) { Text(stringResource(R.string.local_llm_memory_continue)) } },
            dismissButton = { TextButton(onClick = vm::dismissLowMemory) { Text(stringResource(R.string.cancel)) } })
    }
}
