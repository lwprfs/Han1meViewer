package com.yenaly.han1meviewer.FapFolder.ui.post

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.FapFolder.ui.components.HashtagRow
import com.yenaly.han1meviewer.FapFolder.ui.components.PostHeaderRow
import com.yenaly.han1meviewer.FapFolder.ui.components.PostInteractionRow
import com.yenaly.han1meviewer.FapFolder.ui.components.PostMediaSection
import com.yenaly.han1meviewer.FapFolder.ui.video.FapFolderVideoPlayerScreen
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.ExpandableRichText
import com.yenaly.han1meviewer.ui.component.SiteVideoActionBar
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.util.DownloadLayout
import com.yenaly.han1meviewer.util.FapOwnerKind
import kotlinx.coroutines.flow.first

/**
 * Details list shown under the (collapsible) player, Hanime1 style, on ONE page:
 * profile -> expandable details -> other media -> reactions / actions -> tags -> comments.
 */
internal fun LazyListScope.fapFolderVideoDetailItems(
    post: FapFolderPost,
    video: FapFolderMedia.Video,
    slugOrId: String,
    uiState: FapFolderPostDetailUiState,
    viewModel: FapFolderPostDetailViewModel,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    item(key = "profile") {
        PostHeaderRow(
            author = post.author,
            uploaderUsername = post.uploaderUsername,
            uploaderIsGold = post.uploaderIsGold,
            action = post.action,
            postedAtIso = post.postedAtIso,
            isPremium = post.isPremium,
            onClickAuthor = {
                val groupSlug = post.author.groupSlug
                val username = post.author.username
                when {
                    !groupSlug.isNullOrBlank() -> onOpenGroup(groupSlug)
                    !username.isNullOrBlank() -> onOpenProfile(username)
                }
            },
        )
    }

    val bodyText = post.bodyPlain.ifBlank {
        post.bodyHtml.replace(Regex("<[^>]+>"), "").trim()
    }
    if (bodyText.isNotBlank()) {
        item(key = "details") {
            ExpandableRichText(
                text = bodyText,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }

    val otherMedia = post.media.filter { it !is FapFolderMedia.Video }
    if (otherMedia.isNotEmpty()) {
        item(key = "other_media") {
            PostMediaSection(
                media = otherMedia,
                isPollPending = uiState.pollVotePendingOptionId != null,
                onClickVideo = { },
                onClickImage = { _, _ -> },
                onClickPoll = { },
                onClickPollOption = { poll, option -> viewModel.votePoll(poll, option.id) },
                onClickLink = { },
                videoContent = { },
            )
        }
    }

    item(key = "reactions") {
        PostInteractionRow(
            reactions = post.reactions,
            comments = post.commentCount,
            shares = post.shareCount,
            views = post.viewCount,
            saved = uiState.saved,
            reactionPending = uiState.reactionsPending,
            onClickLike = { viewModel.react(positive = true) },
            onClickDislike = { viewModel.react(positive = false) },
            onClickSave = { viewModel.toggleSave() },
        )
    }

    item(key = "site_actions") {
        val ownerKind = if (post.author.isGroupAuthor) FapOwnerKind.GROUP else FapOwnerKind.PROFILE
        val ownerName = post.author.displayName.ifBlank {
            post.author.groupSlug ?: post.author.username
        }
        val folder = DownloadLayout.fapFolderVideoCode(ownerKind, ownerName, post.postId)
            .substringBeforeLast('/')
        SiteVideoActionBar(
            site = SiteType.FAPFOLDER,
            videoCode = post.postId.toString(),
            title = post.bodyPlain.lineSequence().firstOrNull { it.isNotBlank() }
                ?.take(80)
                ?: post.author.displayName.take(40),
            coverUrl = coverUrlOf(post),
            webUrl = FapFolderUrlUtils.absoluteUrl(post.permalink ?: post.slug ?: slugOrId),
            downloadQuality = "default",
            downloadFolder = folder,
            resolveDownloadUrl = { resolveFapFolderVideoUrl(video) },
        )
    }

    if (post.hashtags.isNotEmpty()) {
        item(key = "tags") {
            HashtagRow(
                hashtags = post.hashtags,
                onClickHashtag = onOpenHashtag,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    fapFolderCommentItems(post, uiState, viewModel, onOpenProfile)
}

private suspend fun resolveFapFolderVideoUrl(video: FapFolderMedia.Video): String? {
    if (video.playUrl.isBlank()) return null
    if (!video.playUrl.contains("/video/play")) return video.playUrl.replace("&amp;", "&")
    val state = FapFolderNetworkRepo.resolveVideoPlayUrl(video.playUrl)
        .first { it !is WebsiteState.Loading }
    return (state as? WebsiteState.Success)?.info
}

@Composable
internal fun InlineVideoSlot(
    post: FapFolderPost,
    video: FapFolderMedia.Video,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onExpired: () -> Unit,
) {
    if (playing) {
        FapFolderVideoPlayerScreen(
            video = video,
            title = post.bodyPlain.take(60),
            onDismiss = { onPlayingChange(false) },
            postId = post.postId,
            coverUrl = coverUrlOf(post),
            postPermalinkForReferer = post.permalink,
            onExpired = onExpired,
            inline = true,
            autoStart = true,
            minInlineAspect = INLINE_MIN_ASPECT,
        )
        return
    }
    val aspect = ((video.aspectRatio ?: (16.0 / 9.0)).toFloat()
        .takeIf { it.isFinite() && it > 0f } ?: (16f / 9f))
        .coerceAtLeast(INLINE_MIN_ASPECT)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black)
            .clickable { onPlayingChange(true) },
        contentAlignment = Alignment.Center,
    ) {
        val poster = video.posterUrl
        if (!poster.isNullOrBlank()) {
            RetryableImage(
                model = poster,
                contentDescription = stringResource(R.string.video),
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.h_chan_loading),
                error = painterResource(R.drawable.h_chan_load_failed),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.site_play),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

private const val INLINE_MIN_ASPECT = 1.25f

internal fun coverUrlOf(post: FapFolderPost?): String? {
    if (post == null) return null
    return post.primaryImage?.fullUrl
        ?: post.primaryVideo?.posterUrl
        ?: post.firstGallery?.photos?.firstOrNull()?.fullUrl
}
