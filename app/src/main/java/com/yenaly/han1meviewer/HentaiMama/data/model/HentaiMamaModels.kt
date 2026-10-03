package com.yenaly.han1meviewer.HentaiMama.data.model

import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.serialization.Serializable

data class HentaiMamaHomePage(
    val popularVideos: List<HanimeInfo>,
    val latestVideos: List<HanimeInfo>,
)

data class GenreRef(
    val name: String,
    val slug: String,
    val url: String,
)

data class StudioRef(
    val name: String,
    val slug: String,
    val url: String,
)

data class SeriesHero(
    val postId: Int,
    val slug: String,
    val title: String,
    val altTitle: String?,
    val poster: String,
    val score: Double?,
    val views: Long?,
    val episodeCount: Int?,
    val duration: String?,
    val aired: String?,
    val studios: List<StudioRef>,
    val statusChips: List<String>,
    val ratingValue: Double?,
    val voteCount: Int?,
    val genres: List<GenreRef>,
    val watchEp1Url: String?,
    val favorites: Int?,
    val favoriteNonce: String?,
    val shareCount: Int?,
    val synopsisHtml: String?,
    val synopsisText: String?,
)

data class HentaiMamaEpisode(
    val title: String,
    val url: String,
    val slug: String,
    val date: String?,
    val episodeNumber: Float?,
    val dateTimestamp: Long = 0L,
    val thumb: String = "",
    val thumbVariants: Map<Int, String> = emptyMap(),
    val rating: Double? = null,
    val synopsis: String? = null,
    val isCurrent: Boolean = false,
)

data class SimilarCard(
    val slug: String,
    val url: String,
    val name: String,
    val altTitle: String?,
    val poster: String,
    val posterSmall: String?,
    val rating: Double?,
    val synopsis: String?,
    val year: Int?,
    val views: Long?,
    val episodeCount: Int?,
)

data class Mirror(
    val label: String,
    val optionId: String,
    val isActive: Boolean,
)

data class PlayerBlock(
    val mirrors: List<Mirror>,
    val embedUrl: String?,
    val embedBase64: String?,
    val playerTitle: String?,
    val aspect: String?,
    val duration: String?,
    val qualities: List<String>,
    val speeds: List<String>,
    val mp4Url: String?,
    val mp4UrlByQuality: Map<String, String>,
)

data class EpisodeNav(
    val prevUrl: String?,
    val seriesUrl: String?,
    val nextUrl: String?,
)

data class EpisodeControls(
    val previewsOpen: Boolean,
    val playlistAdd: Boolean,
    val downloadOpen: Boolean,
    val lightToggle: Boolean,
    val reportOpen: Boolean,
    val wideToggle: Boolean,
)

data class EpisodeInfo(
    val postId: Int,
    val slug: String,
    val title: String,
    val seriesUrl: String?,
    val seriesPoster: String,
    val rating: Double?,
    val voteCount: Int?,
    val genres: List<GenreRef>,
    val views: Long?,
    val studio: String?,
    val studioUrl: String?,
    val airedOn: String?,
    val addedOn: String?,
    val synopsisShort: String?,
    val synopsisLong: String?,
    val playlistPostId: Int?,
    val playlistTitle: String?,
    val shareCount: Int?,
    val previewUrls: List<String>,
    val galleryColumns: Int,
)

data class SeriesDetailPage(
    val hero: SeriesHero,
    val episodes: List<HentaiMamaEpisode>,
    val totalEpisodes: Int,
    val similar: List<SimilarCard>,
    val cast: List<List<Pair<String, String>>>,
    val trailerUrl: String?,
)

data class EpisodeDetailPage(
    val info: EpisodeInfo,
    val player: PlayerBlock,
    val nav: EpisodeNav,
    val controls: EpisodeControls,
    val seriesSidebar: List<HentaiMamaEpisode>,
    val seriesSidebarCount: String?,
    val seriesSidebarStatus: String?,
    val similar: List<SimilarCard>,
    val comments: CommentSummary,
)

data class CommentSummary(
    val count: Int,
    val isLoggedIn: Boolean,
)

data class HentaiMamaVideoLink(
    val quality: String,
    val url: String,
    val type: String? = null,
    val label: String? = null,
)

@Serializable
data class HentaiMamaSource(
    val file: String,
    val label: String? = null,
    val type: String? = null,
)

data class HentaiMamaVideoInfo(
    val title: String,
    val coverUrl: String,
    val videoCode: String,
    val url: String,
    val description: String?,
    val genre: String?,
    val author: String?,
    val status: String?,
    val videoUrls: List<HentaiMamaVideoLink>,
    val episodes: List<HentaiMamaEpisode>,
    val relatedVideos: List<HanimeInfo>,
    val page: EpisodeDetailPage? = null,
)

data class GenreSearchResult(
    val query: String?,
    val genreSlug: String?,
    val header: GenreHeader?,
    val series: List<GenreSeries>,
    val paginator: GenrePaginator?,
    val sort: String?,
    val layout: GenreLayout,
    val isFilterMode: Boolean,
) {
    val hasMore: Boolean
        get() = paginator?.nextUrl?.isNotBlank() == true ||
                (paginator != null && paginator.current < paginator.total)

    val currentPage: Int get() = paginator?.current ?: 1
    val totalPages: Int get() = paginator?.total ?: 1
}

fun List<GenreSeries>.toHanimeInfoList(): List<HanimeInfo> = map { it.toHanimeInfo() }
