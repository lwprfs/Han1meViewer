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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaOptions
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaSearchHistoryEntry
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn

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

    val orderOptions = remember {
        HentaiMamaOptions.orders.map { AdvancedOption(it.value, it.name) }
    }
    val genreOptions = remember {
        HentaiMamaOptions.genres.map { AdvancedOption(it, it) }
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
                        text = "Advanced Search",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                if (history.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Recent Combinations",
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
                                        title = "Order",
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
                                    activeDialog = ActiveDialog(
                                        title = "Genre",
                                        options = genreOptions,
                                        selectedKey = genre,
                                        searchable = true,
                                        onSelect = { genre = it },
                                        onReset = { genre = null },
                                    )
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
                                        title = "Year",
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
                                        title = "Producer",
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
                                        title = "Genre Page",
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
                            text = "Long-press a chip to clear that filter.",
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
                                contentDescription = "Search",
                            )
                        }
                    }
                }
            }
        }
    }
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
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .heightIn(min = 52.dp),
        color = containerColor,
        tonalElevation = if (checked) 2.dp else 0.dp,
        shadowElevation = if (checked) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search…") },
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
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
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
        entry.order?.let { add("Order: $it") }
        entry.genre?.let { add("Genre: $it") }
        entry.year?.let { add("Year: $it") }
        entry.producer?.let { add("Producer: $it") }
        entry.genreSlug?.let { add("Genre Page: $it") }
    }.joinToString(" || ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
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
                    contentDescription = "Remove",
                )
            }
        }
    }
}

private fun orderChipLabel(key: String?): String {
    if (key == null) return "Order"
    val name = HentaiMamaOptions.orders.firstOrNull { it.value == key }?.name ?: key
    return "Order: $name"
}

private fun genreChipLabel(key: String?): String =
    if (key == null) "Genre" else "Genre: $key"

private fun yearChipLabel(key: String?): String =
    if (key == null) "Year" else "Year: $key"

private fun producerChipLabel(key: String?): String =
    if (key == null) "Producer" else "Producer: ${key.take(24)}"

private fun slugChipLabel(key: String?): String =
    if (key.isNullOrBlank()) "Genre Page" else "Page: $key"
