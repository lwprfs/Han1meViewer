package com.yenaly.han1meviewer.FapFolder.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object FapFolderHomeRoute

@Serializable
data class FapFolderPostRoute(
    val slugOrId: String,
    val autoPlay: Boolean = false,
)

@Serializable
data class FapFolderSearchRoute(
    val query: String? = null,
    val isHashtag: Boolean = false,
    val tab: String = "posts",
)

@Serializable
data class FapFolderHashtagRoute(
    val tag: String,
)

@Serializable
data class FapFolderGroupRoute(
    val slug: String,
    val tab: String = "posts",
)

@Serializable
data class FapFolderProfileRoute(
    val username: String,
    val tab: String = "posts",
)

@Serializable
data class FapFolderAlbumRoute(
    val albumId: String,
)

@Serializable
object FapFolderHistoryRoute

@Serializable
object FapFolderSearchHistoryRoute

@Serializable
object FapFolderSettingsRoute

@Serializable
object FapFolderCookiesRoute

@Serializable
object FapFolderLoginRoute

@Serializable
object FapFolderGateRoute

@Serializable
object FapFolderFollowedCommunitiesRoute
