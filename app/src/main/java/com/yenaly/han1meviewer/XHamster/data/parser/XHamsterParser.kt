package com.yenaly.han1meviewer.XHamster.data.parser

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterUser
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

object XHamsterParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val TEMPLATE_URL =
        Regex("""https://[^"'\s<>\\]*?_TPL_\.(?:h264|av1)\.mp4\.m3u8""")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.lng(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun findObjectWithKey(el: JsonElement?, key: String): JsonObject? = when (el) {
        is JsonObject ->
            if (el.containsKey(key)) el
            else el.values.firstNotNullOfOrNull { findObjectWithKey(it, key) }

        is JsonArray -> el.firstNotNullOfOrNull { findObjectWithKey(it, key) }
        else -> null
    }

    fun extractInitials(html: String): JsonObject? {
        val marker = Regex("""window\.initials\s*=\s*""").find(html) ?: return null
        val start = html.indexOf('{', marker.range.last)
        if (start < 0) return null
        val end = matchBrace(html, start)
        if (end < 0) return null
        return runCatching {
            json.parseToJsonElement(html.substring(start, end + 1)) as? JsonObject
        }.getOrNull()
    }

    private fun matchBrace(s: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                if (c == '"') inString = true
                else if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun card(o: JsonObject): HanimeInfo? {
        val page = o.str("pageURL") ?: return null
        if (!page.contains("/videos/")) return null
        if (o.str("videoType") == "moment") return null
        val code = XHamsterConstants.codeFromUrl(page) ?: return null
        val title = o.str("title") ?: return null
        val cover = o.str("imageURL") ?: o.str("thumbURL") ?: ""
        return HanimeInfo(
            title = title,
            coverUrl = cover,
            videoCode = code,
            duration = XHamsterFormat.duration(o.lng("duration")),
            views = XHamsterFormat.count(o.lng("views")),
            itemType = HanimeInfo.NORMAL,
        )
    }

    private fun addCards(list: JsonArray?, out: LinkedHashMap<String, HanimeInfo>) {
        list?.forEach { el ->
            val c = (el as? JsonObject)?.let { card(it) }
            if (c != null) out.putIfAbsent(c.videoCode, c)
        }
    }

    private fun collectCards(el: JsonElement?, out: LinkedHashMap<String, HanimeInfo>) {
        when (el) {
            is JsonObject -> {
                card(el)?.let { out.putIfAbsent(it.videoCode, it) }
                el.values.forEach { collectCards(it, out) }
            }

            is JsonArray -> el.forEach { collectCards(it, out) }
            else -> Unit
        }
    }

    fun parseListPage(html: String): XHamsterListPage {
        val root = extractInitials(html) ?: return XHamsterListPage(emptyList(), null)
        val items = LinkedHashMap<String, HanimeInfo>()

        addCards(root.obj("layoutPage")?.obj("videoListProps")?.arr("videoThumbProps"), items)
        if (items.isEmpty()) {
            addCards(root.obj("searchResult")?.arr("videoThumbProps"), items)
        }
        if (items.isEmpty()) {
            addCards(
                root.obj("pagesCategoryComponent")?.obj("trendingVideoListProps")
                    ?.arr("videoThumbProps"),
                items,
            )
        }

        if (items.isEmpty()) {
            addCards(root.obj("newVideoListComponent")?.arr("videoThumbProps"), items)
        }

        if (items.isEmpty()) collectCards(root, items)

        val pagination = findObjectWithKey(root, "pageLinkTemplate")
        val template = pagination?.str("pageLinkTemplate")

        val current = pagination?.lng("currentPageNumber") ?: pagination?.lng("active") ?: 1L
        val last = pagination?.lng("lastPageNumber") ?: pagination?.lng("maxPage")?.takeIf { it > 0 }
        val nextNumber = (pagination?.lng("next")?.takeIf { it > current } ?: (current + 1)).toString()
        val hasMore = last == null || current < last
        val nextUrl = if (template != null && items.isNotEmpty() && hasMore) {
            XHamsterConstants.absolute(
                template.replace("{#}", nextNumber).replace("_URL_PAGE_REPLACER_", nextNumber)
            )
        } else null

        return XHamsterListPage(items.values.toList(), nextUrl)
    }

    fun parsePlaylists(html: String): List<XHamsterLink> {
        val root = extractInitials(html) ?: return emptyList()
        val list = root.arr("favoritesVideoCollectionsCollection") ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            val page = o.str("pageURL") ?: return@mapNotNull null
            val quantity = o.lng("quantity")
            XHamsterLink(
                title = if (quantity != null) "$title ($quantity)" else title,
                url = XHamsterConstants.relativePath(page),
            )
        }
    }

    fun parseUser(html: String): XHamsterUser? {
        val root = extractInitials(html) ?: return null
        val id = root.lng("userId") ?: return null
        if (id <= 0L) return null
        val profileUrl = root.obj("urls")?.str("myProfile").orEmpty()
        val name = profileUrl.trimEnd('/').substringAfterLast('/')
            .takeIf { it.isNotBlank() } ?: "user$id"
        return XHamsterUser(id = id, name = name, profileUrl = profileUrl)
    }

    fun parseDetail(html: String, code: String): XHamsterDetail? {
        val root = extractInitials(html)
        val doc = Jsoup.parse(html, XHamsterConstants.BASE_URL)

        val entity = root?.obj("videoEntity")
        val model = root?.obj("videoModel")

        val title = entity?.str("title")
            ?: model?.str("title")
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: return null

        val description = entity?.str("description") ?: model?.str("description")
        ?: doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()

        val cover = model?.str("thumbURL")
            ?: entity?.str("thumbBig")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()

        val duration = entity?.lng("duration") ?: model?.lng("duration") ?: 0L
        val views = entity?.lng("views") ?: model?.lng("views")
        val rating = entity?.obj("rating")
        val likes = rating?.lng("likes")
        val dislikes = rating?.lng("dislikes")
        val dateAgo = entity?.str("dateAgo")
        val maxResolution = entity?.str("maxResolution")

        var uploader = model?.obj("author")?.str("name")

        val tags = ArrayList<XHamsterLink>()
        val tagList = root?.obj("videoTagsComponent")?.arr("tags")
        tagList?.forEach { el ->
            val t = el as? JsonObject ?: return@forEach
            val name = t.str("name") ?: return@forEach
            val url = t.str("url") ?: t.str("pageURL") ?: t.str("link") ?: t.str("href").orEmpty()
            val isUser = t["isUser"]?.toString() == "true" || t["isChannel"]?.toString() == "true"
            if (isUser) {
                if (uploader == null) uploader = name
            } else {
                tags += XHamsterLink(name, url)
            }
        }
        if (uploader == null) {
            uploader = doc.selectFirst(".entity-author-container__name")?.text()
                ?.takeIf { it.isNotBlank() }
        }

        val normalized = html.replace("\\/", "/").replace("\\u002F", "/").replace("&amp;", "&")
        val templates = TEMPLATE_URL.findAll(normalized).map { it.value }.toList()
        var template = templates.firstOrNull { it.contains(".h264.") } ?: templates.firstOrNull()

        var direct: String? = null
        val xpHls = root?.obj("xplayerSettings")?.obj("sources")?.obj("hls")
            ?.obj("h264")?.str("url")
        if (xpHls != null && (xpHls.startsWith("http") || xpHls.startsWith("/"))) {
            val abs = XHamsterConstants.absolute(xpHls)
            if (abs.contains("_TPL_")) {
                if (template == null) template = abs
            } else {
                direct = abs
            }
        }
        if (direct == null) {
            val preload = doc.selectFirst("link[rel=preload][href*=.m3u8]")?.attr("href")
                ?.replace("\\/", "/")
            if (!preload.isNullOrBlank() && !preload.contains("_TPL_")) {
                direct = XHamsterConstants.absolute(preload)
            }
        }

        val mp4 = (doc.selectFirst("video.player-container__no-script-video")?.attr("src")
            ?: doc.selectFirst("a.player-container__no-player")?.attr("href"))
            ?.replace("&amp;", "&")
            ?.takeIf { it.startsWith("http") }

        val relatedMap = LinkedHashMap<String, HanimeInfo>()
        collectCards(root, relatedMap)
        relatedMap.remove(code)
        val related = relatedMap.values.take(30)

        return XHamsterDetail(
            code = code,
            title = title,
            description = description,
            coverUrl = cover,
            pageUrl = XHamsterConstants.videoUrl(code),
            durationSec = duration,
            views = views,
            likes = likes,
            dislikes = dislikes,
            dateAgo = dateAgo,
            uploader = uploader,
            maxResolution = maxResolution,
            tags = tags,
            hlsTemplate = template,
            hlsDirect = direct,
            mp4Fallback = mp4,
            related = related,
        )
    }
}
