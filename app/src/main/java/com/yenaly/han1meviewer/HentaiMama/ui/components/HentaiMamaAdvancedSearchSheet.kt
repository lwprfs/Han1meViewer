package com.yenaly.han1meviewer.HentaiMama.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaOptions
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaSearchHistoryEntry
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.util.applicationContext

private data class AdvancedOption(val key: String, val label: String)

private data class ActiveDialog(
    val title: String,
    val options: List<AdvancedOption>,
    val selectedKey: String?,
    val searchable: Boolean,
    val onSelect: (String?) -> Unit,
    val onReset: () -> Unit,
)

private data class AdvancedChipSpec(
    val title: String,
    val checked: Boolean,
    val onClick: () -> Unit,
    val onClear: () -> Unit,
)

private data class GenreHit(val group: String, val genre: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaAdvancedSearchSheet(
    initialQuery: String?,
    initialOrder: String?,
    initialGenre: String?,
    initialYear: String?,
    initialProducer: String?,
    initialGenreSlug: String?,
    history: List<HentaiMamaSearchHistoryEntry>,
    onHistoryClick: (HentaiMamaSearchHistoryEntry) -> Unit,
    onHistoryDelete: (HentaiMamaSearchHistoryEntry) -> Unit,
    onApply: (
        query: String?,
        order: String?,
        genre: String?,
        year: String?,
        producer: String?,
        genreSlug: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    var order by remember(initialOrder) { mutableStateOf(initialOrder) }
    var genre by remember(initialGenre) { mutableStateOf(initialGenre) }
    var year by remember(initialYear) { mutableStateOf(initialYear) }
    var producer by remember(initialProducer) { mutableStateOf(initialProducer) }
    var slug by remember(initialGenreSlug) { mutableStateOf(initialGenreSlug) }
    var activeDialog by remember { mutableStateOf<ActiveDialog?>(null) }

    var genrePickerVisible by remember { mutableStateOf(false) }
    var genreGroupOpen by remember { mutableStateOf<String?>(null) }

    val orderOptions = remember {
        HentaiMamaOptions.orders.map { AdvancedOption(it.value, it.name) }
    }
    val yearOptions = remember {
        HentaiMamaOptions.years.map { AdvancedOption(it, it) }
    }
    val producerOptions = remember {
        HentaiMamaOptions.producers.map { AdvancedOption(it, it) }
    }
    val slugOptions = remember {
        HentaiMamaOptions.genres
            .map { g ->
                val key = g.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                AdvancedOption(key, "$g  ·  $key")
            }
            .distinctBy { it.key }
    }

    activeDialog?.let { dialog ->
        AdvancedChoiceDialog(
            title = dialog.title,
            options = dialog.options,
            selectedKey = dialog.selectedKey,
            searchable = dialog.searchable,
            onSelect = { key ->
                dialog.onSelect(key)
                activeDialog = null
            },
            onReset = {
                dialog.onReset()
                activeDialog = null
            },
            onDismiss = { activeDialog = null },
        )
    }

    if (genrePickerVisible) {
        if (genreGroupOpen != null) {
            GenreGroupContentDialog(
                group = genreGroupOpen!!,
                selectedKey = genre,
                onSelect = { key ->
                    genre = key
                    genrePickerVisible = false
                    genreGroupOpen = null
                },
                onBack = { genreGroupOpen = null },
                onReset = {
                    genre = null
                    genrePickerVisible = false
                    genreGroupOpen = null
                },
                onDismiss = {
                    genrePickerVisible = false
                    genreGroupOpen = null
                },
            )
        } else {
            GenreGroupPickerDialog(
                selectedKey = genre,
                onPickGroup = { group -> genreGroupOpen = group },
                onSelectGenreDirectly = { key ->
                    genre = key
                    genrePickerVisible = false
                    genreGroupOpen = null
                },
                onReset = {
                    genre = null
                    genrePickerVisible = false
                    genreGroupOpen = null
                },
                onDismiss = {
                    genrePickerVisible = false
                    genreGroupOpen = null
                },
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.advanced_search),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                if (history.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.site_recent_combinations),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    items = history,
                                    key = { "${it.createdAt}-${it.query}-${it.genreSlug.orEmpty()}" },
                                ) { entry ->
                                    HistoryCard(
                                        entry = entry,
                                        onClick = { onHistoryClick(entry) },
                                        onDelete = { onHistoryDelete(entry) },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val chips = buildList {
                        add(
                            AdvancedChipSpec(
                                title = orderChipLabel(order),
                                checked = order != null,
                                onClick = {
                                    activeDialog = ActiveDialog(
                                        title = applicationContext.getString(R.string.hm_order),
                                        options = orderOptions,
                                        selectedKey = order,
                                        searchable = false,
                                        onSelect = { order = it },
                                        onReset = { order = null },
                                    )
                                },
                                onClear = { order = null },
                            )
                        )
                        add(
                            AdvancedChipSpec(
                                title = genreChipLabel(genre),
                                checked = genre != null,
                                onClick = {
                                    genreGroupOpen = null
                                    genrePickerVisible = true
                                },
                                onClear = { genre = null },
                            )
                        )
                        add(
                            AdvancedChipSpec(
                                title = yearChipLabel(year),
                                checked = year != null,
                                onClick = {
                                    activeDialog = ActiveDialog(
                                        title = applicationContext.getString(R.string.site_year),
                                        options = yearOptions,
                                        selectedKey = year,
                                        searchable = true,
                                        onSelect = { year = it },
                                        onReset = { year = null },
                                    )
                                },
                                onClear = { year = null },
                            )
                        )
                        add(
                            AdvancedChipSpec(
                                title = producerChipLabel(producer),
                                checked = producer != null,
                                onClick = {
                                    activeDialog = ActiveDialog(
                                        title = applicationContext.getString(R.string.hm_producer),
                                        options = producerOptions,
                                        selectedKey = producer,
                                        searchable = true,
                                        onSelect = { producer = it },
                                        onReset = { producer = null },
                                    )
                                },
                                onClear = { producer = null },
                            )
                        )
                        add(
                            AdvancedChipSpec(
                                title = slugChipLabel(slug),
                                checked = !slug.isNullOrBlank(),
                                onClick = {
                                    activeDialog = ActiveDialog(
                                        title = applicationContext.getString(R.string.hm_genre_page),
                                        options = slugOptions,
                                        selectedKey = slug,
                                        searchable = true,
                                        onSelect = { slug = it },
                                        onReset = { slug = null },
                                    )
                                },
                                onClear = { slug = null },
                            )
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        chips.forEach { spec ->
                            AdvancedSearchChip(
                                title = spec.title,
                                checked = spec.checked,
                                modifier = Modifier.weight(1f),
                                onLongClick = spec.onClear,
                                onClick = spec.onClick,
                            )
                        }
                        if (chips.size % 2 == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.hm_long_press_a_chip_to_clear),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledIconButton(
                            onClick = {
                                onApply(
                                    initialQuery?.trim().takeIf { !it.isNullOrEmpty() },
                                    order,
                                    genre,
                                    year,
                                    producer,
                                    slug?.takeIf { it.isNotBlank() },
                                )
                            },
                            modifier = Modifier.size(60.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreGroupPickerDialog(
    selectedKey: String?,
    onPickGroup: (String) -> Unit,
    onSelectGenreDirectly: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember { HentaiMamaOptions.genreGroups.keys.toList() }
    var query by remember { mutableStateOf("") }

    val allGenreHits = remember {
        HentaiMamaOptions.genreGroups.entries.flatMap { (group, genres) ->
            genres.map { GenreHit(group, it) }
        }
    }

    val filteredGenreHits = remember(query, allGenreHits) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) emptyList()
        else allGenreHits.filter { it.genre.contains(trimmed, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_genre)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50)),
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    placeholder = { Text(stringResource(R.string.site_search_genres)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.site_clear))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )

                val listState = rememberLazyListState()

                if (query.isBlank()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = groups,
                            key = { it },
                        ) { group ->
                            val groupSelected = selectedKey != null &&
                                    HentaiMamaOptions.genresInGroup(group).any { it == selectedKey }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPickGroup(group) },
                                color = if (groupSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 12.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (groupSelected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                            color = if (groupSelected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        Text(
                                            text = stringResource(R.string.site_genres, HentaiMamaOptions.genresInGroup(group).size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (filteredGenreHits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.site_no_genres_match, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = filteredGenreHits,
                            key = { "${it.group}::${it.genre}" },
                        ) { hit ->
                            val selected = hit.genre == selectedKey
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectGenreDirectly(hit.genre) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 12.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hit.genre,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (selected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        Text(
                                            text = hit.group,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun GenreGroupContentDialog(
    group: String,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(group) { HentaiMamaOptions.genresInGroup(group) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.site_back_to_groups),
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = group,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = options,
                    key = { it },
                ) { option ->
                    val selected = option == selectedKey
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option) },
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 12.dp,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdvancedSearchChip(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {

    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        color = containerColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AdvancedChoiceDialog(
    title: String,
    options: List<AdvancedOption>,
    selectedKey: String?,
    searchable: Boolean,
    onSelect: (String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(query, options) {
        if (!searchable || query.isBlank()) options
        else options.filter {
            it.label.contains(query, ignoreCase = true) ||
                    it.key.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searchable) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50)),
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        placeholder = { Text(stringResource(R.string.hm_search)) },
                    )
                }
                visible.forEach { option ->
                    val selected = option.key == selectedKey
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option.key) },
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 10.dp,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun HistoryCard(
    entry: HentaiMamaSearchHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val conditions = buildList {
        entry.order?.let { add(stringResource(R.string.hm_order_2, it)) }
        entry.genre?.let { add(stringResource(R.string.site_genre_2, it)) }
        entry.year?.let { add(stringResource(R.string.hm_year, it)) }
        entry.producer?.let { add(stringResource(R.string.hm_producer_2, it)) }
        entry.genreSlug?.let { add(stringResource(R.string.hm_genre_page_2, it)) }
    }.joinToString(" || ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (entry.query.isNotBlank()) {
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (conditions.isNotBlank()) {
                    Text(
                        text = conditions,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.remove),
                )
            }
        }
    }
}

private fun orderChipLabel(key: String?): String {
    if (key == null) return applicationContext.getString(R.string.hm_order)
    val name = HentaiMamaOptions.orders.firstOrNull { it.value == key }?.name ?: key
    return applicationContext.getString(R.string.hm_order_2, name)
}

private fun genreChipLabel(key: String?): String =
    if (key == null) applicationContext.getString(R.string.site_genre) else applicationContext.getString(R.string.site_genre_2, key)

private fun yearChipLabel(key: String?): String =
    if (key == null) applicationContext.getString(R.string.site_year) else applicationContext.getString(R.string.hm_year, key)

private fun producerChipLabel(key: String?): String =
    if (key == null) applicationContext.getString(R.string.hm_producer) else applicationContext.getString(R.string.hm_producer_2, key.take(24))

private fun slugChipLabel(key: String?): String =
    if (key.isNullOrBlank()) applicationContext.getString(R.string.hm_genre_page) else applicationContext.getString(R.string.hm_page, key)
