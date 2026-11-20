package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.LocalPromptDrop
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R

@Composable
fun LocalPromptNoticeBar(drops: Set<LocalPromptDrop>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val texts = buildList {
        if (LocalPromptDrop.TOOLS in drops) add(stringResource(R.string.local_llm_notice_tools_dropped))
        if (LocalPromptDrop.MEMORY in drops) add(stringResource(R.string.local_llm_notice_memory_dropped))
    }
    val text = texts.joinToString(" · ")
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(HugeIcons.Alert01, null, Modifier.size(16.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss) {
                Icon(HugeIcons.Cancel01, stringResource(R.string.update_card_close), Modifier.size(16.dp))
            }
        }
    }
}
