package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupCategory
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupRow
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserRow
import com.yenaly.han1meviewer.FapFolder.data.model.GroupMembership
import com.yenaly.han1meviewer.FapFolder.data.model.GroupVisibility
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderGroupParser {

    data class StreamAttrs(
        val get: String?,
        val filter: String?,
        val id: String?,
        val type: String?,
        val uid: String?,
        val search: String?,
        val hashtag: String?,
        val offset: Int,
        val done: Boolean,
    )

    fun parseStreamAttrs(doc: Document): StreamAttrs? {
        val root = doc.selectFirst("div.js_posts_stream") ?: return null
        val seeMore = doc.selectFirst(".js_see-more-infinite")
            ?: doc.selectFirst(".js_see-more")
        val offsetAttr = seeMore?.attr("data-offset")?.toIntOrNull() ?: 1
        val done = seeMore?.hasClass("done") == true
        return StreamAttrs(
            get = root.attr("data-get").takeIf { it.isNotBlank() },
            filter = root.attr("data-filter").takeIf { it.isNotBlank() },
            id = root.attr("data-id").takeIf { it.isNotBlank() },
            type = root.attr("data-type").takeIf { it.isNotBlank() },
            uid = root.attr("data-uid").takeIf { it.isNotBlank() },
            search = root.attr("data-search").takeIf { it.isNotBlank() },
            hashtag = root.attr("data-hashtag").takeIf { it.isNotBlank() },
            offset = offsetAttr,
            done = done,
        )
    }

    fun hasSeeMore(doc: Document): Boolean {
        val seeMore = doc.selectFirst(".js_see-more-infinite")
            ?: doc.selectFirst(".js_see-more")
            ?: return false
        return !seeMore.hasClass("done")
    }

    fun parseGroup(html: String?, pageUrl: String): FapFolderGroup? {
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

        val slug = FapFolderUrlUtils.extractGroupSlug(pageUrl).orEmpty()
        val displayName = FapFolderHtmlUtils.cleanText(
            header.selectFirst("div.profile-name-wrapper a")?.text()
                ?: header.selectFirst("div.profile-name-wrapper")?.text()
                ?: header.selectFirst("h1")?.text()
        )
        if (displayName.isBlank()) return null

        val bannerUrl: String? = header.selectFirst("div.profile-cover-wrapper > img")
            ?.let { img ->
                img.absUrl("src").takeIf { it.isNotBlank() }
                    ?: img.attr("data-image").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?.let(FapFolderUrlUtils::absoluteUrl)
            }

        val avatarUrl: String? = header.selectFirst("div.profile-avatar-wrapper > img")
            ?.let { img ->
                img.attr("data-image").takeIf { it.isNotBlank() }
                    ?: img.absUrl("src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?.let(FapFolderUrlUtils::absoluteUrl)
            }

        val (bio, bioHtml) = parseBio(doc)
        val bioTags = doc.select("div.about-bio a[href*='/search/hashtag/']")
            .mapNotNull { FapFolderUrlUtils.extractTagFromHashtagHref(it.attr("href")) }
            .distinct()

        val aboutMap = parseAboutList(doc)

        val memberCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/members']")?.text()
                ?: aboutMap["members"]
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
        val followerCount = FapFolderHtmlUtils.parseLeadingInt(
            header.selectFirst("a[href$='/followers']")?.text()
                ?: aboutMap["followers"]
        )

        val joinBtn = header.selectFirst("button.js_join-group")
        val leaveBtn = header.selectFirst("button.js_leave-group")
        val adminPendingAccept = header.selectFirst("button.js_group-request-accept")

        val groupId: Long? = (joinBtn ?: leaveBtn ?: adminPendingAccept)
            ?.attr("data-id")
            ?.toLongOrNull()

        val visibility: GroupVisibility = parseVisibility(joinBtn ?: leaveBtn, header)
        val membership: GroupMembership = when {
            adminPendingAccept != null -> GroupMembership.ADMIN
            joinBtn != null -> GroupMembership.NOT_MEMBER
            leaveBtn != null -> {
                val text = leaveBtn.text().lowercase()
                val classes = leaveBtn.classNames()
                when {
                    text.contains("pending") || classes.any { it.contains("warning") } ->
                        GroupMembership.PENDING
                    else -> GroupMembership.MEMBER
                }
            }
            else -> GroupMembership.UNKNOWN
        }

        val category = parseCategory(doc, aboutMap)
        val tags = parseTags(doc, aboutMap)
        val socialLinks = parseSocialLinks(doc)

        val galleryPreview = parseGalleryPreview(doc)
        val activeTab = parseActiveTab(doc)

        return FapFolderGroup(
            groupId = groupId,
            slug = slug,
            displayName = displayName,
            bio = bio?.takeIf { it.isNotBlank() },
            bioHtml = bioHtml?.takeIf { it.isNotBlank() },
            bioTags = bioTags,
            bannerUrl = bannerUrl,
            avatarUrl = avatarUrl,
            memberCount = memberCount,
            postCount = postCount,
            photoCount = photoCount,
            videoCount = videoCount,
            followerCount = followerCount,
            visibility = visibility,
            membership = membership,
            category = category,
            tags = tags,
            socialLinks = socialLinks,
            galleryPreview = galleryPreview,
            activeTab = activeTab,
        )
    }

    private fun parseBio(doc: Document): Pair<String?, String?> {
        val bioEl = doc.selectFirst("div.about-bio") ?: return null to null
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
                    iconClass?.contains("calendar") == true -> "created"
                    iconClass?.contains("users") == true -> "members"
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

    private fun parseVisibility(
        button: Element?,
        header: Element,
    ): GroupVisibility {
        val attr = button?.attr("data-privacy")?.lowercase()
        return when (attr) {
            "public" -> GroupVisibility.PUBLIC
            "private" -> GroupVisibility.PRIVATE
            "secret" -> GroupVisibility.SECRET
            else -> {
                val chipText = FapFolderHtmlUtils.cleanText(
                    header.selectFirst(".dsc-chips, .profile-chip")?.text()
                ).lowercase()
                when {
                    chipText.contains("secret") -> GroupVisibility.SECRET
                    chipText.contains("private") -> GroupVisibility.PRIVATE
                    chipText.contains("public") -> GroupVisibility.PUBLIC
                    else -> GroupVisibility.UNKNOWN
                }
            }
        }
    }

    private fun parseCategory(doc: Document, aboutMap: Map<String, String>): FapFolderGroupCategory? {
        val anchor = doc.selectFirst("ul.about-list a[href*='/groups/category/']")
            ?: return null
        val href = anchor.attr("href").takeIf { it.isNotBlank() } ?: return null
        val id = Regex("""/category/(\d+)""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val name = FapFolderHtmlUtils.cleanText(anchor.text())
            .ifBlank { aboutMap["category"].orEmpty() }
        if (name.isBlank()) return null
        return FapFolderGroupCategory(id = id, name = name)
    }

    private fun parseTags(doc: Document, aboutMap: Map<String, String>): List<String> {
        val tagsFromAnchors = doc.select("div.about-list a[href*='/search/hashtag/']")
            .mapNotNull { FapFolderUrlUtils.extractTagFromHashtagHref(it.attr("href")) }
        if (tagsFromAnchors.isNotEmpty()) return tagsFromAnchors.distinct()
        return aboutMap["tags"]
            ?.split(',')
            ?.map { FapFolderHtmlUtils.cleanText(it) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
    }

    private fun parseSocialLinks(doc: Document): Map<String, String> {
        val map = mutableMapOf<String, String>()
        doc.select("ul.about-list a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                ?: return@forEach
            val host = FapFolderUrlUtils.hostOf(href)?.lowercase() ?: return@forEach
            val key = when {
                host.contains("instagram") -> "instagram"
                host.contains("tiktok") -> "tiktok"
                host.contains("telegram") || host.contains("t.me") -> "telegram"
                host.contains("fansly") -> "fansly"
                host.contains("onlyfans") -> "onlyfans"
                host.contains("twitter") || host == "x.com" -> "twitter"
                host.contains("youtube") -> "youtube"
                else -> null
            }
            if (key != null && !map.containsKey(key)) {
                map[key] = href
            }
        }
        return map
    }

    private fun parseGalleryPreview(doc: Document): List<FapFolderMedia.Image> {
        val card = doc.selectFirst("div.card.panel-photos") ?: return emptyList()
        return card.select("a.pg_photo, a.js_lightbox").take(12).mapNotNull { anchor ->
            val fullUrl = (anchor.attr("data-image").takeIf { it.isNotBlank() }
                ?: FapFolderHtmlUtils.extractBackgroundImageUrl(anchor.attr("style"))
                ?: anchor.selectFirst("img[data-src]")?.attr("data-src")
                ?: anchor.selectFirst("img[src]")?.absUrl("src"))
                ?.let { FapFolderUrlUtils.normalizeMediaUrl(it) }
                ?: return@mapNotNull null
            FapFolderMedia.Image(
                photoId = anchor.attr("data-id").toLongOrNull()
                    ?: FapFolderUrlUtils.extractPhotoId(anchor.attr("href")),
                fullUrl = fullUrl,
                previewUrl = anchor.selectFirst("img[data-src]")?.attr("data-src")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { FapFolderUrlUtils.normalizeMediaUrl(it) },
                context = anchor.attr("data-context").takeIf { it.isNotBlank() },
                alt = anchor.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun parseActiveTab(doc: Document): String? {
        val active = doc.selectFirst("div.profile-tabs-wrapper a.active")
            ?: doc.selectFirst("div.profile-tabs-wrapper a[aria-current=page]")
            ?: return null
        return FapFolderHtmlUtils.cleanText(active.text()).takeIf { it.isNotBlank() }
    }

    fun parseTabUrls(doc: Document): Map<String, String> {
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

    fun parseGroupRows(doc: Document, baseUrl: String): List<FapFolderGroupRow> {
        val cards = doc.select("div.card-body.with-list li.feeds-item")
            .ifEmpty { doc.select("div.card-body.with-list > ul > li") }
        return cards.mapNotNull { li ->
            val avatarAnchor = li.selectFirst("a.data-avatar[href*='/groups/']")
                ?: return@mapNotNull null
            val href = avatarAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: avatarAnchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            val slug = FapFolderUrlUtils.extractGroupSlug(href) ?: return@mapNotNull null
            val name = FapFolderHtmlUtils.cleanText(
                li.selectFirst("span.name a")?.text()
                    ?: li.selectFirst(".data-content .name")?.text()
            ).ifBlank { slug }
            val avatarUrl = avatarAnchor.selectFirst("img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val joinBtn = li.selectFirst("button.js_join-group, button.js_leave-group")
            val groupId = joinBtn?.attr("data-id")?.toLongOrNull()
            val isMember = joinBtn?.hasClass("js_leave-group") == true
            val visibility = when (joinBtn?.attr("data-privacy")?.lowercase()) {
                "public" -> GroupVisibility.PUBLIC
                "private" -> GroupVisibility.PRIVATE
                "secret" -> GroupVisibility.SECRET
                else -> GroupVisibility.UNKNOWN
            }
            val followerCount = FapFolderHtmlUtils.parseLeadingInt(
                li.selectFirst(".data-content > div:not(.name):not(.text)")?.text()
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

    fun parseMemberRows(doc: Document, baseUrl: String): List<FapFolderUserRow> {
        return doc.select("div.feeds-item").mapNotNull { li ->
            val avatarAnchor = li.selectFirst("div.data-avatar a[href]")
                ?: return@mapNotNull null
            val href = avatarAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: avatarAnchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            val username = FapFolderUrlUtils.extractUsername(href) ?: return@mapNotNull null
            val name = FapFolderHtmlUtils.cleanText(
                li.selectFirst("div.data-content div.name a")?.text()
                    ?: li.selectFirst(".data-content .name")?.text()
            ).ifBlank { username }
            val avatarUrl = avatarAnchor.selectFirst("img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val bio = FapFolderHtmlUtils.cleanText(
                li.selectFirst(".data-content .text")?.text()
            ).takeIf { it.isNotBlank() }
            val uid = li.selectFirst("button[data-uid]")?.attr("data-uid")?.toLongOrNull()
                ?: li.selectFirst("[data-uid]")?.attr("data-uid")?.toLongOrNull()
            val isFollowed = li.selectFirst("button.js_unfollow") != null
            val isFriend = li.selectFirst("button.js_friend-remove") != null
            val isFriendPending = li.selectFirst("button.js_friend-cancel") != null
            val isGold = li.selectFirst("i.gold-badge") != null
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

    fun emptyStreamAttrs(): StreamAttrs = StreamAttrs(
        get = null,
        filter = null,
        id = null,
        type = null,
        uid = null,
        search = null,
        hashtag = null,
        offset = 1,
        done = false,
    )

    fun fallbackStreamGet(): String = FapFolderConstants.FEED_GET_POSTS_GROUP
}
