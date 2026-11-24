package me.rerere.rikkahub.ui.pages.character

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.extensions.LorebookTab
import me.rerere.rikkahub.ui.pages.extensions.PromptVM
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun CharacterLorebookPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.character_lorebook_page_title)) },
            navigationIcon = { BackButton() }, scrollBehavior = scroll, colors = CustomColors.topBarColors)
    }, modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LorebookTab(settings.lorebooks) { books -> vm.updateSettings { it.copy(lorebooks = books) } }
        }
    }
}
