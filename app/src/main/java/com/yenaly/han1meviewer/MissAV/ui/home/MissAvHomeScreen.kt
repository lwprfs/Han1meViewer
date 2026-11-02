package com.yenaly.han1meviewer.MissAV.ui.home
import androidx.compose.material.icons.filled.Menu
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.SiteBarIconButton
import com.yenaly.han1meviewer.ui.component.SiteSearchTopBar
import com.yenaly.han1meviewer.ui.component.SiteSectionHeader
import com.yenaly.han1meviewer.ui.component.SiteSkeletonBox
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteSwitchButton
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.screen.rememberCardResponsiveWidth
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal

import com.yenaly.han1meviewer.MissAV.data.model.MissAvHomePage
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvHomeScreen(
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onSwitchSite: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MissAvHomeViewModel,
) {
    val homeState by viewModel.homePageFlow.collectAsStateWithLifecycle()
    val categoryRows by viewModel.categoryRows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hostLabel = remember { MissAvHostRepo.current(context).hostname }

    LaunchedEffect(Unit) {
        viewModel.getHomePage()
        if (categoryRows.isNotEmpty() && categoryRows.all { it.videos.isEmpty() }) {
            viewModel.refreshAllCategories()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteSearchTopBar(
                hint = "Search MissAV · $hostLabel",
                onSearchClick = { onNavigateToSearch(null) },
                leading = {
                    SiteBarIconButton(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.open_menu),
                        onClick = onNavigateToSettings,
                    )
                },
                actions = {
                    SiteBarIconButton(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.watch_history),
                        onClick = onNavigateToHistory,
                    )
                    SiteSwitchButton(onClick = onSwitchSite)
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isLoadingEverything =
                homeState is WebsiteState.Loading && categoryRows.isEmpty()
            val isErrorEverything =
                homeState is WebsiteState.Error && categoryRows.isEmpty()

            when {
                isLoadingEverything -> SiteLoadingContent()

                isErrorEverything -> SiteErrorContent(
                    message = (homeState as WebsiteState.Error).throwable.message
                        ?: "Failed to load home page",
                    onRetry = {
                        viewModel.getHomePage(force = true)
                        viewModel.refreshAllCategories()
                    },
                )

                else -> HomeContent(
                    homeState = homeState,
                    categoryRows = categoryRows,
                    onRetryCategory = viewModel::retryCategory,
                    onNavigateToVideo = onNavigateToVideo,
                    onNavigateToSearch = onNavigateToSearch,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    homeState: WebsiteState<MissAvHomePage>,
    categoryRows: List<HomeCategoryRowState>,
    onRetryCategory: (MissAvHomeCategory) -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
) {
    val (cardWidth, _) = rememberCardResponsiveWidth()
    val popular = (homeState as? WebsiteState.Success)?.info?.popularVideos.orEmpty()

    if (popular.isEmpty() && categoryRows.all { it.videos.isEmpty() && !it.isLoading }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SiteEmptyContent(hint = "No content available")
            }
            TextButton(
                onClick = { onNavigateToSearch(null) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            ) {
                Text("Try searching")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(SpacingNormal),
    ) {
        if (popular.isNotEmpty()) {
            item(key = "popular_header") {
                SiteSectionHeader(
                    title = "Popular Videos",
                    onMoreClick = { onNavigateToSearch(null) },
                )
            }
            item(key = "popular_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                    contentPadding = PaddingValues(horizontal = SpacingLarge),
                ) {
                    items(popular.take(10), key = { it.videoCode }) { video ->
                        VideoCardItem(
                            modifier = Modifier.width(cardWidth),
                            videoItem = video,
                            isHorizontalCard = true,
                            onClickVideosItem = {
                                onNavigateToVideo(video.videoCode, "/en/${video.videoCode}")
                            },
                            onLongClickVideosItem = { _, _ -> },
                        )
                    }
                }
            }
        }

        categoryRows.forEach { row ->
            val key = row.category.key

            item(key = "cat_header_$key") {
                SiteSectionHeader(
                    title = row.category.title,
                    onMoreClick = { onNavigateToSearch(row.category.title) },
                )
            }

            item(key = "cat_body_$key") {
                when {
                    row.videos.isNotEmpty() -> {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                            contentPadding = PaddingValues(horizontal = SpacingLarge),
                        ) {
                            items(row.videos, key = { it.videoCode }) { video ->
                                VideoCardItem(
                                    modifier = Modifier.width(cardWidth),
                                    videoItem = video,
                                    isHorizontalCard = true,
                                    onClickVideosItem = {
                                        onNavigateToVideo(video.videoCode, "/en/${video.videoCode}")
                                    },
                                    onLongClickVideosItem = { _, _ -> },
                                )
                            }
                        }
                    }

                    row.error != null -> {
                        CategoryErrorRow(
                            message = row.error,
                            onRetry = { onRetryCategory(row.category) },
                        )
                    }

                    else -> {
                        CategorySkeletonRow(cardWidth = cardWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySkeletonRow(cardWidth: Dp) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
        contentPadding = PaddingValues(horizontal = SpacingLarge),
        userScrollEnabled = false,
    ) {
        items(4) {
            SiteSkeletonBox(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardWidth * 9f / 16f + 64.dp),
            )
        }
    }
}

@Composable
private fun CategoryErrorRow(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpacingLarge),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}


