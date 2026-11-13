package com.yenaly.han1meviewer.XHamster.data.parser

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCollection
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterEngagement
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterFeedItem
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListKind
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parsers for the signed-in "my" pages (history, favourites, liked, playlists), the
 * subscriptions feed, star country lists and photo search. All of them read the
 * `window.initials` blob (or the x-api JSON) exactly like [XHamsterParser].
 */
object XHamsterListsParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.lng(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() } == true

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    // ───────────────────────── video page: like / favourite state ─────────────────────────

    fun parseEngagement(root: JsonObject?, fallbackEntityId: Long?): XHamsterEngagement? {
        root ?: return null
        val block = root.obj("videoControlsBlock")
        val ratingProps = block?.obj("ratingProps")
        val rating = ratingProps?.obj("ratingModel") ?: root.obj("videoEntity")?.obj("rating")
        val entityId = rating?.lng("entityID") ?: fallbackEntityId ?: return null
        val state = (rating?.get("state") as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: 0
        val ratingState = when {
            state == 1 -> XHamsterEngagement.RATING_LIKED
            state == 2 || state < 0 -> XHamsterEngagement.RATING_DISLIKED
            else -> XHamsterEngagement.RATING_NONE
        }
        val collections = block?.arr("collections").orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val favorite = block?.bool("isFavorite") == true || collections.isNotEmpty()
        val userId = root.lng("userId")?.takeIf { it > 0L }
        return XHamsterEngagement(
            entityId = entityId,
            userId = userId,
            watchId = ratingProps?.str("watchId") ?: root.str("watchId"),
            likes = rating?.lng("likes"),
            dislikes = rating?.lng("dislikes"),
            ratingState = ratingState,
            isFavorite = favorite,
            collectionIds = collections,
        )
    }

    // ───────────────────────── favourites playlists ─────────────────────────

    fun parseCollections(html: String): List<XHamsterCollection> {
        val root = XHamsterParser.extractInitials(html) ?: return emptyList()
        val list = root.arr("favoritesVideoCollectionsCollection") ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val page = o.str("pageURL") ?: return@mapNotNull null
            val watchLater = o.bool("isWatchLater")
            val default = o.bool("default")
            XHamsterCollection(
                id = id,
                title = o.str("title") ?: if (watchLater) "Watch Later" else "Default playlist",
                path = XHamsterConstants.relativePath(page),
                quantity = o.lng("quantity"),
                isWatchLater = watchLater,
                isDefault = default,
                visibility = o.str("visibility"),
            )
        }
    }

    // ───────────────────────── my lists (history / favourites / liked / collection) ─────────────────────────

    private val NOISE_KEYS = Regex("trending|recommend|related|banner|popular|suggest|sidebar", RegexOption.IGNORE_CASE)

    private fun cardsIn(el: JsonElement?, out: LinkedHashMap<String, HanimeInfo>) {
        when (el) {
            is JsonArray -> {
                XHamsterParser.parseCards(el).forEach { out.putIfAbsent(it.videoCode, it) }
                el.forEach { if (it is JsonObject || it is JsonArray) cardsIn(it, out) }
            }

            is JsonObject -> el.forEach { (key, value) ->
                if (!NOISE_KEYS.containsMatchIn(key)) cardsIn(value, out)
            }

            else -> Unit
        }
    }

    private fun pathArray(root: JsonObject, vararg path: String): JsonArray? {
        var cur: JsonElement? = root
        for (key in path) cur = (cur as? JsonObject)?.get(key)
        return cur as? JsonArray
    }

    private fun nextUrl(layout: JsonObject?, hasItems: Boolean): String? {
        if (!hasItems || layout == null) return null
        val pagination = layout.obj("pagination") ?: return null
        val template = pagination.str("pageLinkTemplate") ?: return null
        val current = pagination.lng("active") ?: pagination.lng("currentPageNumber") ?: 1L
        val last = pagination.lng("maxPages")?.takeIf { it > 0 }
            ?: pagination.lng("lastPageNumber")?.takeIf { it > 0 }
        if (last != null && current >= last) return null
        val next = (pagination.lng("next")?.takeIf { it > current } ?: (current + 1)).toString()
        return XHamsterConstants.absolute(template.replace("{#}", next).replace("_URL_PAGE_REPLACER_", next))
    }

    fun parseMyVideos(html: String, kind: XHamsterListKind): XHamsterListPage {
        val root = XHamsterParser.extractInitials(html) ?: return XHamsterListPage(emptyList(), null)
        val layout = root.obj("layoutPage")
        val items = LinkedHashMap<String, HanimeInfo>()
        val specific: JsonArray? = when (kind) {
            XHamsterListKind.WATCH_HISTORY -> pathArray(root, "layoutPage", "watchVideoListProps", "videoThumbProps")
            XHamsterListKind.LIKED -> pathArray(root, "layoutPage", "likedVideosListProps", "videoThumbProps")
            XHamsterListKind.FAVORITES -> pathArray(root, "layoutPage", "defaultPlaylistPreview", "list")
            XHamsterListKind.WATCH_LATER -> pathArray(root, "layoutPage", "watchLaterPlaylistPreview", "list")
            XHamsterListKind.COLLECTION -> null
        }
        if (specific != null) XHamsterParser.parseCards(specific).forEach { items.putIfAbsent(it.videoCode, it) }
        if (items.isEmpty() && kind != XHamsterListKind.LIKED && kind != XHamsterListKind.WATCH_HISTORY) {
            // Collection pages (watch later, custom playlists) were not captured; scan the page
            // body but skip recommendation blocks so we never mix in "trending" videos.
            cardsIn(layout ?: root, items)
        }
        return XHamsterListPage(items.values.toList(), nextUrl(layout, items.isNotEmpty()))
    }

    // ───────────────────────── stars ─────────────────────────

    fun parseStarCountries(html: String): List<XHamsterOption> {
        val list = XHamsterParser.extractInitials(html)?.obj("layoutPage")?.arr("countries") ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            val count = o.lng("count")
            XHamsterOption(if (count != null) "$name ($count)" else name, XHamsterConstants.relativePath(url))
        }
    }

    // ───────────────────────── photo search ─────────────────────────

    fun parsePhotoSearch(html: String): XHamsterPage<XHamsterGallery> {
        val root = XHamsterParser.extractInitials(html) ?: return XHamsterPage(emptyList(), null)
        val component = root.obj("searchResultsPhotoComponent")
        val list = component?.arr("list").orEmpty()
        val items = list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.str("pageURL") ?: return@mapNotNull null
            if (o.bool("isDeleted") || o.bool("isGeoBlocked")) return@mapNotNull null
            val author = o.obj("author")
            XHamsterGallery(
                id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: url.trimEnd('/').substringAfterLast('/'),
                title = o.str("titleLocalized") ?: o.str("title") ?: "",
                url = XHamsterConstants.absolute(url),
                coverUrl = o.str("thumbURL") ?: o.str("imageURL") ?: o.str("previewThumbURL").orEmpty(),
                photosCount = o.lng("imgCount"),
                viewsText = o.lng("views")?.toString(),
                authorName = author?.str("name"),
                authorPath = (author?.str("pageURL") ?: author?.str("link"))
                    ?.let { XHamsterConstants.relativePath(it) },
            )
        }
        val pagination = component?.obj("pagination")
        val template = pagination?.str("pageLinkTemplate")
        val current = pagination?.lng("active") ?: 1L
        val last = pagination?.lng("maxPages")?.takeIf { it > 0 }
        val next = if (template != null && items.isNotEmpty() && (last == null || current < last)) {
            XHamsterConstants.absolute(template.replace("{#}", (current + 1).toString()))
        } else null
        return XHamsterPage(items, next)
    }

    // ───────────────────────── subscriptions feed (x-api newsCollectionFetch) ─────────────────────────

    fun parseNewsFeed(body: String): List<XHamsterFeedItem> {
        val root = (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray)
            ?.firstOrNull() as? JsonObject ?: return emptyList()
        val data = root.arr("responseData") ?: return emptyList()
        val out = ArrayList<XHamsterFeedItem>()
        val seen = HashSet<String>()
        for (el in data) {
            val news = el as? JsonObject ?: continue
            if (news.str("contentType") != "video") continue
            val author = news.obj("author")
            val authorName = author?.str("name") ?: continue
            val authorPath = (author?.str("pageURL") ?: author?.str("pageUrl"))?.let { XHamsterConstants.relativePath(it) }
            val cards = XHamsterParser.parseCards(news.arr("content"))
            for (card in cards) {
                if (seen.add(card.videoCode)) out += XHamsterFeedItem(authorName, authorPath, card)
            }
        }
        return out
    }
}
