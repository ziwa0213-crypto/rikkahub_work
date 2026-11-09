package me.rerere.rikkahub.ui.pages.pulse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.data.repository.PulseRepository
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun PulseEntryPage() {
    val repository = koinInject<PulseRepository>()
    var conversationId by remember { mutableStateOf<Uuid?>(null) }
    LaunchedEffect(repository) { conversationId = repository.ensurePulseConversation() }
    conversationId?.let { ChatPage(id = it, text = null, files = emptyList()) }
}
