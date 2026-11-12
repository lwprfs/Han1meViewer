package com.yenaly.han1meviewer.RouVideo.ui.home

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoFormat
import com.yenaly.han1meviewer.RouVideo.common.RouVideoImageLoader
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoCard
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun RouVideoCardItem(
    card: RouVideoCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    skipTag: String? = null,
) {
    val context = LocalContext.current
    SiteVideoCard(
        modifier = modifier,
        title = card.displayTitle,
        coverUrl = card.coverUrl,
        onClick = onClick,
        aspectRatio = if (card.isSeries && card.isPoster) 3f / 4f else 16f / 10f,
        views = RouVideoFormat.count(card.viewCount).takeIf { card.viewCount > 0 },
        duration = RouVideoFormat.duration(card.durationSec).takeIf { it.isNotBlank() },
        subtitle = RouVideoFormat.displayTag(card, skipTag),
        uploadTime = RouVideoFormat.age(card.createdAt).takeIf { it.isNotBlank() },
        topEndBadge = RouVideoFormat.badge(card),
        bottomStartBadge = card.code,
        imageLoader = RouVideoImageLoader.get(context),
    )
}

@Composable
fun RouVideoVideoGrid(
    state: RouVideoListUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = stringResource(R.string.rouvideo_nothing_here),
    skipTag: String? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
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
            val videoColumns = rememberVideoGridColumns()
            LazyVerticalGrid(
                columns = GridCells.Fixed(videoColumns),
                state = gridState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { card ->
                    RouVideoCardItem(
                        card = card,
                        onClick = { onOpenVideo(card.id) },
                        skipTag = skipTag,
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
