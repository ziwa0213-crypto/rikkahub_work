package me.rerere.rikkahub.ui.pages.group.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.ui.components.ai.*
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.group.GroupVM
import me.rerere.rikkahub.ui.pages.group.rememberGroupVM
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun GroupExtensionsPage(groupId: String, vm: GroupVM = rememberGroupVM()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val group = settings.groups.find { it.id.toString() == groupId } ?: return
    val skillManager: SkillManager = koinInject()
    var skills by remember { mutableStateOf<List<SkillMetadata>>(emptyList()) }
    LaunchedEffect(skillManager) { skills = withContext(Dispatchers.IO) { skillManager.listSkills() } }
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 3 }
    val titles = listOf(
        R.string.extension_selector_tab_mode_injections,
        R.string.extension_selector_tab_lorebooks,
        R.string.extension_selector_tab_skills,
    )
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_extensions_page_title)) },
                navigationIcon = { BackButton() }, scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        }, modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent) {
                titles.forEachIndexed { index, title ->
                    Tab(selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(title)) })
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                val empty = when (page) {
                    0 -> settings.modeInjections.isEmpty()
                    1 -> settings.lorebooks.isEmpty()
                    else -> skills.isEmpty()
                }
                val destination = if (page == 2) Screen.Skills else Screen.Prompts
                val manageLabel = stringResource(if (page == 2)
                    R.string.assistant_extensions_page_goto_extensions else R.string.assistant_extensions_page_goto_prompts)
                if (empty) {
                    ExtensionEmptyState(
                        message = stringResource(when (page) {
                            0 -> R.string.assistant_extensions_page_empty_mode_injections
                            1 -> R.string.assistant_extensions_page_empty_lorebooks
                            else -> R.string.assistant_extensions_page_empty_skills
                        }), buttonText = manageLabel, onAction = { navController.navigate(destination) },
                    )
                } else Column {
                    when (page) {
                        0 -> ModeInjectionsContent(
                            modeInjections = settings.modeInjections, selectedIds = group.modeInjectionIds,
                            modifier = Modifier.weight(1f), onToggle = { id, checked ->
                                vm.updateGroup(group.id) { latest -> latest.copy(modeInjectionIds =
                                    if (checked) latest.modeInjectionIds + id else latest.modeInjectionIds - id) }
                            },
                        )
                        1 -> LorebooksContent(
                            lorebooks = settings.lorebooks, selectedIds = group.lorebookIds,
                            modifier = Modifier.weight(1f), onToggle = { id, checked ->
                                vm.updateGroup(group.id) { latest -> latest.copy(lorebookIds =
                                    if (checked) latest.lorebookIds + id else latest.lorebookIds - id) }
                            },
                        )
                        2 -> SkillsContent(
                            skills = skills, enabledSkills = group.enabledSkills,
                            modifier = Modifier.weight(1f), onToggle = { name, checked ->
                                vm.updateGroup(group.id) { latest -> latest.copy(enabledSkills =
                                    if (checked) latest.enabledSkills + name else latest.enabledSkills - name) }
                            },
                        )
                    }
                    TextButton(onClick = { navController.navigate(destination) }, modifier = Modifier.fillMaxWidth()) {
                        Text(manageLabel)
                    }
                }
            }
        }
    }
}
