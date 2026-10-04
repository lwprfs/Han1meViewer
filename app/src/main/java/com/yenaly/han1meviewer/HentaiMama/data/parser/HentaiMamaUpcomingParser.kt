package com.yenaly.han1meviewer.HentaiMama.data.parser

import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingCard
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingMonthOption
import com.yenaly.han1meviewer.HentaiMama.data.model.UpcomingPage
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseSrcset
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.slugFromStudioUrl
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.slugFromUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object HentaiMamaUpcomingParser {

    private const val TAG = "HentaiMamaUpcoming"

    private const val CARD_SELECTOR = ".dt-series-cards article.series-card"

    private val DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    private val EPISODE_NUMBER_REGEX =
        Regex("""Episode\s+(\d+)""", RegexOption.IGNORE_CASE)

    private val MONTH_MAP = mapOf(
        "january" to 1,
        "february" to 2,
        "march" to 3,
        "april" to 4,
        "may" to 5,
        "june" to 6,
        "july" to 7,
        "august" to 8,
        "september" to 9,
        "october" to 10,
        "november" to 11,
        "december" to 12,
    )

    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )

    fun parse(body: String, baseUrl: String): UpcomingPage {
        val doc = Jsoup.parse(body, baseUrl)
        val header = parseHeader(doc)
        val cards = parseCards(doc)
        return UpcomingPage(header = header, cards = cards)
    }

    private fun parseHeader(doc: Document): UpcomingHeader {
        val parentAnchor = doc.selectFirst(
            ".dt-up-crumb a[href*=/upcoming/]:not(.dt-up-crumb-home)"
        )
        val nameText = doc.selectFirst(".dt-up-crumb-name")?.text().orEmpty()
        val parts = nameText.trim().split(' ').filter { it.isNotBlank() }

        val monthName = parts.getOrNull(0).orEmpty()
        val monthNumber = MONTH_MAP[monthName.lowercase()]
        val year = parts.getOrNull(1)?.toIntOrNull()

        return UpcomingHeader(
            parentLabel = parentAnchor?.text().orEmpty(),
            parentUrl = parentAnchor?.absUrl("href").orEmpty(),
            monthName = monthName,
            monthNumber = monthNumber,
            year = year,
        )
    }

    private fun parseCards(doc: Document): List<UpcomingCard> =
        doc.select(CARD_SELECTOR).mapNotNull { element ->
            runCatching { parseCard(element) }
                .onFailure { Log.w(TAG, "Failed to parse upcoming card: ${it.message}") }
                .getOrNull()
        }

    private fun parseCard(element: Element): UpcomingCard {
        val titleAnchor = element.selectFirst(".sc-title a")
            ?: element.selectFirst("a.sc-poster")
            ?: error("no title link")

        val url = titleAnchor.absUrl("href").takeIf { it.isNotBlank() }
            ?: titleAnchor.attr("href")
        val slug = slugFromUrl(url)
        val episodeTitle = titleAnchor.text().trim()

        val episodeNumber = EPISODE_NUMBER_REGEX
            .find(episodeTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val altAnchor = element.selectFirst("a.sc-alt")
        val seriesUrl = altAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
        val seriesSlug = seriesUrl?.let(::slugFromUrl)
        val seriesTitle = altAnchor?.text()?.takeIf { it.isNotBlank() }

        val img = element.selectFirst("a.sc-poster img")
        val srcsetRaw = img?.attr("data-savepage-srcset").orEmpty()
            .ifBlank { img?.attr("srcset").orEmpty() }
        val variants = parseSrcset(srcsetRaw)

        val poster = img?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("src").orEmpty()
        val posterSmall = variants[175] ?: variants[350]
        val altText = img?.attr("alt").orEmpty()

        val rating = element.selectFirst(".sc-btn-rating")
            ?.ownText()
            ?.trim()
            ?.toDoubleOrNull()

        val favAnchor = element.selectFirst(".sc-btn-fav")
        val favoritePostId = favAnchor?.attr("data-post-id")?.toIntOrNull()
            ?.takeIf { it > 0 }
        val favoriteNonce = favAnchor?.attr("data-nonce")?.takeIf { it.isNotBlank() }

        val metaSpans = element.select(".sc-meta > span")

        val airDateRaw = metaSpans
            .firstOrNull { span ->
                span.selectFirst(".sc-ml")?.text()?.contains("Air Date", ignoreCase = false) == true
            }
            ?.selectFirst("b")
            ?.text()
            ?.trim()
            .orEmpty()

        val airDate = airDateRaw.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { LocalDate.parse(raw, DATE_FORMATTER) }.getOrNull()
        }

        val studioAnchor = element.selectFirst(".sc-meta .sc-studio a")
        val studioUrl = studioAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
        val studioSlug = studioUrl?.let(::slugFromStudioUrl)?.takeIf { it.isNotBlank() }
        val studio = studioAnchor?.text()?.takeIf { it.isNotBlank() }

        val synopsis = element.selectFirst(".sc-desc")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val hasReadMore = element.selectFirst(".sc-desc-toggle") != null

        return UpcomingCard(
            slug = slug,
            url = url,
            episodeTitle = episodeTitle,
            episodeNumber = episodeNumber,
            seriesTitle = seriesTitle,
            seriesSlug = seriesSlug,
            seriesUrl = seriesUrl,
            poster = poster,
            posterSmall = posterSmall,
            altText = altText,
            rating = rating,
            favoritePostId = favoritePostId,
            favoriteNonce = favoriteNonce,
            airDateRaw = airDateRaw,
            airDate = airDate,
            studio = studio,
            studioSlug = studioSlug,
            studioUrl = studioUrl,
            synopsis = synopsis,
            hasReadMore = hasReadMore,
        )
    }

    fun buildMonthOptions(
        centerYear: Int,
        centerMonth: Int,
        monthsBefore: Int = 12,
        monthsAfter: Int = 6,
    ): List<UpcomingMonthOption> {
        val options = mutableListOf<UpcomingMonthOption>()
        var year = centerYear
        var month = centerMonth
        for (i in 0 until monthsBefore) {
            options += monthOptionFor(year, month)
            month -= 1
            if (month < 1) {
                month = 12
                year -= 1
            }
        }
        options.reverse()
        year = centerYear
        month = centerMonth
        for (i in 0 until monthsAfter) {
            year += if (month == 12) 1 else 0
            month = if (month == 12) 1 else month + 1
            options += monthOptionFor(year, month)
        }
        return options.distinctBy { it.slug }
    }

    private fun monthOptionFor(year: Int, month: Int): UpcomingMonthOption {
        val name = MONTH_NAMES[(month - 1).coerceIn(0, 11)]
        return UpcomingMonthOption(
            displayName = "${name.replaceFirstChar(Char::uppercaseChar)} $year",
            monthName = name,
            monthNumber = month,
            year = year,
        )
    }

    fun currentMonthOption(): UpcomingMonthOption {
        val now = LocalDate.now()
        val name = MONTH_NAMES[(now.monthValue - 1).coerceIn(0, 11)]
        return UpcomingMonthOption(
            displayName = "${name.replaceFirstChar(Char::uppercaseChar)} ${now.year}",
            monthName = name,
            monthNumber = now.monthValue,
            year = now.year,
        )
    }
}
