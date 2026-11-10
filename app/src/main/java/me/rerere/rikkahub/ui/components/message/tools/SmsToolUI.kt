package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R

object SendSmsToolUI : ToolUIRenderer {
    override val toolName = "send_sms"

    @Composable
    override fun title(context: ToolUIContext): String = stringResource(R.string.pulse_sms_send_title)

    override fun hasSummary(context: ToolUIContext) = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        var expanded by remember(context.tool.toolCallId) { mutableStateOf(false) }
        val text = context.arguments.getStringContent("text").orEmpty()
        Column {
            Text(stringResource(R.string.pulse_sms_recipient, context.arguments.getStringContent("to").orEmpty()))
            SelectionContainer { Text(if (expanded || text.length <= 200) text else text.take(200) + "…",
                style = MaterialTheme.typography.bodySmall) }
            if (text.length > 200) TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.pulse_sms_collapse else R.string.pulse_sms_show_complete))
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        Column(Modifier.fillMaxHeight(0.8f).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pulse_sms_send_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.pulse_sms_recipient, context.arguments.getStringContent("to").orEmpty()))
            SelectionContainer { Text(context.arguments.getStringContent("text").orEmpty()) }
        }
    }
}
