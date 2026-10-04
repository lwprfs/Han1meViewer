package com.yenaly.han1meviewer.HentaiMama.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SeriesCard(
    val url: String,
    val slug: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val thumbSmall: String? = null,
    val thumbFull: String = "",
    val posterAlt: String = "",
    val rating: Double? = null,
    val favorites: Int? = null,
    val postId: Int? = null,
    val nonce: String? = null,
    val studios: List<String> = emptyList(),
    val studioUrls: List<String> = emptyList(),
    val year: Int? = null,
    val viewsRaw: String? = null,
    val views: Long? = null,
    val episodeCount: Int? = null,
    val description: String? = null,
    val hasLongDescription: Boolean = false,
    val genres: List<String> = emptyList(),
    val genreSlugs: List<String> = emptyList(),
)

data class PageInfo(
    val current: Int = 1,
    val total: Int = 1,
    val templateUrl: String? = null,
    val nextUrl: String? = null,
    val lastUrl: String? = null,
    val prevUrl: String? = null,
)

data class PageResult<T>(
    val page: Int,
    val total: Int,
    val items: List<T>,
    val pageInfo: PageInfo? = null,
)

data class GenreBreadcrumb(
    val homeUrl: String = "",
    val parentUrl: String? = null,
    val parentName: String? = null,
    val currentName: String = "",
)

data class FilterOptions(
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val studios: List<String> = emptyList(),
)

data class AzLink(
    val label: String,
    val url: String,
)
