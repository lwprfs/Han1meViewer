package com.yenaly.han1meviewer.FapFolder.ui.post

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderPostCard
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SiteVideoActionBar
import com.yenaly.han1meviewer.util.DownloadLayout
import com.yenaly.han1meviewer.util.FapOwnerKind
import com.yenaly.han1meviewer.util.SiteImageSaver

internal fun FapFolderPost.allImages(): List<FapFolderMedia.Image> {
    val out = LinkedHashMap<String, FapFolderMedia.Image>()
    media.forEach { m ->
        when (m) {
            is FapFolderMedia.Image -> out.putIfAbsent(m.fullUrl, m)
            is FapFolderMedia.Gallery -> m.photos.forEach { out.putIfAbsent(it.fullUrl, it) }
            else -> Unit
        }
    }
    return out.values.toList()
}

internal fun LazyListScope.fapFolderImagePostItems(
    post: FapFolderPost,
    slugOrId: String,
    uiState: FapFolderPostDetailUiState,
    viewModel: FapFolderPostDetailViewModel,
    onOpenImages: (List<FapFolderMedia.Image>, Int) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGroupPhotos: (String) -> Unit,
    onOpenProfilePhotos: (String) -> Unit,
    context: Context,
) {
    val images = post.allImages()
    val ownerKind = if (post.author.isGroupAuthor) FapOwnerKind.GROUP else FapOwnerKind.PROFILE
    val ownerName = post.author.displayName.ifBlank {
        post.author.groupSlug ?: post.author.username
    }
    val title = post.bodyPlain.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)
        ?: post.author.displayName.take(40)

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
            onClickImage = { clicked, all ->
                val idx = all.indexOfFirst { it.fullUrl == clicked.fullUrl }
                onOpenImages(all, idx.coerceAtLeast(0))
            },
            onClickPollOption = { poll, option -> viewModel.votePoll(poll, option.id) },
            onClickLike = { viewModel.react(positive = true) },
            onClickDislike = { viewModel.react(positive = false) },
            onClickSave = { viewModel.toggleSave() },
        )
    }

    item(key = "site_actions") {
        SiteVideoActionBar(
            site = SiteType.FAPFOLDER,
            videoCode = post.postId.toString(),
            title = title,
            coverUrl = coverUrlOf(post),
            webUrl = FapFolderUrlUtils.absoluteUrl(post.permalink ?: post.slug ?: slugOrId),
            onDownloadClick = {
                if (images.isEmpty()) {
                    GlobalToasts.show(
                        "This post has no images to download.",
                        level = GlobalToasts.ToastLevel.WARNING,
                    )
                } else {
                    GlobalToasts.show(
                        "Downloading ${images.size} image(s)…",
                        level = GlobalToasts.ToastLevel.INFO,
                    )
                    SiteImageSaver.saveBatchInBackground(
                        context = context,
                        urls = images.map { it.fullUrl },
                        segments = DownloadLayout.fapFolderPhotoSegments(
                            ownerKind, ownerName, "Post ${post.postId}",
                        ),
                        baseName = "photo",
                    ) { result ->
                        when (result) {
                            is SiteImageSaver.SaveResult.Error -> GlobalToasts.show(
                                result.message, level = GlobalToasts.ToastLevel.ERROR,
                            )
                            is SiteImageSaver.SaveResult.Saved -> GlobalToasts.show(
                                "Saved ${result.count} image(s)" +
                                        (if (result.skipped > 0) ", ${result.skipped} already existed" else "") +
                                        (if (result.failed > 0) ", ${result.failed} failed" else ""),
                                level = if (result.failed > 0) GlobalToasts.ToastLevel.WARNING
                                else GlobalToasts.ToastLevel.SUCCESS,
                            )
                        }
                    }
                }
            },
        )
    }

    val albumId = post.firstGallery?.albumId
    val groupSlug = post.author.groupSlug
    val username = post.author.username
    val canOpen = !albumId.isNullOrBlank() || !groupSlug.isNullOrBlank() || !username.isNullOrBlank()
    if (canOpen) {
        item(key = "album_link") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        when {
                            !albumId.isNullOrBlank() -> onOpenAlbum(albumId)
                            !groupSlug.isNullOrBlank() -> onOpenGroupPhotos(groupSlug)
                            !username.isNullOrBlank() -> onOpenProfilePhotos(username)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open album", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    fapFolderCommentItems(post, uiState, viewModel, onOpenProfile)
}
