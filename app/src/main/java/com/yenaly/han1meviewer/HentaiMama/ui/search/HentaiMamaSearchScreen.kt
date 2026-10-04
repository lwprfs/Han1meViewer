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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaOptions
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetworkRepo
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaFilterSheet
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSortBar
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSeriesCard
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSeriesRow
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaVideoCard
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaSeriesCardView
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeCategoryRepo
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaViewModel
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BASE_GRID_MIN_SIZE_DP = 150

@Composable
private fun rememberSearchCardWidth(
    availableWidthDp: Dp,
    columns: Int,
): Dp {
    val cardMultiplier by HentaiMamaCardSettings.cardMultiplierState
    val widthMultiplier by HentaiMamaCardSettings.widthMultiplierState
    val effectiveCount = (columns * cardMultiplier).coerceAtLeast(1f)
    val availableForCards = availableWidthDp - SpacingLarge * 2 - SpacingNormal * (effectiveCount - 1)
    return (availableForCards / effectiveCount) * widthMultiplier
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HentaiMamaSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    initialCategoryKey: String? = null,
    initialGenre: String? = null,
    initialOrder: String? = null,
    initialGenreSlug: String? = null,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        HentaiMamaCardSettings.load()
    }

    val context = LocalContext.current

    var searchQuery by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchSeries by viewModel.searchSeriesResults.collectAsStateWithLifecycle()
    val searchCards by viewModel.searchSeriesCards.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedProducer by viewModel.selectedProducer.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedOrder by viewModel.selectedOrder.collectAsStateWithLifecycle()
    val isFilterMode by viewModel.searchIsFilterMode.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.searchIsLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val genreSlug by viewModel.genreSlug.collectAsStateWithLifecycle()
    val genreHeader by viewModel.genreHeader.collectAsStateWithLifecycle()
    val genreSort by viewModel.genreSort.collectAsStateWithLifecycle()
    val genreLayout by viewModel.genreLayout.collectAsStateWithLifecycle()
    val currentPage by viewModel.searchCurrentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.searchTotalPages.collectAsStateWithLifecycle()

    val orders = remember { HentaiMamaOptions.orders }
    val activeFilterCount = listOfNotNull(
        selectedOrder, selectedGenre, selectedYear, selectedProducer,
        genreSlug?.takeIf { it.isNotBlank() },
    ).size

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    val isGenreMode = !genreSlug.isNullOrBlank()

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

    LaunchedEffect(initialQuery, initialCategoryKey, initialGenre, initialOrder, initialGenreSlug) {
        if (hasSearched) return@LaunchedEffect

        if (!initialGenreSlug.isNullOrBlank()) {
            viewModel.setGenreSlug(initialGenreSlug)
            viewModel.setGenreSort(initialOrder)
            hasSearched = true
            viewModel.filterVideos(1)
            return@LaunchedEffect
        }

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
                val path = category.genrePath
                if (HentaiMamaNetworkRepo.isGenrePath(path)) {
                    val slug = HentaiMamaNetworkRepo.extractGenreSlug(path)
                    viewModel.setGenreSlug(slug)
                    viewModel.setGenreSort(category.sort)
                    hasSearched = true
                    viewModel.filterVideos(1)
                    return@LaunchedEffect
                }
                val inferredGenre = extractGenreFromPath(path)
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
            initialGenreSlug = genreSlug,
            onDismiss = { showFilterSheet = false },
            onApply = { order, genre, year, producer, slug ->
                viewModel.setOrder(order)
                viewModel.setGenre(genre)
                viewModel.setYear(year)
                viewModel.setProducer(producer)
                viewModel.setGenreSlug(slug)
                showFilterSheet = false
                if (!slug.isNullOrBlank()) {
                    viewModel.setGenreSort(order)
                    runSearch()
                } else if (searchQuery.isBlank()) {
                    runSearch()
                }
            },
            onReset = {
                viewModel.clearFilters()
                viewModel.setGenreSlug(null)
                viewModel.setGenreSort(null)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isGenreMode) {
                                genreHeader?.genreName ?: "Genre"
                            } else {
                                "Search HentaiMama"
                            },
                            maxLines = 1,
                        )
                        if (totalPages > 1) {
                            Text(
                                text = "Page $currentPage / $totalPages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
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
                    if (newValue.isBlank() && !isFilterMode && !isGenreMode) {
                        viewModel.clearSearch()
                        hasSearched = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = {
                    Text(
                        if (isGenreMode) "Search in ${genreHeader?.genreName ?: genreSlug}"
                        else "Search videos…"
                    )
                },
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

            if (isGenreMode) {
                HentaiMamaGenreSortBar(
                    header = genreHeader,
                    sortOptions = genreHeader?.sortOptions.orEmpty(),
                    selectedSort = genreSort,
                    layout = genreLayout,
                    onSortSelected = { sort ->
                        viewModel.setGenreSort(sort)
                        coroutineScope.launch { gridState.scrollToItem(0) }
                    },
                    onLayoutToggled = { newLayout: GenreLayout ->
                        viewModel.setGenreLayout(newLayout)
                    },
                )
            } else {
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
                                label = {
                                    Text(
                                        label,
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
                                    viewModel.clearFilters()
                                    runSearch()
                                },
                                label = { Text("Clear all") },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (searchSeries.isEmpty() && searchResults.isEmpty() && hasSearched) {
                        LoadingContent()
                    } else if (searchSeries.isEmpty() && searchResults.isEmpty() && !hasSearched) {
                        SearchPlaceholder()
                    } else {
                        DisplayResults(
                            allVideos = searchResults,
                            allSeries = searchSeries,
                            allCards = searchCards,
                            isGenreMode = isGenreMode,
                            layout = genreLayout,
                            isLoadingMore = isLoadingMore,
                            gridState = gridState,
                            onNavigateToVideo = onNavigateToVideo,
                        )
                    }
                }

                is PageLoadingState.Success,
                is PageLoadingState.NoMoreData -> {
                    if (searchSeries.isEmpty() && searchResults.isEmpty() && searchCards.isEmpty() && hasSearched) {
                        EmptyResults()
                    } else if (searchSeries.isEmpty() && searchResults.isEmpty() && searchCards.isEmpty()) {
                        SearchPlaceholder()
                    } else {
                        DisplayResults(
                            allVideos = searchResults,
                            allSeries = searchSeries,
                            allCards = searchCards,
                            isGenreMode = isGenreMode,
                            layout = genreLayout,
                            isLoadingMore = isLoadingMore,
                            gridState = gridState,
                            onNavigateToVideo = onNavigateToVideo,
                        )
                    }
                }

                is PageLoadingState.Error -> {
                    if (searchSeries.isEmpty() && searchResults.isEmpty() && searchCards.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { runSearch() },
                        )
                    } else {
                        Column {
                            DisplayResults(
                                allVideos = searchResults,
                                allSeries = searchSeries,
                                allCards = searchCards,
                                isGenreMode = isGenreMode,
                                layout = genreLayout,
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
    allSeries: List<GenreSeries>,
    allCards: List<SeriesCard>,
    isGenreMode: Boolean,
    layout: GenreLayout,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String) -> Unit,
) {
    val uniqueVideos = remember(allVideos) { allVideos.distinctBy { it.videoCode } }
    val uniqueSeries = remember(allSeries) { allSeries.distinctBy { it.slug } }
    val uniqueCards = remember(allCards) { allCards.distinctBy { it.slug } }

    val useCards: Boolean = uniqueCards.isNotEmpty()
    val useSeries: Boolean = !useCards && isGenreMode && uniqueSeries.isNotEmpty()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        val gridWidth = maxWidth
        val baseColumns = (gridWidth.value / BASE_GRID_MIN_SIZE_DP).toInt().coerceAtLeast(2)
        val cardWidth = rememberSearchCardWidth(gridWidth, baseColumns)

        if (useSeries && layout == GenreLayout.BARE) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = uniqueSeries.size,
                    key = { i -> uniqueSeries[i].slug },
                ) { index ->
                    val series = uniqueSeries[index]
                    HentaiMamaGenreSeriesRow(
                        series = series,
                        onClick = { onNavigateToVideo(series.slug) },
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
            return@BoxWithConstraints
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cardWidth.coerceAtLeast(80.dp)),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (useCards) {
                items(
                    count = uniqueCards.size,
                    key = { i -> "card_${uniqueCards[i].slug}" },
                ) { index ->
                    val card = uniqueCards[index]
                    HentaiMamaSeriesCardView(
                        card = card,
                        onClick = { onNavigateToVideo(card.slug) },
                    )
                }
            } else if (useSeries) {
                items(
                    count = uniqueSeries.size,
                    key = { i -> "series_${uniqueSeries[i].slug}" },
                ) { index ->
                    val series = uniqueSeries[index]
                    HentaiMamaGenreSeriesCard(
                        series = series,
                        onClick = { onNavigateToVideo(series.slug) },
                    )
                }
            } else {
                items(
                    count = uniqueVideos.size,
                    key = { i -> "video_${uniqueVideos[i].videoCode}" },
                ) { index ->
                    val video = uniqueVideos[index]
                    HentaiMamaVideoCard(
                        videoItem = video,
                        onClick = { onNavigateToVideo(video.videoCode) },
                        onLongClick = { _, _ -> },
                    )
                }
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
