package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderProfileParser {

    data class ProfileTab(
        val label: String,
        val param: String,
        val url: String,
    )

    fun parseProfile(
        html: String?,
        pageUrl: String,
    ): FapFolderUserProfile? {
        if (html.isNullOrBlank()) return null
        val doc: Document = try {
            Jsoup.parse(html, pageUrl)
        } catch (_: Exception) {
            return null
        }

        val header = doc.selectFirst("div.profile-header") ?: return null

        val username = FapFolderUrlUtils.extractUsername(pageUrl)
            ?.takeIf { it.isNotBlank() }
            ?: parseDisplayName(header)

        val displayName: String = parseDisplayName(header)
        if (displayName.isBlank()) return null

        val avatarUrl: String? = header.selectFirst("div.profile-avatar-wrapper > img")
            ?.let { img ->
                img.attr("data-image").takeIf { it.isNotBlank() }
                    ?: img.absUrl("src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?.let(FapFolderUrlUtils::absoluteUrl)
            }

        val coverUrl: String? = header.selectFirst("div.profile-cover-wrapper > img")
            ?.let { img ->
                img.attr("data-image").takeIf { it.isNotBlank() }
                    ?: img.absUrl("src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?.let(FapFolderUrlUtils::absoluteUrl)
            }

        val (bio, bioHtml) = parseBio(doc)
        val aboutMap = parseAboutList(doc)

        val joinedLabel: String? = aboutMap["joined"]
            ?: aboutMap["join date"]
            ?: aboutMap["joined date"]

        val followerCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/followers']")?.text()
                ?: aboutMap["followers"]
        )
        val followingCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/following']")?.text()
                ?: aboutMap["following"]
        )
        val postCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/posts']")?.text()
                ?: aboutMap["posts"]
        )
        val photoCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/photos']")?.text()
                ?: aboutMap["photos"]
        )
        val videoCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/videos']")?.text()
                ?: aboutMap["videos"]
        )

        val followButton = header.selectFirst("button.js_follow, button.js_unfollow")
        val isFollowed = followButton?.hasClass("js_unfollow") == true

        val isFriend = header.selectFirst("button.js_friend-remove") != null
        val isFriendPending = header.selectFirst("button.js_friend-cancel") != null

        val isSelf = header.selectFirst("button.js_follow") == null &&
                header.selectFirst("button.js_unfollow") == null &&
                header.selectFirst(".js_friend-add") == null

        val uid = extractUid(doc, header)
        val activeTab = parseActiveTab(doc)

        return FapFolderUserProfile(
            uid = uid,
            username = username.orEmpty(),
            displayName = displayName,
            bio = bio?.takeIf { it.isNotBlank() },
            bioHtml = bioHtml?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            followerCount = followerCount,
            followingCount = followingCount,
            postCount = postCount,
            photoCount = photoCount,
            videoCount = videoCount,
            joinedLabel = joinedLabel,
            isFollowed = isFollowed,
            isFriend = isFriend,
            isFriendPending = isFriendPending,
            isSelf = isSelf,
            activeTab = activeTab,
        )
    }

    fun parseStreamAttrs(doc: Document): FapFolderGroupParser.StreamAttrs? =
        FapFolderGroupParser.parseStreamAttrs(doc)

    fun hasSeeMore(doc: Document): Boolean =
        FapFolderGroupParser.hasSeeMore(doc)

    fun parseTabs(doc: Document, pageUrl: String): List<ProfileTab> {
        val tabs = doc.select("div.profile-tabs-wrapper a[href]")
        val result = mutableListOf<ProfileTab>()
        tabs.forEach { anchor ->
            val label = FapFolderHtmlUtils.cleanText(anchor.text())
            if (label.isBlank()) return@forEach
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@forEach
            val param = paramForLabel(label)
            result += ProfileTab(label = label, param = param, url = href)
        }
        if (result.isEmpty()) {
            val base = pageUrl.trimEnd('/')
            result += ProfileTab("Posts", "posts", base)
            result += ProfileTab("Photos", "photos", "$base/photos")
            result += ProfileTab("Videos", "videos", "$base/videos")
            result += ProfileTab("Groups", "groups", "$base/groups")
            result += ProfileTab("Friends", "friends", "$base/friends")
        }
        return result
    }

    fun paramForLabel(label: String): String {
        val v = label.lowercase()
        return when {
            v.contains("photo") -> "photos"
            v.contains("video") -> "videos"
            v.contains("group") || v.contains("communit") -> "groups"
            v.contains("friend") -> "friends"
            v.contains("post") -> "posts"
            else -> "posts"
        }
    }

    private fun extractUid(doc: Document, header: Element): Long? {
        header.selectFirst("button.js_follow[data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        header.selectFirst("button.js_unfollow[data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        header.selectFirst("button.js_friend-add[data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        header.selectFirst("button.js_friend-cancel[data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        header.selectFirst("button.js_friend-remove[data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        doc.selectFirst("div.js_posts_stream[data-id]")
            ?.attr("data-id")?.toLongOrNull()?.let { return it }
        doc.selectFirst("button[data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        return null
    }

    private fun parseDisplayName(header: Element): String {
        return FapFolderHtmlUtils.cleanText(
            header.selectFirst("div.profile-name-wrapper a")?.text()
                ?: header.selectFirst("div.profile-name-wrapper")?.text()
        )
    }

    private fun parseBio(doc: Document): Pair<String?, String?> {
        val bioEl = doc.selectFirst("div.about-bio")
            ?: doc.selectFirst(".about-bio .js_readmore")
            ?: return null to null
        val html = bioEl.html().takeIf { it.isNotBlank() }
        val plain = FapFolderHtmlUtils.cleanText(bioEl.text()).takeIf { it.isNotBlank() }
        return plain to html
    }

    private fun parseAboutList(doc: Document): Map<String, String> {
        val result = mutableMapOf<String, String>()
        doc.select("ul.about-list > li.about-list-item").forEach { item ->
            val iconClass = item.selectFirst("i")?.classNames()?.firstOrNull {
                it.startsWith("fa-") && it != "fa-fw" && it != "fa"
            }
            val detailsText = FapFolderHtmlUtils.cleanText(
                item.selectFirst(".details")?.text() ?: item.text()
            )
            if (detailsText.isNotBlank()) {
                val key = when {
                    iconClass?.contains("calendar") == true -> "joined"
                    iconClass?.contains("users") == true -> "followers"
                    iconClass?.contains("user-friends") == true -> "following"
                    iconClass?.contains("newspaper") == true -> "posts"
                    iconClass?.contains("images") == true -> "photos"
                    iconClass?.contains("video") == true -> "videos"
                    iconClass?.contains("tag") == true -> "category"
                    else -> item.selectFirst("strong, .label")?.text()?.trim()?.lowercase()
                }
                if (!key.isNullOrBlank()) {
                    result[key] = detailsText
                }
            }
        }
        return result
    }

    private fun parseActiveTab(doc: Document): String? {
        val active = doc.selectFirst("div.profile-tabs-wrapper a.active")
            ?: doc.selectFirst("div.profile-tabs-wrapper a[aria-current=page]")
            ?: return null
        return FapFolderHtmlUtils.cleanText(active.text()).takeIf { it.isNotBlank() }
    }

    fun parseTabUrls(doc: Document, pageUrl: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        doc.select("div.profile-tabs-wrapper a[href]").forEach { anchor ->
            val label = FapFolderHtmlUtils.cleanText(anchor.text()).lowercase()
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
            if (!href.isNullOrBlank() && label.isNotBlank()) {
                map[label] = href
            }
        }
        return map
    }

    companion object Constants {
        const val TAB_POSTS = "posts"
        const val TAB_PHOTOS = "photos"
        const val TAB_VIDEOS = "videos"
        const val TAB_FOLLOWERS = "followers"
        const val TAB_FOLLOWING = "following"
        const val FALLBACK_GET_POSTS = FapFolderConstants.FEED_GET_POSTS_PROFILE
        const val FALLBACK_GET_GROUPS = "profile_groups"
        const val FALLBACK_GET_FRIENDS = "friends"
    }
}