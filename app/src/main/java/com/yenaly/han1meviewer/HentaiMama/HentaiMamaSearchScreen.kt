package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var currentPage by remember { mutableIntStateOf(1) }
    var allVideos by remember { mutableStateOf<List<HanimeInfo>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedProducer by viewModel.selectedProducer.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedOrder by viewModel.selectedOrder.collectAsStateWithLifecycle()

    val genres = remember { HentaiMamaOptions.genres }
    val producers = remember { HentaiMamaOptions.producers }
    val years = remember { HentaiMamaOptions.years }
    val orders = remember { HentaiMamaOptions.orders }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    fun doSearch(resetPage: Boolean = true) {
        if (resetPage) {
            currentPage = 1
            allVideos = emptyList()
            hasMorePages = true
            isLoading = true
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

    fun applyFilters() {
        currentPage = 1
        allVideos = emptyList()
        hasMorePages = true
        hasSearched = true
        isLoadingMore = true
        isLoading = true
        coroutineScope.launch { gridState.scrollToItem(0) }
        viewModel.filterVideos(currentPage)
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrEmpty() && !hasSearched) {
            searchQuery = initialQuery
            doSearch()
        }
    }

    LaunchedEffect(searchState) {
        isLoading = false
        when (val state = searchState) {
            is PageLoadingState.Success -> {
                val newVideos = state.info
                allVideos = if (currentPage == 1) newVideos else allVideos + newVideos
                isLoadingMore = false
                if (newVideos.isEmpty()) hasMorePages = false
            }
            is PageLoadingState.Error -> isLoadingMore = false
            is PageLoadingState.NoMoreData -> {
                isLoadingMore = false
                hasMorePages = false
            }
            is PageLoadingState.Loading -> {}
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to lastVisible
        }
            .distinctUntilChanged()
            .filter { (total, _) -> total > 4 && hasMorePages && hasSearched && !isLoadingMore }
            .collect { (total, lastVisible) ->
                if (lastVisible >= total - 4) {
                    isLoadingMore = true
                    currentPage++
                    if (searchQuery.isNotBlank()) {
                        viewModel.searchVideos(currentPage, searchQuery)
                    } else {
                        viewModel.filterVideos(currentPage)
                    }
                }
            }
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
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (showFilters || selectedGenre != null ||
                                selectedProducer != null || selectedOrder != null ||
                                selectedYear != null
                            ) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search videos...") },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(onClick = { doSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() })
            )

            if (showFilters) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterSection(title = "Order By") {
                            FilterChip(
                                selected = selectedOrder == null,
                                onClick = { viewModel.setOrder(null); applyFilters() },
                                label = { Text("Default") }
                            )
                            orders.forEach { order ->
                                FilterChip(
                                    selected = selectedOrder == order.value,
                                    onClick = {
                                        viewModel.setOrder(
                                            if (selectedOrder == order.value) null else order.value
                                        )
                                        applyFilters()
                                    },
                                    label = { Text(order.name) }
                                )
                            }
                        }

                        HorizontalDivider()

                        FilterSection(title = "Genre") {
                            FilterChip(
                                selected = selectedGenre == null,
                                onClick = { viewModel.setGenre(null); applyFilters() },
                                label = { Text("All") }
                            )
                            genres.forEach { genre ->
                                FilterChip(
                                    selected = selectedGenre == genre,
                                    onClick = {
                                        viewModel.setGenre(if (selectedGenre == genre) null else genre)
                                        applyFilters()
                                    },
                                    label = { Text(genre) }
                                )
                            }
                        }

                        HorizontalDivider()

                        FilterSection(title = "Year") {
                            FilterChip(
                                selected = selectedYear == null,
                                onClick = { viewModel.setYear(null); applyFilters() },
                                label = { Text("All") }
                            )
                            years.forEach { year ->
                                FilterChip(
                                    selected = selectedYear == year,
                                    onClick = {
                                        viewModel.setYear(if (selectedYear == year) null else year)
                                        applyFilters()
                                    },
                                    label = { Text(year) }
                                )
                            }
                        }

                        HorizontalDivider()

                        FilterSection(title = "Producer") {
                            FilterChip(
                                selected = selectedProducer == null,
                                onClick = { viewModel.setProducer(null); applyFilters() },
                                label = { Text("All") }
                            )
                            producers.forEach { producer ->
                                FilterChip(
                                    selected = selectedProducer == producer,
                                    onClick = {
                                        viewModel.setProducer(
                                            if (selectedProducer == producer) null else producer
                                        )
                                        applyFilters()
                                    },
                                    label = { Text(producer.take(20)) }
                                )
                            }
                        }

                        if (selectedGenre != null || selectedProducer != null ||
                            selectedOrder != null || selectedYear != null
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.clearFilters()
                                    applyFilters()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear All Filters")
                            }
                        }
                    }
                }
            } else if (selectedGenre != null || selectedProducer != null ||
                selectedOrder != null || selectedYear != null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedOrder?.let { key ->
                        val label = orders.find { it.value == key }?.name ?: key
                        ActiveFilterChip(label = "Order: $label") {
                            viewModel.setOrder(null); applyFilters()
                        }
                    }
                    selectedGenre?.let { key ->
                        ActiveFilterChip(label = "Genre: $key") {
                            viewModel.setGenre(null); applyFilters()
                        }
                    }
                    selectedYear?.let { key ->
                        ActiveFilterChip(label = "Year: $key") {
                            viewModel.setYear(null); applyFilters()
                        }
                    }
                    selectedProducer?.let { key ->
                        ActiveFilterChip(label = "Producer: ${key.take(15)}") {
                            viewModel.setProducer(null); applyFilters()
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (allVideos.isEmpty()) LoadingContent()
                    else DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                }
                is PageLoadingState.Success, is PageLoadingState.NoMoreData -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                    }
                }
                is PageLoadingState.Error -> {
                    if (allVideos.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { doSearch() }
                        )
                    } else {
                        Column {
                            DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                            Text(
                                text = "Failed to load more: ${state.throwable.message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Default.Clear,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun DisplayResults(
    allVideos: List<HanimeInfo>,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allVideos, key = { it.videoCode }) { video ->
            VideoCardItem(
                videoItem = video,
                onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                onLongClickVideosItem = { _, _ -> }
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
