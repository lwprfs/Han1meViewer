package com.yenaly.han1meviewer.FapFolder.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPollOption
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.launch

private const val REACTION_LIKE = "like"
private const val REACTION_ANGRY = "angry"
private const val ACTION_REACT_POST = "react_post"
private const val ACTION_UNREACT_POST = "unreact_post"
private const val ACTION_SAVE_POST = "save_post"
private const val ACTION_UNSAVE_POST = "unsave_post"

@Composable
fun FapFolderInteractivePostCard(
    post: FapFolderPost,
    modifier: Modifier = Modifier,
    onClickPost: () -> Unit = {},
    onClickHashtag: (String) -> Unit = {},
    onClickAuthor: (FapFolderAuthor) -> Unit = {},
    onClickVideo: (FapFolderMedia.Video) -> Unit = {},
    onClickImage: (FapFolderMedia.Image, List<FapFolderMedia.Image>) -> Unit = { _, _ -> },
    onClickPoll: (FapFolderMedia.Poll) -> Unit = {},
    onClickPollOption: (FapFolderMedia.Poll, FapFolderPollOption) -> Unit = { _, _ -> },
    onClickLink: (FapFolderMedia.LinkPreview) -> Unit = {},
    onLongClickPost: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var localPost by remember(post.postId, post.reactions) { mutableStateOf(post) }
    var saved by remember(post.postId) { mutableStateOf(false) }
    var reactionPending by remember(post.postId) { mutableStateOf(false) }
    var savePending by remember(post.postId) { mutableStateOf(false) }

    LaunchedEffect(post.postId, post.reactions) {
        if (!reactionPending) localPost = post
    }

    fun react(positive: Boolean) {
        if (reactionPending) return
        val targetReaction = if (positive) REACTION_LIKE else REACTION_ANGRY
        val current = localPost.reactions
        val currentByKind = current.byKind.orEmpty()
        val currentLike = currentByKind[REACTION_LIKE] ?: 0
        val currentAngry = currentByKind[REACTION_ANGRY] ?: 0
        val alreadyLiked = currentLike > 0
        val alreadyAngry = currentAngry > 0

        val newLike = when {
            targetReaction == REACTION_LIKE && alreadyLiked -> currentLike - 1
            targetReaction == REACTION_LIKE -> currentLike + 1
            else -> 0
        }
        val newAngry = when {
            targetReaction == REACTION_ANGRY && alreadyAngry -> currentAngry - 1
            targetReaction == REACTION_ANGRY -> currentAngry + 1
            else -> 0
        }
        val removedCount =
            (if (targetReaction == REACTION_LIKE && alreadyLiked) 1 else 0) +
                    (if (targetReaction == REACTION_ANGRY && alreadyAngry) 1 else 0)
        val addedCount =
            (if (targetReaction == REACTION_LIKE && !alreadyLiked) 1 else 0) +
                    (if (targetReaction == REACTION_ANGRY && !alreadyAngry) 1 else 0)
        val newTotal = (current.total + addedCount - removedCount).coerceAtLeast(0)
        val optimistic = ReactionCount(
            total = newTotal,
            byKind = buildMap {
                if (newLike > 0) put(REACTION_LIKE, newLike)
                if (newAngry > 0) put(REACTION_ANGRY, newAngry)
            }.takeIf { it.isNotEmpty() },
        )

        localPost = localPost.copy(reactions = optimistic)
        reactionPending = true
        val wasReacted = if (targetReaction == REACTION_LIKE) alreadyLiked else alreadyAngry
        val action = if (wasReacted) ACTION_UNREACT_POST else ACTION_REACT_POST
        val reaction = if (wasReacted) null else targetReaction

        scope.launch {
            FapFolderNetworkRepo.reactToPost(
                postId = localPost.postId,
                action = action,
                reaction = reaction,
            ).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> reactionPending = false
                    is WebsiteState.Error -> {
                        localPost = localPost.copy(reactions = post.reactions)
                        reactionPending = false
                    }
                }
            }
        }
    }

    fun toggleSave() {
        if (savePending) return
        val willSave = !saved
        saved = willSave
        savePending = true
        val action = if (willSave) ACTION_SAVE_POST else ACTION_UNSAVE_POST
        scope.launch {
            FapFolderNetworkRepo.reactToPost(
                postId = localPost.postId,
                action = action,
            ).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> savePending = false
                    is WebsiteState.Error -> {
                        saved = !willSave
                        savePending = false
                    }
                }
            }
        }
    }

    FapFolderPostCard(
        post = localPost,
        modifier = modifier,
        isSaved = saved,
        isReactionPending = reactionPending,
        onClickPost = onClickPost,
        onClickHashtag = onClickHashtag,
        onClickAuthor = onClickAuthor,
        onClickVideo = onClickVideo,
        onClickImage = onClickImage,
        onClickPoll = onClickPoll,
        onClickPollOption = onClickPollOption,
        onClickLink = onClickLink,
        onClickLike = { react(positive = true) },
        onClickDislike = { react(positive = false) },
        onClickSave = { toggleSave() },
        onLongClickPost = onLongClickPost,
    )
}
