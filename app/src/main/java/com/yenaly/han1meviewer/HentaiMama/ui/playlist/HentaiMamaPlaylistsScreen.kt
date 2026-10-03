package com.yenaly.han1meviewer.HentaiMama.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistCard
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaFilterSheetPlaceholder
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaPlaylistsScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (playlistId: String) -> Unit,
    onPlayFirstEpisode: (playlistId: String, episodeUrl: String) -> Unit,
    viewModel: HentaiMamaPlaylistViewModel = viewModel(),
) {
    val uiState by viewModel.indexState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (uiState.items.isEmpty() && uiState.isInitialLoading) {
            viewModel.loadIndex()
        }
    }

    LaunchedEffect(gridState, uiState.hasMore, uiState.isLoadingMore, uiState.isInitialLoading) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 &&
                    last >= total - 4 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isInitialLoading
                ) {
                    viewModel.loadMoreIndex()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Playlists", maxLines = 1)
                        if (uiState.totalPages > 1) {
                            Text(
                                text = "Page ${uiState.page} / ${uiState.totalPages}",
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
                    IconButton(onClick = { viewModel.loadIndex(uiState.sortKey) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                uiState.isInitialLoading && uiState.items.isEmpty() -> {
                    LoadingContent(message = "Loading playlists…")
                }

                uiState.error != null && uiState.items.isEmpty() -> {
                    ErrorContent(
                        message = uiState.error?.message ?: "Failed to load playlists",
                        onRetry = { viewModel.loadIndex(uiState.sortKey) },
                    )
                }

                uiState.items.isEmpty() -> {
                    EmptyContent(hint = "No playlists")
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (uiState.sortOptions.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SortChips(
                                    options = uiState.sortOptions,
                                    activeSort = uiState.activeSort,
                                    onSortSelected = { key ->
                                        scope.launch { gridState.scrollToItem(0) }
                                        viewModel.loadIndex(key)
                                    },
                                )
                            }
                        }

                        items(uiState.items, key = { it.id }) { card ->
                            PlaylistCardItem(
                                card = card,
                                onClick = { onOpenPlaylist(card.id) },
                                onPlay = {
                                    card.playUrl?.let { url ->
                                        onPlayFirstEpisode(card.id, url)
                                    } ?: onOpenPlaylist(card.id)
                                },
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
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

                        if (!uiState.hasMore && uiState.items.isNotEmpty
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No more playlists",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChips(
    options: List<com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistSortOption>,
    activeSort: String?,
    onSortSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Sort by:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            val selected = option.isCurrent ||
                    (activeSort != null && option.label == activeSort)
            val sortKey = when (option.label.lowercase()) {
                "views" -> null
                "likes" -> "likes"
                "recent" -> "recent"
                else -> option.label.lowercase()
            }
            FilterChip(
                selected = selected,
                onClick = { onSortSelected(sortKey) },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun PlaylistCardItem(
    card: PlaylistCard,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                RetryableImage(
                    model = card.thumbFull ?: card.thumbMid ?: card.thumbSmall.orEmpty(),
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = buildString {
                            append("${card.videoCount ?: 0} videos")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = if (card.visibility == "private")
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_lock_24),
                            contentDescription = null,
                            tint = if (card.visibility == "private")
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = card.visibility.replaceFirstChar { it.uppercaseChar() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (card.visibility == "private")
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                if (card.playUrl != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clickable { onPlay() },
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "by ${card.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = card.viewsRaw.ifBlank { "${card.views ?: 0}" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (card.liked)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${card.likes ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}