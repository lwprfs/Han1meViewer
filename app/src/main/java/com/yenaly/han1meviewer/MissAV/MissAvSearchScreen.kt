package com.yenaly.han1meviewer.MissAV

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

/**
 * MissAV search & browse screen.
 *
 * Behaviour:
 *  - When the user has typed a query → "search mode".
 *      Sort + Filter apply to the search request. Genre does NOT apply.
 *  - When the query is blank → "browse mode".
 *      Sort + Filter + Genre all apply to the browse request.
 *
 * Filter options come from `assets/missav_options/tags.json`
 * via [MissAvOptions]. No hardcoded lists live in this file.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MissAvSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    viewModel: MissAvViewModel = viewModel(),
) {
    // ── State ────────────────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var selectedSort by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }

    var currentPage by remember { mutableIntStateOf(1) }
    var allVideos by remember { mutableStateOf<List<HanimeInfo>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var initialLoadDone by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }

    // Two independent sheets
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var showGenreSheet by remember { mutableStateOf(false) }

    val searchState by viewModel.searchFlow.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    // Genre only applies when we are browsing (no query typed)
    val isBrowsing = searchQuery.isBlank()

    // ── Search / browse dispatcher ───────────────────────────────────────
    fun doSearch(resetPage: Boolean = true) {
        if (resetPage) {
            currentPage = 1
            allVideos = emptyList()
            hasMorePages = true
            coroutineScope.launch { gridState.scrollToItem(0) }
        }
        hasSearched = true
        isLoadingMore = true

        // In search mode we deliberately ignore the genre selection
        val genreToUse = if (isBrowsing) {
            selectedGenre ?: MissAvOptions.DEFAULT_GENRE_KEY
        } else {
            MissAvOptions.DEFAULT_GENRE_KEY
        }

        viewModel.searchVideosWithGenre(
            query = searchQuery,
            page = currentPage,
            sort = selectedSort,
            genre = genreToUse,
            filter = selectedFilter,
        )
    }

    // ── Effects ──────────────────────────────────────────────────────────
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrEmpty() && !initialLoadDone) {
            searchQuery = initialQuery
            initialLoadDone = true
            doSearch(resetPage = true)
        }
    }

    LaunchedEffect(searchState) {
        when (val state = searchState) {
            is PageLoadingState.Success -> {
                val newVideos = state.info
                if (currentPage == 1) {
                    allVideos = newVideos
                } else {
                    val existing = allVideos.map { it.videoCode }.toSet()
                    allVideos = allVideos + newVideos.filter { it.videoCode !in existing }
                }
                isLoadingMore = false
                if (newVideos.isEmpty()) {
                    hasMorePages = false
                }
            }

            is PageLoadingState.Error -> {
                isLoadingMore = false
            }

            is PageLoadingState.NoMoreData -> {
                isLoadingMore = false
                hasMorePages = false
            }

            is PageLoadingState.Loading -> Unit
        }
    }

    // Infinite scroll trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= total - 4 &&
                    total > 4 &&
                    hasMorePages &&
                    !isLoadingMore &&
                    hasSearched &&
                    allVideos.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoadingMore = true
            val next = currentPage + 1
            currentPage = next

            val genreToUse = if (isBrowsing) {
                selectedGenre ?: MissAvOptions.DEFAULT_GENRE_KEY
            } else {
                MissAvOptions.DEFAULT_GENRE_KEY
            }

            viewModel.searchVideosWithGenre(
                query = searchQuery,
                page = next,
                sort = selectedSort,
                genre = genreToUse,
                filter = selectedFilter,
            )
        }
    }

    // ── Sheets ───────────────────────────────────────────────────────────
    if (showSortFilterSheet) {
        MissAvSortFilterSheet(
            initialSort = selectedSort,
            initialFilter = selectedFilter,
            onDismiss = { showSortFilterSheet = false },
            onApply = { sort, filter ->
                selectedSort = sort
                selectedFilter = filter
                showSortFilterSheet = false
                doSearch(resetPage = true)
            },
            onReset = {
                selectedSort = null
                selectedFilter = null
            },
        )
    }

    if (showGenreSheet) {
        MissAvGenreSheet(
            initialGenre = selectedGenre,
            onDismiss = { showGenreSheet = false },
            onApply = { genre ->
                selectedGenre = genre
                showGenreSheet = false
                doSearch(resetPage = true)
            },
            onReset = { selectedGenre = null },
        )
    }

    // ── UI ───────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search MissAV") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Genre button — only when browsing (no query)
                    if (isBrowsing) {
                        IconButton(onClick = { showGenreSheet = true }) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = "Browse genre",
                                tint = if (selectedGenre != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }

                    // Sort + Filter button — always available
                    BadgedBox(
                        badge = {
                            val count = setOfNotNull(selectedSort, selectedFilter).size
                            if (count > 0) {
                                Badge { Text(count.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showSortFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Sort & Filter",
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            // ── Search bar ───────────────────────────────────────────────
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
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(
                            onClick = { doSearch() },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            )

            // ── Active filter chips ──────────────────────────────────────
            val activeChips = buildList {
                if (isBrowsing) {
                    selectedGenre?.let { key ->
                        val label = MissAvOptions.genreLabel(key) ?: key
                        add("Genre: $label" to {
                            selectedGenre = null
                            doSearch()
                        })
                    }
                }
                selectedSort?.let { key ->
                    val label = MissAvOptions.displayName(MissAvGroup.SORT, key) ?: key
                    add("Sort: $label" to {
                        selectedSort = null
                        doSearch()
                    })
                }
                selectedFilter?.let { key ->
                    val label = MissAvOptions.displayName(MissAvGroup.FILTER, key) ?: key
                    add("Filter: $label" to {
                        selectedFilter = null
                        doSearch()
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
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
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
                                selectedSort = null
                                selectedFilter = null
                                if (isBrowsing) selectedGenre = null
                                doSearch()
                            },
                            label = { Text("Clear all") },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Results ──────────────────────────────────────────────────
            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (allVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        DisplayResults(
                            allVideos = allVideos,
                            isLoadingMore = isLoadingMore,
                            gridState = gridState,
                            onNavigateToVideo = onNavigateToVideo,
                        )
                    }
                }

                is PageLoadingState.Success,
                is PageLoadingState.NoMoreData -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!hasSearched && isBrowsing) {
                        // Haven't searched yet, nothing to display
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Type to search, or pick a genre",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        DisplayResults(
                            allVideos = allVideos,
                            isLoadingMore = isLoadingMore,
                            gridState = gridState,
                            onNavigateToVideo = onNavigateToVideo,
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
                                allVideos = allVideos,
                                isLoadingMore = isLoadingMore,
                                gridState = gridState,
                                onNavigateToVideo = onNavigateToVideo,
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

/**
 * Renders the video grid, deduplicated by videoCode.
 */
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
        items(
            count = uniqueVideos.size,
            key = { index -> uniqueVideos[index].videoCode },
        ) { index ->
            val video = uniqueVideos[index]
            VideoCardItem(
                videoItem = video,
                onClickVideosItem = {
                    onNavigateToVideo(video.videoCode, "/en/${video.videoCode}")
                },
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