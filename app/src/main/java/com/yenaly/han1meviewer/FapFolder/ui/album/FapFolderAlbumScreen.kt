package com.yenaly.han1meviewer.FapFolder.ui.album

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderImageLightboxDialog
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderAlbumScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: FapFolderAlbumViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var lightboxPayload by remember {
        mutableStateOf<Pair<List<FapFolderMedia.Image>, Int>?>(null)
    }

    LaunchedEffect(albumId) {
        viewModel.load(albumId)
    }

    LaunchedEffect(
        gridState,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.isLoading,
        uiState.photos.size,
    ) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .filter { it.first > 0 && it.second >= 0 }
            .conflate()
            .collect { (total, last) ->
                if (total > 6 &&
                    last >= total - 6 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isLoading
                ) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title ?: "Album",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
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
                uiState.isLoading && uiState.photos.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                uiState.headerError != null && uiState.photos.isEmpty() -> AlbumError(
                    title = "Failed to load album",
                    message = uiState.headerError ?: "",
                    onRetry = { viewModel.refresh() },
                )

                uiState.photos.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No photos in this album.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 6.dp,
                            end = 6.dp,
                            top = 6.dp,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        uiState.ownerName?.let { owner ->
                            item(
                                key = "album_header",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    if (!owner.isBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "by ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = owner,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    val total = uiState.totalCount
                                    if (total != null) {
                                        Text(
                                            text = "$total photos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = uiState.photos,
                            key = { photo ->
                                "photo_${photo.photoId ?: photo.fullUrl.hashCode()}"
                            },
                        ) { photo ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val idx = uiState.photos.indexOf(photo)
                                            .coerceAtLeast(0)
                                        lightboxPayload = uiState.photos to idx
                                    },
                            ) {
                                RetryableImage(
                                    model = photo.previewUrl ?: photo.fullUrl,
                                    contentDescription = photo.alt,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = painterResource(R.drawable.h_chan_loading_small),
                                    error = painterResource(R.drawable.h_chan_load_failed_small),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item(
                                key = "album_more_loading",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
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

                        if (uiState.itemsError != null && uiState.photos.isNotEmpty()) {
                            item(
                                key = "album_more_error",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = uiState.itemsError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    lightboxPayload?.let { (images, startIndex) ->
        FapFolderImageLightboxDialog(
            images = images,
            initialIndex = startIndex,
            onDismiss = { lightboxPayload = null },
        )
    }
}

@Composable
private fun AlbumError(title: String, message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}