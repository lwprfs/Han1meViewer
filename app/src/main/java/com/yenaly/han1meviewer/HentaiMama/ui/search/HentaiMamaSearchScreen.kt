package com.yenaly.han1meviewer.HentaiMama.ui.search

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaOptions
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaFilterSheet
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaVideoCard
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeCategoryRepo
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaViewModel
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BASE_GRID_MIN_SIZE_DP = 150

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HentaiMamaSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    initialCategoryKey: String? = null,
    initialGenre: String? = null,
    initialOrder: String? = null,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    var settingsReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HentaiMamaCardSettings.load()
        settingsReady = true
    }

    if (!settingsReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val context = LocalContext.current

    var searchQuery by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedProducer by viewModel.selectedProducer.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedOrder by viewModel.selectedOrder.collectAsStateWithLifecycle()
    val isFilterMode by viewModel.searchIsFilterMode.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.searchIsLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()

    val orders = remember { HentaiMamaOptions.orders }
    val activeFilterCount = listOfNotNull(
        selectedOrder, selectedGenre, selectedYear, selectedProducer
    ).size

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    fun runSearch(reset: Boolean = true) {
        if (reset) {
            coroutineScope.launch { gridState.scrollToItem(0) }
        }
        hasSearched = true
        if (searchQuery.isNotBlank()) {
            viewModel.searchVideos(1, searchQuery)
        } else {
            viewModel.filterVideos(1)
        }
    }

    LaunchedEffect(initialQuery, initialCategoryKey, initialGenre, initialOrder) {
        if (hasSearched) return@LaunchedEffect

        if (!initialQuery.isNullOrEmpty()) {
            searchQuery = initialQuery
            runSearch()
            return@LaunchedEffect
        }

        if (!initialCategoryKey.isNullOrBlank()) {
            val category = withContext(Dispatchers.IO) {
                HentaiMamaHomeCategoryRepo.load(context)
                    .firstOrNull { it.key == initialCategoryKey }
            }
            if (category != null) {
                val inferredGenre = extractGenreFromPath(category.genrePath)
                viewModel.setGenre(inferredGenre)
                viewModel.setOrder(category.sort)
                viewModel.setProducer(null)
                viewModel.setYear(null)
                runSearch()
                return@LaunchedEffect
            }
        }

        if (!initialGenre.isNullOrBlank() || !initialOrder.isNullOrBlank()) {
            viewModel.setGenre(initialGenre)
            viewModel.setOrder(initialOrder)
            runSearch()
        }
    }

    LaunchedEffect(gridState, hasMore, isLoadingMore, hasSearched) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .filter { (total, _) -> total > 4 && hasMore && hasSearched && !isLoadingMore }
            .collect { (total, last) ->
                if (last >= total - 4) {
                    viewModel.loadNextSearchPage()
                }
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
                    runSearch()
                }
            },
            onReset = { viewModel.clearFilters() },
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
                onValueChange = { newValue ->
                    searchQuery = newValue
                    if (newValue.isBlank() && !isFilterMode) {
                        viewModel.clearSearch()
                        hasSearched = false
                    }
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
                                    searchQuery = ""
                                    viewModel.clearSearch()
                                    hasSearched = false
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(
                            onClick = { runSearch() },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )

            val activeChips = buildList {
                selectedOrder?.let { key ->
                    val label = orders.find { it.value == key }?.name ?: key
                    add("Order: $label" to {
                        viewModel.setOrder(null)
                        runSearch()
                    })
                }
                selectedGenre?.let { key ->
                    add("Genre: $key" to {
                        viewModel.setGenre(null)
                        runSearch()
                    })
                }
                selectedYear?.let { key ->
                    add("Year: $key" to {
                        viewModel.setYear(null)
                        runSearch()
                    })
                }
                selectedProducer?.let { key ->
                    add("Producer: ${key.take(20)}" to {
                        viewModel.setProducer(null)
                        runSearch()
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
                                viewModel.clearFilters()
                                runSearch()
                            },
                            label = { Text("Clear all") },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (searchResults.isEmpty() && hasSearched) {
                        LoadingContent()
                    } else if (searchResults.isEmpty() && !hasSearched) {
                        SearchPlaceholder()
                    } else {
                        DisplayResults(
                            searchResults,
                            isLoadingMore,
                            gridState,
                            onNavigateToVideo,
                        )
                    }
                }

                is PageLoadingState.Success,
                is PageLoadingState.NoMoreData -> {
                    if (searchResults.isEmpty() && hasSearched) {
                        EmptyResults()
                    } else if (searchResults.isEmpty()) {
                        SearchPlaceholder()
                    } else {
                        DisplayResults(
                            searchResults,
                            isLoadingMore,
                            gridState,
                            onNavigateToVideo,
                        )
                    }
                }

                is PageLoadingState.Error -> {
                    if (searchResults.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { runSearch() },
                        )
                    } else {
                        Column {
                            DisplayResults(
                                searchResults,
                                isLoadingMore,
                                gridState,
                                onNavigateToVideo,
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

private fun extractGenreFromPath(path: String): String? {
    if (path.isBlank()) return null
    val fullUrl = if (path.startsWith("http")) path
    else "https://placeholder.local/$path"
    return runCatching {
        Uri.parse(fullUrl).getQueryParameter("genres_filter[]")
    }.getOrNull()?.takeIf { it.isNotBlank() }
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
    allVideos: List<com.yenaly.han1meviewer.logic.model.HanimeInfo>,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String) -> Unit,
) {
    val uniqueVideos = remember(allVideos) { allVideos.distinctBy { it.videoCode } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        val gridWidth = maxWidth
        val columns = HentaiMamaCardSettings
            .effectiveCardCount(gridWidth.value / BASE_GRID_MIN_SIZE_DP)
            .toInt()
            .coerceAtLeast(2)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(
                count = uniqueVideos.size,
                key = { i -> uniqueVideos[i].videoCode },
            ) { index ->
                val video = uniqueVideos[index]
                HentaiMamaVideoCard(
                    videoItem = video,
                    onClick = { onNavigateToVideo(video.videoCode) },
                    onLongClick = { _, _ -> },
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
}
