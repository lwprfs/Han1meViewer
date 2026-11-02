package com.yenaly.han1meviewer.FapFolder.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderPostCard
import com.yenaly.han1meviewer.FapFolder.ui.video.FapFolderVideoPlayerScreen
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.SiteVideoActionBar
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.util.DownloadLayout
import com.yenaly.han1meviewer.util.FapOwnerKind
import kotlinx.coroutines.flow.first

/**
 * Video post screen content: the post and its video live on one screen. Tapping play starts the
 * video right inside the post (all the player controls are kept), no separate video screen.
 */
internal fun LazyListScope.fapFolderVideoPostItems(
    post: FapFolderPost,
    video: FapFolderMedia.Video,
    slugOrId: String,
    uiState: FapFolderPostDetailUiState,
    viewModel: FapFolderPostDetailViewModel,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onVideoExpired: (FapFolderMedia.Video) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    item(key = "post_card") {
        FapFolderPostCard(
            post = post,
            isSaved = uiState.saved,
            isReactionPending = uiState.reactionsPending,
            isPollPending = uiState.pollVotePendingOptionId != null,
            onClickPost = { },
            onClickHashtag = onOpenHashtag,
            onClickAuthor = { author ->
                val groupSlug = author.groupSlug
                val username = author.username
                when {
                    !groupSlug.isNullOrBlank() -> onOpenGroup(groupSlug)
                    !username.isNullOrBlank() -> onOpenProfile(username)
                }
            },
            onClickPollOption = { poll, option -> viewModel.votePoll(poll, option.id) },
            onClickLike = { viewModel.react(positive = true) },
            onClickDislike = { viewModel.react(positive = false) },
            onClickSave = { viewModel.toggleSave() },
            videoContent = { v ->
                if (v == video) {
                    InlineVideoSlot(
                        post = post,
                        video = v,
                        playing = playing,
                        onPlayingChange = onPlayingChange,
                        onExpired = { onVideoExpired(v) },
                    )
                }
            },
        )
    }

    item(key = "site_actions") {
        val ownerKind = if (post.author.isGroupAuthor) FapOwnerKind.GROUP else FapOwnerKind.PROFILE
        val ownerName = post.author.displayName.ifBlank {
            post.author.groupSlug ?: post.author.username
        }
        val folder = DownloadLayout.fapFolderVideoCode(ownerKind, ownerName, post.postId)
            .substringBeforeLast('/') // Groups/<name>/Videos
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
private fun InlineVideoSlot(
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
        )
        return
    }
    val aspect = (video.aspectRatio ?: (16.0 / 9.0)).toFloat()
        .takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
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
                contentDescription = "Video",
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
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

internal fun coverUrlOf(post: FapFolderPost?): String? {
    if (post == null) return null
    return post.primaryImage?.fullUrl
        ?: post.primaryVideo?.posterUrl
        ?: post.firstGallery?.photos?.firstOrNull()?.fullUrl
}
