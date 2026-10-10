package com.yenaly.han1meviewer.HentaiMama.ui.search

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaSearchHistoryEntry
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaSearchHistoryRepo
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetworkRepo
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaAdvancedSearchSheet
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSeriesCard
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSeriesRow
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSortBar
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaSeriesCardView
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaVideoCard
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeCategoryRepo
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaViewModel
import com.yenaly.han1meviewer.logic.model.HanimeInfo
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
    availableWidthDp: androidx.compose.ui.unit.Dp,
    columns: Int,
): androidx.compose.ui.unit.Dp {
    val cardMultiplier by HentaiMamaCardSettings.cardMultiplierState
    val widthMultiplier by HentaiMamaCardSettings.widthMultiplierState
    val effectiveCount = (columns * cardMultiplier).coerceAtLeast(1f)
    val availableForCards =
        availableWidthDp - SpacingLarge * 2 - SpacingNormal * (effectiveCount - 1)
    return (availableForCards / effectiveCount) * widthMultiplier
}

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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var history by remember { mutableStateOf(HentaiMamaSearchHistoryRepo.load()) }
    var isRefreshing by remember { mutableStateOf(false) }

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchSeries by viewModel.searchSeriesResults.collectAsStateWithLifecycle()
    val searchCards by viewModel.searchSeriesCards.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedProducer by viewModel.selectedProducer.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedOrder by viewModel.selectedOrder.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.searchIsLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val genreSlug by viewModel.genreSlug.collectAsStateWithLifecycle()
    val genreHeader by viewModel.genreHeader.collectAsStateWithLifecycle()
    val genreSort by viewModel.genreSort.collectAsStateWithLifecycle()
    val genreLayout by viewModel.genreLayout.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val refreshState = rememberPullToRefreshState()
    val isGenreMode = !genreSlug.isNullOrBlank()

    fun persistHistory() {
        HentaiMamaSearchHistoryRepo.push(
            HentaiMamaSearchHistoryEntry(
                query = query.trim(),
                genre = viewModel.selectedGenre.value,
                year = viewModel.selectedYear.value,
                producer = viewModel.selectedProducer.value,
                order = viewModel.selectedOrder.value,
                genreSlug = viewModel.genreSlug.value,
            )
        )
        history = HentaiMamaSearchHistoryRepo.load()
    }

    fun runSearch() {
        hasSearched = true
        focusManager.clearFocus()
        keyboard?.hide()
        coroutineScope.launch { gridState.scrollToItem(0) }
        if (query.isNotBlank()) viewModel.searchVideos(1, query)
        else viewModel.filterVideos(1)
        persistHistory()
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
            query = initialQuery
            hasSearched = true
            focusManager.clearFocus()
            keyboard?.hide()
            viewModel.searchVideos(1, initialQuery)
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
                viewModel.setGenre(extractGenreFromPath(path))
                viewModel.setOrder(category.sort)
                viewModel.setProducer(null)
                viewModel.setYear(null)
                hasSearched = true
                viewModel.filterVideos(1)
                return@LaunchedEffect
            }
        }

        if (!initialGenre.isNullOrBlank() || !initialOrder.isNullOrBlank()) {
            viewModel.setGenre(initialGenre)
            viewModel.setOrder(initialOrder)
            hasSearched = true
            viewModel.filterVideos(1)
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
            .filter { (total, last) -> last >= total - 4 }
            .collect { viewModel.loadNextSearchPage() }
    }

    LaunchedEffect(searchState) {
        if (searchState !is PageLoadingState.Loading) isRefreshing = false
    }

    val activeFilters = buildList {
        selectedOrder?.let { add("Order: $it" to { viewModel.setOrder(null); runSearch() }) }
        selectedGenre?.let { add("Genre: $it" to { viewModel.setGenre(null); runSearch() }) }
        selectedYear?.let { add("Year: $it" to { viewModel.setYear(null); runSearch() }) }
        selectedProducer?.let {
            add("Producer: ${it.take(24)}" to { viewModel.setProducer(null); runSearch() })
        }
    }

    if (showAdvanced) {
        HentaiMamaAdvancedSearchSheet(
            initialQuery = query,
            initialOrder = selectedOrder,
            initialGenre = selectedGenre,
            initialYear = selectedYear,
            initialProducer = selectedProducer,
            initialGenreSlug = genreSlug,
            history = history,
            onHistoryClick = { entry ->
                query = entry.query
                viewModel.setOrder(entry.order)
                viewModel.setGenre(entry.genre)
                viewModel.setYear(entry.year)
                viewModel.setProducer(entry.producer)
                viewModel.setGenreSlug(entry.genreSlug)
                if (!entry.genreSlug.isNullOrBlank()) {
                    viewModel.setGenreSort(entry.order)
                }
                showAdvanced = false
                hasSearched = true
                focusManager.clearFocus()
                keyboard?.hide()
                coroutineScope.launch { gridState.scrollToItem(0) }
                if (entry.query.isNotBlank()) viewModel.searchVideos(1, entry.query)
                else viewModel.filterVideos(1)
                persistHistory()
            },
            onHistoryDelete = { entry ->
                HentaiMamaSearchHistoryRepo.remove(entry)
                history = HentaiMamaSearchHistoryRepo.load()
            },
            onApply = { appliedQuery, order, genre, year, producer, slug ->
                query = appliedQuery.orEmpty()
                viewModel.setOrder(order)
                viewModel.setGenre(genre)
                viewModel.setYear(year)
                viewModel.setProducer(producer)
                viewModel.setGenreSlug(slug)
                if (!slug.isNullOrBlank()) {
                    viewModel.setGenreSort(order)
                }
                showAdvanced = false
                runSearch()
            },
            onDismiss = { showAdvanced = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SearchTopBar(
            query = query,
            onQueryChange = { query = it },
            onBack = onBack,
            onClear = {
                query = ""
                hasSearched = false
                viewModel.clearSearch()
            },
            onSearch = { runSearch() },
            onOpenAdvanced = { showAdvanced = true },
            focusRequester = focusRequester,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AnimatedVisibility(
            visible = !isGenreMode && activeFilters.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activeFilters.forEach { (label, onClick) ->
                    AssistChip(
                        onClick = onClick,
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
                if (activeFilters.size > 1) {
                    AssistChip(
                        onClick = {
                            viewModel.clearFilters()
                            runSearch()
                        },
                        label = { Text("Reset") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        }

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
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch { gridState.scrollToItem(0) }
                if (query.isNotBlank()) viewModel.searchVideos(1, query)
                else viewModel.filterVideos(1)
            },
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    hasSearched -> SearchResultsArea(
                        state = searchState,
                        videos = searchResults,
                        series = searchSeries,
                        cards = searchCards,
                        isGenreMode = isGenreMode,
                        layout = genreLayout,
                        isLoadingMore = isLoadingMore,
                        gridState = gridState,
                        onNavigateToVideo = onNavigateToVideo,
                        onRetry = { runSearch() },
                    )

                    query.isBlank() && history.isNotEmpty() -> RecentHistoryList(
                        entries = history,
                        onEntryClick = { entry ->
                            query = entry.query
                            viewModel.setOrder(entry.order)
                            viewModel.setGenre(entry.genre)
                            viewModel.setYear(entry.year)
                            viewModel.setProducer(entry.producer)
                            viewModel.setGenreSlug(entry.genreSlug)
                            if (!entry.genreSlug.isNullOrBlank()) {
                                viewModel.setGenreSort(entry.order)
                            }
                            hasSearched = true
                            focusManager.clearFocus()
                            keyboard?.hide()
                            coroutineScope.launch { gridState.scrollToItem(0) }
                            if (entry.query.isNotBlank()) viewModel.searchVideos(1, entry.query)
                            else viewModel.filterVideos(1)
                            persistHistory()
                        },
                        onEntryDelete = { entry ->
                            HentaiMamaSearchHistoryRepo.remove(entry)
                            history = HentaiMamaSearchHistoryRepo.load()
                        },
                    )

                    else -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Type to search, or tap Advanced for filters.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }

                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onOpenAdvanced: () -> Unit,
    focusRequester: FocusRequester,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .focusRequester(focusRequester),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search videos…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenAdvanced() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RecentHistoryList(
    entries: List<HentaiMamaSearchHistoryEntry>,
    onEntryClick: (HentaiMamaSearchHistoryEntry) -> Unit,
    onEntryDelete: (HentaiMamaSearchHistoryEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEntryClick(entry) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.query.ifBlank {
                            entry.genreSlug?.let { "Page: $it" }
                                ?: entry.genre?.let { "Genre: $it" }
                                ?: "Filter"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val sub = buildList {
                        entry.order?.let { add("Order: $it") }
                        entry.genre?.let { add("Genre: $it") }
                        entry.year?.let { add("Year: $it") }
                        entry.producer?.let { add("Producer: $it") }
                        entry.genreSlug?.let { add("Page: $it") }
                    }.joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { onEntryDelete(entry) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsArea(
    state: PageLoadingState<*>?,
    videos: List<HanimeInfo>,
    series: List<GenreSeries>,
    cards: List<SeriesCard>,
    isGenreMode: Boolean,
    layout: GenreLayout,
    isLoadingMore: Boolean,
    gridState: LazyGridState,
    onNavigateToVideo: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val empty = videos.isEmpty() && series.isEmpty() && cards.isEmpty()
    when {
        state is PageLoadingState.Loading && empty -> LoadingContent()
        state is PageLoadingState.Error && empty -> ErrorContent(
            message = state.throwable.message ?: "Failed to load results",
            onRetry = onRetry,
        )
        empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No results found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> DisplayResults(
            allVideos = videos,
            allSeries = series,
            allCards = cards,
            isGenreMode = isGenreMode,
            layout = layout,
            isLoadingMore = isLoadingMore,
            gridState = gridState,
            onNavigateToVideo = onNavigateToVideo,
        )
    }
}

@Composable
private fun DisplayResults(
    allVideos: List<HanimeInfo>,
    allSeries: List<GenreSeries>,
    allCards: List<SeriesCard>,
    isGenreMode: Boolean,
    layout: GenreLayout,
    isLoadingMore: Boolean,
    gridState: LazyGridState,
    onNavigateToVideo: (String) -> Unit,
) {
    val uniqueVideos = remember(allVideos) { allVideos.distinctBy { it.videoCode } }
    val uniqueSeries = remember(allSeries) { allSeries.distinctBy { it.slug } }
    val uniqueCards = remember(allCards) { allCards.distinctBy { it.slug } }

    val useCards = uniqueCards.isNotEmpty()
    val useSeries = !useCards && isGenreMode && uniqueSeries.isNotEmpty()

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

private fun extractGenreFromPath(path: String): String? {
    if (path.isBlank()) return null
    val fullUrl = if (path.startsWith("http")) path else "https://placeholder.local/$path"
    return runCatching {
        Uri.parse(fullUrl).getQueryParameter("genres_filter[]")
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
