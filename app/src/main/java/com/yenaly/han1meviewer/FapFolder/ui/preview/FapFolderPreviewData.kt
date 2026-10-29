package com.yenaly.han1meviewer.FapFolder.ui.preview

import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPollOption
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.PostVariant
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount

val fakeFapFolderAuthor: FapFolderAuthor = FapFolderAuthor(
    uid = 1083707L,
    username = "sample_user",
    groupSlug = null,
    displayName = "Sample User",
    profileUrl = "https://fapfolder.club/sample_user",
    avatarUrl = null,
    isGold = true,
    isAnonymous = false,
)

val fakeFapFolderPost: FapFolderPost = FapFolderPost(
    postId = 187023L,
    slug = "sample-post-slug",
    permalink = "https://fapfolder.club/posts/sample-post-slug",
    author = fakeFapFolderAuthor,
    uploaderUsername = "sample_user",
    uploaderUrl = "https://fapfolder.club/sample_user",
    uploaderIsGold = true,
    action = "added photo",
    postedAtIso = "2026-10-28 12:34:56",
    bodyHtml = "This is a sample post body used for previews.",
    bodyPlain = "This is a sample post body used for previews.",
    hashtags = listOf("sample", "preview", "onlyfans"),
    media = emptyList(),
    variants = setOf(PostVariant.TEXT),
    isPremium = false,
    reactions = ReactionCount(27, null),
    commentCount = 3,
    shareCount = 0,
    viewCount = 1523,
)

val fakeFapFolderPostWithVideo: FapFolderPost = fakeFapFolderPost.copy(
    postId = 187024L,
    media = listOf(
        FapFolderMedia.Video(
            videoId = 112235L,
            onPlayId = 112235L,
            playUrl = "https://fapfolder.club/video/play?src=FAKE&exp=0&sig=FAKE",
            posterUrl = "https://picsum.photos/seed/fapfolder/640/360",
            durationSeconds = 666,
            aspectRatio = 16.0 / 9.0,
            adEligible = false,
            fileExtension = "mp4",
        )
    ),
    variants = setOf(PostVariant.VIDEO, PostVariant.TEXT),
)

val fakeFapFolderPostWithPoll: FapFolderPost = fakeFapFolderPost.copy(
    postId = 187025L,
    media = listOf(
        FapFolderMedia.Poll(
            totalVotes = 128,
            options = listOf(
                FapFolderPollOption(id = 1L, label = "Option A", votes = 64, percent = 50f, isSelected = true),
                FapFolderPollOption(id = 2L, label = "Option B", votes = 32, percent = 25f, isSelected = false),
                FapFolderPollOption(id = 3L, label = "Option C", votes = 32, percent = 25f, isSelected = false),
            ),
            hasVoted = true,
        )
    ),
    variants = setOf(PostVariant.POLL, PostVariant.TEXT),
)

val fakeFapFolderPostWithGallery: FapFolderPost = fakeFapFolderPost.copy(
    postId = 187026L,
    media = listOf(
        FapFolderMedia.Gallery(
            photos = (1..6).map { idx ->
                FapFolderMedia.Image(
                    photoId = 700000L + idx,
                    fullUrl = "https://picsum.photos/seed/fapfolder_$idx/800/1200",
                    previewUrl = "https://picsum.photos/seed/fapfolder_${idx}_preview/400/600",
                    context = "album",
                    alt = "Photo $idx",
                )
            },
            totalCount = 12,
            albumId = "700000",
        )
    ),
    variants = setOf(PostVariant.GALLERY),
)
