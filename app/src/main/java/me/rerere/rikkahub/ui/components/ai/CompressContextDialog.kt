package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.LoadingAnimationIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
    provider: ProviderSetting? = null,
    autoCompressThreshold: Float? = null,
    onAutoCompressThresholdChange: ((Float) -> Unit)? = null,
    estimatedMaxTokens: Int? = null,
    initialTargetTokens: Int = 2000,
    initialKeepRecentMessages: Int = 32,
) {
    var additionalPrompt by remember { mutableStateOf("") }
    val selectedTokens = initialTargetTokens
    var keepRecentMessages by remember { mutableIntStateOf(initialKeepRecentMessages.coerceAtLeast(0)) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = currentJob?.isActive == true

    // Monitor job completion
    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
            onDismiss()
        }
        currentJob = null
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.chat_page_compress_context_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    // Loading state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingAnimationIndicator(
                            modifier = Modifier.size(32.dp),
                            provider = provider,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_page_compressing))
                    }
                } else {
                    Text(stringResource(R.string.chat_page_compress_context_desc))

                    if (autoCompressThreshold != null && onAutoCompressThresholdChange != null) {
                        var threshold by remember(autoCompressThreshold) { mutableFloatStateOf(autoCompressThreshold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.group_compress_threshold_label), style = MaterialTheme.typography.labelMedium)
                            Text("${(threshold * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.30f..0.95f,
                            steps = 12, onValueChangeFinished = { onAutoCompressThresholdChange(threshold) })
                        if (estimatedMaxTokens != null) Text(stringResource(R.string.group_compress_threshold_hint,
                            NumberFormat.getIntegerInstance().format((estimatedMaxTokens * threshold).toLong())),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                    }

                    // Additional context input
                    OutlinedTextField(
                        value = additionalPrompt,
                        onValueChange = { additionalPrompt = it },
                        label = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt))
                        },
                        placeholder = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.chat_page_compress_keep_recent),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalIconButton(
                                onClick = { keepRecentMessages = (keepRecentMessages - 1).coerceAtLeast(0) },
                                enabled = keepRecentMessages > 0,
                            ) {
                                Icon(HugeIcons.Remove01, contentDescription = null)
                            }
                            Text(
                                text = keepRecentMessages.toString(),
                                modifier = Modifier.widthIn(min = 40.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    if (keepRecentMessages < Int.MAX_VALUE) keepRecentMessages++
                                },
                                enabled = keepRecentMessages < Int.MAX_VALUE,
                            ) {
                                Icon(HugeIcons.Add01, contentDescription = null)
                            }
                        }
                        Text(
                            text = stringResource(R.string.chat_page_compress_keep_recent_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                TextButton(onClick = {
                    currentJob?.cancel()
                    currentJob = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                TextButton(onClick = {
                    currentJob = onConfirm(additionalPrompt, selectedTokens, keepRecentMessages)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
