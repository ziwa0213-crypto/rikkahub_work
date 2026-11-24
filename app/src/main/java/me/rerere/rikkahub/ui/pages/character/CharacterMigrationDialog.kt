package me.rerere.rikkahub.ui.pages.character

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R

@Composable
internal fun CharacterMigrationDialog(onDecline: () -> Unit, onTransfer: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.character_migration_title)) },
        text = { Text(stringResource(R.string.character_migration_question)) },
        confirmButton = { TextButton(onClick = onTransfer) { Text(stringResource(R.string.character_transfer)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.character_migration_decline)) } },
    )
}
