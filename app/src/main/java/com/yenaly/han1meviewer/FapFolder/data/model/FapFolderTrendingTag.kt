package com.yenaly.han1meviewer.FapFolder.data.model

data class FapFolderTrendingTag(
    val tag: String,
    val postCount: Int?,
    val url: String,
)

data class FapFolderSuggestedCommunity(
    val groupId: Long?,
    val slug: String,
    val name: String,
    val avatarUrl: String?,
    val followerCount: Int?,
    val isMember: Boolean,
)

sealed interface FapFolderAutocompleteSuggestion {
    val label: String
    val href: String?

    data class User(
        override val label: String,
        override val href: String?,
        val username: String,
        val avatarUrl: String?,
        val bio: String?,
    ) : FapFolderAutocompleteSuggestion

    data class Community(
        override val label: String,
        override val href: String?,
        val slug: String,
        val avatarUrl: String?,
        val followerCount: Int?,
    ) : FapFolderAutocompleteSuggestion

    data class Hashtag(
        override val label: String,
        override val href: String?,
        val tag: String,
    ) : FapFolderAutocompleteSuggestion

    data class SearchFor(
        override val label: String,
        override val href: String?,
        val query: String,
        val isHashtag: Boolean,
    ) : FapFolderAutocompleteSuggestion
}
