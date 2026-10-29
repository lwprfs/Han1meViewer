package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupRow
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchResult
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.GroupVisibility
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderSearchParser {

    fun parseSearchPage(
        html: String?,
        pageUrl: String,
        query: String,
        isHashtag: Boolean,
        requestedTab: FapFolderSearchTab = FapFolderSearchTab.POSTS,
    ): FapFolderSearchResult {
        if (html.isNullOrBlank()) {
            return emptyResult(query, isHashtag, requestedTab, empty = true)
        }

        val doc: Document = try {
            Jsoup.parse(html, pageUrl)
        } catch (_: Exception) {
            return emptyResult(query, isHashtag, requestedTab, empty = true)
        }

        if (isNoDataState(doc)) {
            return emptyResult(query, isHashtag, requestedTab, empty = true)
        }

        val detectedTab = detectTab(doc, requestedTab)

        val items: List<FapFolderFeedItem> = when (detectedTab) {
            FapFolderSearchTab.POSTS -> parsePostItems(doc, pageUrl)
            FapFolderSearchTab.USERS -> parseUserItems(doc, pageUrl)
            FapFolderSearchTab.COMMUNITIES -> parseCommunityItems(doc, pageUrl)
        }

        val streamEl = doc.selectFirst("div.js_posts_stream")

        val feedGet = streamEl?.attr("data-get")?.takeIf { it.isNotBlank() }
        val feedFilter = streamEl?.attr("data-filter")?.takeIf { it.isNotBlank() }
        val feedId = streamEl?.attr("data-id")?.takeIf { it.isNotBlank() }
        val feedHashtag = streamEl?.attr("data-hashtag")?.takeIf { it.isNotBlank() }
        val feedSearch = streamEl?.attr("data-search")?.takeIf { it.isNotBlank() }

        val seeMore = doc.selectFirst(".js_see-more-infinite")
        val hasMore = when {
            seeMore == null -> false
            seeMore.hasClass("done") -> false
            else -> items.isNotEmpty()
        }

        return FapFolderSearchResult(
            query = query,
            isHashtag = isHashtag,
            tab = detectedTab,
            items = items,
            page = 1,
            hasMore = hasMore,
            empty = items.isEmpty(),
            feedGet = feedGet,
            feedFilter = feedFilter,
            feedId = feedId,
            feedHashtag = feedHashtag,
            feedSearch = feedSearch,
        )
    }

    fun parseLoadMoreHtml(
        htmlFragment: String?,
        baseUrl: String,
        tab: FapFolderSearchTab,
    ): List<FapFolderFeedItem> {
        if (htmlFragment.isNullOrBlank()) return emptyList()
        val doc: Document = try {
            Jsoup.parseBodyFragment(htmlFragment, baseUrl)
        } catch (_: Exception) {
            return emptyList()
        }
        return when (tab) {
            FapFolderSearchTab.POSTS -> parsePostItems(doc, baseUrl)
            FapFolderSearchTab.USERS -> parseUserItems(doc, baseUrl)
            FapFolderSearchTab.COMMUNITIES -> parseCommunityItems(doc, baseUrl)
        }
    }

    fun parseUserRows(doc: Document, baseUrl: String): List<FapFolderUserRow> {
        return doc.select(".data-container[data-uid], .data-container[href]").mapNotNull { el ->
            if (el.containsOnlyAdContent()) return@mapNotNull null
            val uid = el.attr("data-uid").toLongOrNull()
                ?: el.selectFirst("[data-uid]")?.attr("data-uid")?.toLongOrNull()
            val href = el.absUrl("href").takeIf { it.isNotBlank() }
                ?: el.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            val username = FapFolderUrlUtils.extractUsername(href) ?: return@mapNotNull null
            val name = FapFolderHtmlUtils.cleanText(
                el.selectFirst(".data-content .name")?.text()
            ).ifBlank { username }
            val avatarUrl = el.selectFirst(".data-avatar img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val bio = FapFolderHtmlUtils.cleanText(
                el.selectFirst(".data-content .text")?.text()
            ).takeIf { it.isNotBlank() }
            val followButton = el.selectFirst("button.js_follow, button.js_unfollow")
            val isFollowed = followButton?.hasClass("js_unfollow") == true
            val isFriend = el.selectFirst("button.js_friend-remove") != null
            val isFriendPending = el.selectFirst("button.js_friend-cancel") != null
            val isGold = el.selectFirst("i.gold-badge") != null
            FapFolderUserRow(
                uid = uid,
                username = username,
                name = name,
                avatarUrl = avatarUrl,
                bio = bio,
                profileUrl = href,
                isFollowed = isFollowed,
                isFriend = isFriend,
                isFriendPending = isFriendPending,
                isGold = isGold,
            )
        }
    }

    fun parseCommunityRows(doc: Document, baseUrl: String): List<FapFolderGroupRow> {
        return doc.select(".data-container[href*='/groups/']").mapNotNull { el ->
            if (el.containsOnlyAdContent()) return@mapNotNull null
            val href = el.absUrl("href").takeIf { it.isNotBlank() }
                ?: el.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            val slug = FapFolderUrlUtils.extractGroupSlug(href) ?: return@mapNotNull null
            val name = FapFolderHtmlUtils.cleanText(
                el.selectFirst(".data-content .name")?.text()
            ).ifBlank { slug }
            val avatarUrl = el.selectFirst(".data-avatar img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val joinBtn = el.selectFirst("button.js_join-group, button.js_leave-group")
            val groupId = joinBtn?.attr("data-id")?.toLongOrNull()
                ?: el.attr("data-uid").toLongOrNull()
            val isMember = joinBtn?.hasClass("js_leave-group") == true
            val visibility = when (joinBtn?.attr("data-privacy")?.lowercase()) {
                "public" -> GroupVisibility.PUBLIC
                "private" -> GroupVisibility.PRIVATE
                "secret" -> GroupVisibility.SECRET
                else -> GroupVisibility.UNKNOWN
            }
            val followerCount = FapFolderHtmlUtils.parseLeadingInt(
                el.selectFirst(".data-content > div:not(.name):not(.text)")?.text()
            )
            FapFolderGroupRow(
                groupId = groupId,
                slug = slug,
                name = name,
                avatarUrl = avatarUrl,
                followerCount = followerCount,
                isMember = isMember,
                visibility = visibility,
            )
        }
    }

    fun isNoDataState(doc: Document): Boolean {
        val text = doc.selectFirst(".tab-pane.active .text-center.text-muted")
            ?.text()
            ?.lowercase()
            ?: return false
        return text.contains("no data to show") || text.contains("no results")
    }

    fun hasChallenge(html: String?): Boolean =
        FapFolderHtmlUtils.looksLikeCloudflareChallenge(html)

    fun detectTab(doc: Document, fallback: FapFolderSearchTab): FapFolderSearchTab {
        val activeLabel = FapFolderHtmlUtils.cleanText(
            doc.selectFirst(".nav-pills.nav-search a.nav-link.active")?.text()
        ).lowercase()
        return when {
            activeLabel.contains("user") -> FapFolderSearchTab.USERS
            activeLabel.contains("communit") -> FapFolderSearchTab.COMMUNITIES
            activeLabel.contains("post") -> FapFolderSearchTab.POSTS
            else -> fallback
        }
    }

    fun extractTabUrls(doc: Document, pageUrl: String): Map<FapFolderSearchTab, String> {
        val map = mutableMapOf<FapFolderSearchTab, String>()
        doc.select(".nav-pills.nav-search a.nav-link[href]").forEach { anchor ->
            val label = FapFolderHtmlUtils.cleanText(anchor.text()).lowercase()
            val url = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@forEach
            when {
                label.contains("user") -> map[FapFolderSearchTab.USERS] = url
                label.contains("communit") -> map[FapFolderSearchTab.COMMUNITIES] = url
                label.contains("post") -> map[FapFolderSearchTab.POSTS] = url
            }
        }
        return map
    }

    private fun parsePostItems(doc: Document, baseUrl: String): List<FapFolderFeedItem> {
        val streamRoot = doc.selectFirst("div.js_posts_stream") ?: doc.body()
        val posts = FapFolderPostParser.parsePostsFromList(
            root = streamRoot,
            baseUrl = baseUrl,
            uploadsBase = null,
            includeComments = false,
        )
        return posts.map { FapFolderFeedItem.Post(it) }
    }

    private fun parseUserItems(doc: Document, baseUrl: String): List<FapFolderFeedItem> {
        val rows = parseUserRows(doc, baseUrl)
        return rows.map { FapFolderFeedItem.User(it) }
    }

    private fun parseCommunityItems(doc: Document, baseUrl: String): List<FapFolderFeedItem> {
        val rows = parseCommunityRows(doc, baseUrl)
        return rows.map { FapFolderFeedItem.Community(it) }
    }

    private fun emptyResult(
        query: String,
        isHashtag: Boolean,
        tab: FapFolderSearchTab,
        empty: Boolean,
    ): FapFolderSearchResult = FapFolderSearchResult(
        query = query,
        isHashtag = isHashtag,
        tab = tab,
        items = emptyList(),
        page = 1,
        hasMore = false,
        empty = empty,
        feedGet = null,
        feedFilter = null,
        feedId = null,
        feedHashtag = if (isHashtag) query else null,
        feedSearch = if (!isHashtag) query else null,
    )

    private fun Element.isAdContainerLegacy(): Boolean =
        hasClass("hbanner") || hasClass("hmbanner") || hasClass("postbanner")
}
