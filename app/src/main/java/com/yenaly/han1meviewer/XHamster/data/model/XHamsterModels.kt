package com.yenaly.han1meviewer.XHamster.data.model

import com.yenaly.han1meviewer.logic.model.HanimeInfo

data class XHamsterListPage(
    val items: List<HanimeInfo>,

    val nextUrl: String?,
)

data class XHamsterUser(
    val id: Long,
    val name: String,
    val profileUrl: String,
)

data class XHamsterLink(val title: String, val url: String)

data class XHamsterStream(
    val label: String,
    val url: String,

    val height: Int = 0,
)

data class XHamsterDetail(
    val code: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val pageUrl: String,
    val durationSec: Long,
    val views: Long?,
    val likes: Long?,
    val dislikes: Long?,
    val dateAgo: String?,
    val uploader: String?,
    val maxResolution: String?,
    val tags: List<XHamsterLink>,

    val hlsTemplate: String?,

    val hlsDirect: String?,

    val mp4Fallback: String?,
    val related: List<com.yenaly.han1meviewer.logic.model.HanimeInfo>,
    val uploaderInfo: XHamsterUploader? = null,
    val commentsTotal: Long? = null,
    val comments: List<XHamsterComment> = emptyList(),
    val entityId: Long? = null,
)

data class XHamsterUploader(
    val name: String,
    val path: String,
    val avatarUrl: String?,
    val subscribers: Long?,
    val verified: Boolean,
    val isChannel: Boolean,
    val subscriptionId: Long? = null,
    val subscriptionModel: String? = null,
    val subscribed: Boolean = false,
)

data class XHamsterComment(
    val id: String,
    val author: String,
    val authorPath: String?,
    val avatarUrl: String?,
    val text: String,
    val date: String?,
    val likes: Long?,
    val replies: List<XHamsterComment> = emptyList(),
    val replyToId: String? = null,
    val replyToName: String? = null,
    val isOwner: Boolean = false,
)

data class XHamsterCommentsPage(
    val items: List<XHamsterComment>,
    val total: Long,
    val page: Int,
    val maxPages: Int,
    val writeDenied: Boolean,
) {
    val hasMore: Boolean get() = page < maxPages
}

data class XHamsterPage<T>(
    val items: List<T>,
    val nextUrl: String?,
)

data class XHamsterChannel(
    val name: String,
    val path: String,
    val logoUrl: String,
    val videoCount: Long?,
    val subscribers: Long?,
)

data class XHamsterCreator(
    val name: String,
    val path: String,
    val avatarUrl: String,
    val videoCount: Long?,
    val viewsCount: Long?,
    val verified: Boolean,
)

data class XHamsterGallery(
    val id: String,
    val title: String,
    val url: String,
    val coverUrl: String,
    val photosCount: Long?,
    val viewsText: String?,
    val authorName: String?,
    val authorPath: String?,
)

data class XHamsterOption(val name: String, val path: String, val selected: Boolean = false)

data class XHamsterProfile(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val isMine: Boolean,
    val isPublic: Boolean,
    val videosTotal: Long?,
    val momentsTotal: Long?,
    val friendsTotal: Long?,
    val favoritesTotal: Long?,
    val channels: List<XHamsterChannel>,
    val videos: List<com.yenaly.han1meviewer.logic.model.HanimeInfo>,
)

data class XHamsterDashboard(
    val balance: String?,
    val pending: String?,
    val premiumRevenue: String?,
    val adShareEnabled: Boolean,
    val canUpload: Boolean,
    val menu: List<XHamsterLink>,
)

data class XHamsterAccountSettings(
    val displayName: String?,
    val email: String?,
    val emailVerified: Boolean,
    val gender: String?,
    val country: String?,
    val relation: String?,
    val profileVisibility: String?,
    val privacy: List<XHamsterLink>,
)
