package com.yenaly.han1meviewer.SupJav.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import androidx.compose.ui.platform.LocalContext
import com.yenaly.han1meviewer.SupJav.common.SupJavImageLoader
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SupJavVideoGrid(
    state: SupJavListUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "Nothing here",
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val imageLoader = SupJavImageLoader.get(LocalContext.current)
    when {
        state.isLoading && state.items.isEmpty() -> SiteLoadingContent(modifier = modifier)
        state.error != null && state.items.isEmpty() -> SiteErrorContent(
            message = state.error,
            onRetry = onRetry,
            modifier = modifier,
        )

        state.items.isEmpty() -> SiteEmptyContent(hint = emptyHint)
        else -> {
            val nearEnd by remember(gridState) {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
                }
            }
            LaunchedEffect(gridState, state.items.size) {
                snapshotFlow { nearEnd }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadMore() }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = gridState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.videoCode }) { video ->
                    SiteVideoCard(
                        title = video.title,
                        coverUrl = video.coverUrl,
                        onClick = { onOpenVideo(video.videoCode) },
                        aspectRatio = 320f / 216f,
                        imageLoader = imageLoader,
                    )
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
