package com.yenaly.han1meviewer.logic.model

data class VideoComments(
    val videoComment: MutableList<VideoComment>,
    val currentUserId: String? = null,
    val csrfToken: String? = null,
) {
    data class VideoComment(

        val avatar: String,

        val username: String,

        val date: String,

        val content: String,

        var thumbUp: Int? = null,

        val isChildComment: Boolean,

        val hasMoreReplies: Boolean = false,

        val replyCount: Int? = 0,

        val id: String? = "-1",

        val post: POST,

        val redirectUrl: String? = null,
        val reportableId: String? = null,
        val reportableType: String? = null
    ) {

        val replyTargetIdOrNull get() = post.foreignId ?: id
        val stableKey get() = replyTargetIdOrNull
            ?: reportableId
            ?: buildString {
                append(username)
                append('|')
                append(date)
                append('|')
                append(content.hashCode())
            }
        val realLikesCount get() = thumbUp
        fun incLikesCount(cancel: Boolean = false): VideoComment {
            return thumbUp?.let {
                copy(
                    thumbUp = it + if (cancel) -1 else 1,
                    post = post.copy(
                        likeCommentStatus = !cancel,
                        unlikeCommentStatus = false
                    )
                )
            } ?: this
        }

        fun decLikesCount(cancel: Boolean = false): VideoComment {
            return thumbUp?.let {
                copy(
                    thumbUp = it - if (cancel) -1 else 1,
                    post = post.copy(
                        likeCommentStatus = false,
                        unlikeCommentStatus = !cancel
                    )
                )
            } ?: this
        }

        data class POST(

            val foreignId: String? = null,

            var isPositive: Boolean = false,

            val likeUserId: String? = null,
            var commentLikesCount: Int? = null,
            var commentLikesSum: Int? = null,

            var likeCommentStatus: Boolean = false,

            var unlikeCommentStatus: Boolean = false,
        )
    }
}

data class VideoCommentArgs(

    val commentPosition: Int,

    val isPositive: Boolean,
    val comment: VideoComments.VideoComment,
)
