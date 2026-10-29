package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderCommentParser {

    const val FILTER_MOST_RECENT = "post_comments"
    const val FILTER_TOP = "post_comments_top"
    const val FILTER_ALL = "post_comments_all"

    fun parseComments(
        root: Element?,
        baseUrl: String,
        uploadsBase: String? = null,
    ): List<FapFolderComment> {
        if (root == null) return emptyList()
        val list = root.select("ul.js_comments > li, ul.comments-list > li")
            .ifEmpty { root.children() }
        return list.mapNotNull { li ->
            val comment = li.selectFirst("div.comment[data-id]") ?: li
            parseComment(comment, baseUrl, uploadsBase)
        }
    }

    fun parseComment(
        commentEl: Element,
        baseUrl: String,
        uploadsBase: String? = null,
        parentId: Long? = null,
    ): FapFolderComment? {
        val commentId: Long = commentEl.attr("data-id").toLongOrNull()
            ?: commentEl.selectFirst("div.comment[data-id]")?.attr("data-id")?.toLongOrNull()
            ?: return null

        val author = parseAuthor(commentEl, baseUrl)

        val bodyHtml: String = commentEl.selectFirst("div.comment-text")?.html().orEmpty()
        val bodyPlain: String = (
                commentEl.selectFirst("div.comment-text-plain")?.text()
                    ?: commentEl.selectFirst("div.comment-text")?.text()
                    ?: ""
                ).let(FapFolderHtmlUtils::cleanText)

        val postedAt: String = commentEl.selectFirst("small.js_moment[data-time]")
            ?.attr("data-time")
            ?.let(FapFolderHtmlUtils::parseDataTimeIso)
            ?: commentEl.selectFirst("small.js_moment")?.text()?.trim().orEmpty()

        val reactions = parseCommentReactions(commentEl)

        val repliesContainer = commentEl.selectFirst("div.comment-replies ul.js_replies")
        val replies = repliesContainer?.select("li")
            ?.mapNotNull { replyLi ->
                val replyEl = replyLi.selectFirst("div.comment[data-id]") ?: replyLi
                parseComment(replyEl, baseUrl, uploadsBase, parentId = commentId)
            }
            .orEmpty()

        val replyCount = FapFolderHtmlUtils.parseLeadingInt(
            commentEl.selectFirst(".comment-replies-toggle, .js_replies-toggle")?.text()
        ) ?: replies.size

        val attachments = parseCommentAttachments(commentEl, baseUrl, uploadsBase)

        return FapFolderComment(
            commentId = commentId,
            author = author,
            bodyHtml = bodyHtml,
            bodyPlain = bodyPlain,
            postedAtIso = postedAt,
            reactions = reactions,
            replyCount = replyCount,
            replies = replies,
            parentId = parentId,
            attachments = attachments,
            repliesPartial = replyCount > replies.size,
        )
    }

    private fun parseAuthor(
        commentEl: Element,
        baseUrl: String,
    ): FapFolderAuthor {
        val popover = commentEl.selectFirst(".js_user-popover[data-uid]")
        val uid = popover?.attr("data-uid")?.toLongOrNull()

        val authorAnchor = commentEl.selectFirst(".comment-author a[href]")
            ?: commentEl.selectFirst("a.comment-author[href]")

        val href = authorAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: authorAnchor?.attr("href")?.takeIf { it.isNotBlank() }
                ?.let(FapFolderUrlUtils::absoluteUrl)

        val username: String? = href?.let(FapFolderUrlUtils::extractUsername)
            ?: authorAnchor?.text()?.trim()?.takeIf { it.isNotBlank() }

        val displayName: String = authorAnchor?.text()?.trim()
            ?: username
            ?: "Anonymous"

        val groupSlug: String? = href?.let(FapFolderUrlUtils::extractGroupSlug)

        val avatarUrl: String? = run {
            val avatarEl = commentEl.selectFirst(".comment-avatar-picture") ?: return@run null
            val style = avatarEl.attr("style")
            FapFolderHtmlUtils.extractBackgroundImageUrl(style)
                ?.let(FapFolderUrlUtils::normalizeMediaUrl)
                ?: avatarEl.selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: avatarEl.attr("data-image").takeIf { it.isNotBlank() }
        }

        val isGold: Boolean = commentEl.selectFirst("i.gold-badge, i.fa-star, .gold-badge") != null

        val isAnonymous: Boolean = avatarUrl?.contains("deleted_user", ignoreCase = true) == true ||
                (username.isNullOrBlank() && uid == null)

        return FapFolderAuthor(
            uid = uid,
            username = username,
            groupSlug = groupSlug,
            displayName = displayName,
            profileUrl = href,
            avatarUrl = avatarUrl,
            isGold = isGold,
            isAnonymous = isAnonymous,
        )
    }

    private fun parseCommentReactions(commentEl: Element): ReactionCount {
        val actions = commentEl.selectFirst("ul.comment-actions")
            ?: commentEl.selectFirst(".comment-actions")
        val reactionItem = actions?.selectFirst(".reactions-stats-item span")
            ?: actions?.selectFirst("div.reactions-stats span")
        val total = FapFolderHtmlUtils.parseLeadingInt(reactionItem?.text()) ?: 0
        return ReactionCount(total, null)
    }

    private fun parseCommentAttachments(
        commentEl: Element,
        baseUrl: String,
        uploadsBase: String?,
    ): List<FapFolderMedia.Image> {
        val container = commentEl.selectFirst(".comment-attachments")
            ?: return emptyList()
        return container.select("li.item").mapNotNull { item ->
            val imgSrc = item.selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: item.selectFirst("img[src]")?.attr("src")?.takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            FapFolderMedia.Image(
                photoId = null,
                fullUrl = imgSrc,
                previewUrl = null,
                context = "comment",
                alt = null,
            )
        }
    }

    fun parseCommentsFromHtml(
        html: String?,
        baseUrl: String,
        uploadsBase: String? = null,
    ): List<FapFolderComment> {
        if (html.isNullOrBlank()) return emptyList()
        val doc: Document = try {
            Jsoup.parseBodyFragment(html, baseUrl)
        } catch (_: Exception) {
            return emptyList()
        }
        return parseComments(doc.body(), baseUrl, uploadsBase)
    }

    fun parseCommentFilterLabel(filterValue: String?): String {
        return when (filterValue) {
            FILTER_TOP -> "Top Comments"
            FILTER_ALL -> "All Comments"
            FILTER_MOST_RECENT -> "Most Recent"
            else -> "Most Recent"
        }
    }

    fun defaultFilterValue(): String = FILTER_MOST_RECENT
}