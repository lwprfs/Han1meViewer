package com.yenaly.han1meviewer.OnAir.data.model

import com.yenaly.han1meviewer.logic.model.HanimeInfo

data class OnAirHomePage(
    val videos: List<OnAirVideoCard>,
    val totalVideos: Int?,
    val totalPictures: Int?,
    val totalMembers: Int?,
    val announcement: OnAirAnnouncement?,
    val pageInfo: OnAirPageInfo?,
    val currentUsername: String? = null,
)

data class OnAirVideoCard(
    val videoId: String,
    val title: String,
    val thumbUrl: String,
    val previewUrl: String?,
    val duration: String?,
    val uploaderName: String?,
    val viewsRaw: String?,
    val views: Long?,
) {
    fun toHanimeInfo(): HanimeInfo = HanimeInfo(
        title = title,
        coverUrl = thumbUrl,
        videoCode = videoId,
        duration = duration,
        views = viewsRaw,
        uploadTime = null,
        currentArtist = uploaderName,
        itemType = HanimeInfo.NORMAL,
    )
}

data class OnAirPageInfo(
    val current: Int,
    val total: Int,
    val nextUrl: String?,
    val prevUrl: String?,
    val lastUrl: String? = null,
)

data class OnAirAnnouncement(
    val title: String,
    val url: String,
)

data class OnAirAnnouncementEntry(
    val title: String,
    val author: String?,
    val body: String,
    val isPinned: Boolean,
)

data class OnAirChatMessage(
    val id: String,
    val author: String?,
    val authorUrl: String?,
    val timestampLabel: String?,
    val timestampRaw: String?,
    val body: String,
)

data class OnAirChatPage(
    val messages: List<OnAirChatMessage>,
    val csrfToken: String,
    val latestId: String?,
    val onLastPage: Boolean,
    val formError: String?,
)

data class OnAirTag(
    val name: String,
    val url: String,
    val count: Int?,
)

data class OnAirPictureCard(
    val pictureId: String,
    val title: String,
    val thumbUrl: String,
    val uploaderName: String?,
    val viewsRaw: String?,
    val views: Long?,
)

data class StreamResult(
    val quality: String,
    val url: String,
    val mediaType: String?,
    val isHls: Boolean,
)

data class OnAirVideoDetail(
    val videoId: String,
    val title: String,
    val uploaderName: String?,
    val uploaderUrl: String?,
    val views: Long?,
    val viewsRaw: String?,
    val duration: String?,
    val fileSize: String?,
    val favoriteCount: Int?,
    val isFavorited: Boolean,
    val tags: List<OnAirVideoTag>,
    val qualityLinks: List<OnAirQualityLink>,
    val qualityLinksByQuality: Map<String, String>,
    val streamUrlByQuality: Map<String, String>,
    val currentQuality: String,
    val currentMediaType: String?,
    val recommendedVideos: List<OnAirVideoCard>,
)

data class OnAirVideoTag(
    val tagId: String,
    val name: String,
    val score: Int,
)

data class OnAirQualityLink(
    val quality: String,
    val label: String,
    val url: String,
    val sizeLabel: String?,
    val isActive: Boolean,
)

data class OnAirSearchResult(
    val query: String?,
    val field: String,
    val pageInfo: OnAirPageInfo?,
    val cards: List<OnAirVideoCard>,
    val headingText: String?,
)

data class OnAirUserProfile(
    val username: String,
    val displayName: String,
    val bio: String?,
    val avatarUrl: String?,
    val followerCount: Int?,
    val followingCount: Int?,
    val videoCount: Int?,
    val pictureCount: Int?,
    val isFollowing: Boolean,
    val isCurrentUser: Boolean = false,
    val videos: List<OnAirVideoCard>,
    val pictures: List<OnAirPictureCard>,
    val pageInfo: OnAirPageInfo?,
)
