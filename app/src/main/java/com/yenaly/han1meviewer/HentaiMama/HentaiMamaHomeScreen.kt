package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.WebsiteState
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
    onSwitchSite: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
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
                        modifier = Modifier.clickable { onNavigateToSearch(null) },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = onSwitchSite) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_switch_24),
                            contentDescription = stringResource(R.string.switch_site)
                        )
                    }
                    IconButton(onClick = { onNavigateToSearch(null) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    }
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

                if (popularVideos.isEmpty() && latestVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No content available",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onNavigateToSearch(null) }) {
                                Text("Try searching")
                            }
                        }
                    }
                    return@Scaffold
                }

                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (popularVideos.isNotEmpty()) {
                        item(key = "popular_header") {
                            SectionHeader(
                                title = "Popular Videos",
                                onMore = { onNavigateToSearch(null) }
                            )
                        }
                        item(key = "popular_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                                contentPadding = PaddingValues(horizontal = SpacingLarge)
                            ) {
                                items(
                                    items = popularVideos,
                                    key = { video ->
                                        video.videoCode.ifEmpty {
                                            "popular_${System.identityHashCode(video)}"
                                        }
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
                            SectionHeader(
                                title = "Latest Videos",
                                onMore = { onNavigateToSearch(null) }
                            )
                        }
                        item(key = "latest_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                                contentPadding = PaddingValues(horizontal = SpacingLarge)
                            ) {
                                items(
                                    items = latestVideos,
                                    key = { video ->
                                        video.videoCode.ifEmpty {
                                            "latest_${System.identityHashCode(video)}"
                                        }
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
                    onRetry = { viewModel.getHomePage() },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onMore: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onMore) {
            Text(stringResource(R.string.more))
        }
    }
}
