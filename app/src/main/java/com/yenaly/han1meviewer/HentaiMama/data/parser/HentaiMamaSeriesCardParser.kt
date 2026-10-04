package com.yenaly.han1meviewer.HentaiMama.data.parser

import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.data.model.AzLink
import com.yenaly.han1meviewer.HentaiMama.data.model.FilterOptions
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreBreadcrumb
import com.yenaly.han1meviewer.HentaiMama.data.model.PageInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseFavoritesCount
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseSrcsetMap
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseViews
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.resolveUrl
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.slugFromUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HentaiMamaSeriesCardParser {

    private const val TAG = "SeriesCardParser"
    private const val CARD_SELECTOR = "article.series-card"
    private const val ALT_CARD_SELECTOR = ".dt-series-cards article"

    fun parseCards(doc: Document, baseUrl: String): List<SeriesCard> {
        val primary = doc.select(CARD_SELECTOR)
        val cards = if (primary.isNotEmpty()) primary else doc.select(ALT_CARD_SELECTOR)
        return cards.mapNotNull { el ->
            runCatching { parseOne(el, baseUrl) }
                .onFailure { Log.w(TAG, "Failed to parse series card: ${it.message}") }
                .getOrNull()
        }
    }

    fun parseCards(body: String, baseUrl: String): List<SeriesCard> =
        parseCards(Jsoup.parse(body, baseUrl), baseUrl)

    private fun parseOne(el: Element, baseUrl: String): SeriesCard? {
        val link: Element = el.selectFirst("a.sc-poster")
            ?: el.selectFirst("h3.sc-title a")
            ?: el.selectFirst("a.sc-title")
            ?: return null

        val url: String = link.absUrl("href").takeIf { it.isNotBlank() }
            ?: resolveUrl(link.attr("href"), baseUrl)
        if (url.isBlank()) return null
        val slug: String = slugFromUrl(url)
        if (slug.isBlank()) return null

        val title: String = el.selectFirst("h3.sc-title a")?.text()?.trim().orEmpty()
            .ifBlank { el.selectFirst("a.sc-title")?.text()?.trim().orEmpty() }
            .ifBlank { el.selectFirst(".sc-alt")?.text()?.trim().orEmpty() }
        if (title.isBlank()) return null

        val altTitles: List<String> = el.selectFirst(".sc-alt")?.text()
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val img: Element? = el.selectFirst("a.sc-poster img")
            ?: el.selectFirst(".sc-poster img")
        val srcsetRaw: String = img?.attr("data-savepage-srcset").orEmpty()
            .ifBlank { img?.attr("srcset").orEmpty() }
        val variants: Map<Int, String> = parseSrcsetMap(srcsetRaw)
        val thumbSmall: String? = variants[175]
        val thumbFull: String = variants[500]
            ?: variants[268]
            ?: img?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("src").orEmpty()
            ?: thumbSmall.orEmpty()

        val posterAlt: String = img?.attr("alt").orEmpty()

        val rating: Double? = el.selectFirst(".sc-btn-rating")
            ?.text()
            ?.trim()
            ?.filter { it.isDigit() || it == '.' }
            ?.toDoubleOrNull()

        val favText: String? = el.selectFirst(".sc-fav-n")?.text()
            ?.takeIf { it.isNotBlank() }
        val favorites: Int? = parseFavoritesCount(favText)

        val favLink: Element? = el.selectFirst("a.sc-fav-login")
            ?: el.selectFirst("a.sc-btn-fav")
        val postId: Int? = favLink?.attr("data-post-id")?.toIntOrNull()
            ?.takeIf { it > 0 }
        val nonce: String? = favLink?.attr("data-nonce")?.takeIf { it.isNotBlank() }

        val studioAnchors: List<Element> = el.select(".sc-tag-studio")
        val studios: List<String> = studioAnchors.map { it.text().trim() }
            .filter { it.isNotBlank() }
        val studioUrls: List<String> = studioAnchors.map { it.absUrl("href") }

        val plainTags: List<String> = el.select(".sc-meta .sc-tag")
            .filterNot { it.hasClass("sc-tag-studio") }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val year: Int? = plainTags.getOrNull(0)
            ?.takeIf { it.length in 4..5 && it.all(Char::isDigit) }
            ?.toIntOrNull()
        val viewsRaw: String? = plainTags.getOrNull(1)
        val views: Long? = parseViews(viewsRaw)
        val episodeCount: Int? = plainTags.getOrNull(2)
            ?.substringBefore(' ')
            ?.filter { it.isDigit() }
            ?.toIntOrNull()

        val description: String? = el.selectFirst("p.sc-desc")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }

        val hasLongDesc: Boolean = el.selectFirst("a.sc-desc-toggle")
            ?.attr("style")
            ?.contains("display:none") == false

        val genreAnchors: List<Element> = el.select(".sc-genres a")
        val genres: List<String> = genreAnchors.map { it.text().trim() }
            .filter { it.isNotBlank() }
        val genreSlugs: List<String> = genreAnchors.map { slugFromUrl(it.absUrl("href")) }
            .filter { it.isNotBlank() }

        return SeriesCard(
            url = url,
            slug = slug,
            title = title,
            altTitles = altTitles,
            thumbSmall = thumbSmall,
            thumbFull = thumbFull,
            posterAlt = posterAlt,
            rating = rating,
            favorites = favorites,
            postId = postId,
            nonce = nonce,
            studios = studios,
            studioUrls = studioUrls,
            year = year,
            viewsRaw = viewsRaw,
            views = views,
            episodeCount = episodeCount,
            description = description,
            hasLongDescription = hasLongDesc,
            genres = genres,
            genreSlugs = genreSlugs,
        )
    }

    fun parsePaginator(doc: Document): PageInfo? {
        val p: Element = doc.selectFirst(".dt-series-pagination-top .pagination.dt-pg")
            ?: doc.selectFirst(".pagination.dt-pg")
            ?: return null
        return PageInfo(
            current = p.attr("data-page").toIntOrNull() ?: 1,
            total = p.attr("data-pages").toIntOrNull() ?: 1,
            templateUrl = p.attr("data-tpl").takeIf { it.isNotBlank() },
            nextUrl = p.selectFirst("a.dt-pg-next")
                ?.absUrl("href")?.takeIf { it.isNotBlank() },
            lastUrl = p.selectFirst("a.dt-pg-last")
                ?.absUrl("href")?.takeIf { it.isNotBlank() },
            prevUrl = p.selectFirst("a.dt-pg-prev")
                ?.absUrl("href")?.takeIf { it.isNotBlank() },
        )
    }

    fun parseAzBar(doc: Document): List<AzLink> =
        doc.select(".dt-az-bar a").mapNotNull { a: Element ->
            val label: String = a.text().trim()
            val url: String = a.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (label.isBlank()) null else AzLink(label, url)
        }

    fun parseFilterOptions(doc: Document): FilterOptions = FilterOptions(
        genres = doc.select("#lstGenres option").map { it.attr("value") }
            .filter { it.isNotBlank() },
        years = doc.select("#lstYears option").mapNotNull { it.attr("value").toIntOrNull() },
        studios = doc.select("#lstStudios option").map { it.attr("value") }
            .filter { it.isNotBlank() },
    )

    fun parseCurrentSelection(doc: Document): Triple<List<String>, List<Int>, List<String>> {
        val g = doc.select("#lstGenres option[selected]").map { it.attr("value") }
        val y = doc.select("#lstYears option[selected]").mapNotNull { it.attr("value").toIntOrNull() }
        val s = doc.select("#lstStudios option[selected]").map { it.attr("value") }
        return Triple(g, y, s)
    }

    fun parseGenreBreadcrumb(doc: Document): GenreBreadcrumb? {
        val h: Element = doc.selectFirst("h1.dt-tax-crumb")
            ?: doc.selectFirst("h1.dt-up-crumb")
            ?: return null
        val parent: Element? = h.selectFirst("a:not(.dt-up-crumb-home)")
        return GenreBreadcrumb(
            homeUrl = h.selectFirst("a.dt-up-crumb-home")
                ?.absUrl("href").orEmpty(),
            parentUrl = parent?.absUrl("href")?.takeIf { it.isNotBlank() },
            parentName = parent?.text()?.trim()?.takeIf { it.isNotBlank() },
            currentName = h.selectFirst("span.dt-up-crumb-name")
                ?.text()?.trim().orEmpty(),
        )
    }

    fun parseSearchQuery(doc: Document): String? =
        doc.selectFirst("#dt-search-input")?.attr("value")?.takeIf { it.isNotBlank() }
}
