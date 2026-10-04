package com.yenaly.han1meviewer.HentaiMama.data.parser

import com.yenaly.han1meviewer.HentaiMama.data.model.GenreBreadcrumb
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePaginator
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.model.PageInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import com.yenaly.han1meviewer.HentaiMama.data.model.SortOption
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.resolveUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HentaiMamaGenreParser {

    private const val CRUMB_NAME_SELECTOR = ".dt-up-crumb-name"
    private const val CRUMB_PARENT_SELECTOR = ".dt-up-crumb a[href*=/genres-filter/]"
    private const val SORT_BAR_SELECTOR = ".popularity_sort .sort_type a"

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
        HentaiMamaSeriesCardParser.parseCards(doc, baseUrl).map { it.toGenreSeries() }

    fun parseUnifiedCards(doc: Document, baseUrl: String): List<SeriesCard> =
        HentaiMamaSeriesCardParser.parseCards(doc, baseUrl)

    private fun SeriesCard.toGenreSeries(): GenreSeries = GenreSeries(
        slug = slug,
        url = url,
        title = title,
        altTitle = altTitles.firstOrNull(),
        posterSmall = thumbSmall,
        posterMid = thumbSmall,
        posterFull = thumbFull,
        altText = posterAlt,
        rating = rating,
        favorites = favorites,
        favoritePostId = postId,
        favoriteNonce = nonce,
        studios = studios,
        studioUrls = studioUrls,
        year = year,
        viewsRaw = viewsRaw.orEmpty(),
        views = views,
        episodeCount = episodeCount,
        synopsis = description,
        genres = genres,
        genreSlugs = genreSlugs,
    )

    fun parseHeader(
        doc: Document,
        baseUrl: String,
        slug: String,
        sortOverride: String? = null,
    ): GenreHeader {
        val crumb: GenreBreadcrumb? = HentaiMamaSeriesCardParser.parseGenreBreadcrumb(doc)
        val name: String = crumb?.currentName?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst(CRUMB_NAME_SELECTOR)?.text()?.trim().orEmpty()
            ?: slug.replaceFirstChar(Char::uppercaseChar)

        val parentAnchor: Element? = doc.selectFirst(CRUMB_PARENT_SELECTOR)
        val parentLabel: String? = parentAnchor?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: crumb?.parentName?.takeIf { it.isNotBlank() }
        val parentUrl: String? = parentAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: crumb?.parentUrl?.takeIf { it.isNotBlank() }

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
        val info: PageInfo = HentaiMamaSeriesCardParser.parsePaginator(doc) ?: return null
        return GenrePaginator(
            current = info.current,
            total = info.total,
            templateUrl = info.templateUrl,
            nextUrl = info.nextUrl,
            lastUrl = info.lastUrl,
            prevUrl = info.prevUrl,
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
        }
        return if (params.isEmpty()) path else "$path?${params.joinToString("&")}"
    }
}
