package com.yenaly.han1meviewer.FapFolder.data.model

import kotlinx.serialization.Serializable

enum class PostVariant {
    TEXT,
    SINGLE_IMAGE,
    GALLERY,
    VIDEO,
    EMBED,
    LINK,
    POLL,
    FUNDING,
    PRODUCT,
    JOB,
    CUSTOM_FIELDS,
}

enum class GroupVisibility {
    PUBLIC,
    PRIVATE,
    SECRET,
    UNKNOWN,
}

enum class GroupMembership {
    NOT_MEMBER,
    PENDING,
    MEMBER,
    ADMIN,
    OWNER,
    UNKNOWN,
}

enum class ProfileTab {
    POSTS,
    PHOTOS,
    VIDEOS,
    MEMBERS,
    FRIENDS,
    ABOUT,
    UNKNOWN,
}

sealed interface FapFolderFeedItem {
    val stableKey: String

    data class Post(val post: FapFolderPost) : FapFolderFeedItem {
        override val stableKey: String get() = "post_${post.postId}"
    }

    data class User(val user: FapFolderUserRow) : FapFolderFeedItem {
        override val stableKey: String get() = "user_${user.uid ?: user.username}"
    }

    data class Community(val group: FapFolderGroupRow) : FapFolderFeedItem {
        override val stableKey: String get() = "group_${group.groupId ?: group.slug}"
    }
}

data class FapFolderAuthor(
    val uid: Long?,
    val username: String?,
    val groupSlug: String?,
    val displayName: String,
    val profileUrl: String?,
    val avatarUrl: String?,
    val isGold: Boolean,
    val isAnonymous: Boolean,
) {
    val isGroupAuthor: Boolean get() = !groupSlug.isNullOrBlank()

    val stableKey: String
        get() = when {
            isGroupAuthor -> "group:$groupSlug"
            !username.isNullOrBlank() -> "user:$username"
            uid != null -> "uid:$uid"
            else -> "anon:$displayName"
        }
}

data class FapFolderPost(
    val postId: Long,
    val slug: String?,
    val permalink: String?,
    val author: FapFolderAuthor,
    val uploaderUsername: String?,
    val uploaderUrl: String?,
    val uploaderIsGold: Boolean,
    val action: String?,
    val postedAtIso: String,
    val bodyHtml: String,
    val bodyPlain: String,
    val hashtags: List<String>,
    val media: List<FapFolderMedia>,
    val variants: Set<PostVariant>,
    val isPremium: Boolean,
    val reactions: ReactionCount,
    val commentCount: Int,
    val shareCount: Int,
    val viewCount: Int?,
) {
    val stableKey: String get() = "post_$postId"

    val hasMedia: Boolean get() = media.isNotEmpty()

    val primaryImage: FapFolderMedia.Image?
        get() = media.filterIsInstance<FapFolderMedia.Image>().firstOrNull()

    val primaryVideo: FapFolderMedia.Video?
        get() = media.filterIsInstance<FapFolderMedia.Video>().firstOrNull()

    val firstGallery: FapFolderMedia.Gallery?
        get() = media.filterIsInstance<FapFolderMedia.Gallery>().firstOrNull()
}

sealed interface FapFolderMedia {

    data class Image(
        val photoId: Long?,
        val fullUrl: String,
        val previewUrl: String?,
        val context: String?,
        val alt: String?,
    ) : FapFolderMedia

    data class Gallery(
        val photos: List<Image>,
        val totalCount: Int?,
        val albumId: String?,
    ) : FapFolderMedia

    data class Video(
        val videoId: Long?,
       val onPlayId: Long?,
        val playUrl: String,
        val posterUrl: String?,
        val durationSeconds: Int?,
        val aspectRatio: Double?,
        val adEligible: Boolean,
        val fileExtension: String?,
        val expirationEpoch: Long? = null,
        val signature: String? = null,
    ) : FapFolderMedia {
        fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
            expirationEpoch != null && expirationEpoch <= nowEpochSeconds + 30L

        val safeAspectRatio: Double
            get() {
                val raw = aspectRatio ?: return 16.0 / 9.0
                if (raw.isNaN() || raw.isInfinite() || raw <= 0.0) return 16.0 / 9.0
                return raw.coerceIn(0.3, 4.0)
            }
    }

    data class Embed(
        val iframeUrl: String,
        val provider: String?,
    ) : FapFolderMedia

    data class LinkPreview(
        val title: String,
        val description: String?,
        val thumbnailUrl: String?,
        val host: String,
        val targetUrl: String?,
    ) : FapFolderMedia

    data class Poll(
        val totalVotes: Int,
        val options: List<FapFolderPollOption>,
        val hasVoted: Boolean,
    ) : FapFolderMedia

    data class FileAttachment(
        val url: String,
        val name: String?,
        val sizeLabel: String?,
    ) : FapFolderMedia

    data class Funding(
        val title: String,
        val description: String?,
        val current: Long?,
        val goal: Long?,
    ) : FapFolderMedia

    data class Product(
        val name: String,
        val description: String?,
        val price: String?,
        val imageUrl: String?,
    ) : FapFolderMedia

    data class Job(
        val title: String,
        val description: String?,
        val location: String?,
    ) : FapFolderMedia
}

data class FapFolderPollOption(
    val id: Long,
    val label: String,
    val votes: Int,
    val percent: Float,
    val isSelected: Boolean,
)

data class ReactionCount(
    val total: Int,
    val byKind: Map<String, Int>? = null,
) {
    companion object {
        val EMPTY = ReactionCount(0, null)
    }
}

data class FapFolderComment(
    val commentId: Long,
    val author: FapFolderAuthor,
    val bodyHtml: String,
    val bodyPlain: String,
    val postedAtIso: String,
    val reactions: ReactionCount,
    val replyCount: Int,
    val replies: List<FapFolderComment>,
    val parentId: Long?,
    val attachments: List<FapFolderMedia.Image>,
    val repliesPartial: Boolean = false,
) {
    val stableKey: String get() = "comment_$commentId"
}

data class FapFolderGroup(
    val groupId: Long?,
    val slug: String,
    val displayName: String,
    val bio: String?,
    val bioHtml: String?,
    val bioTags: List<String>,
    val bannerUrl: String?,
    val avatarUrl: String?,
    val memberCount: Int?,
    val postCount: Int?,
    val photoCount: Int?,
    val videoCount: Int?,
    val followerCount: Int?,
    val visibility: GroupVisibility,
    val membership: GroupMembership,
    val category: FapFolderGroupCategory?,
    val tags: List<String>,
    val socialLinks: Map<String, String>,
    val galleryPreview: List<FapFolderMedia.Image>,
    val activeTab: String?,
)

data class FapFolderGroupCategory(
    val id: Int?,
    val name: String,
)

data class FapFolderProfileTabStream(
    val get: String? = null,
    val filter: String? = null,
    val id: String? = null,
    val type: String? = null,
    val uid: String? = null,
    val search: String? = null,
    val hashtag: String? = null,
    val offset: Int = 1,
    val done: Boolean = false,
)

data class FapFolderUserProfile(
    val uid: Long?,
    val username: String,
    val displayName: String,
    val bio: String?,
    val bioHtml: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val followerCount: Int?,
    val followingCount: Int?,
    val postCount: Int?,
    val photoCount: Int?,
    val videoCount: Int?,
    val joinedLabel: String?,
    val isFollowed: Boolean,
    val isFriend: Boolean,
    val isFriendPending: Boolean,
    val isSelf: Boolean,
    val activeTab: String?,
    val followUid: Long? = null,
    val tabUrls: Map<String, String> = emptyMap(),
    val tabStreams: Map<String, FapFolderProfileTabStream> = emptyMap(),
    val isGroup: Boolean = false,
    val groupSlug: String? = null,
)

data class FapFolderGroupRow(
    val groupId: Long?,
    val slug: String,
    val name: String,
    val avatarUrl: String?,
    val followerCount: Int?,
    val isMember: Boolean,
    val visibility: GroupVisibility,
)

data class FapFolderUserRow(
    val uid: Long?,
    val username: String,
    val name: String,
    val avatarUrl: String?,
    val bio: String?,
    val profileUrl: String?,
    val isFollowed: Boolean,
    val isFriend: Boolean,
    val isFriendPending: Boolean,
    val isGold: Boolean,
)

data class FapFolderLightboxResponse(
    val currentPhotoId: Long,
    val currentImageUrl: String?,
    val post: FapFolderPost?,
    val comments: List<FapFolderComment>,
    val nextPhotoId: Long?,
    val prevPhotoId: Long?,
    val nextPhotoSource: String?,
    val prevPhotoSource: String?,
)

@Serializable
data class FapFolderSearchHistoryEntry(
    val query: String = "",
    val isHashtag: Boolean = false,
    val tab: String = "posts",
    val createdAt: Long = System.currentTimeMillis(),
)

data class FapFolderSiteStats(
    val totalMembers: Int?,
    val online: Int?,
    val guests: Int?,
)

data class FapFolderViewer(
    val userId: Long?,
    val username: String?,
    val avatarUrl: String?,
    val points: String?,
    val wallet: String?,
    val isLoggedIn: Boolean,
)

data class FapFolderSessionContext(
    val loggedIn: Boolean,
    val sitePath: String,
    val ajaxPath: String,
    val uploadsPath: String,
    val secret: String?,
    val viewer: FapFolderViewer,
    val siteStats: FapFolderSiteStats?,
    val currentPage: String?,
) {
    companion object {
        fun empty(): FapFolderSessionContext = FapFolderSessionContext(
            loggedIn = false,
            sitePath = "https://fapfolder.club",
            ajaxPath = "https://fapfolder.club/includes/ajax/",
            uploadsPath = "https://fap.onl/uploads",
            secret = null,
            viewer = FapFolderViewer(null, null, null, null, null, false),
            siteStats = null,
            currentPage = null,
        )
    }
}

data class FapFolderFeedPage(
    val items: List<FapFolderFeedItem>,
    val session: FapFolderSessionContext?,
    val nextOffset: Int,
    val hasMore: Boolean,
    val feedGet: String,
    val feedFilter: String?,
    val feedId: String?,
    val feedType: String?,
    val feedUid: String?,
)

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

data class FapFolderHomeExtras(
    val trending: List<FapFolderTrendingTag> = emptyList(),
    val suggested: List<FapFolderSuggestedCommunity> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis(),
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
