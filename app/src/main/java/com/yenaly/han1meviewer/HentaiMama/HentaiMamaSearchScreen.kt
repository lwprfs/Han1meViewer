package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HentaiMamaSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    var searchQuery by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var currentPage by remember { mutableIntStateOf(1) }
    var allVideos by remember { mutableStateOf<List<HanimeInfo>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedProducer by viewModel.selectedProducer.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedOrder by viewModel.selectedOrder.collectAsStateWithLifecycle()

    val orders = remember { HentaiMamaOptions.orders }
    val activeFilterCount = listOfNotNull(
        selectedOrder, selectedGenre, selectedYear, selectedProducer
    ).size

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    fun doSearch(resetPage: Boolean = true) {
        if (resetPage) {
            currentPage = 1
            allVideos = emptyList()
            hasMorePages = true
            coroutineScope.launch { gridState.scrollToItem(0) }
        }
        hasSearched = true
        isLoadingMore = true

        if (searchQuery.isNotBlank()) {
            viewModel.searchVideos(currentPage, searchQuery)
        } else {
            viewModel.filterVideos(currentPage)
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrEmpty() && !hasSearched) {
            searchQuery = initialQuery
            doSearch()
        }
    }

    LaunchedEffect(searchState) {
        when (val state = searchState) {
            is PageLoadingState.Success -> {
                val incoming = state.info
                allVideos = if (currentPage == 1) incoming else allVideos + incoming
                isLoadingMore = false
                if (incoming.isEmpty()) hasMorePages = false
            }
            is PageLoadingState.NoMoreData -> {
                isLoadingMore = false
                hasMorePages = false
            }
            is PageLoadingState.Error -> isLoadingMore = false
            is PageLoadingState.Loading -> Unit
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .filter { (total, _) -> total > 4 && hasMorePages && hasSearched && !isLoadingMore }
            .collect { (total, last) ->
                if (last >= total - 4) {
                    isLoadingMore = true
                    currentPage += 1
                    if (searchQuery.isNotBlank()) {
                        viewModel.searchVideos(currentPage, searchQuery)
                    } else {
                        viewModel.filterVideos(currentPage)
                    }
                }
            }
    }

    fun applyFilterAndSearch() {
        if (searchQuery.isBlank()) {
            doSearch(resetPage = true)
        }
    }

    if (showFilterSheet) {
        HentaiMamaFilterSheet(
            initialOrder = selectedOrder,
            initialGenre = selectedGenre,
            initialYear = selectedYear,
            initialProducer = selectedProducer,
            onDismiss = { showFilterSheet = false },
            onApply = { order, genre, year, producer ->
                viewModel.setOrder(order)
                viewModel.setGenre(genre)
                viewModel.setYear(year)
                viewModel.setProducer(producer)
                showFilterSheet = false
                if (searchQuery.isBlank()) {
                    doSearch(resetPage = true)
                }
            },
            onReset = {
                viewModel.clearFilters()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search HentaiMama") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge { Text(activeFilterCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                FloatingActionButton(
                    onClick = { coroutineScope.launch { gridState.scrollToItem(0) } },
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search videos…") },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    allVideos = emptyList()
                                    hasSearched = false
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(
                            onClick = { doSearch(resetPage = true) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch(resetPage = true) }),
            )

            val activeChips = buildList {
                selectedOrder?.let { key ->
                    val label = orders.find { it.value == key }?.name ?: key
                    add("Order: $label" to { viewModel.setOrder(null); applyFilterAndSearch() })
                }
                selectedGenre?.let { key ->
                    add("Genre: $key" to { viewModel.setGenre(null); applyFilterAndSearch() })
                }
                selectedYear?.let { key ->
                    add("Year: $key" to { viewModel.setYear(null); applyFilterAndSearch() })
                }
                selectedProducer?.let { key ->
                    add("Producer: ${key.take(20)}" to {
                        viewModel.setProducer(null); applyFilterAndSearch()
                    })
                }
            }

            AnimatedVisibility(
                visible = activeChips.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    activeChips.forEach { (label, onRemove) ->
                        InputChip(
                            selected = true,
                            onClick = onRemove,
                            label = {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    }
                    if (activeChips.size > 1) {
                        AssistChip(
                            onClick = {
                                viewModel.clearFilters()
                                applyFilterAndSearch()
                            },
                            label = { Text("Clear all") },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        LoadingContent()
                    } else if (allVideos.isEmpty() && !hasSearched) {
                        SearchPlaceholder()
                    } else {
                        DisplayResults(
                            allVideos, isLoadingMore, gridState, onNavigateToVideo
                        )
                    }
                }

                is PageLoadingState.Success,
                is PageLoadingState.NoMoreData -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        EmptyResults()
                    } else if (allVideos.isEmpty()) {
                        SearchPlaceholder()
                    } else {
                        DisplayResults(
                            allVideos, isLoadingMore, gridState, onNavigateToVideo
                        )
                    }
                }

                is PageLoadingState.Error -> {
                    if (allVideos.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { doSearch(resetPage = true) },
                        )
                    } else {
                        Column {
                            DisplayResults(
                                allVideos, isLoadingMore, gridState, onNavigateToVideo
                            )
                            Text(
                                text = "Failed to load more: ${state.throwable.message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Type to search, or tap the filter icon to browse by genre, year, producer, or order.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun EmptyResults() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No results found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DisplayResults(
    allVideos: List<HanimeInfo>,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String) -> Unit,
) {
    val uniqueVideos = remember(allVideos) { allVideos.distinctBy { it.videoCode } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(count = uniqueVideos.size, key = { i -> uniqueVideos[i].videoCode }) { index ->
            val video = uniqueVideos[index]
            VideoCardItem(
                videoItem = video,
                onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                onLongClickVideosItem = { _, _ -> },
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}