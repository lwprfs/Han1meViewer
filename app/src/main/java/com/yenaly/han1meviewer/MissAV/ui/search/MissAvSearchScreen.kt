package com.yenaly.han1meviewer.MissAV.ui.search
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import kotlinx.coroutines.launch
import com.yenaly.han1meviewer.MissAV.ui.components.MissAvSortFilterSheet
import com.yenaly.han1meviewer.MissAV.ui.components.MissAvGenreSheet

import com.yenaly.han1meviewer.MissAV.common.MissAvGroup
import com.yenaly.han1meviewer.MissAV.common.MissAvOptions
import com.yenaly.han1meviewer.MissAV.viewmodel.MissAvViewModel
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MissAvSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    viewModel: MissAvViewModel,
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val searchState by viewModel.searchFlow.collectAsStateWithLifecycle()
    val allVideos by viewModel.searchResults.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val hasMorePages by viewModel.hasMorePages.collectAsStateWithLifecycle()

    var showSortFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showGenreSheet by rememberSaveable { mutableStateOf(false) }
    var showSuggestionDropdown by remember { mutableStateOf(false) }
    var recentHistory by remember { mutableStateOf(MissAvSearchHistoryRepo.load()) }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val isBrowsing = searchQuery.isBlank()

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNullOrEmpty()) return@LaunchedEffect
        if (viewModel.searchQuery.value == initialQuery && hasSearched) return@LaunchedEffect
        viewModel.setSearchQuery(initialQuery)
        MissAvSearchHistoryRepo.push(initialQuery)
        recentHistory = MissAvSearchHistoryRepo.load()
        viewModel.performSearch(resetPage = true)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= total - 4 && total > 4 && hasMorePages && !isLoadingMore &&
                    hasSearched && allVideos.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    if (showSortFilterSheet) {
        MissAvSortFilterSheet(
            initialSort = selectedSort,
            initialFilter = selectedFilter,
            onDismiss = { showSortFilterSheet = false },
            onApply = { sort, filter ->
                viewModel.setSort(sort)
                viewModel.setFilter(filter)
                showSortFilterSheet = false
                viewModel.performSearch(resetPage = true)
            },
            onReset = {
                viewModel.setSort(null)
                viewModel.setFilter(null)
            },
        )
    }

    if (showGenreSheet) {
        MissAvGenreSheet(
            initialGenre = selectedGenre,
            onDismiss = { showGenreSheet = false },
            onApply = { genre ->
                viewModel.setGenre(genre)
                showGenreSheet = false
                viewModel.performSearch(resetPage = true)
            },
            onReset = { viewModel.setGenre(null) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search MissAV") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isBrowsing) {
                        IconButton(onClick = { showGenreSheet = true }) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = "Browse genre",
                                tint = if (selectedGenre != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    BadgedBox(
                        badge = {
                            val count = setOfNotNull(selectedSort, selectedFilter).size
                            if (count > 0) Badge { Text(count.toString()) }
                        },
                    ) {
                        IconButton(onClick = { showSortFilterSheet = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort & Filter")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch { gridState.scrollToItem(0) }
                    },
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            Box {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        viewModel.setSearchQuery(it)
                        showSuggestionDropdown = it.isBlank() && recentHistory.isNotEmpty()
                    },
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
                                        viewModel.resetSearch()
                                        coroutineScope.launch { gridState.scrollToItem(0) }
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(
                                onClick = {
                                    val q = searchQuery.trim()
                                    if (q.isNotEmpty()) {
                                        MissAvSearchHistoryRepo.push(q)
                                        recentHistory = MissAvSearchHistoryRepo.load()
                                    }
                                    viewModel.performSearch(resetPage = true)
                                    showSuggestionDropdown = false
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val q = searchQuery.trim()
                            if (q.isNotEmpty()) {
                                MissAvSearchHistoryRepo.push(q)
                                recentHistory = MissAvSearchHistoryRepo.load()
                            }
                            viewModel.performSearch(resetPage = true)
                            showSuggestionDropdown = false
                        }
                    ),
                )

                if (showSuggestionDropdown && recentHistory.isNotEmpty()) {
                    SuggestionDropdown(
                        history = recentHistory,
                        onPick = { q ->
                            viewModel.setSearchQuery(q)
                            MissAvSearchHistoryRepo.push(q)
                            recentHistory = MissAvSearchHistoryRepo.load()
                            viewModel.performSearch(resetPage = true)
                            showSuggestionDropdown = false
                        },
                        onClearHistory = {
                            MissAvSearchHistoryRepo.clear()
                            recentHistory = emptyList()
                            showSuggestionDropdown = false
                        },
                        onDismiss = { showSuggestionDropdown = false },
                    )
                }
            }

            val activeChips = buildList {
                if (isBrowsing) {
                    selectedGenre?.let { key ->
                        val label = MissAvOptions.genreLabel(key) ?: key
                        add("Genre: $label" to {
                            viewModel.setGenre(null)
                            viewModel.performSearch(resetPage = true)
                        })
                    }
                }
                selectedSort?.let { key ->
                    val label = MissAvOptions.displayName(MissAvGroup.SORT, key) ?: key
                    add("Sort: $label" to {
                        viewModel.setSort(null)
                        viewModel.performSearch(resetPage = true)
                    })
                }
                selectedFilter?.let { key ->
                    val label = MissAvOptions.displayName(MissAvGroup.FILTER, key) ?: key
                    add("Filter: $label" to {
                        viewModel.setFilter(null)
                        viewModel.performSearch(resetPage = true)
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
                    activeChips.forEach { (label, onClick) ->
                        InputChip(
                            selected = true,
                            onClick = onClick,
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
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
                                viewModel.setSort(null)
                                viewModel.setFilter(null)
                                if (isBrowsing) viewModel.setGenre(null)
                                viewModel.performSearch(resetPage = true)
                            },
                            label = { Text("Clear all") },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when (val state = searchState) {
                null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank())
                                "Type to search, or pick a genre"
                            else
                                "Tap the search icon to search for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is PageLoadingState.Loading -> {
                    if (allVideos.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                    }
                }

                is PageLoadingState.Success,
                is PageLoadingState.NoMoreData -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                    }
                }

                is PageLoadingState.Error -> {
                    if (allVideos.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { viewModel.performSearch(resetPage = true) },
                        )
                    } else {
                        Column {
                            DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
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
private fun SuggestionDropdown(
    history: List<String>,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(horizontal = 16.dp),
    ) {
        history.take(8).forEach { q ->
            DropdownMenuItem(
                text = { Text(q) },
                onClick = { onPick(q) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Clear search history") },
            onClick = onClearHistory,
            leadingIcon = {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
    }
}

@Composable
private fun DisplayResults(
    allVideos: List<HanimeInfo>,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String, String) -> Unit,
) {
    val uniqueVideos = allVideos.distinctBy { it.videoCode }

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
                onClickVideosItem = { onNavigateToVideo(video.videoCode, "/en/${video.videoCode}") },
                onLongClickVideosItem = { _, _ -> },
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
