package com.yenaly.han1meviewer.OnAir.ui.picture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPageInfo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPictureCard
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirPicturesScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onLoadFailure: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridStateCompat()
    val columns = rememberVideoGridColumns()

    var cards by remember { mutableStateOf<List<OnAirPictureCard>>(emptyList()) }
    var state by remember { mutableStateOf<PageLoadingState<*>?>(null) }
    var pageInfo by remember { mutableStateOf<OnAirPageInfo?>(null) }
    var loadedPage by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    fun load(page: Int, replace: Boolean) {
        if (isLoadingMore) return
        isLoadingMore = true
        if (replace) state = PageLoadingState.Loading
        scope.launch {
            OnAirNetworkRepo.getPicturesPage(page = page).collect { s ->
                state = s
                when (s) {
                    is PageLoadingState.Success -> {
                        val pair = s.info as Pair<List<OnAirPictureCard>, OnAirPageInfo?>
                        cards = if (replace) pair.first
                        else (cards + pair.first).distinctBy { it.pictureId }
                        pageInfo = pair.second
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

    LaunchedEffect(Unit) { load(1, replace = true) }

    LaunchedEffect(gridState, isLoadingMore, pageInfo) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 && last >= total - 4 && !isLoadingMore && pageInfo != null) {
                    load(loadedPage + 1, replace = false)
                }
            }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is PageLoadingState.Error && cards.isEmpty()) {
            onLoadFailure(s.throwable.message ?: "Pictures load failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pictures") },
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
                    Text("No pictures")
                }
                else -> {
                    LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpacingNormal),
                        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                        verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                        enableItemAnimation = false,
                    ) {
                        items(cards, key = { it.pictureId }) { card ->
                            val idx = cards.indexOf(card)
                            OnAirPictureItem(
                                card = card,
                                onClick = { viewerIndex = idx },
                                onOpenUser = onOpenUser,
                            )
                        }
                        if (cards.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                com.yenaly.han1meviewer.OnAir.ui.home.InfiniteScrollFooter(
                                    isLoadingMore = isLoadingMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewerIndex?.let { startIndex ->
        PictureFullscreenViewer(
            cards = cards,
            initialIndex = startIndex,
            onDismiss = { viewerIndex = null },
        )
    }
}

@Composable
private fun OnAirPictureItem(
    card: OnAirPictureCard,
    onClick: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RetryableImage(
                model = card.thumbUrl,
                contentDescription = card.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                placeholder = painterResource(R.drawable.h_chan_loading),
                error = painterResource(R.drawable.h_chan_load_failed),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = card.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            card.uploaderName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PictureFullscreenViewer(
    cards: List<OnAirPictureCard>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (cards.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, cards.lastIndex),
        pageCount = { cards.size },
    )
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible, pagerState.currentPage) {
        if (controlsVisible) {
            delay(2000L)
            controlsVisible = false
        }
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 2,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    RetryableImage(
                        model = cards[page].thumbUrl,
                        contentDescription = cards[page].title,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${cards.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

private typealias LazyGridStateCompat = androidx.compose.foundation.lazy.grid.LazyGridState

@Composable
private fun rememberLazyGridStateCompat(): LazyGridStateCompat =
    androidx.compose.foundation.lazy.grid.rememberLazyGridState()
