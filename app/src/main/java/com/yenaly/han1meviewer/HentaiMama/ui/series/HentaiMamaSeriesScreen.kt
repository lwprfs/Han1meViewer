package com.yenaly.han1meviewer.HentaiMama.ui.series

import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.han1meviewer.ui.component.TagChipGroup
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import com.yenaly.han1meviewer.ui.component.ExpandableRichText
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ElevatedCard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesHero
import com.yenaly.han1meviewer.HentaiMama.data.model.SimilarCard
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaViewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HentaiMamaSeriesScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenEpisode: (videoCode: String, url: String) -> Unit,
    onOpenRelatedSeries: (slug: String) -> Unit,
    onGenreClick: (String) -> Unit,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    val seriesState by viewModel.seriesState.collectAsStateWithLifecycle()

    val seriesUrl: String = remember(slug) {
        if (slug.startsWith("http")) slug
        else "${HentaiMamaNetwork.baseUrl}/tvshows/$slug/"
    }

    LaunchedEffect(seriesUrl) {
        viewModel.getSeriesDetail(seriesUrl)
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    val title: String = (seriesState as? VideoLoadingState.Success)
                        ?.info
                        ?.hero
                        ?.title
                        ?: "Series"
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val state = seriesState
            when (state) {
                is VideoLoadingState.Loading -> SiteLoadingContent()

                is VideoLoadingState.Error -> SiteErrorContent(
                    message = state.throwable.message ?: "Failed to load series",
                    onRetry = { viewModel.getSeriesDetail(seriesUrl) },
                )

                is VideoLoadingState.NoContent -> SiteErrorContent(
                    message = "Series not found",
                    onRetry = { viewModel.getSeriesDetail(seriesUrl) },
                )

                is VideoLoadingState.Success -> SeriesDetailContent(
                    page = state.info,
                    onOpenEpisode = onOpenEpisode,
                    onOpenRelatedSeries = onOpenRelatedSeries,
                    onGenreClick = onGenreClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesDetailContent(
    page: SeriesDetailPage,
    onOpenEpisode: (videoCode: String, url: String) -> Unit,
    onOpenRelatedSeries: (slug: String) -> Unit,
    onGenreClick: (String) -> Unit,
) {
    val nonEmptyCastGroups: List<List<Pair<String, String>>> =
        page.cast.filter { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 6.dp,
            top = 12.dp,
            end = 6.dp,
            bottom = 24.dp + WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hero") {
            SeriesHeroCard(
                hero = page.hero,
                onGenreClick = onGenreClick,
                onPlayFirstEpisode = {
                    val first: HentaiMamaEpisode? = page.episodes
                        .minByOrNull { it.episodeNumber ?: Float.MAX_VALUE }
                    if (first != null) {
                        onOpenEpisode(first.slug, first.url)
                    }
                },
            )
        }

        if (nonEmptyCastGroups.isNotEmpty()) {
            item(key = "cast") {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                ) {
                    SiteVideoSectionHeader(title = "Cast")
                    nonEmptyCastGroups.forEachIndexed { index, group ->
                        SeriesCastRow(
                            label = if (index == 0) "Creator" else "Cast",
                            members = group,
                        )
                    }
                }
            }
        }

        if (page.trailerUrl != null) {
            item(key = "trailer") {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                ) {
                    SiteVideoSectionHeader(title = "Trailer")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Watch trailer",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        item(key = "episodes_header") {
            SiteVideoSectionHeader(
                title = "Episodes (${page.totalEpisodes})",
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (page.episodes.isEmpty()) {
            item(key = "episodes_empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Text(
                        text = "No episodes available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        } else {
            items(
                items = page.episodes,
                key = { episode: HentaiMamaEpisode -> episode.slug },
            ) { episode: HentaiMamaEpisode ->
                SeriesEpisodeRow(
                    episode = episode,
                    onClick = { onOpenEpisode(episode.slug, episode.url) },
                )
            }
        }

        if (page.similar.isNotEmpty()) {
            item(key = "similar") {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(SpacingNormal),
                ) {
                    SiteVideoSectionHeader(title = "Similar titles")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                    ) {
                        items(
                            items = page.similar,
                            key = { sim: SimilarCard -> sim.slug },
                        ) { sim: SimilarCard ->
                            SiteVideoCard(
                                modifier = Modifier.width(120.dp),
                                title = sim.name,
                                coverUrl = sim.poster,
                                aspectRatio = 2f / 3f,
                                duration = sim.episodeCount?.takeIf { it > 0 }?.let { "$it EP" },
                                reviews = sim.rating?.let { "★ %.1f".format(it) },
                                uploadTime = sim.year?.toString(),
                                onClick = { onOpenRelatedSeries(sim.slug) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesHeroCard(
    hero: SeriesHero,
    onGenreClick: (String) -> Unit,
    onPlayFirstEpisode: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                RetryableImage(
                    model = hero.poster,
                    contentDescription = hero.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = hero.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                val altTitle: String? = hero.altTitle
                    ?.takeIf { it.isNotBlank() && it != hero.title }
                if (altTitle != null) {
                    SelectionContainer {
                        Text(
                            text = altTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val rating: Double? = hero.ratingValue
        val views: Long? = hero.views
        val duration: String? = hero.duration
        val aired: String? = hero.aired
        val episodeCount: Int? = hero.episodeCount
        val studio: String? = hero.studios.firstOrNull()?.name
        if (rating != null || views != null || duration != null || aired != null ||
            episodeCount != null || studio != null
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (rating != null) {
                    SeriesMetaInfoItem(
                        icon = Icons.Filled.Star,
                        iconSize = 16,
                        iconTint = MaterialTheme.colorScheme.primary,
                        text = "%.1f".format(rating),
                    )
                }
                if (views != null) {
                    SeriesMetaInfoItem(
                        icon = Icons.Rounded.PlayArrow,
                        iconSize = 16,
                        text = formatViewsSeries(views),
                    )
                }
                if (duration != null) {
                    SeriesMetaInfoItem(icon = Icons.Rounded.Schedule, text = duration)
                }
                if (aired != null) {
                    SeriesMetaInfoItem(icon = Icons.Rounded.Schedule, text = "Aired $aired")
                }
                if (episodeCount != null) {
                    SeriesMetaInfoItem(icon = null, text = "$episodeCount episodes")
                }
                if (studio != null) {
                    SeriesMetaInfoItem(icon = null, text = studio)
                }
            }
        }

        if (!hero.synopsisText.isNullOrBlank()) {
            ExpandableRichText(
                text = hero.synopsisText,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onPlayFirstEpisode,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "Watch Ep 1")
        }

        val statusChips: List<String> = hero.statusChips
        if (statusChips.isNotEmpty()) {
            TagChipGroup(
                tags = statusChips,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hero.genres.isNotEmpty()) {
            val genreNames = remember(hero.genres) { hero.genres.map { it.name } }
            TagChipGroup(
                tags = genreNames,
                collapsible = true,
                collapsedMaxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                onTagClick = onGenreClick,
            )
        }
    }
}

@Composable
private fun SeriesMetaInfoItem(
    icon: ImageVector?,
    text: String,
    iconSize: Int = 14,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
) {
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize.dp),
                tint = iconTint,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeriesCastRow(
    label: String,
    members: List<Pair<String, String>>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            for (pair in members) {
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SeriesEpisodeRow(
    episode: HentaiMamaEpisode,
    onClick: () -> Unit,
) {

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                RetryableImage(
                    model = episode.thumb,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val date: String? = episode.date
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = episode.episodeNumber
                        ?.let { n: Float -> "EP ${n.toInt()}" }
                        ?: "EP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatViewsSeries(views: Long): String = when {
    views >= 1_000_000_000 -> "%.1fB views".format(views / 1_000_000_000.0)
    views >= 1_000_000 -> "%.1fM views".format(views / 1_000_000.0)
    views >= 1_000 -> "%.1fK views".format(views / 1_000.0)
    else -> "$views views"
}
