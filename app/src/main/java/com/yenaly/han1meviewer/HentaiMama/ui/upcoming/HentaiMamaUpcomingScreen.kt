package com.yenaly.han1meviewer.HentaiMama.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaImageLoader
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingCard
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingMonthOption
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DISPLAY_DATE_FORMATTER =
    DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaUpcomingScreen(
    onBack: () -> Unit,
    onOpenEpisode: (slug: String, url: String) -> Unit,
    onOpenSeries: (slug: String) -> Unit,
    onOpenStudio: (slug: String) -> Unit,
    onNavigateToCardSettings: () -> Unit = {},
    viewModel: HentaiMamaUpcomingViewModel = viewModel(),
) {
    var settingsReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HentaiMamaCardSettings.load()
        settingsReady = true
    }

    if (!settingsReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Upcoming",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                        Text(
                            text = uiState.selectedMonth.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMonthPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = "Pick month",
                        )
                    }
                    IconButton(onClick = onNavigateToCardSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Card settings",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && uiState.cards.isEmpty() -> {
                    LoadingContent(message = "Loading ${uiState.selectedMonth.displayName}…")
                }

                uiState.error != null && uiState.cards.isEmpty() -> {
                    ErrorContent(
                        title = "Failed to load upcoming",
                        message = uiState.error?.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                uiState.cards.isEmpty() -> {
                    EmptyContent(
                        hint = "No upcoming episodes",
                        subHint = "${uiState.selectedMonth.displayName} has nothing scheduled yet.",
                    )
                }

                else -> {
                    UpcomingGrid(
                        cards = uiState.cards,
                        gridState = gridState,
                        onOpenEpisode = onOpenEpisode,
                        onOpenSeries = onOpenSeries,
                        onOpenStudio = onOpenStudio,
                    )
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            months = uiState.availableMonths,
            selectedSlug = uiState.selectedMonth.slug,
            onDismiss = { showMonthPicker = false },
            onSelect = { option ->
                showMonthPicker = false
                coroutineScope.launch { gridState.scrollToItem(0) }
                viewModel.selectMonth(option)
            },
        )
    }
}

@Composable
private fun UpcomingGrid(
    cards: List<UpcomingCard>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenEpisode: (String, String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
) {
    val grouped: Map<String, List<UpcomingCard>> = remember(cards) {
        cards.groupBy { it.airDateRaw.ifBlank { "TBA" } }
            .toSortedMap()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridWidth = maxWidth
        val columns = HentaiMamaCardSettings
            .effectiveUpcomingCardCount(gridWidth.value / 150f)
            .toInt()
            .coerceAtLeast(2)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            grouped.forEach { (dateLabel, items) ->
                item(
                    key = "date_header_$dateLabel",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    DateHeader(label = dateLabel, count = items.size)
                }

                items(
                    items = items,
                    key = { it.slug },
                ) { card ->
                    UpcomingCardView(
                        card = card,
                        onOpenEpisode = onOpenEpisode,
                        onOpenSeries = onOpenSeries,
                        onOpenStudio = onOpenStudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(label: String, count: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun UpcomingCardView(
    card: UpcomingCard,
    onOpenEpisode: (String, String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenStudio: (String) -> Unit,
) {
    val aspectRatio by HentaiMamaCardSettings.upcomingEffectiveAspectRatioState
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenEpisode(card.slug, card.url) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
            ) {
                RetryableImage(
                    model = card.posterSmall ?: card.poster,
                    contentDescription = card.altText.ifBlank { card.episodeTitle },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader,
                )

                if (card.isBrandNewSeries) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                card.rating?.let { rating ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = formatRating(rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = card.episodeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                card.seriesTitle?.takeIf { it.isNotBlank() }?.let { series ->
                    Text(
                        text = series,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = card.seriesSlug != null) {
                            card.seriesSlug?.let(onOpenSeries)
                        },
                    )
                }

                card.airDate?.let { date ->
                    Text(
                        text = date.format(DISPLAY_DATE_FORMATTER),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } ?: card.airDateRaw.takeIf { it.isNotBlank() }?.let { raw ->
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                card.studio?.takeIf { it.isNotBlank() }?.let { studio ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = studio,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        card.studioSlug?.let { slug ->
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onOpenStudio(slug) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPickerDialog(
    months: List<UpcomingMonthOption>,
    selectedSlug: String,
    onDismiss: () -> Unit,
    onSelect: (UpcomingMonthOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a month") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                months.forEach { option ->
                    val selected = option.slug == selectedSlug
                    Surface(
                        onClick = { onSelect(option) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun formatRating(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
