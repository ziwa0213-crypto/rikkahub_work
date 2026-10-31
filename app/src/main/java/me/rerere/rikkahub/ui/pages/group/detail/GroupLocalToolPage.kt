package me.rerere.rikkahub.ui.pages.group.detail

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantLocalToolContent
import me.rerere.rikkahub.ui.pages.group.GroupVM
import me.rerere.rikkahub.ui.pages.group.rememberGroupVM
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun GroupLocalToolPage(groupId: String, vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val group = settings.groups.find { it.id.toString() == groupId } ?: return
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val proxy = remember(group) { Assistant(id = group.id, localTools = group.localTools) }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_tab_local_tools)) },
                navigationIcon = { BackButton() }, scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        }, modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        AssistantLocalToolContent(padding, proxy, showBrowser = false) { updated ->
            vm.updateGroup(group.id) { it.copy(localTools = updated.localTools) }
        }
    }
}
