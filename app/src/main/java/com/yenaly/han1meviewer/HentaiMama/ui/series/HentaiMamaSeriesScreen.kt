package com.yenaly.han1meviewer.HentaiMama.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
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
            TopAppBar(
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
            val state = seriesState
            when (state) {
                is VideoLoadingState.Loading -> LoadingContent()

                is VideoLoadingState.Error -> ErrorContent(
                    message = state.throwable.message ?: "Failed to load series",
                    onRetry = { viewModel.getSeriesDetail(seriesUrl) },
                )

                is VideoLoadingState.NoContent -> ErrorContent(
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
        contentPadding = PaddingValues(bottom = 32.dp),
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
            item(key = "cast_header") {
                SeriesSectionHeader("Cast")
            }
            items(
                items = nonEmptyCastGroups,
                key = { group: List<Pair<String, String>> ->
                    "cast_" + group.joinToString("|") { it.first }
                },
            ) { group: List<Pair<String, String>> ->
                val isFirst: Boolean = group === nonEmptyCastGroups.firstOrNull()
                SeriesCastRow(
                    label = if (isFirst) "Creator" else "Cast",
                    members = group,
                )
            }
        }

        if (page.trailerUrl != null) {
            item(key = "trailer_header") {
                SeriesSectionHeader("Trailer")
            }
            item(key = "trailer") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Watch trailer",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item(key = "episodes_header") {
            SeriesSectionHeader("Episodes (${page.totalEpisodes})")
        }

        if (page.episodes.isEmpty()) {
            item(key = "episodes_empty") {
                Text(
                    text = "No episodes available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
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
            item(key = "similar_header") {
                SeriesSectionHeader("Similar titles")
            }
            items(
                items = page.similar,
                key = { sim: SimilarCard -> sim.slug },
            ) { sim: SimilarCard ->
                SeriesSimilarRow(
                    card = sim,
                    onClick = { onOpenRelatedSeries(sim.slug) },
                )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.Top) {

                Box(
                    modifier = Modifier
                        .width(120.dp)
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

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = hero.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val altTitle: String? = hero.altTitle
                        ?.takeIf { it.isNotBlank() && it != hero.title }
                    if (altTitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = altTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val rating: Double? = hero.ratingValue
                        if (rating != null) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "%.1f".format(rating),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        val views: Long? = hero.views
                        if (views != null) {
                            Text(
                                text = formatViewsSeries(views),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onPlayFirstEpisode,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 6.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Watch Ep 1",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            if (hero.statusChips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (chip: String in hero.statusChips) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(chip, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            if (hero.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (genre in hero.genres) {
                        AssistChip(
                            onClick = { onGenreClick(genre.name) },
                            label = {
                                Text(
                                    genre.name,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            if (!hero.synopsisText.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hero.synopsisText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val metaItems: List<Pair<String, String>> = buildList {
                hero.duration?.let { add("Duration" to it) }
                hero.aired?.let { add("Aired" to it) }
                hero.episodeCount?.let { add("Episodes" to it.toString()) }
                hero.studios.firstOrNull()?.let { add("Studio" to it.name) }
            }
            if (metaItems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (pair in metaItems) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 6.dp,
                                ),
                            ) {
                                Text(
                                    text = pair.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = pair.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
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
private fun SeriesSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SeriesCastRow(
    label: String,
    members: List<Pair<String, String>>,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            for (pair in members) {
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.bodySmall,
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
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

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.episodeNumber
                        ?.let { n: Float -> "EP ${n.toInt()}" }
                        ?: "EP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val date: String? = episode.date
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SeriesSimilarRow(
    card: SimilarCard,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                RetryableImage(
                    model = card.poster,
                    contentDescription = card.name,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    val rating: Double? = card.rating
                    if (rating != null) {
                        Text(
                            text = "★ %.1f".format(rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    val year: Int? = card.year
                    if (year != null) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    val episodeCount: Int? = card.episodeCount
                    if (episodeCount != null) {
                        Text(
                            text = "$episodeCount eps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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