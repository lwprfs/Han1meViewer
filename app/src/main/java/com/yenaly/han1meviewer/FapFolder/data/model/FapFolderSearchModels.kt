package com.yenaly.han1meviewer.FapFolder.data.model

enum class FapFolderSearchTab(val param: String) {
    POSTS("posts"),
    USERS("users"),
    COMMUNITIES("communities");

    companion object {
        fun fromParam(value: String?): FapFolderSearchTab =
            entries.firstOrNull { it.param.equals(value, ignoreCase = true) } ?: POSTS
    }
}

data class FapFolderSearchResult(
    val query: String,
    val isHashtag: Boolean,
    val tab: FapFolderSearchTab,
    val items: List<FapFolderFeedItem>,
    val page: Int,
    val hasMore: Boolean,
    val empty: Boolean,
    val feedGet: String?,
    val feedFilter: String?,
    val feedId: String?,
    val feedHashtag: String?,
    val feedSearch: String?,
)

data class FapFolderSearchContext(
    val rawQuery: String,
    val isHashtag: Boolean,
    val tab: FapFolderSearchTab,
    val requestUrl: String,
    val feedGet: String?,
    val feedFilter: String?,
    val feedHashtag: String?,
    val feedSearch: String?,
)

sealed interface FapFolderSortOption {
    val label: String
    val value: String

    data class BuiltIn(override val label: String, override val value: String) : FapFolderSortOption
}

data class FapFolderLoadMoreRequest(
    val get: String,
    val offset: Int,
    val filter: String? = null,
    val id: String? = null,
    val type: String? = null,
    val uid: String? = null,
    val premfeed: Int = 0,
    val time: Int = -1,
    val search: String? = null,
    val hashtag: String? = null,
)

data class FapFolderLoadMoreResponse(
    val dataHtml: String?,
    val append: Boolean,
    val hasCallback: Boolean,
    val rawBody: String,
)

sealed interface FapFolderLoadState<out T> {
    data class Success<T>(val info: T) : FapFolderLoadState<T>
    data object Loading : FapFolderLoadState<Nothing>
    data object NoMoreData : FapFolderLoadState<Nothing>
    data class Error(val throwable: Throwable) : FapFolderLoadState<Nothing>
}

data class FapFolderAlbumPage(
    val albumId: String,
    val title: String?,
    val ownerName: String?,
    val ownerUrl: String?,
    val photos: List<FapFolderMedia.Image>,
    val hasMore: Boolean,
    val nextOffset: Int,
)

data class FapFolderGroupMembersPage(
    val groupId: Long?,
    val members: List<FapFolderUserRow>,
    val hasMore: Boolean,
    val nextOffset: Int,
)

data class FapFolderCommentsPage(
    val postId: Long,
    val comments: List<FapFolderComment>,
    val hasMore: Boolean,
    val nextOffset: Int,
    val activeFilter: String?,
)
