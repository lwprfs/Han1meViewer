package com.yenaly.han1meviewer.HentaiMama.data.parser

import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistCard
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistHero
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistPageInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistSortOption
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistsHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.PlaylistsIndexPage
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HentaiMamaPlaylistParser {

    private const val CARD_INDEX = "div.dt-pl-card"
    private const val CARD_EPISODE = "ol.dt-plp-list li.dt-plp-item"
    private const val PAGINATOR = ".pagination.dt-pg"

    fun parseIndex(body: String, baseUrl: String): PlaylistsIndexPage {
        val doc = Jsoup.parse(body, baseUrl)
        return PlaylistsIndexPage(
            header = parseIndexHeader(doc, baseUrl),
            cards = parseIndexCards(doc),
            paginator = parsePaginator(doc),
        )
    }

    private fun parseIndexHeader(doc: Document, baseUrl: String): PlaylistsHeader {
        val title = doc.selectFirst(".dt-up-crumb-name")?.text().orEmpty()
        val homeUrl = doc.selectFirst("a.dt-up-crumb-home")?.absUrl("href").orEmpty()
        val sortBar = doc.select(".dt-plx-sort .sort_type a")
        val sortOptions = sortBar.map { a ->
            PlaylistSortOption(
                label = a.text().trim(),
                url = a.absUrl("href").takeIf { it.isNotBlank() } ?: a.attr("href"),
                isCurrent = a.hasClass("current"),
            )
        }
        val activeSort = sortBar.firstOrNull { it.hasClass("current") }?.text()?.trim()
        val paginator = parsePaginator(doc)
        return PlaylistsHeader(
            title = title,
            homeUrl = homeUrl,
            sortOptions = sortOptions,
            activeSort = activeSort,
            totalPages = paginator?.total ?: 1,
            currentPage = paginator?.current ?: 1,
            templateUrl = paginator?.templateUrl.orEmpty(),
            nextUrl = paginator?.nextUrl,
            lastUrl = paginator?.lastUrl,
        )
    }

    private fun parseIndexCards(doc: Document): List<PlaylistCard> =
        doc.select(CARD_INDEX).mapIndexedNotNull { idx, el ->
            runCatching { parseIndexCard(el, idx + 1) }.getOrNull()
        }

    private fun parseIndexCard(el: Element, rank: Int): PlaylistCard {
        val id = el.attr("data-id")
        val url = el.absUrl("data-url").ifBlank { el.attr("data-url") }
        val play = el.absUrl("data-play").takeIf { it.isNotBlank() }

        val vis = el.attr("data-vis").ifBlank {
            el.selectFirst(".dt-pl-vis")?.classNames()
                ?.firstOrNull { it == "public" || it == "private" }
                .orEmpty()
        }

        val nameLink = el.selectFirst("a.dt-pl-name")
        val title = nameLink?.text().orEmpty()

        val ownerLink = el.selectFirst("a.dt-pl-owner")
        val ownerName = ownerLink?.text().orEmpty()
        val ownerUrl = ownerLink?.absUrl("href").orEmpty()

        val videoCount = el.selectFirst(".dt-pl-count")
            ?.text()
            ?.removeSuffix("videos")
            ?.removeSuffix("video")
            ?.trim()
            ?.toIntOrNull()

        val viewsRaw = el.selectFirst(".dt-pl-tviews")
            ?.ownText()?.trim().orEmpty()
        val views = parseCompactNumber(viewsRaw.removeSuffix("views").trim())

        val likes = el.selectFirst("b.dt-pl-tlikes-n")
            ?.text()?.replace(",", "")?.toIntOrNull()

        val img = el.selectFirst("img")
        val srcset = img?.attr("data-savepage-srcset").orEmpty()
            .ifBlank { img?.attr("srcset").orEmpty() }
        val map = parseSrcset(srcset)
        val thumbSmall = map[300]
        val thumbMid = map[400]
        val thumbFull = img?.attr("data-savepage-src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("src").orEmpty()

        val likeBtn = el.selectFirst(".dt-plx-like")
        val likeId = likeBtn?.attr("data-id")
        val liked = likeBtn?.attr("aria-pressed") == "true"

        return PlaylistCard(
            id, url, play, vis, title, ownerName, ownerUrl,
            videoCount, viewsRaw, views, likes,
            thumbSmall, thumbMid, thumbFull,
            likeId, liked, rank,
        )
    }

    fun parseDetail(body: String, baseUrl: String): PlaylistDetailPage {
        val doc = Jsoup.parse(body, baseUrl)
        return PlaylistDetailPage(
            hero = parseHero(doc, baseUrl),
            episodes = parseEpisodes(doc),
            paginator = parsePaginator(doc),
        )
    }

    private fun parseHero(doc: Document, baseUrl: String): PlaylistHero {
        val root = doc.selectFirst(".dt-plp")
            ?: error("not a playlist page")

        val id = root.attr("data-id")
        val kind = root.attr("data-kind").ifBlank { "list" }

        val visPill = doc.selectFirst(".dt-plp-pill-vis")
        val visibility = visPill?.classNames()
            ?.firstOrNull { it == "public" || it == "private" }
            ?: visPill?.ownText()?.trim()?.lowercase().orEmpty()

        val title = doc.selectFirst("h1.dt-plp-name")?.text().orEmpty()

        val ownerChip = doc.selectFirst(".dt-plp-chip-owner")
        val ownerName = ownerChip?.selectFirst("b")?.text().orEmpty()
        val ownerUrl = ownerChip?.absUrl("href").orEmpty()
        val ownerAv = ownerChip?.selectFirst("img")?.absUrl("src")
            ?.takeIf { it.isNotBlank() }

        val videoCount = doc.selectFirst(".dt-plp-pill-count")
            ?.ownText()
            ?.removeSuffix("videos")?.removeSuffix("video")
            ?.trim()?.toIntOrNull()

        val metaSpans = doc.select(".dt-plp-metaline span").map { it.text().trim() }
        val viewsRaw = metaSpans.getOrNull(0).orEmpty()
        val updated = metaSpans.getOrNull(1)?.takeIf { it.startsWith("Updated") }
        val created = metaSpans.getOrNull(2)?.takeIf { it.startsWith("Created") }
        val views = parseCompactNumber(viewsRaw.removeSuffix("views").trim())

        val playAll = doc.selectFirst("a.dt-plp-play")?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        val shuffle = doc.selectFirst("a.dt-plp-shuffle")?.absUrl("href")
            ?.takeIf { it.isNotBlank() }

        val likeBtn = doc.selectFirst(".dt-plp-like")
        val likes = likeBtn?.selectFirst(".dt-plp-like-n")?.text()
            ?.replace(",", "")?.toIntOrNull()
        val liked = likeBtn?.attr("aria-pressed") == "true"

        val backUrl = doc.selectFirst("a.dt-plp-back")?.absUrl("href")
            ?.takeIf { it.isNotBlank() } ?: "$baseUrl/playlists/"

        return PlaylistHero(
            id, kind, visibility, title, ownerName, ownerUrl, ownerAv,
            videoCount, viewsRaw, views, updated, created,
            playAll, shuffle, likes, liked, backUrl,
        )
    }

    private fun parseEpisodes(doc: Document): List<PlaylistEpisode> =
        doc.select(CARD_EPISODE).mapNotNull {
            runCatching { parseEpisode(it) }.getOrNull()
        }

    private fun parseEpisode(el: Element): PlaylistEpisode {
        val postId = el.attr("data-ep").toIntOrNull() ?: error("no data-ep")

        val titleLink = el.selectFirst(".dt-plp-title") ?: error("no title")
        val url = titleLink.absUrl("href").takeIf { it.isNotBlank() }
            ?: titleLink.attr("href")
        val title = titleLink.text()

        val position = el.selectFirst(".dt-plp-num")
            ?.text()?.trim()?.toIntOrNull() ?: 0

        val sub = el.selectFirst(".dt-plp-sub")?.text().orEmpty()
        val parts = sub.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
        val studio = parts.getOrNull(0)
            ?.takeIf { it.isNotBlank() && it != "—" && !it.endsWith("views") }
        val viewsRaw = parts.lastOrNull { it.endsWith("views") }.orEmpty()
        val views = parseCompactNumber(viewsRaw.removeSuffix("views").trim())

        val img = el.selectFirst("img")
        val srcset = img?.attr("data-savepage-srcset").orEmpty()
            .ifBlank { img?.attr("srcset").orEmpty() }
        val map = parseSrcset(srcset)
        val thumbSmall = map[300]
        val thumbFull = img?.attr("data-savepage-src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("src").orEmpty()

        return PlaylistEpisode(
            postId, position, url, title, studio, viewsRaw, views,
            thumbSmall, thumbFull,
        )
    }

    fun parsePaginator(doc: Document): PlaylistPageInfo? {
        val pg = doc.selectFirst(PAGINATOR) ?: return null
        return PlaylistPageInfo(
            total = pg.attr("data-pages").toIntOrNull() ?: 1,
            current = pg.attr("data-page").toIntOrNull() ?: 1,
            templateUrl = pg.attr("data-tpl").orEmpty(),
            nextUrl = doc.selectFirst("a.dt-pg-next")?.absUrl("href")
                ?.takeIf { it.isNotBlank() },
            lastUrl = doc.selectFirst("a.dt-pg-last")?.absUrl("href")
                ?.takeIf { it.isNotBlank() },
        )
    }

    fun parseCompactNumber(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return when {
            t.endsWith("K", true) -> t.dropLast(1).toDoubleOrNull()
                ?.let { (it * 1_000).toLong() }
            t.endsWith("M", true) -> t.dropLast(1).toDoubleOrNull()
                ?.let { (it * 1_000_000).toLong() }
            else -> t.replace(",", "").toLongOrNull()
        }
    }

    private fun parseSrcset(s: String): Map<Int, String> =
        s.split(',').mapNotNull {
            val b = it.trim().split(' ')
            if (b.size == 2 && b[1].endsWith("w"))
                b[1].dropLast(1).toIntOrNull()?.let { w -> w to b[0] }
            else null
        }.toMap()
}