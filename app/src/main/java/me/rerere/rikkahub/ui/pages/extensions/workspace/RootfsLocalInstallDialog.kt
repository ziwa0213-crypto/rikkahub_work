package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.workspace.rootfs.RootfsArch
import me.rerere.workspace.rootfs.RootfsError
import me.rerere.workspace.rootfs.RootfsInstallException

@Composable
internal fun RootfsLocalInstallDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val phrase = stringResource(R.string.rootfs_local_confirm_phrase)
    val arch = remember { RootfsArch.current()?.token ?: "—" }
    val maxHeight = minOf(440.dp, LocalConfiguration.current.screenHeightDp.dp * 0.4f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rootfs_local_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sections = listOf(
                        R.string.rootfs_local_what_happens_title to listOf(R.string.rootfs_local_what_happens),
                        R.string.rootfs_local_unverified_title to listOf(R.string.rootfs_local_unverified),
                        R.string.rootfs_local_executed_title to listOf(R.string.rootfs_local_executed),
                        R.string.rootfs_local_may_fail_title to listOf(R.string.rootfs_local_may_fail, R.string.rootfs_local_checks_note),
                        R.string.rootfs_local_dont_install_title to listOf(R.string.rootfs_local_dont_desktop, R.string.rootfs_local_dont_arch,
                            R.string.rootfs_local_dont_format, R.string.rootfs_local_dont_huge, R.string.rootfs_local_dont_unknown,
                            R.string.rootfs_local_dont_service, R.string.rootfs_local_dont_kernel, R.string.rootfs_local_dont_32bit),
                        R.string.rootfs_local_advice_title to listOf(R.string.rootfs_local_advice_source, R.string.rootfs_local_advice_size, R.string.rootfs_local_advice_update),
                    )
                    sections.forEach { (title, body) ->
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        body.forEach { key ->
                            val text = if (key == R.string.rootfs_local_dont_arch || key == R.string.rootfs_local_may_fail) stringResource(key, arch) else stringResource(key)
                            Text("• $text", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(stringResource(R.string.rootfs_local_confirm_prompt), style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(phrase, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rootfs_local_confirm_hint)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = input.trim() == phrase) { Text(stringResource(R.string.rootfs_local_confirm_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

internal fun rootfsErrorMessage(context: Context, error: Throwable): String {
    if (error !is RootfsInstallException) return context.getString(R.string.rootfs_error_generic, error.message.orEmpty().take(400))
    val args = error.arguments
    return when (error.code) {
        RootfsError.UNSUPPORTED_ARCH -> context.getString(R.string.rootfs_picker_arch_unsupported)
        RootfsError.INVALID_EXTENSION -> context.getString(R.string.rootfs_local_bad_extension)
        RootfsError.INVALID_FORMAT -> context.getString(R.string.rootfs_error_format)
        RootfsError.MISSING_SHELL -> context.getString(R.string.rootfs_error_missing_shell)
        RootfsError.ARCH_MISMATCH -> context.getString(R.string.rootfs_picker_arch_mismatch, args.getOrNull(0) ?: "—", args.getOrNull(1) ?: "—")
        RootfsError.DESKTOP -> context.getString(R.string.rootfs_error_desktop)
        RootfsError.TOO_MANY_FILES -> context.getString(R.string.rootfs_error_file_count)
        RootfsError.CHECKSUM -> context.getString(R.string.rootfs_error_checksum, args.firstOrNull() ?: "1")
        RootfsError.DOWNLOAD -> context.getString(R.string.rootfs_error_download, args.firstOrNull() ?: "1")
        RootfsError.NO_SPACE -> context.getString(R.string.rootfs_error_space, args.getOrNull(0)?.toLongOrNull()?.fileSizeToString() ?: "—", args.getOrNull(1)?.toLongOrNull()?.fileSizeToString() ?: "—")
        RootfsError.SWITCH_FAILED -> context.getString(R.string.rootfs_error_generic, error.message.orEmpty())
    }
}
