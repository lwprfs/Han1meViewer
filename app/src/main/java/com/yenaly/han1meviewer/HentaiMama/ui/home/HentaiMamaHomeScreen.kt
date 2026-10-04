package com.yenaly.han1meviewer.HentaiMama.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetworkRepo
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettings
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaGenreSeriesCard
import com.yenaly.han1meviewer.HentaiMama.ui.components.HentaiMamaVideoCard
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiMamaHomeScreen(
    onNavigateToVideo: (code: String, path: String) -> Unit,
    onNavigateToSearch: (query: String?) -> Unit,
    onNavigateToCategorySearch: (categoryKey: String) -> Unit = {},
    onNavigateToGenre: (genreSlug: String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onSwitchSite: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        HentaiMamaCardSettings.load()
    }

    val categoryRows by viewModel.categoryRows.collectAsStateWithLifecycle()
    val horizontalCardCountConfig = remember { Preferences.horizontalCardCountConfig }

    LaunchedEffect(Unit) {
        if (categoryRows.isEmpty()) {
            viewModel.refreshAllCategories()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HentaiMama",
                        modifier = Modifier.clickable { onNavigateToHome() },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onSwitchSite) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_switch_24),
                            contentDescription = stringResource(R.string.switch_site),
                        )
                    }
                    IconButton(onClick = { onNavigateToSearch(null) }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (categoryRows.isEmpty()) {
                HomeLoadingState(onRetry = { viewModel.refreshAllCategories() })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    categoryRows.forEach { row ->
                        val key = row.category.key
                        val hasContent = row.videos.isNotEmpty() ||
                                row.series.isNotEmpty() ||
                                row.error != null ||
                                row.isLoading
                        if (!hasContent) return@forEach

                        item(key = "header_$key") {
                            SectionHeader(
                                title = row.category.title,
                                onMore = {
                                    if (row.isGenre) {
                                        val slug = HentaiMamaNetworkRepo.extractGenreSlug(
                                            row.category.genrePath
                                        )
                                        if (slug.isNotBlank()) {
                                            onNavigateToGenre(slug)
                                        } else {
                                            onNavigateToCategorySearch(key)
                                        }
                                    } else {
                                        onNavigateToCategorySearch(key)
                                    }
                                },
                            )
                        }

                        item(key = "body_$key") {
                            when {
                                row.series.isNotEmpty() -> GenreRowContent(
                                    series = row.series,
                                    horizontalCardCountConfig = horizontalCardCountConfig,
                                    onNavigateToVideo = onNavigateToVideo,
                                )

                                row.videos.isNotEmpty() -> CategoryRowContent(
                                    videos = row.videos,
                                    horizontalCardCountConfig = horizontalCardCountConfig,
                                    onNavigateToVideo = onNavigateToVideo,
                                )

                                row.error != null -> CategoryErrorRow(
                                    message = row.error,
                                    onRetry = { viewModel.retryCategory(row.category) },
                                )

                                else -> CategorySkeletonRowContent(
                                    horizontalCardCountConfig = horizontalCardCountConfig,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberCategoryCardWidth(
    horizontalCardCountConfig: com.yenaly.han1meviewer.HorizontalCardCountConfig,
    availableWidthDp: Dp,
): Dp {
    val baseCount = horizontalCardCountConfig.countForWidthDp(availableWidthDp.value.toInt())
    val cardMultiplier by HentaiMamaCardSettings.cardMultiplierState
    val widthMultiplier by HentaiMamaCardSettings.widthMultiplierState
    val effectiveCount = (baseCount * cardMultiplier).coerceAtLeast(1f)
    val availableForCards = availableWidthDp - SpacingLarge * 2 - SpacingNormal * (effectiveCount - 1)
    return (availableForCards / effectiveCount) * widthMultiplier
}

@Composable
private fun CategoryRowContent(
    videos: List<HanimeInfo>,
    horizontalCardCountConfig: com.yenaly.han1meviewer.HorizontalCardCountConfig,
    onNavigateToVideo: (String, String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = rememberCategoryCardWidth(horizontalCardCountConfig, maxWidth)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
            contentPadding = PaddingValues(horizontal = SpacingLarge),
        ) {
            items(videos, key = { it.videoCode }) { video ->
                HentaiMamaVideoCard(
                    modifier = Modifier.width(cardWidth),
                    videoItem = video,
                    onClick = {
                        val path = HentaiMamaNetwork.normalizeUrl("/${video.videoCode}")
                        onNavigateToVideo(video.videoCode, path)
                    },
                    onLongClick = { _, _ -> },
                )
            }
        }
    }
}

@Composable
private fun GenreRowContent(
    series: List<GenreSeries>,
    horizontalCardCountConfig: com.yenaly.han1meviewer.HorizontalCardCountConfig,
    onNavigateToVideo: (String, String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = rememberCategoryCardWidth(horizontalCardCountConfig, maxWidth)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
            contentPadding = PaddingValues(horizontal = SpacingLarge),
        ) {
            items(series, key = { it.slug }) { item ->
                HentaiMamaGenreSeriesCard(
                    modifier = Modifier.width(cardWidth),
                    series = item,
                    onClick = { slug ->
                        val path = HentaiMamaNetwork.normalizeUrl("/tvshows/$slug/")
                        onNavigateToVideo(slug, path)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategorySkeletonRowContent(
    horizontalCardCountConfig: com.yenaly.han1meviewer.HorizontalCardCountConfig,
) {
    val aspectRatio by HentaiMamaCardSettings.effectiveAspectRatioState
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = rememberCategoryCardWidth(horizontalCardCountConfig, maxWidth)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
            contentPadding = PaddingValues(horizontal = SpacingLarge),
            userScrollEnabled = false,
        ) {
            items(4) {
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardWidth / aspectRatio)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun HomeLoadingState(
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Tap to retry")
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMore) {
            Text(stringResource(R.string.more))
        }
    }
}

@Composable
private fun CategoryErrorRow(
    message: String,
    onRetry: () -> Unit,
) {
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
