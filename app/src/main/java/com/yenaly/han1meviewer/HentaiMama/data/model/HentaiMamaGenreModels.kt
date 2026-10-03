package com.yenaly.han1meviewer.HentaiMama.data.model

import com.yenaly.han1meviewer.logic.model.HanimeInfo

enum class GenreLayout(val param: String) {
    DETAILS("details"),
    BARE("bare");

    companion object {
        fun from(param: String?): GenreLayout =
            entries.firstOrNull { it.param.equals(param, ignoreCase = true) } ?: DETAILS
    }
}

data class SortOption(
    val label: String,
    val param: String,
    val url: String,
)

data class GenreHeader(
    val genreName: String,
    val parentLabel: String?,
    val parentUrl: String?,
    val sortOptions: List<SortOption>,
    val activeSort: String?,
    val totalPages: Int,
    val currentPage: Int,
    val templateUrl: String,
)

data class FavoriteBtn(
    val postId: Int,
    val nonce: String,
    val count: Int,
    val isUserFavorited: Boolean,
)

data class GenreSeries(
    val slug: String,
    val url: String,
    val title: String,
    val altTitle: String?,
    val posterSmall: String?,
    val posterMid: String?,
    val posterFull: String,
    val altText: String,
    val rating: Double?,
    val favorites: Int?,
    val favoritePostId: Int?,
    val favoriteNonce: String?,
    val studios: List<String>,
    val studioUrls: List<String>,
    val year: Int?,
    val viewsRaw: String,
    val views: Long?,
    val episodeCount: Int?,
    val synopsis: String?,
    val genres: List<String>,
    val genreSlugs: List<String>,
) {
    val displayTitle: String get() = title.ifBlank { altTitle.orEmpty() }
}

data class GenrePaginator(
    val current: Int,
    val total: Int,
    val templateUrl: String?,
    val nextUrl: String?,
    val lastUrl: String?,
    val prevUrl: String?,
)

data class GenrePage(
    val slug: String,
    val header: GenreHeader,
    val series: List<GenreSeries>,
    val paginator: GenrePaginator?,
    val sort: String?,
    val layout: GenreLayout,
)

fun GenreSeries.toHanimeInfo(): HanimeInfo = HanimeInfo(
    title = title.ifBlank { altTitle.orEmpty() },
    coverUrl = posterFull.ifBlank { posterMid.orEmpty() },
    videoCode = slug,
    itemType = HanimeInfo.NORMAL,
)