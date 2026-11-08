package com.yenaly.han1meviewer.RouVideo.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTagLabels
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.Sort
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTagCount
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteChipRow
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

private val TAG_SORTS = listOf(Sort.NEWEST, Sort.VIEWS, Sort.LIKES)

@Composable
fun RouVideoTagScreen(
    tag: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    viewModel: RouVideoListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortName by rememberSaveable { mutableStateOf(Sort.NEWEST.name) }
    val sort = runCatching { Sort.valueOf(sortName) }.getOrDefault(Sort.NEWEST)
    val sortLabels = TAG_SORTS.associateWith { stringResource(it.labelRes) }

    LaunchedEffect(tag, sortName) {
        viewModel.setSource(RouVideoSource.Tag(tag, sort))
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(text = RouVideoTagLabels.label(tag), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SiteChipRow(
                options = TAG_SORTS,
                selected = sort,
                label = { sortLabels.getValue(it) },
                onSelect = { sortName = it.name },
            )
            if (state.siblings.isNotEmpty()) {
                SiteChipRow(
                    options = state.siblings,
                    selected = RouVideoTagCount(tag),
                    label = { "${RouVideoTagLabels.label(it.id)} (${it.count})" },
                    onSelect = { onNavigateToTag(it.id) },
                    key = { it.id },
                )
            }
            RouVideoVideoGrid(
                state = state,
                onRetry = { viewModel.refresh() },
                onLoadMore = { viewModel.loadMore() },
                onOpenVideo = onNavigateToVideo,
                skipTag = tag,
            )
        }
    }
}
