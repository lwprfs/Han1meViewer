package com.yenaly.han1meviewer.MissAV

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.rememberCardResponsiveWidth
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import kotlinx.coroutines.delay

private val HOME_CATEGORIES = listOf(
    HomeCategory("Release Date", "en/release", "released_at"),
    HomeCategory("Weekly Views", "en/weekly-hot", "released_at"),
    HomeCategory("Monthly Views", "en/monthly-hot", "released_at"),
    HomeCategory("Total Views", "en/release", "published_at"),
    HomeCategory("Uncensored Leak", "en/uncensored-leak", "published_at"),
    HomeCategory("Creampie", "en/genres/Creampie", "published_at"),
    HomeCategory("Breast Milk", "en/genres/Breast%20Milk", "published_at"),
    HomeCategory("Premature Ejaculation", "en/genres/Premature%20Ejaculation", "published_at"),
    HomeCategory("Harem", "en/genres/Harem", "published_at"),
    HomeCategory("Virgin", "en/genres/Virgin", "published_at"),
    HomeCategory("Sister", "en/genres/Sister", "published_at"),
    HomeCategory("Incest", "en/genres/Incest", "published_at"),
)

data class HomeCategory(
    val title: String,
    val genrePath: String,
    val sort: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvHomeScreen(
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onSwitchSite: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MissAvViewModel = viewModel(),
) {
    val homeState by viewModel.homePageFlow.collectAsStateWithLifecycle()
    val categoryStates = remember { mutableStateMapOf<String, List<HanimeInfo>>() }
    
    LaunchedEffect(Unit) {
        viewModel.getHomePage()
        HOME_CATEGORIES.forEach { category ->
            viewModel.getGenreVideos(category.genrePath, 1, category.sort) { videos ->
                categoryStates[category.title] = videos
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MissAV",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { onNavigateToSearch(null) }
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.watch_history)
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
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = homeState) {
                is WebsiteState.Loading -> {
                    if (categoryStates.isEmpty()) {
                        LoadingContent()
                    } else {
                        DisplayContent(
                            categoryStates = categoryStates,
                            onNavigateToVideo = onNavigateToVideo,
                            onNavigateToSearch = onNavigateToSearch
                        )
                    }
                }
                is WebsiteState.Success -> {
                    DisplayContent(
                        categoryStates = categoryStates,
                        onNavigateToVideo = onNavigateToVideo,
                        onNavigateToSearch = onNavigateToSearch
                    )
                }
                is WebsiteState.Error -> {
                    if (categoryStates.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load home page",
                            onRetry = { 
                                viewModel.getHomePage()
                                HOME_CATEGORIES.forEach { category ->
                                    viewModel.getGenreVideos(category.genrePath, 1, category.sort) { videos ->
                                        categoryStates[category.title] = videos
                                    }
                                }
                            }
                        )
                    } else {
                        DisplayContent(
                            categoryStates = categoryStates,
                            onNavigateToVideo = onNavigateToVideo,
                            onNavigateToSearch = onNavigateToSearch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayContent(
    categoryStates: Map<String, List<HanimeInfo>>,
    onNavigateToVideo: (String, String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
) {
    val (cardWidth, _) = rememberCardResponsiveWidth()
    
    val allVideos = categoryStates.values.flatten().distinctBy { it.videoCode }
    val hasContent = allVideos.isNotEmpty()
    
    if (!hasContent) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No content available",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onNavigateToSearch(null) }) {
                    Text("Try searching")
                }
            }
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
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
        
        if (allVideos.isNotEmpty()) {
            item(key = "popular_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                    contentPadding = PaddingValues(horizontal = SpacingLarge)
                ) {
                    items(allVideos.take(10), key = { it.videoCode }) { video ->
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

        HOME_CATEGORIES.forEach { category ->
            val videos = categoryStates[category.title] ?: emptyList()
            if (videos.isNotEmpty()) {
                item(key = "cat_header_${category.title}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onNavigateToSearch(category.title) }) {
                            Text(stringResource(R.string.more))
                        }
                    }
                }
                item(key = "cat_row_${category.title}") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                        contentPadding = PaddingValues(horizontal = SpacingLarge)
                    ) {
                        items(videos, key = { it.videoCode }) { video ->
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
        }
    }
}