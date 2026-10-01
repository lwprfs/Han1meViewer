package com.yenaly.han1meviewer.HentaiMama.ui.components
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaOptions
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaOrder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private enum class FilterTab(val title: String) {
    ORDER("Order"),
    GENRE("Genre"),
    YEAR("Year"),
    PRODUCER("Producer"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaFilterSheet(
    initialOrder: String?,
    initialGenre: String?,
    initialYear: String?,
    initialProducer: String?,
    onDismiss: () -> Unit,
    onApply: (order: String?, genre: String?, year: String?, producer: String?) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    var selectedTab by remember {
        mutableIntStateOf(
            when {
                initialOrder != null -> FilterTab.ORDER.ordinal
                initialGenre != null -> FilterTab.GENRE.ordinal
                initialYear != null -> FilterTab.YEAR.ordinal
                initialProducer != null -> FilterTab.PRODUCER.ordinal
                else -> FilterTab.ORDER.ordinal
            }
        )
    }

    var order by remember { mutableStateOf(initialOrder) }
    var genre by remember { mutableStateOf(initialGenre) }
    var year by remember { mutableStateOf(initialYear) }
    var producer by remember { mutableStateOf(initialProducer) }

    var genreQuery by remember { mutableStateOf("") }
    var producerQuery by remember { mutableStateOf("") }

    val allOrders = remember { HentaiMamaOptions.orders }
    val allGenres = remember { HentaiMamaOptions.genres }
    val allYears = remember { HentaiMamaOptions.years }
    val allProducers = remember { HentaiMamaOptions.producers }

    val filteredGenres = remember(genreQuery, allGenres) {
        if (genreQuery.isBlank()) allGenres
        else allGenres.filter { it.contains(genreQuery, ignoreCase = true) }
    }
    val filteredProducers = remember(producerQuery, allProducers) {
        if (producerQuery.isBlank()) allProducers
        else allProducers.filter { it.contains(producerQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sort & Filter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    order = null
                    genre = null
                    year = null
                    producer = null
                    genreQuery = ""
                    producerQuery = ""
                    onReset()
                }) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(8.dp))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                FilterTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val active = when (tab) {
                                FilterTab.ORDER -> order != null
                                FilterTab.GENRE -> genre != null
                                FilterTab.YEAR -> year != null
                                FilterTab.PRODUCER -> producer != null
                            }
                            Text(if (active) "${tab.title} •" else tab.title)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when (FilterTab.entries[selectedTab]) {
                FilterTab.ORDER -> OrderTab(
                    orders = allOrders,
                    selected = order,
                    onSelect = { order = if (order == it) null else it },
                )

                FilterTab.GENRE -> SearchableSingleSelectTab(
                    query = genreQuery,
                    onQueryChange = { genreQuery = it },
                    placeholder = "Search genres…",
                    items = filteredGenres,
                    selected = genre,
                    onSelect = { genre = if (genre == it) null else it },
                )

                FilterTab.YEAR -> YearTab(
                    years = allYears,
                    selected = year,
                    onSelect = { year = if (year == it) null else it },
                )

                FilterTab.PRODUCER -> SearchableSingleSelectTab(
                    query = producerQuery,
                    onQueryChange = { producerQuery = it },
                    placeholder = "Search producers…",
                    items = filteredProducers,
                    selected = producer,
                    onSelect = { producer = if (producer == it) null else it },
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApply(order, genre, year, producer) },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OrderTab(
    orders: List<HentaiMamaOrder>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(orders, key = { it.value }) { order ->
            SingleSelectChip(
                label = order.name,
                selected = order.value == selected,
                onClick = { onSelect(order.value) },
            )
        }
    }
}

@Composable
private fun YearTab(
    years: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(years, key = { it }) { y ->
            SingleSelectChip(
                label = y,
                selected = y == selected,
                onClick = { onSelect(y) },
            )
        }
    }
}

@Composable
private fun SearchableSingleSelectTab(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    items: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it }) { item ->
                    SingleSelectChip(
                        label = item,
                        selected = item == selected,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleSelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
