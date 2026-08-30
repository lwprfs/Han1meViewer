package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.rememberCardResponsiveWidth
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaHomeScreen(
    onNavigateToVideo: (String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    val context = LocalContext.current
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasLoaded) {
            viewModel.getHomePage()
            hasLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HentaiMama",
                        modifier = Modifier.clickable { 
                            onNavigateToSearch(null) 
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    ) { paddingValues ->
        when (val state = homeState) {
            is WebsiteState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            is WebsiteState.Success -> {
                val popularVideos = state.info.popularVideos
                    .filter { it.videoCode.isNotEmpty() && it.videoCode != "unknown" }
                val latestVideos = state.info.latestVideos
                    .filter { it.videoCode.isNotEmpty() && it.videoCode != "unknown" }
                val (cardWidth, _) = rememberCardResponsiveWidth()
                
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (popularVideos.isNotEmpty()) {
                        item(key = "popular_header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Popular Videos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onNavigateToSearch(null) }) {
                                    Text(stringResource(R.string.more))
                                }
                            }
                        }
                        item(key = "popular_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                                contentPadding = PaddingValues(horizontal = SpacingLarge)
                            ) {
                                items(
                                    items = popularVideos,
                                    key = { video -> 
                                        video.videoCode.ifEmpty { "popular_${System.identityHashCode(video)}" }
                                    }
                                ) { video ->
                                    VideoCardItem(
                                        modifier = Modifier.width(cardWidth),
                                        videoItem = video,
                                        isHorizontalCard = true,
                                        onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                                        onLongClickVideosItem = { _, _ -> },
                                    )
                                }
                            }
                        }
                    }
                    if (latestVideos.isNotEmpty()) {
                        item(key = "latest_header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Latest Videos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onNavigateToSearch(null) }) {
                                    Text(stringResource(R.string.more))
                                }
                            }
                        }
                        item(key = "latest_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                                contentPadding = PaddingValues(horizontal = SpacingLarge)
                            ) {
                                items(
                                    items = latestVideos,
                                    key = { video ->
                                        video.videoCode.ifEmpty { "latest_${System.identityHashCode(video)}" }
                                    }
                                ) { video ->
                                    VideoCardItem(
                                        modifier = Modifier.width(cardWidth),
                                        videoItem = video,
                                        isHorizontalCard = true,
                                        onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                                        onLongClickVideosItem = { _, _ -> },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is WebsiteState.Error -> {
                ErrorContent(
                    message = state.throwable.message ?: "Failed to load home page",
                    onRetry = { 
                        viewModel.getHomePage()
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}