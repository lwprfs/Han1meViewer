package com.yenaly.han1meviewer.FapFolder.data.model

import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount

val FapFolderComment.authorName: String
    get() = author.displayName.ifBlank { author.username.orEmpty() }

val FapFolderComment.authorAvatar: String?
    get() = author.avatarUrl

val FapFolderComment.authorUrl: String?
    get() = author.profileUrl

val FapFolderComment.authorUid: Long?
    get() = author.uid

val FapFolderComment.isGold: Boolean
    get() = author.isGold

val FapFolderComment.isAnonymous: Boolean
    get() = author.isAnonymous

val FapFolderComment.timestampIso: String
    get() = postedAtIso

val FapFolderComment.liked: Boolean
    get() = (reactions.byKind?.get("like") ?: 0) > 0

val FapFolderComment.reactionCount: Int
    get() = reactions.total

val FapFolderComment.attachmentUrls: List<String>
    get() = attachments.map { it.fullUrl }

val FapFolderComment.isEmptyBody: Boolean
    get() = bodyPlain.isBlank() && bodyHtml.isBlank() && attachments.isEmpty()

fun FapFolderComment.withLiked(liked: Boolean): FapFolderComment {
    if (liked == this.liked) return this
    val current = reactions.byKind?.toMutableMap() ?: mutableMapOf()
    val currentLike = current["like"] ?: 0
    val delta = if (liked) 1 else -1
    current["like"] = (currentLike + delta).coerceAtLeast(0)
    val newTotal = (reactions.total + delta).coerceAtLeast(0)
    return copy(reactions = ReactionCount(newTotal, current))
}
