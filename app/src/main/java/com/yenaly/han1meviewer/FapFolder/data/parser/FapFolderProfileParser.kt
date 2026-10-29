package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderProfileTabStream
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderProfileParser {

    const val TAB_POSTS = "posts"
    const val TAB_PHOTOS = "photos"
    const val TAB_VIDEOS = "videos"
    const val TAB_GROUPS = "groups"
    const val TAB_FRIENDS = "friends"
    const val TAB_ABOUT = "about"

    const val FALLBACK_GET_POSTS = FapFolderConstants.FEED_GET_POSTS_PROFILE
    const val FALLBACK_GET_GROUPS = "profile_groups"
    const val FALLBACK_GET_FRIENDS = "friends"

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

        val header = doc.selectFirst("div.profile-header")
            ?: doc.selectFirst("div.profile-header-cover")
            ?: doc.selectFirst(".profile-header-wrapper")
            ?: return null

        val groupSlug = FapFolderUrlUtils.extractGroupSlug(pageUrl)
        val urlUsername = FapFolderUrlUtils.extractUsername(pageUrl)

        val displayName = parseDisplayName(header)
        if (displayName.isBlank()) return null

        val username = urlUsername
            ?.takeIf { it.isNotBlank() }
            ?: groupSlug
            ?: displayName.lowercase().replace(' ', '_')

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
            ?: header.attr("style")
                .let { FapFolderHtmlUtils.extractBackgroundImageUrl(it) }
                ?.let(FapFolderUrlUtils::normalizeMediaUrl)

        val (bio, bioHtml) = parseBio(doc)
        val aboutMap = parseAboutList(doc)

        val joinedLabel: String? = aboutMap["joined"]
            ?: aboutMap["join date"]
            ?: aboutMap["joined date"]
            ?: aboutMap["created"]

        val followerCount = firstIntFrom(
            listOf(
                header.selectFirst("a[href$='/followers']")?.text(),
                header.selectFirst("a[href*='/followers']")?.text(),
                aboutMap["followers"],
            )
        )
        val followingCount = firstIntFrom(
            listOf(
                header.selectFirst("a[href$='/following']")?.text(),
                header.selectFirst("a[href*='/following']")?.text(),
                aboutMap["following"],
            )
        )
        val postCount = firstIntFrom(
            listOf(
                header.selectFirst("a[href$='/posts']")?.text(),
                header.selectFirst("a[href*='/posts']")?.text(),
                aboutMap["posts"],
            )
        )
        val photoCount = firstIntFrom(
            listOf(
                header.selectFirst("a[href$='/photos']")?.text(),
                header.selectFirst("a[href*='/photos']")?.text(),
                aboutMap["photos"],
            )
        )
        val videoCount = firstIntFrom(
            listOf(
                header.selectFirst("a[href$='/videos']")?.text(),
                header.selectFirst("a[href*='/videos']")?.text(),
                aboutMap["videos"],
            )
        )

        val followButton = header.selectFirst("button.js_follow, button.js_unfollow")
        val followUid = followButton?.attr("data-uid")?.toLongOrNull()
            ?: header.selectFirst("button[data-uid]")?.attr("data-uid")?.toLongOrNull()

        val uid = followUid
            ?: doc.selectFirst("div.js_posts_stream[data-id]")
                ?.attr("data-id")?.toLongOrNull()
            ?: doc.selectFirst("button[data-uid]")
                ?.attr("data-uid")?.toLongOrNull()

        val isFollowed = followButton?.hasClass("js_unfollow") == true
        val isFriend = header.selectFirst("button.js_friend-remove") != null
        val isFriendPending = header.selectFirst("button.js_friend-cancel") != null
        val isSelf = followButton == null &&
                header.selectFirst(".js_friend-add") == null &&
                header.selectFirst("button.js_friend-remove") == null

        val activeTab = parseActiveTab(doc)
        val tabUrls = parseTabUrls(doc, pageUrl)
        val tabStreams = parseTabStreams(doc)

        return FapFolderUserProfile(
            uid = uid,
            username = username,
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
            followUid = followUid ?: uid,
            tabUrls = tabUrls,
            tabStreams = tabStreams,
            isGroup = !groupSlug.isNullOrBlank(),
            groupSlug = groupSlug,
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
            v.contains("about") -> "about"
            v.contains("post") || v.contains("timeline") -> "posts"
            else -> "posts"
        }
    }

    private fun parseDisplayName(header: Element): String {
        return FapFolderHtmlUtils.cleanText(
            header.selectFirst("div.profile-name-wrapper a")?.text()
                ?: header.selectFirst("div.profile-name-wrapper")?.text()
                ?: header.selectFirst("h1")?.text()
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
                    ?: return@forEach
            val param = paramForLabel(label)
            if (param.isNotBlank()) map[param] = href
        }
        return map
    }

    private fun parseTabStreams(doc: Document): Map<String, FapFolderProfileTabStream> {
        val result = mutableMapOf<String, FapFolderProfileTabStream>()
        val root = doc.selectFirst("div.js_posts_stream") ?: return result
        val seeMore = doc.selectFirst(".js_see-more-infinite")
            ?: doc.selectFirst(".js_see-more")
        val offset = seeMore?.attr("data-offset")?.toIntOrNull() ?: 1
        val done = seeMore?.hasClass("done") == true
        val stream = FapFolderProfileTabStream(
            get = root.attr("data-get").takeIf { it.isNotBlank() },
            filter = root.attr("data-filter").takeIf { it.isNotBlank() },
            id = root.attr("data-id").takeIf { it.isNotBlank() },
            type = root.attr("data-type").takeIf { it.isNotBlank() },
            uid = root.attr("data-uid").takeIf { it.isNotBlank() },
            search = root.attr("data-search").takeIf { it.isNotBlank() },
            hashtag = root.attr("data-hashtag").takeIf { it.isNotBlank() },
            offset = offset,
            done = done,
        )
        val activeLabel = parseActiveTab(doc)?.lowercase()
        val activeParam = activeLabel?.let { paramForLabel(it) } ?: "posts"
        result[activeParam] = stream
        result["posts"] = stream
        return result
    }

    private fun firstIntFrom(candidates: List<String?>): Int? {
        candidates.forEach { candidate ->
            val parsed = FapFolderHtmlUtils.parseLeadingInt(candidate)
            if (parsed != null) return parsed
        }
        return null
    }
}
