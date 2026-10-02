package com.yenaly.han1meviewer.HentaiMama.data.model

data class RecentEpisode(
    val postId: Int,
    val slug: String,
    val url: String,
    val seriesTitle: String,
    val shortSeriesTitle: String,
    val episodeLabel: String,
    val episodeNumber: Int,
    val thumbSmall: String?,
    val thumbMedium: String?,
    val thumbFull: String,
    val altText: String,
    val rating: Double?,
    val releaseDate: String,
    val year: Int,
    val month: Int,
    val isRaw: Boolean,
    val isSub: Boolean,
    val languageFlags: List<String>,
    val isLazy: Boolean,
) {
    val availabilityLabel: String?
        get() = when {
            isRaw && isSub -> "RAW + SUB"
            isRaw -> "RAW"
            isSub -> "SUB"
            else -> null
        }
}

data class RecentEpisodesPageInfo(
    val current: Int,
    val total: Int,
    val nextUrl: String?,
    val lastUrl: String?,
)

data class RecentEpisodesPage(
    val pageNumber: Int,
    val pageInfo: RecentEpisodesPageInfo?,
    val items: List<RecentEpisode>,
)