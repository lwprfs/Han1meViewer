package com.yenaly.han1meviewer.HentaiMama.data.parser

import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.data.model.FavoriteBtn
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePaginator
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.model.SortOption
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseSrcsetMap
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseViews
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.resolveUrl
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.slugFromUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HentaiMamaGenreParser {

    private const val TAG = "HentaiMamaGenreParser"

    private const val CARD_SELECTOR = "article.series-card"
    private const val SORT_BAR_SELECTOR = ".popularity_sort .sort_type a"
    private const val CRUMB_NAME_SELECTOR = ".dt-up-crumb-name"
    private const val CRUMB_PARENT_SELECTOR = ".dt-up-crumb a[href*=/genres-filter/]"
    private const val PAGINATOR_TOP_SELECTOR = ".dt-series-pagination-top .pagination.dt-pg"
    private const val PAGINATOR_ANY_SELECTOR = ".pagination.dt-pg"

    fun parse(
        body: String,
        baseUrl: String,
        slug: String,
        sort: String? = null,
        layout: GenreLayout = GenreLayout.DETAILS,
    ): GenrePage {
        val doc: Document = Jsoup.parse(body, baseUrl)
        val header: GenreHeader = parseHeader(doc, baseUrl, slug, sort)
        val series: List<GenreSeries> = parseSeriesCards(doc, baseUrl)
        val paginator: GenrePaginator? = parsePaginator(doc, baseUrl)
        return GenrePage(
            slug = slug,
            header = header,
            series = series,
            paginator = paginator,
            sort = header.activeSort ?: sort,
            layout = layout,
        )
    }

    fun parseSeriesCards(body: String, baseUrl: String): List<GenreSeries> {
        val doc: Document = Jsoup.parse(body, baseUrl)
        return parseSeriesCards(doc, baseUrl)
    }

    fun parseSeriesCards(doc: Document, baseUrl: String): List<GenreSeries> =
        doc.select(CARD_SELECTOR).mapNotNull { element: Element ->
            runCatching { parseOneCard(element, baseUrl) }
                .onFailure { Log.w(TAG, "Failed to parse genre card: ${it.message}") }
                .getOrNull()
        }

    private fun parseOneCard(el: Element, baseUrl: String): GenreSeries? {

        val poster: Element = el.selectFirst("a.sc-poster")
            ?: el.selectFirst("a.sc-title")
            ?: el.selectFirst("h3.sc-title a")
            ?: return null

        val hrefRaw: String = poster.attr("href")
        val url: String = resolveUrl(hrefRaw, baseUrl)
        if (url.isBlank()) return null
        val slug: String = slugFromUrl(url)
        if (slug.isBlank()) return null

        val titleAnchor: Element? = el.selectFirst(".sc-title a")
            ?: el.selectFirst("h3.sc-title a")
        val title: String = titleAnchor?.text()?.trim().orEmpty()
            .ifBlank { el.selectFirst(".sc-alt")?.text()?.trim().orEmpty() }
        if (title.isBlank()) return null

        val altTitle: String? = el.selectFirst(".sc-alt")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val img: Element? = poster.selectFirst("img")
            ?: el.selectFirst(".sc-poster img")
        val srcsetRaw: String = img?.attr("data-savepage-srcset")
            .orEmpty()
            .ifBlank { img?.attr("srcset").orEmpty() }
        val variants: Map<Int, String> = parseSrcsetMap(srcsetRaw)
        val posterSmall: String? = variants[175] ?: variants[300]
        val posterMid: String? = variants[256] ?: variants[350]
        val posterFull: String = img?.attr("data-savepage-src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("src").orEmpty()
            ?: posterSmall.orEmpty()
        val altText: String = img?.attr("alt").orEmpty()

        val rating: Double? = el.selectFirst(".sc-btn-rating")
            ?.ownText()
            ?.trim()
            ?.toDoubleOrNull()

        val fav: FavoriteBtn? = parseFavorite(el)
        val favorites: Int? = fav?.count
        val favoritePostId: Int? = fav?.postId
        val favoriteNonce: String? = fav?.nonce

        val studioAnchors: List<Element> = el.select(".sc-meta-studios a.sc-tag-studio")
        val studios: List<String> = studioAnchors.map { it.text().trim() }
            .filter { it.isNotBlank() }
        val studioUrls: List<String> = studioAnchors.map { it.absUrl("href") }

        val numMeta: List<String> = el.select(".sc-meta:not(.sc-meta-studios) .sc-tag")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val year: Int? = numMeta.getOrNull(0)
            ?.takeIf { it.length >= 4 }
            ?.filter { it.isDigit() }
            ?.toIntOrNull()

        val viewsRaw: String = numMeta.getOrNull(1).orEmpty()
        val views: Long? = parseViews(viewsRaw)

        val episodeCount: Int? = numMeta.getOrNull(2)
            ?.substringBefore(' ')
            ?.filter { it.isDigit() }
            ?.toIntOrNull()

        val synopsis: String? = el.selectFirst(".sc-desc")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val genreAnchors: List<Element> = el.select(".sc-genres a[rel=tag]")
        val genres: List<String> = genreAnchors.map { it.text().trim() }
            .filter { it.isNotBlank() }
        val genreSlugs: List<String> = genreAnchors.map { slugFromUrl(it.absUrl("href")) }
            .filter { it.isNotBlank() }

        return GenreSeries(
            slug = slug,
            url = url,
            title = title,
            altTitle = altTitle,
            posterSmall = posterSmall,
            posterMid = posterMid,
            posterFull = posterFull,
            altText = altText,
            rating = rating,
            favorites = favorites,
            favoritePostId = favoritePostId,
            favoriteNonce = favoriteNonce,
            studios = studios,
            studioUrls = studioUrls,
            year = year,
            viewsRaw = viewsRaw,
            views = views,
            episodeCount = episodeCount,
            synopsis = synopsis,
            genres = genres,
            genreSlugs = genreSlugs,
        )
    }

    fun parseFavorite(el: Element): FavoriteBtn? {
        val anchor: Element = el.selectFirst(".sc-btn-fav") ?: return null
        val postId: Int = anchor.attr("data-post-id").toIntOrNull() ?: return null
        val nonce: String = anchor.attr("data-nonce").takeIf { it.isNotBlank() }.orEmpty()
        val count: Int = anchor.selectFirst(".sc-fav-n")
            ?.text()
            ?.replace(",", "")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val isFav: Boolean = anchor.hasClass("sc-fav-active") ||
                !anchor.hasClass("sc-fav-login")
        return FavoriteBtn(
            postId = postId,
            nonce = nonce,
            count = count,
            isUserFavorited = isFav,
        )
    }

    fun parseHeader(
        doc: Document,
        baseUrl: String,
        slug: String,
        sortOverride: String? = null,
    ): GenreHeader {
        val name: String = doc.selectFirst(CRUMB_NAME_SELECTOR)
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { slug.replaceFirstChar(Char::uppercaseChar) }

        val parentAnchor: Element? = doc.selectFirst(CRUMB_PARENT_SELECTOR)
        val parentLabel: String? = parentAnchor?.text()?.trim()?.takeIf { it.isNotBlank() }
        val parentUrl: String? = parentAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }

        val sortOptions: List<SortOption> = parseSortBar(doc, baseUrl)
        val activeFromDom: String? = doc.selectFirst(".popularity_sort a.current")
            ?.absUrl("href")
            ?.substringAfter("filter=", "")
            ?.substringBefore('&')
            ?.takeIf { it.isNotBlank() }
        val activeSort: String? = activeFromDom ?: sortOverride

        val paginator: GenrePaginator? = parsePaginator(doc, baseUrl)

        return GenreHeader(
            genreName = name,
            parentLabel = parentLabel,
            parentUrl = parentUrl,
            sortOptions = sortOptions,
            activeSort = activeSort,
            totalPages = paginator?.total ?: 1,
            currentPage = paginator?.current ?: 1,
            templateUrl = paginator?.templateUrl.orEmpty(),
        )
    }

    fun parseSortBar(doc: Document, baseUrl: String): List<SortOption> =
        doc.select(SORT_BAR_SELECTOR).mapNotNull { a: Element ->
            val rawUrl: String = a.absUrl("href").takeIf { it.isNotBlank() }
                ?: resolveUrl(a.attr("href"), baseUrl)
            if (rawUrl.isBlank()) return@mapNotNull null
            val param: String = rawUrl.substringAfter("filter=", "")
                .substringBefore('&')
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SortOption(
                label = a.text().trim(),
                param = param,
                url = rawUrl,
            )
        }.distinctBy { it.param }

    fun parsePaginator(doc: Document, baseUrl: String): GenrePaginator? {
        val p: Element = doc.selectFirst(PAGINATOR_TOP_SELECTOR)
            ?: doc.selectFirst(PAGINATOR_ANY_SELECTOR)
            ?: return null
        val current: Int = p.attr("data-page").toIntOrNull() ?: 1
        val total: Int = p.attr("data-pages").toIntOrNull() ?: 1
        val template: String? = p.attr("data-tpl").takeIf { it.isNotBlank() }
        val next: String? = p.selectFirst("a.dt-pg-next")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        val last: String? = p.selectFirst("a.dt-pg-last")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        val prev: String? = p.selectFirst("a.dt-pg-prev")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        return GenrePaginator(
            current = current,
            total = total,
            templateUrl = template,
            nextUrl = next,
            lastUrl = last,
            prevUrl = prev,
        )
    }

    fun buildGenreUrl(
        baseUrl: String,
        slug: String,
        sort: String?,
        layout: GenreLayout,
        page: Int,
    ): String {
        val root: String = baseUrl.trimEnd('/')
        val path: String = if (page <= 1) {
            "$root/genre/$slug/"
        } else {
            "$root/genre/$slug/page/$page/"
        }
        val params: List<String> = buildList {
            if (!sort.isNullOrBlank()) add("filter=$sort")
            if (layout != GenreLayout.DETAILS) add("layout=${layout.param}")
        }
        return if (params.isEmpty()) path else "$path?${params.joinToString("&")}"
    }
}
