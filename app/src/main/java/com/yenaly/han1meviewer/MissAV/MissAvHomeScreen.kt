package com.yenaly.han1meviewer.MissAV

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.screen.rememberCardResponsiveWidth
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal

private val HOME_CATEGORIES = listOf(
    HomeCategory("Release Date", "en/release", null),
    HomeCategory("Weekly Views", "en/weekly-hot", null),
    HomeCategory("Monthly Views", "en/monthly-hot", null),
    HomeCategory("Total Views", "en/release", "views"),
    HomeCategory("Uncensored Leak", "en/uncensored-leak", "released_at"),
    HomeCategory("Creampie", "en/genres/Creampie", "released_at"),
    HomeCategory("Breast Milk", "en/genres/Breast%20Milk", "released_at"),
    HomeCategory("Premature Ejaculation", "en/genres/Premature%20Ejaculation", "released_at"),
    HomeCategory("Harem", "en/genres/Harem", "released_at"),
    HomeCategory("Virgin", "en/genres/Virgin", "released_at"),
    HomeCategory("Sister", "en/genres/Sister", "released_at"),
    HomeCategory("Incest", "en/genres/Incest", "released_at"),
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
    modifier: Modifier = Modifier,
    viewModel: MissAvViewModel = viewModel(),
) {
    val context = LocalContext.current
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

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = homeState) {
            is WebsiteState.Loading -> {
                LoadingContent()
            }
            is WebsiteState.Success -> {
                val (cardWidth, _) = rememberCardResponsiveWidth()
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
                    item(key = "popular_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                            contentPadding = PaddingValues(horizontal = SpacingLarge)
                        ) {
                            items(state.info.popularVideos, key = { it.videoCode }) { video ->
                                VideoCardItem(
                                    modifier = Modifier.width(cardWidth),
                                    videoItem = video,
                                    isHorizontalCard = true,
                                    onClickVideosItem = { onNavigateToVideo(video.videoCode, "/en/${video.videoCode}") },
                                    onLongClickVideosItem = { _, _ -> },
                                )
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
                                    TextButton(onClick = {
                                        onNavigateToSearch(category.title)
                                    }) {
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
                                            onClickVideosItem = { onNavigateToVideo(video.videoCode, "/en/${video.videoCode}") },
                                            onLongClickVideosItem = { _, _ -> },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is WebsiteState.Error -> {
                ErrorContent(
                    message = state.throwable.message,
                    onRetry = { viewModel.getHomePage() },
                )
            }
        }
    }
}