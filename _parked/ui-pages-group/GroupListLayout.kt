package me.rerere.rikkahub.ui.pages.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors

// Same list-page skeleton as AssistantPage; only the rows and search text vary.
@Composable
fun GroupListLayout(title: String, searchHint: String, query: String, onQuery: (String) -> Unit,
    onCreate: () -> Unit, listState: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) }, navigationIcon = { BackButton() },
                actions = { IconButton(onClick = onCreate) { Icon(HugeIcons.Add01, stringResource(R.string.group_create)) } },
                scrollBehavior = scrollBehavior, colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(top = 16.dp).consumeWindowInsets(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(searchHint) },
                leadingIcon = { Icon(HugeIcons.Search01, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(HugeIcons.Cancel01, null) } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), state = listState,
                content = content,
            )
        }
    }
}
