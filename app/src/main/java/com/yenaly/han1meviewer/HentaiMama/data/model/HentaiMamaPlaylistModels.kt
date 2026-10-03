package com.yenaly.han1meviewer.HentaiMama.data.model

data class PlaylistsHeader(
    val title: String,
    val homeUrl: String,
    val sortOptions: List<PlaylistSortOption>,
    val activeSort: String?,
    val totalPages: Int,
    val currentPage: Int,
    val templateUrl: String,
    val nextUrl: String?,
    val lastUrl: String?,
)

data class PlaylistSortOption(
    val label: String,
    val url: String,
    val isCurrent: Boolean,
)

data class PlaylistCard(
    val id: String,
    val url: String,
    val playUrl: String?,
    val visibility: String,
    val title: String,
    val ownerName: String,
    val ownerUrl: String,
    val videoCount: Int?,
    val viewsRaw: String,
    val views: Long?,
    val likes: Int?,
    val thumbSmall: String?,
    val thumbMid: String?,
    val thumbFull: String?,
    val likeButtonId: String?,
    val liked: Boolean,
    val position: Int,
)

data class PlaylistHero(
    val id: String,
    val kind: String,
    val visibility: String,
    val title: String,
    val ownerName: String,
    val ownerUrl: String,
    val ownerAvatar: String?,
    val videoCount: Int?,
    val viewsRaw: String,
    val views: Long?,
    val updatedText: String?,
    val createdText: String?,
    val playAllUrl: String?,
    val shuffleUrl: String?,
    val likes: Int?,
    val liked: Boolean,
    val backUrl: String,
)

data class PlaylistEpisode(
    val postId: Int,
    val position: Int,
    val url: String,
    val title: String,
    val studio: String?,
    val viewsRaw: String,
    val views: Long?,
    val thumbSmall: String?,
    val thumbFull: String?,
)

data class PlaylistPageInfo(
    val total: Int,
    val current: Int,
    val templateUrl: String,
    val nextUrl: String?,
    val lastUrl: String?,
)

data class PlaylistsIndexPage(
    val header: PlaylistsHeader,
    val cards: List<PlaylistCard>,
    val paginator: PlaylistPageInfo?,
)

data class PlaylistDetailPage(
    val hero: PlaylistHero,
    val episodes: List<PlaylistEpisode>,
    val paginator: PlaylistPageInfo?,
)