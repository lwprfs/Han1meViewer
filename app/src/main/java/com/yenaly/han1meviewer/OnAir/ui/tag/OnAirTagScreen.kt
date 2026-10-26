package com.yenaly.han1meviewer.OnAir.ui.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPageInfo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
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
fun OnAirTagScreen(
    tagName: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onLoadFailure: (String) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val columns = rememberVideoGridColumns()
    var cards by remember { mutableStateOf<List<OnAirVideoCard>>(emptyList()) }
    var state by remember { mutableStateOf<PageLoadingState<*>?>(null) }
    var pageInfo by remember { mutableStateOf<OnAirPageInfo?>(null) }
    var loadedPage by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }

    fun load(page: Int, replace: Boolean) {
        if (isLoadingMore) return
        isLoadingMore = true
        if (replace) state = PageLoadingState.Loading
        scope.launch {
            OnAirNetworkRepo.getTagPage(
                tagName = tagName,
                page = page,
                query = null,
                field = null,
                length = null,
                date = null,
                sort = null,
            ).collect { s ->
                state = s
                when (s) {
                    is PageLoadingState.Success -> {
                        val result = s.info
                        cards = if (replace) result.cards
                        else (cards + result.cards).distinctBy { it.videoId }
                        pageInfo = result.pageInfo
                        loadedPage = page
                        isLoadingMore = false
                    }
                    is PageLoadingState.NoMoreData,
                    is PageLoadingState.Error -> isLoadingMore = false
                    is PageLoadingState.Loading -> Unit
                }
            }
        }
    }

    LaunchedEffect(tagName) { load(1, replace = true) }

    LaunchedEffect(gridState, isLoadingMore, pageInfo) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }.distinctUntilChanged().collect { (total, last) ->
            if (total > 4 && last >= total - 4 && !isLoadingMore && pageInfo != null) {
                load(loadedPage + 1, replace = false)
            }
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is PageLoadingState.Error && cards.isEmpty()) {
            onLoadFailure(s.throwable.message ?: "Tag load failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#$tagName", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                state is PageLoadingState.Loading && cards.isEmpty() -> LoadingContent()
                state is PageLoadingState.Error && cards.isEmpty() -> {
                    val err = state as PageLoadingState.Error
                    ErrorContent(
                        message = err.throwable.message ?: "Failed",
                        onRetry = { load(1, replace = true) },
                    )
                }
                cards.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No videos for this tag",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "#$tagName",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpacingNormal),
                        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                        verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                        enableItemAnimation = false,
                    ) {
                        items(cards, key = { it.videoId }) { card ->
                            OnAirVideoCardItem(card = card, onClick = { onOpenVideo(card.videoId) })
                        }
                        if (cards.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                InfiniteScrollFooter(isLoadingMore = isLoadingMore)
                            }
                        }
                    }
                }
            }
        }
    }
}
