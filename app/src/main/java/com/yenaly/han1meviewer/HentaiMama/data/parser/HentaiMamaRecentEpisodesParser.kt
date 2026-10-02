package com.yenaly.han1meviewer.HentaiMama.data.parser

import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.data.model.RecentEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.RecentEpisodesPage
import com.yenaly.han1meviewer.HentaiMama.data.model.RecentEpisodesPageInfo
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseSrcset
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HentaiMamaRecentEpisodesParser {

    private const val TAG = "HentaiMamaRecent"

    private const val CARD_SELECTOR = "article.item.se.episodes"

    private val FLAG_REGEX = Regex("""flags/(\w+)\.png""")

    fun parse(body: String, baseUrl: String, pageNumber: Int): RecentEpisodesPage {
        val doc = Jsoup.parse(body, baseUrl)
        val items = parseCards(doc)
        val pageInfo = parsePaginator(doc)
        return RecentEpisodesPage(
            pageNumber = pageNumber,
            pageInfo = pageInfo,
            items = items,
        )
    }

    private fun parseCards(doc: Document): List<RecentEpisode> =
        doc.select(CARD_SELECTOR).mapNotNull { element ->
            runCatching { parseCard(element) }
                .onFailure { Log.w(TAG, "Failed to parse recent card: ${it.message}") }
                .getOrNull()
        }

    private fun parseCard(el: Element): RecentEpisode {
        val cover = el.selectFirst("a.dt-ep-cover")
            ?: el.selectFirst("div.data h3 a")
            ?: el.selectFirst("div.season_m a")
            ?: error("no cover link")

        val url = cover.absUrl("href").takeIf { it.isNotBlank() }
            ?: cover.attr("href")
        val slug = url.trimEnd('/').substringAfterLast('/')

        val seriesTitle = el.selectFirst("div.data h3 a")?.text()?.trim().orEmpty()
        val shortTitle = el.selectFirst("div.season_m .b")?.text()?.trim().orEmpty()
        val episodeLabel = el.selectFirst("div.season_m .c")?.text()?.trim().orEmpty()

        val badgeNumber = el.selectFirst("span.dt-ep-badge")
            ?.text()
            ?.filter(Char::isDigit)
            ?.toIntOrNull()

        val episodeNumber = badgeNumber
            ?: Regex("""Episode\s+(\d+)""", RegexOption.IGNORE_CASE)
                .find(episodeLabel)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""Episode\s+(\d+)""", RegexOption.IGNORE_CASE)
                .find(seriesTitle)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 0

        val img = el.selectFirst("div.poster img")
        val srcsetRaw = img?.attr("data-savepage-srcset").orEmpty()
            .ifBlank { img?.attr("srcset").orEmpty() }
        val variants = parseSrcset(srcsetRaw)

        val thumbSmall = variants[300]
        val thumbMedium = variants[400]
        val thumbFull = variants[1280]
            ?: img?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("src").orEmpty()

        val altText = img?.attr("alt").orEmpty()
        val isLazy = img?.hasAttr("data-savepage-loading") == true ||
                img?.attr("loading") == "lazy"

        val rating = el.selectFirst("div.rating")
            ?.text()
            ?.trim()
            ?.toDoubleOrNull()

        val tags = el.select("div.card-tags span.tag")
        val dateText = tags.firstOrNull {
            !it.hasClass("tag-raw") && !it.hasClass("tag-sub")
        }?.text()?.trim().orEmpty()

        val (month, year) = parseMonthYear(dateText)

        val isRaw = el.selectFirst("div.card-tags span.tag-raw") != null
        val isSub = el.selectFirst("div.card-tags span.tag-sub") != null

        val flags = el.select("div.data h3 div.flag").mapNotNull { flagEl ->
            val style = flagEl.attr("style")
            FLAG_REGEX.find(style)?.groupValues?.getOrNull(1)
        }.distinct()

        val postId = el.attr("rel").toIntOrNull()
            ?: el.id().removePrefix("post-").toIntOrNull()
            ?: 0

        return RecentEpisode(
            postId = postId,
            slug = slug,
            url = url,
            seriesTitle = seriesTitle,
            shortSeriesTitle = shortTitle,
            episodeLabel = episodeLabel,
            episodeNumber = episodeNumber,
            thumbSmall = thumbSmall,
            thumbMedium = thumbMedium,
            thumbFull = thumbFull,
            altText = altText,
            rating = rating,
            releaseDate = dateText,
            year = year,
            month = month,
            isRaw = isRaw,
            isSub = isSub,
            languageFlags = flags,
            isLazy = isLazy,
        )
    }

    private fun parseMonthYear(raw: String): Pair<Int, Int> {
        if (raw.isBlank()) return 0 to 0
        val parts = raw.split('/')
        if (parts.size != 2) return 0 to 0
        val month = parts[0].toIntOrNull() ?: 0
        val year = parts[1].toIntOrNull() ?: 0
        return month to year
    }

    fun parsePaginator(doc: Document): RecentEpisodesPageInfo? {
        val p = doc.selectFirst(".pagination.dt-pg") ?: return null
        return RecentEpisodesPageInfo(
            current = p.attr("data-page").toIntOrNull() ?: 1,
            total = p.attr("data-pages").toIntOrNull() ?: 1,
            nextUrl = p.selectFirst("a.dt-pg-next")?.absUrl("href")
                ?.takeIf { it.isNotBlank() },
            lastUrl = p.selectFirst("a.dt-pg-last")?.absUrl("href")
                ?.takeIf { it.isNotBlank() },
        )
    }
}
