package com.yenaly.han1meviewer.OnAir.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.data.local.OnAirSearchHistoryEntry
import com.yenaly.han1meviewer.OnAir.data.local.OnAirSearchHistoryRepo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirOptions
import com.yenaly.han1meviewer.OnAir.data.model.OnAirSelectOption
import com.yenaly.han1meviewer.OnAir.ui.components.OnAirVideoCardItem
import com.yenaly.han1meviewer.OnAir.ui.home.InfiniteScrollFooter
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirSearchScreen(
    viewModel: OnAirSearchViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onLoadFailure: (String) -> Unit = {},
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val field by viewModel.field.collectAsStateWithLifecycle()
    val length by viewModel.length.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val pageInfo by viewModel.pageInfo.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val columns = rememberVideoGridColumns()

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var history by remember { mutableStateOf(OnAirSearchHistoryRepo.load()) }

    fun runSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch { gridState.scrollToItem(0) }
        viewModel.performSearch()
        history = OnAirSearchHistoryRepo.load()
    }

    LaunchedEffect(gridState, hasSearched, isLoadingMore, pageInfo) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (hasSearched &&
                    total > 4 &&
                    last >= total - 4 &&
                    !isLoadingMore &&
                    pageInfo != null &&
                    currentPage < pageInfo!!.total
                ) {
                    viewModel.loadNextPage()
                }
            }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is PageLoadingState.Error && results.isEmpty()) {
            onLoadFailure(s.throwable.message ?: "Search failed")
        }
    }

    if (showFilterSheet) {
        SearchFilterSheet(
            field = field,
            length = length,
            date = date,
            sort = sort,
            onFieldChange = viewModel::setField,
            onLengthChange = viewModel::setLength,
            onDateChange = viewModel::setDate,
            onSortChange = viewModel::setSort,
            onApply = {
                showFilterSheet = false
                runSearch()
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showHistorySheet) {
        SearchHistorySheet(
            history = history,
            onPick = { entry ->
                viewModel.applyFiltersFromHistory(entry)
                showHistorySheet = false
                runSearch()
            },
            onDelete = { entry ->
                OnAirSearchHistoryRepo.remove(entry)
                history = OnAirSearchHistoryRepo.load()
            },
            onClear = {
                OnAirSearchHistoryRepo.clear()
                history = emptyList()
            },
            onDismiss = { showHistorySheet = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SearchTopBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onBack = onBack,
            onClear = { viewModel.reset() },
            onSearch = { runSearch() },
            onOpenFilters = { showFilterSheet = true },
            onOpenHistory = { showHistorySheet = true },
            focusRequester = focusRequester,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val filterChips = buildList {
            if (tagFilter != null) {
                add("Tag: $tagFilter" to {
                    viewModel.setTagFilter(null)
                    runSearch()
                })
            }
            if (field != OnAirConstants.SEARCH_FIELD_TITLE) {
                val label = OnAirOptions.searchFields.firstOrNull { it.value == field }?.label ?: field
                add("Field: $label" to {
                    viewModel.setField(OnAirConstants.SEARCH_FIELD_TITLE)
                    runSearch()
                })
            }
            if (length != OnAirConstants.LENGTH_ANY) {
                val label = OnAirOptions.lengths.firstOrNull { it.value == length }?.label ?: length
                add("Length: $label" to {
                    viewModel.setLength(OnAirConstants.LENGTH_ANY)
                    runSearch()
                })
            }
            if (date != OnAirConstants.DATE_ANY) {
                val label = OnAirOptions.dates.firstOrNull { it.value == date }?.label ?: date
                add("Date: $label" to {
                    viewModel.setDate(OnAirConstants.DATE_ANY)
                    runSearch()
                })
            }
            if (sort != OnAirConstants.SORT_NEWEST) {
                val label = OnAirOptions.sorts.firstOrNull { it.value == sort }?.label ?: sort
                add("Sort: $label" to {
                    viewModel.setSort(OnAirConstants.SORT_NEWEST)
                    runSearch()
                })
            }
        }

        if (filterChips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                filterChips.forEach { (label, onClick) ->
                    AssistChip(
                        onClick = onClick,
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
                if (filterChips.size > 1) {
                    AssistChip(
                        onClick = {
                            viewModel.reset()
                            history = OnAirSearchHistoryRepo.load()
                        },
                        label = { Text("Clear all") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        }

        when {
            !hasSearched -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Type to search, or tap Filters.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }

            state == null || (state is PageLoadingState.Loading && results.isEmpty()) -> {
                LoadingContent()
            }

            state is PageLoadingState.Error && results.isEmpty() -> {
                ErrorContent(
                    message = (state as PageLoadingState.Error).throwable.message ?: "Failed",
                    onRetry = { runSearch() },
                )
            }

            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(SpacingNormal),
                    horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                    verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                    enableItemAnimation = false,
                ) {
                    items(results, key = { it.videoId }) { card ->
                        OnAirVideoCardItem(
                            card = card,
                            onClick = { onOpenVideo(card.videoId) },
                        )
                    }
                    if (results.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            InfiniteScrollFooter(isLoadingMore = isLoadingMore)
                        }
                    }
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
    onOpenFilters: () -> Unit,
    onOpenHistory: () -> Unit,
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
                                    text = "Search OnAir…",
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
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onOpenFilters) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterSheet(
    field: String,
    length: String,
    date: String,
    sort: String,
    onFieldChange: (String) -> Unit,
    onLengthChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onFieldChange(OnAirConstants.SEARCH_FIELD_TITLE)
                    onLengthChange(OnAirConstants.LENGTH_ANY)
                    onDateChange(OnAirConstants.DATE_ANY)
                    onSortChange(OnAirConstants.SORT_NEWEST)
                }) { Text("Reset") }
            }

            FilterSection(
                title = "Field",
                options = OnAirOptions.searchFields,
                selected = field,
                onSelect = onFieldChange,
            )
            FilterSection(
                title = "Length",
                options = OnAirOptions.lengths,
                selected = length,
                onSelect = onLengthChange,
            )
            FilterSection(
                title = "Date",
                options = OnAirOptions.dates,
                selected = date,
                onSelect = onDateChange,
            )
            FilterSection(
                title = "Sort",
                options = OnAirOptions.sorts,
                selected = sort,
                onSelect = onSortChange,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                TextButton(onClick = onApply, modifier = Modifier.weight(2f)) {
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    options: List<OnAirSelectOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHistorySheet(
    history: List<OnAirSearchHistoryEntry>,
    onPick: (OnAirSearchHistoryEntry) -> Unit,
    onDelete: (OnAirSearchHistoryEntry) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear, enabled = history.isNotEmpty()) {
                    Text("Clear")
                }
            }

            if (history.isEmpty()) {
                Text(
                    text = "No recent searches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                history.forEach { entry ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(entry) },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.query.ifBlank { entry.tag ?: "Filters" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = buildList {
                                    if (entry.field != OnAirConstants.SEARCH_FIELD_TITLE) add("field=${entry.field}")
                                    if (entry.length != OnAirConstants.LENGTH_ANY) add("len=${entry.length}")
                                    if (entry.date != OnAirConstants.DATE_ANY) add("date=${entry.date}")
                                    if (entry.sort != OnAirConstants.SORT_NEWEST) add("sort=${entry.sort}")
                                    if (entry.tag != null) add("tag=${entry.tag}")
                                }.joinToString(" · ")
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove",
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
