package com.yenaly.han1meviewer.RouVideo.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTagLabels
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants.Sort
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoSearchLanding
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoRepo
import com.yenaly.han1meviewer.RouVideo.ui.home.RouVideoCardItem
import com.yenaly.han1meviewer.RouVideo.ui.home.RouVideoListViewModel
import com.yenaly.han1meviewer.RouVideo.ui.home.RouVideoSource
import com.yenaly.han1meviewer.RouVideo.ui.home.RouVideoVideoGrid
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteChipRow
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader

private val SEARCH_SORTS = listOf(Sort.RELEVANCE, Sort.NEWEST, Sort.VIEWS, Sort.LIKES)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouVideoSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    initialTag: String? = null,
    viewModel: RouVideoListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var tagFilter by rememberSaveable { mutableStateOf(initialTag.orEmpty()) }
    var sortName by rememberSaveable { mutableStateOf(Sort.RELEVANCE.name) }
    var seriesTab by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    val hasQuery = submitted.isNotBlank()
    val inResultMode = hasQuery || tagFilter.isNotBlank()
    val rawSort = runCatching { Sort.valueOf(sortName) }.getOrDefault(Sort.RELEVANCE)

    val sort = if (rawSort == Sort.RELEVANCE && !hasQuery) Sort.NEWEST else rawSort

    LaunchedEffect(submitted, tagFilter, sort, seriesTab) {
        if (!inResultMode) return@LaunchedEffect
        val videoId = RouVideoConstants.parseVideoReference(submitted)
        if (videoId != null) {
            onNavigateToVideo(videoId)
            submitted = ""
        } else {
            viewModel.setSource(RouVideoSource.Search(submitted, tagFilter, sort, seriesTab))
        }
    }
    LaunchedEffect(Unit) {
        if (initialQuery.isNullOrBlank() && initialTag.isNullOrBlank()) focus.requestFocus()
    }

    fun submit() {
        submitted = query.trim()
        keyboard?.hide()
    }

    val sortLabels = SEARCH_SORTS.associateWith { stringResource(it.labelRes) }
    val allLabel = stringResource(R.string.rouvideo_all)
    val videosLabel = stringResource(R.string.rouvideo_tab_videos)
    val seriesLabel = stringResource(R.string.rouvideo_tab_series)

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.rouvideo_search_hint)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                    )
                },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = { submit() }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val tagOptions = listOf("") + RouVideoConstants.MAIN_TAGS
            SiteChipRow(
                options = tagOptions,
                selected = tagFilter.takeIf { it in tagOptions } ?: "",
                label = { if (it.isEmpty()) allLabel else RouVideoTagLabels.label(it) },
                onSelect = { tagFilter = it },
            )
            if (!inResultMode) {
                SearchLanding(
                    onKeyword = { word ->
                        query = word
                        submitted = word
                    },
                    onTag = { tag -> tagFilter = tag },
                    onOpenVideo = onNavigateToVideo,
                )
            } else {
                SiteChipRow(
                    options = listOf(false, true),
                    selected = seriesTab,
                    label = { if (it) seriesLabel else videosLabel },
                    onSelect = { seriesTab = it },
                )
                SiteChipRow(
                    options = SEARCH_SORTS.filter { it != Sort.RELEVANCE || hasQuery },
                    selected = sort,
                    label = { sortLabels.getValue(it) },
                    onSelect = { sortName = it.name },
                )
                RouVideoVideoGrid(
                    state = state,
                    onRetry = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() },
                    onOpenVideo = onNavigateToVideo,
                    emptyHint = stringResource(R.string.rouvideo_no_results),
                    skipTag = tagFilter.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    onKeyword: (String) -> Unit,
    onTag: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val landing by produceState<RouVideoSearchLanding?>(initialValue = null) {
        value = RouVideoRepo.searchLanding().getOrNull()
    }
    val data = landing
    if (data == null) {
        Text(
            text = stringResource(R.string.rouvideo_search_prompt),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (data.hotSearches.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SiteVideoSectionHeader(title = stringResource(R.string.rouvideo_hot_searches))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        data.hotSearches.forEach { word ->
                            AssistChip(onClick = { onKeyword(word) }, label = { Text(word) })
                        }
                    }
                }
            }
        }
        if (data.cats.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SiteVideoSectionHeader(title = stringResource(R.string.rouvideo_start_from_category))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        data.cats.forEach { cat ->
                            AssistChip(onClick = { onTag(cat.id) }, label = { Text(RouVideoTagLabels.label(cat.id)) })
                        }
                    }
                }
            }
        }
        if (data.trending.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SiteVideoSectionHeader(title = stringResource(R.string.rouvideo_trending))
            }
            items(data.trending, key = { it.id }) { card ->
                RouVideoCardItem(card = card, onClick = { onOpenVideo(card.id) })
            }
        }
    }
}
