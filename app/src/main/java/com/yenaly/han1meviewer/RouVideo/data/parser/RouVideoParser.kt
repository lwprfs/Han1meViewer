package com.yenaly.han1meviewer.RouVideo.data.parser

import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoCard
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoCategory
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoDetail
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoEpisode
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoListPage
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoSearchLanding
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoSeriesInfo
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTagCount
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTaxonomy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object RouVideoParser {

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull

    private fun JsonObject.dbl(key: String): Double =
        prim(key)?.let { it.doubleOrNull } ?: 0.0

    private fun JsonObject.lng(key: String): Long =
        prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0L

    private fun JsonObject.int(key: String): Int = lng(key).toInt()

    private fun JsonObject.bool(key: String): Boolean = prim(key)?.booleanOrNull ?: false

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun JsonObject.objList(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    internal fun videoCard(o: JsonObject): RouVideoCard? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        return RouVideoCard(
            id = id,
            title = o.str("name") ?: o.str("name_hant") ?: "",
            titleZh = o.str("nameZh") ?: o.str("name_hans"),
            coverUrl = o.str("coverImageUrl").orEmpty(),
            durationSec = o.dbl("duration"),
            viewCount = o.lng("viewCount"),
            createdAt = o.str("createdAt"),
            code = o.str("vid")?.takeIf { it.isNotBlank() },
            tags = o.strList("tags"),
            maxResolution = o.objList("sources").maxOfOrNull { it.int("resolution") } ?: 0,
        )
    }

    internal fun seriesCard(o: JsonObject): RouVideoCard? {
        val id = (o.str("firstEpisodeId") ?: o.str("coverVideoId"))
            ?.takeIf { it.isNotBlank() } ?: return null
        return RouVideoCard(
            id = id,
            title = o.str("name").orEmpty(),
            titleZh = o.str("nameZh"),
            coverUrl = o.str("coverImageUrl").orEmpty(),
            viewCount = o.lng("viewCount"),
            createdAt = o.str("lastEpisodeAt") ?: o.str("createdAt"),
            tags = o.strList("tags"),
            isSeries = true,
            isPoster = o.bool("isPoster"),
            seriesLabel = o.str("epLabel"),
        )
    }

    private fun tagCounts(root: JsonObject, key: String): List<RouVideoTagCount> =
        root.objList(key).mapNotNull { t ->
            t.str("id")?.let { RouVideoTagCount(it, t.int("count")) }
        }

    private fun category(o: JsonObject): RouVideoCategory? =
        o.str("id")?.let {
            RouVideoCategory(
                id = it,
                count = o.int("count"),
                kids = o.int("kids"),
                coverUrl = o.str("coverImageUrl"),
            )
        }

    fun parseVideoList(html: String): RouVideoListPage {
        val root = RouVideoLiteral.loaderObject(html)
        return RouVideoListPage(
            items = root.objList("videos").mapNotNull { videoCard(it) },
            pageNum = root.int("pageNum").coerceAtLeast(1),
            totalPage = root.int("totalPage"),
            total = root.int("totalVideoNum"),
            siblings = tagCounts(root, "siblings"),
        )
    }

    fun parseSearch(html: String, series: Boolean): RouVideoListPage {
        val root = RouVideoLiteral.loaderObject(html)
        val items = if (series) {
            root.objList("seriesList").mapNotNull { seriesCard(it) }
        } else {
            root.objList("videos").mapNotNull { videoCard(it) }
        }
        return RouVideoListPage(
            items = items,
            pageNum = root.int("pageNum").coerceAtLeast(1),
            totalPage = root.int("totalPage"),
            total = if (series) root.int("totalSeriesNum") else root.int("totalVideoNum"),
        )
    }

    fun parseSearchLanding(html: String): RouVideoSearchLanding {
        val root = RouVideoLiteral.loaderObject(html)
        return RouVideoSearchLanding(
            hotSearches = root.strList("hotSearches").filter { it.isNotBlank() && it != "*" },
            cats = root.obj("taxonomy")?.objList("cats")?.mapNotNull { category(it) } ?: emptyList(),
            trending = root.objList("trending").mapNotNull { videoCard(it) },
        )
    }

    fun parseSeriesList(html: String): RouVideoListPage {
        val root = RouVideoLiteral.loaderObject(html)
        return RouVideoListPage(
            items = root.objList("list").mapNotNull { seriesCard(it) },
            pageNum = root.int("pageNum").coerceAtLeast(1),
            totalPage = root.int("totalPage"),
            total = root.int("total"),
            tagOptions = tagCounts(root, "tagOptions"),
        )
    }

    fun parseTaxonomy(html: String): RouVideoTaxonomy {
        val root = RouVideoLiteral.loaderObject(html)
        val tax = root.obj("taxonomy") ?: error("Category data not found")
        val byParent = LinkedHashMap<String, List<RouVideoTagCount>>()
        tax.obj("byParent")?.forEach { (parent, value) ->
            val list = (value as? JsonArray)?.mapNotNull { it as? JsonObject }?.mapNotNull { t ->
                t.str("id")?.let { RouVideoTagCount(it, t.int("count")) }
            } ?: emptyList()
            byParent[parent] = list
        }
        return RouVideoTaxonomy(
            cats = tax.objList("cats").mapNotNull { category(it) },
            genre = tagCounts(tax, "genre"),
            byParent = byParent,
            totalTags = tax.int("totalTags"),
        )
    }

    private fun seriesInfo(o: JsonObject): RouVideoSeriesInfo? {
        val id = o.str("id") ?: return null
        return RouVideoSeriesInfo(
            id = id,
            title = o.str("name").orEmpty(),
            titleZh = o.str("nameZh"),
            status = o.str("status"),
            epLabel = o.str("epLabel"),
            episodeCount = o.int("episodeCount"),
            totalEpisodes = o.int("totalEpisodes"),
            description = o.str("description"),
            tags = o.strList("tags"),
            viewCount = o.lng("viewCount"),
        )
    }

    private fun episode(o: JsonObject): RouVideoEpisode? {
        val id = o.str("id") ?: return null
        return RouVideoEpisode(
            id = id,
            title = o.str("name").orEmpty(),
            titleZh = o.str("nameZh"),
            episode = o.int("episode"),
            durationSec = o.dbl("duration"),
            viewCount = o.lng("viewCount"),
            coverUrl = o.str("coverImageUrl").orEmpty(),
        )
    }

    fun parseDetail(html: String, requestedId: String): RouVideoDetail? {
        val root = RouVideoLiteral.loaderObject(html)
        val video = root.obj("video") ?: return null
        val id = video.str("id") ?: requestedId
        val streamPath = RouVideoLiteral.streamPathFromEv(root.obj("ev"))
            ?: "/api/hls/$id"
        val related = root.objList("relatedVideos").mapNotNull { videoCard(it) } +
                root.objList("moreSeries").mapNotNull { seriesCard(it) }
        return RouVideoDetail(
            id = id,
            title = video.str("name").orEmpty(),
            titleZh = video.str("nameZh"),
            code = video.str("vid")?.takeIf { it.isNotBlank() },
            coverUrl = video.str("coverImageUrl").orEmpty(),
            durationSec = video.dbl("duration"),
            viewCount = video.lng("viewCount"),
            likeCount = video.lng("likeCount"),
            createdAt = video.str("createdAt"),
            tags = video.strList("tags"),
            tagsZh = video.strList("tagsZh"),
            pageUrl = RouVideoConstants.videoPageUrl(id),
            streamPath = streamPath,
            series = root.obj("series")?.let { seriesInfo(it) },
            episodes = root.objList("episodes").mapNotNull { episode(it) }
                .sortedBy { it.episode },
            related = related,
        )
    }
}
