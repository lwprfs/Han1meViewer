package com.yenaly.han1meviewer.FapFolder.ui.post

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPollOption
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount
import com.yenaly.han1meviewer.FapFolder.data.model.withLiked
import com.yenaly.han1meviewer.FapFolder.data.model.liked
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderCommentParser
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FapFolderPostDetailUiState(
    val slugOrId: String = "",
    val post: FapFolderPost? = null,
    val postLoading: Boolean = true,
    val postError: String? = null,
    val comments: List<FapFolderComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentsError: String? = null,
    val commentsHasMore: Boolean = false,
    val commentsNextPage: Int = 1,
    val commentsActiveFilter: String = FapFolderCommentParser.defaultFilterValue(),
    val sessionExpired: Boolean = false,
    val reactionsPending: Boolean = false,
    val savePending: Boolean = false,
    val saved: Boolean = false,
    val pollVotePendingOptionId: Long? = null,
    val commentPostPending: Boolean = false,
    val commentPostError: String? = null,
    val expandedReplies: Set<Long> = emptySet(),
    val repliesLoadingFor: Long? = null,
    val refreshToken: Long = 0L,
)

class FapFolderPostDetailViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FapFolderPostDetailVM"
        private const val REACTION_LIKE = "like"
        private const val REACTION_ANGRY = "angry"
        private const val ACTION_REACT_POST = "react_post"
        private const val ACTION_UNREACT_POST = "unreact_post"
        private const val ACTION_SAVE_POST = "save_post"
        private const val ACTION_UNSAVE_POST = "unsave_post"
        private const val ACTION_ADD_VOTE = "add_vote"
        private const val ACTION_CHANGE_VOTE = "change_vote"
    }

    private val _uiState = MutableStateFlow(FapFolderPostDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var postJob: Job? = null
    private var commentsJob: Job? = null
    private var reactionJob: Job? = null
    private var saveJob: Job? = null
    private var voteJob: Job? = null
    private var commentJob: Job? = null
    private var repliesJob: Job? = null

    private var pendingAutoPlayVideoId: Long? = null

    fun load(slugOrId: String) {
        if (slugOrId.isBlank()) return
        if (_uiState.value.slugOrId == slugOrId && _uiState.value.post != null) return

        _uiState.update {
            it.copy(
                slugOrId = slugOrId,
                post = null,
                postLoading = true,
                postError = null,
                comments = emptyList(),
                commentsHasMore = false,
                commentsNextPage = 1,
                commentsError = null,
                sessionExpired = false,
            )
        }

        postJob?.cancel()
        commentsJob?.cancel()

        postJob = viewModelScope.launch {
            FapFolderNetworkRepo.getPostDetail(slugOrId).collect { state ->
                when (state) {
                    is VideoLoadingState.Loading -> Unit
                    is VideoLoadingState.Success -> {
                        _uiState.update {
                            it.copy(
                                post = state.info,
                                postLoading = false,
                                postError = null,
                            )
                        }
                        persistInitialHistory(state.info)
                        loadComments(reset = true)
                    }
                    is VideoLoadingState.Error -> {
                        val msg = state.throwable.message
                        _uiState.update {
                            it.copy(
                                postLoading = false,
                                postError = msg ?: "Failed to load post.",
                                sessionExpired = msg?.contains("session", true) == true,
                            )
                        }
                    }
                    is VideoLoadingState.NoContent -> {
                        _uiState.update {
                            it.copy(
                                postLoading = false,
                                postError = "Post not found.",
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        val currentSlug = _uiState.value.slugOrId
        if (currentSlug.isNotBlank()) {
            _uiState.update {
                it.copy(
                    slugOrId = "",
                    refreshToken = it.refreshToken + 1,
                )
            }
            load(currentSlug)
        }
    }

    fun requestAutoPlayAfterRefresh(videoId: Long) {
        pendingAutoPlayVideoId = videoId
    }

    fun pendingAutoPlayVideoId(): Long? = pendingAutoPlayVideoId

    fun clearPendingAutoPlay() {
        pendingAutoPlayVideoId = null
    }

    fun loadMoreComments() {
        val state = _uiState.value
        if (state.commentsLoading || !state.commentsHasMore) return
        loadComments(reset = false)
    }

    fun changeCommentsFilter(filter: String) {
        if (_uiState.value.commentsActiveFilter == filter) return
        _uiState.update {
            it.copy(
                commentsActiveFilter = filter,
                comments = emptyList(),
                commentsNextPage = 1,
                commentsHasMore = false,
            )
        }
        loadComments(reset = true)
    }

    fun react(positive: Boolean) {
        val state = _uiState.value
        val post = state.post ?: return
        if (state.reactionsPending) return

        val targetReaction = if (positive) REACTION_LIKE else REACTION_ANGRY
        val currentByKind = post.reactions.byKind.orEmpty()
        val currentLike = currentByKind[REACTION_LIKE] ?: 0
        val currentAngry = currentByKind[REACTION_ANGRY] ?: 0
        val currentTotal = post.reactions.total

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

        val newTotal = (currentTotal + addedCount - removedCount).coerceAtLeast(0)

        val optimistic = ReactionCount(
            total = newTotal,
            byKind = buildMap {
                if (newLike > 0) put(REACTION_LIKE, newLike)
                if (newAngry > 0) put(REACTION_ANGRY, newAngry)
            }.takeIf { it.isNotEmpty() },
        )

        _uiState.update {
            it.copy(
                reactionsPending = true,
                post = it.post?.copy(reactions = optimistic),
            )
        }

        reactionJob?.cancel()
        reactionJob = viewModelScope.launch {
            val wasReacted = if (targetReaction == REACTION_LIKE) alreadyLiked else alreadyAngry
            val action = if (wasReacted) ACTION_UNREACT_POST else ACTION_REACT_POST
            val reaction = if (wasReacted) null else targetReaction

            FapFolderNetworkRepo.reactToPost(
                postId = post.postId,
                action = action,
                reaction = reaction,
            ).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> _uiState.update {
                        it.copy(reactionsPending = false)
                    }
                    is WebsiteState.Error -> {
                        Log.w(TAG, "react failed: ${result.throwable.message}")
                        _uiState.update {
                            it.copy(
                                reactionsPending = false,
                                post = it.post?.copy(reactions = post.reactions),
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleSave() {
        val state = _uiState.value
        val post = state.post ?: return
        if (state.savePending) return

        val willSave = !state.saved
        _uiState.update { it.copy(saved = willSave, savePending = true) }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val action = if (willSave) ACTION_SAVE_POST else ACTION_UNSAVE_POST
            FapFolderNetworkRepo.reactToPost(postId = post.postId, action = action)
                .collect { result ->
                    when (result) {
                        is WebsiteState.Loading -> Unit
                        is WebsiteState.Success -> _uiState.update {
                            it.copy(savePending = false)
                        }
                        is WebsiteState.Error -> {
                            Log.w(TAG, "toggleSave failed: ${result.throwable.message}")
                            _uiState.update {
                                it.copy(savePending = false, saved = state.saved)
                            }
                        }
                    }
                }
        }
    }

    fun votePoll(poll: FapFolderMedia.Poll, optionId: Long) {
        val state = _uiState.value
        val post = state.post ?: return
        if (state.pollVotePendingOptionId != null) return

        val previouslySelected = poll.options.firstOrNull { it.isSelected }?.id
        val action = if (previouslySelected == null) ACTION_ADD_VOTE else ACTION_CHANGE_VOTE

        val optimisticOptions = poll.options.map { option ->
            when {
                option.id == optionId -> option.copy(isSelected = true)
                option.isSelected -> option.copy(isSelected = false)
                else -> option
            }
        }
        val deltaVotes = if (previouslySelected == null) 1 else 0
        val optimisticPoll = poll.copy(
            totalVotes = poll.totalVotes + deltaVotes,
            options = optimisticOptions,
            hasVoted = true,
        )

        val optimisticMedia = post.media.map { item ->
            if (item is FapFolderMedia.Poll) optimisticPoll else item
        }

        _uiState.update {
            it.copy(
                pollVotePendingOptionId = optionId,
                post = it.post?.copy(media = optimisticMedia),
            )
        }

        voteJob?.cancel()
        voteJob = viewModelScope.launch {
            FapFolderNetworkRepo.reactToPost(
                postId = post.postId,
                action = action,
                reaction = null,
            ).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> _uiState.update {
                        it.copy(pollVotePendingOptionId = null)
                    }
                    is WebsiteState.Error -> {
                        Log.w(TAG, "votePoll failed: ${result.throwable.message}")
                        _uiState.update {
                            it.copy(
                                pollVotePendingOptionId = null,
                                post = it.post?.copy(media = post.media),
                            )
                        }
                    }
                }
            }
        }
    }

    fun postComment(message: String) {
        val state = _uiState.value
        val post = state.post ?: return
        val trimmed = message.trim()
        if (trimmed.isEmpty() || state.commentPostPending) return

        _uiState.update { it.copy(commentPostPending = true, commentPostError = null) }

        commentJob?.cancel()
        commentJob = viewModelScope.launch {
            FapFolderNetworkRepo.postComment(post.postId, trimmed).collect { result ->
                when (result) {
                    is WebsiteState.Loading -> Unit
                    is WebsiteState.Success -> {
                        val comment = result.info
                        _uiState.update { current ->
                            current.copy(
                                commentPostPending = false,
                                commentPostError = null,
                                comments = listOf(comment) + current.comments,
                                post = current.post?.copy(
                                    commentCount = (current.post.commentCount + 1)
                                        .coerceAtLeast(0),
                                ),
                            )
                        }
                    }
                    is WebsiteState.Error -> {
                        Log.w(TAG, "postComment failed: ${result.throwable.message}")
                        _uiState.update {
                            it.copy(
                                commentPostPending = false,
                                commentPostError = result.throwable.message
                                    ?: "Failed to post comment.",
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleReplies(commentId: Long) {
        val state = _uiState.value
        val expanded = state.expandedReplies
        if (expanded.contains(commentId)) {
            _uiState.update { it.copy(expandedReplies = expanded - commentId) }
            return
        }
        val comment = state.comments.firstOrNull { it.commentId == commentId } ?: return

        if (comment.replies.isNotEmpty()) {
            _uiState.update { it.copy(expandedReplies = expanded + commentId) }
            return
        }

        _uiState.update {
            it.copy(
                expandedReplies = expanded + commentId,
                repliesLoadingFor = commentId,
            )
        }

        repliesJob?.cancel()
        repliesJob = viewModelScope.launch {
            val postId = state.post?.postId ?: return@launch
            FapFolderNetworkRepo.loadMoreReplies(
                postId = postId,
                commentId = commentId,
                offset = 1,
            ).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit
                    is PageLoadingState.Success -> {
                        val fetched = pageState.info
                        _uiState.update { current ->
                            val merged = current.comments.map { c ->
                                if (c.commentId == commentId) {
                                    c.copy(replies = fetched.take(3))
                                } else c
                            }
                            current.copy(
                                comments = merged,
                                repliesLoadingFor = null,
                            )
                        }
                    }
                    is PageLoadingState.NoMoreData -> _uiState.update {
                        it.copy(repliesLoadingFor = null)
                    }
                    is PageLoadingState.Error -> {
                        Log.w(TAG, "loadReplies failed: ${pageState.throwable.message}")
                        _uiState.update { it.copy(repliesLoadingFor = null) }
                    }
                }
            }
        }
    }

    fun toggleLiked(commentId: Long) {
        val state = _uiState.value
        val comment = state.comments.firstOrNull { it.commentId == commentId } ?: return
        val wasLiked = comment.liked
        val optimistic = comment.withLiked(!wasLiked)
        _uiState.update { current ->
            current.copy(
                comments = current.comments.map {
                    if (it.commentId == commentId) optimistic else it
                }
            )
        }
        viewModelScope.launch {
            FapFolderNetworkRepo.reactToComment(
                commentId = commentId,
                action = if (wasLiked) ACTION_UNREACT_POST else ACTION_REACT_POST,
                reaction = if (wasLiked) null else REACTION_LIKE,
            ).collect { result ->
                if (result is WebsiteState.Error) {
                    Log.w(TAG, "toggleLiked failed: ${result.throwable.message}")
                    _uiState.update { current ->
                        current.copy(
                            comments = current.comments.map {
                                if (it.commentId == commentId) comment else it
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadComments(reset: Boolean) {
        val state = _uiState.value
        val post = state.post ?: return
        val page = if (reset) 1 else state.commentsNextPage

        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            _uiState.update { it.copy(commentsLoading = true, commentsError = null) }

            FapFolderNetworkRepo.getComments(
                postId = post.postId,
                filter = state.commentsActiveFilter,
                page = page,
            ).collect { pageState ->
                when (pageState) {
                    is PageLoadingState.Loading -> Unit

                    is PageLoadingState.Success -> {
                        val newComments = pageState.info.comments
                        _uiState.update { prev ->
                            val merged = if (reset) newComments
                            else (prev.comments + newComments)
                                .distinctBy { it.commentId }
                            prev.copy(
                                comments = merged,
                                commentsLoading = false,
                                commentsHasMore = newComments.isNotEmpty(),
                                commentsNextPage = page + 1,
                                commentsError = null,
                            )
                        }
                    }

                    is PageLoadingState.NoMoreData -> {
                        _uiState.update {
                            it.copy(
                                commentsLoading = false,
                                commentsHasMore = false,
                            )
                        }
                    }

                    is PageLoadingState.Error -> {
                        _uiState.update {
                            it.copy(
                                commentsLoading = false,
                                commentsError = pageState.throwable.message,
                                sessionExpired = pageState.throwable.message?.contains(
                                    "session", ignoreCase = true
                                ) == true,
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun persistInitialHistory(post: FapFolderPost) {
        val cover = post.primaryImage?.fullUrl
            ?: post.primaryVideo?.posterUrl
            ?: post.firstGallery?.photos?.firstOrNull()?.fullUrl
            ?: ""
        val isVideo = post.primaryVideo != null
        runCatching {
            FapFolderHistoryRepo.saveProgress(
                postId = post.postId,
                title = post.bodyPlain.take(120).ifBlank { "FapFolder post ${post.postId}" },
                coverUrl = cover,
                authorName = post.author.displayName,
                postSlug = post.slug,
                isVideo = isVideo,
                positionMs = 0L,
                durationMs = 0L,
                isPlaying = false,
            )
        }.onFailure { Log.w(TAG, "persistInitialHistory failed: ${it.message}") }
    }

    override fun onCleared() {
        super.onCleared()
        postJob?.cancel()
        commentsJob?.cancel()
        reactionJob?.cancel()
        saveJob?.cancel()
        voteJob?.cancel()
        commentJob?.cancel()
        repliesJob?.cancel()
        Log.d(TAG, "onCleared")
    }
}
