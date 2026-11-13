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
    val engagement: XHamsterEngagement? = null,
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

data class XHamsterPhoto(
    val id: Long,
    val thumbUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val commentsCount: Int,
    val pageUrl: String,
    val alt: String,
)

data class XHamsterGalleryInfo(
    val id: Long,
    val title: String,
    val description: String?,
    val pageUrl: String,
    val photosTotal: Long?,
    val views: Long?,
    val authorName: String?,
    val authorPath: String?,
    val createdSec: Long?,
    val tags: List<XHamsterLink>,
)

data class XHamsterGalleryPage(
    val info: XHamsterGalleryInfo,
    val photos: List<XHamsterPhoto>,
    val nextUrl: String?,
)

data class XHamsterPhotoDetail(
    val photoId: Long,
    val likes: Long?,
    val dislikes: Long?,
    val views: Long?,
    val commentsTotal: Long,
    val comments: XHamsterCommentsPage,
)

data class XHamsterShort(
    val id: Long,
    val title: String,
    val pageUrl: String,
    val videoUrl: String,
    val coverUrl: String,
    val views: Long?,
)

/** Extra info for a short video, fetched from `moments-by-id-list` (real stream + rating). */
data class XHamsterShortMeta(
    val streamUrl: String?,
    val likes: Long,
    val liked: Boolean,
)

data class XHamsterShortsPage(
    val items: List<XHamsterShort>,
    val nextUrl: String?,
)

data class XHamsterProfileTab(
    val title: String,
    val path: String,
    val count: Long?,
    val selected: Boolean = false,
)

data class XHamsterEntityProfile(
    val path: String,
    val kind: Kind,
    val entityId: Long,
    val name: String,
    val avatarUrl: String?,
    val country: String?,
    val verified: Boolean,
    val isPublic: Boolean,
    val isMine: Boolean,
    val videoCount: Long?,
    val shortsCount: Long?,
    val photosCount: Long?,
    val viewsCount: Long?,
    val favoritesCount: Long?,
    val subscribers: Long?,
    val subscribed: Boolean,
    val subscriptionModel: String?,
    val aliases: String?,
    val channels: List<XHamsterChannel>,
    val tabs: List<XHamsterProfileTab>,
    val videos: List<com.yenaly.han1meviewer.logic.model.HanimeInfo>,
    val shorts: List<XHamsterShort>,
    /** Channel only: tag filters and sort options shown above the video list. */
    val tags: List<XHamsterOption> = emptyList(),
    val sorts: List<XHamsterOption> = emptyList(),
) {
    enum class Kind { CREATOR, CHANNEL, USER }
}

/** The signed-in user's relationship to a video (rating + favourite state). */
data class XHamsterEngagement(
    val entityId: Long,
    val userId: Long?,
    val watchId: String?,
    val likes: Long?,
    val dislikes: Long?,
    /** 0 = no rating, [RATING_LIKED] / [RATING_DISLIKED] otherwise. */
    val ratingState: Int,
    val isFavorite: Boolean,
    val collectionIds: List<String>,
) {
    companion object {
        const val RATING_NONE = 0
        const val RATING_LIKED = 1
        const val RATING_DISLIKED = -1
    }
}

/** A favourites playlist ("collection"), including the built-in Default and Watch Later lists. */
data class XHamsterCollection(
    val id: String,
    val title: String,
    val path: String,
    val quantity: Long?,
    val isWatchLater: Boolean,
    val isDefault: Boolean,
    val visibility: String?,
)

/** One entry of the subscriptions feed (videos published by channels / creators the user follows). */
data class XHamsterFeedItem(
    val authorName: String,
    val authorPath: String?,
    val video: com.yenaly.han1meviewer.logic.model.HanimeInfo,
)

enum class XHamsterListKind { WATCH_HISTORY, FAVORITES, WATCH_LATER, LIKED, COLLECTION }

sealed interface XHamsterCommentResult {
    object Posted : XHamsterCommentResult
    object LoginRequired : XHamsterCommentResult
    /** The site wants a captcha for this comment; it cannot be solved in-app. */
    object CaptchaRequired : XHamsterCommentResult
    data class Rejected(val message: String?) : XHamsterCommentResult
}
