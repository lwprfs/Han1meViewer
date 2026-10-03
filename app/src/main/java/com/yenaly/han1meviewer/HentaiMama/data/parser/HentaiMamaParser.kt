package com.yenaly.han1meviewer.HentaiMama.data.parser

import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.HentaiMama.data.model.CommentSummary
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeControls
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeNav
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreRef
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaSource
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoLink
import com.yenaly.han1meviewer.HentaiMama.data.model.Mirror
import com.yenaly.han1meviewer.HentaiMama.data.model.PlayerBlock
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesHero
import com.yenaly.han1meviewer.HentaiMama.data.model.SimilarCard
import com.yenaly.han1meviewer.HentaiMama.data.model.StudioRef
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseSrcset
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.parseViews
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.resolveUrl
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.slugFromStudioUrl
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.slugFromUrl
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaHtmlUtils.unescapeHtml
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale

object HentaiMamaParser {

    private const val TAG = "HentaiMamaParser"

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val EPISODE_NUMBER_REGEX: Regex =
        Regex("""Episode\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val EP_BADGE_REGEX: Regex = Regex("""(\d+(?:\.\d+)?)""")
    private val EPISODE_DATE_FORMAT: SimpleDateFormat =
        SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private val EPISODE_DATE_FORMAT_LONG: SimpleDateFormat =
        SimpleDateFormat("MMM. dd, yyyy", Locale.US)

    private val SOURCES_ARRAY_REGEX: Regex =
        Regex("""sources:\s*(\[.+?])""", RegexOption.DOT_MATCHES_ALL)
    private val SOURCES_QUOTED_REGEX: Regex =
        Regex("""["']sources["']\s*:\s*(\[.+?])""", RegexOption.DOT_MATCHES_ALL)
    private val IDPOST_REGEX: Regex =
        Regex("""["']?idpost["']?\s*[:=]\s*["']?(\d+)""", RegexOption.IGNORE_CASE)

    private val MP4_URL_REGEX: Regex =
        Regex("""https?://[^"'\s<>\\]+\.(?:mp4|m3u8)[^"'\s<>\\]*""", RegexOption.IGNORE_CASE)
    private val BASE64_P_REGEX: Regex = Regex("""[?&]p=([^&]+)""")
    private val RESOLUTION_SEGMENT: Regex = Regex("""/(\d+)p/""")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun parseVideoList(body: String): PageLoadingState<List<HanimeInfo>> {
        return try {
            val base: String = HentaiMamaNetwork.baseUrl
            val videos: List<HanimeInfo> = parseCardList(body, base)
            if (videos.isEmpty()) PageLoadingState.NoMoreData
            else PageLoadingState.Success(videos)
        } catch (e: Exception) {
            PageLoadingState.Error(e)
        }
    }

    fun parseSearchResults(
        body: String,
        isFilterSearch: Boolean,
    ): PageLoadingState<List<HanimeInfo>> {
        return try {
            val base: String = HentaiMamaNetwork.baseUrl
            val videos: List<HanimeInfo> = parseCardList(body, base)
            if (videos.isEmpty()) PageLoadingState.NoMoreData
            else PageLoadingState.Success(videos)
        } catch (e: Exception) {
            PageLoadingState.Error(e)
        }
    }

    fun parseCardList(body: String, baseUrl: String): List<HanimeInfo> {
        return try {
            val doc: Document = Jsoup.parse(body, baseUrl)
            val cards = doc.select("article.series-card")
            if (cards.isNotEmpty()) {
                return cards.mapNotNull { parseSeriesCard(it, baseUrl) }
            }
            val altCards = doc.select(".dt-series-cards article")
            if (altCards.isNotEmpty()) {
                return altCards.mapNotNull { parseSeriesCard(it, baseUrl) }
            }
            doc.select("a.sc-poster").mapNotNull { a ->
                runCatching {
                    val href = a.absUrl("href").takeIf { it.isNotBlank() }
                        ?: return@runCatching null
                    val slug = href.trimEnd('/').substringAfterLast('/')
                    if (slug.isBlank()) return@runCatching null
                    val title = a.selectFirst("h3.sc-title a")?.text()
                        ?: a.selectFirst("h3")?.text()
                        ?: a.attr("title").takeIf { it.isNotBlank() }
                        ?: return@runCatching null
                    val img = a.selectFirst("img")
                    val cover = img?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
                        ?: img?.absUrl("src").orEmpty()
                    HanimeInfo(
                        title = title,
                        coverUrl = cover,
                        videoCode = slug,
                        itemType = HanimeInfo.NORMAL,
                    )
                }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseCardList failed for $baseUrl", e)
            emptyList()
        }
    }

    private fun parseSeriesCard(el: Element, baseUrl: String): HanimeInfo? {
        return try {
            val poster = el.selectFirst("a.sc-poster")
                ?: el.selectFirst("a.sc-title")
                ?: el.selectFirst("h3.sc-title a")
                ?: return null

            val rawHref = poster.attr("href")
            val url = if (rawHref.startsWith("http")) rawHref
            else baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
            val slug = url.trimEnd('/').substringAfterLast('/')
            if (slug.isBlank()) return null

            val titleLink = el.selectFirst("a.sc-title") ?: el.selectFirst("h3.sc-title a")
            val title = titleLink?.text()?.trim().orEmpty()
                .ifBlank { el.selectFirst(".sc-alt")?.text()?.trim().orEmpty() }
            if (title.isBlank()) return null

            val img = poster.selectFirst("img") ?: el.selectFirst(".sc-poster img")
            val srcsetRaw = img?.attr("data-savepage-srcset").orEmpty()
                .ifBlank { img?.attr("srcset").orEmpty() }
            val variants = parseSrcsetMap(srcsetRaw)
            val posterSmall = variants[175] ?: variants[300]
            val posterFull = img?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                ?: img?.absUrl("src").orEmpty()
                ?: posterSmall.orEmpty()

            val rating: Double? = el.selectFirst(".sc-btn-rating")
                ?.ownText()?.trim()?.toDoubleOrNull()

            val favAnchor = el.selectFirst(".sc-btn-fav")
            val favorites: Int? = favAnchor?.selectFirst(".sc-fav-n")
                ?.text()?.replace(",", "")?.trim()?.toIntOrNull()

            val studioAnchors = el.select(".sc-meta-studios a.sc-tag-studio")
            val studios: List<String> = studioAnchors.map { it.text().trim() }
                .filter { it.isNotBlank() }

            val metaSpans: List<String> = el.select(".sc-meta:not(.sc-meta-studios) .sc-tag")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            val year: Int? = metaSpans.getOrNull(0)
                ?.takeIf { it.length in 4..5 && it.all(Char::isDigit) }
                ?.toIntOrNull()

            val viewsRaw: String = metaSpans.getOrNull(1).orEmpty()
            val views: Long? = parseViews(viewsRaw)

            val episodeCount: Int? = metaSpans.getOrNull(2)
                ?.substringBefore(' ')
                ?.filter(Char::isDigit)
                ?.toIntOrNull()

            val synopsis: String? = el.selectFirst(".sc-desc")
                ?.text()?.trim()?.takeIf { it.isNotBlank() }

            val genreAnchors = el.select(".sc-genres a[rel=tag]")
            val genres: List<String> = genreAnchors.map { it.text().trim() }
                .filter { it.isNotBlank() }

            HanimeInfo(
                title = title,
                coverUrl = posterFull,
                videoCode = slug,
                duration = episodeCount?.let { "$it eps" },
                views = viewsRaw.takeIf { it.isNotBlank() },
                uploadTime = year?.toString(),
                genre = genres.firstOrNull(),
                reviews = rating?.let { "%.1f".format(it) },
                currentArtist = studios.firstOrNull(),
                itemType = HanimeInfo.NORMAL,
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseSeriesCard failed", e)
            null
        }
    }

    private fun parseViews(raw: String): Long? {
        val t = raw.trim().replace(",", "")
        if (t.isEmpty()) return null
        return when {
            t.endsWith("K", ignoreCase = true) ->
                t.dropLast(1).toDoubleOrNull()?.let { (it * 1_000).toLong() }
            t.endsWith("M", ignoreCase = true) ->
                t.dropLast(1).toDoubleOrNull()?.let { (it * 1_000_000).toLong() }
            t.endsWith("B", ignoreCase = true) ->
                t.dropLast(1).toDoubleOrNull()?.let { (it * 1_000_000_000).toLong() }
            else -> t.toLongOrNull()
        }
    }

    private fun parseSrcsetMap(raw: String): Map<Int, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<Int, String>()
        raw.split(',').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val pieces = trimmed.split(' ').filter { it.isNotBlank() }
            if (pieces.size < 2) return@forEach
            val url = pieces[0]
            val token = pieces[1].trim()
            if (!token.endsWith("w", ignoreCase = true)) return@forEach
            val width = token.dropLast(1).toIntOrNull() ?: return@forEach
            if (url.isNotBlank() && width > 0) out[width] = url
        }
        return out
    }

    fun parseSeriesDetail(body: String, url: String): VideoLoadingState<SeriesDetailPage> {
        return try {
            val base: String = if (url.startsWith("http")) url
            else HentaiMamaNetwork.normalizeUrl(url)
            val doc: Document = Jsoup.parse(body, base)
            val hero: SeriesHero = parseSeriesHero(doc, base)
                ?: return VideoLoadingState.Error(
                    IllegalStateException("Series hero not found")
                )
            val episodes: List<HentaiMamaEpisode> =
                parseEpisodeListFromRoot(doc, "#episodes .dt-se-item", base)
            val totalEpisodes: Int = doc.selectFirst("#episodes h2 span")
                ?.text()
                ?.filter(Char::isDigit)
                ?.toIntOrNull()
                ?: episodes.size
            val similar: List<SimilarCard> =
                parseSimilarCards(doc, ".ep-similar-page", base)
            val cast: List<List<Pair<String, String>>> =
                doc.select("#cast .persons").map { persons: Element ->
                    persons.select("a").map { a: Element ->
                        a.text() to a.absUrl("href")
                    }
                }
            val trailer: String? = doc.selectFirst("#trailer .embed iframe")?.attr("src")
            VideoLoadingState.Success(
                SeriesDetailPage(
                    hero = hero,
                    episodes = episodes,
                    totalEpisodes = totalEpisodes,
                    similar = similar,
                    cast = cast,
                    trailerUrl = trailer,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseSeriesDetail error", e)
            VideoLoadingState.Error(e)
        }
    }

    fun parseEpisodeDetail(body: String, url: String): VideoLoadingState<EpisodeDetailPage> {
        return try {
            val base: String = if (url.startsWith("http")) url
            else HentaiMamaNetwork.normalizeUrl(url)
            val doc: Document = Jsoup.parse(body, base)

            val info: EpisodeInfo = parseEpisodeInfo(doc, base)
                ?: return VideoLoadingState.Error(
                    IllegalStateException("Episode info card not found")
                )

            val player: PlayerBlock = parsePlayerBlock(doc, base)
            val nav: EpisodeNav = parseEpisodeNav(doc, base)
            val controls: EpisodeControls = parseEpisodeControls(doc)
            val sidebar: List<HentaiMamaEpisode> = parseEpisodeListFromRoot(
                doc, "#dtw_series_episodes-2 .dt-se-item", base,
            )
            val sidebarCount: String? = doc.selectFirst(".dt-se-count")?.text()
            val sidebarStatus: String? =
                doc.selectFirst(".dt-se-list[data-status]")?.attr("data-status")
            val similar: List<SimilarCard> = parseSimilarCards(doc, ".ep-similar", base)
            val comments: CommentSummary = parseCommentSummary(doc)

            VideoLoadingState.Success(
                EpisodeDetailPage(
                    info = info,
                    player = player,
                    nav = nav,
                    controls = controls,
                    seriesSidebar = sidebar,
                    seriesSidebarCount = sidebarCount,
                    seriesSidebarStatus = sidebarStatus,
                    similar = similar,
                    comments = comments,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseEpisodeDetail error", e)
            VideoLoadingState.Error(e)
        }
    }

    private fun parseSeriesHero(doc: Document, base: String): SeriesHero? {
        val box: Element = doc.selectFirst(".sbox.dt-show-card") ?: return null

        fun statByLabel(label: String): String? =
            box.selectFirst(".dsc-stat:has(span:contains($label)) b")?.text()

        val studioAnchor: Element? =
            box.selectFirst(".dsc-stat:has(span:contains(Studio)) b a")
        val studioUrl: String? = studioAnchor?.absUrl("href")
        val studios: List<StudioRef> = if (studioAnchor != null && studioUrl != null) {
            listOf(
                StudioRef(
                    name = studioAnchor.text(),
                    slug = slugFromStudioUrl(studioUrl),
                    url = studioUrl,
                )
            )
        } else {
            emptyList()
        }

        val rating: Element? = box.selectFirst(".starstruck-main")
        val fav: Element? = box.selectFirst(".sc-btn-fav")

        val genres: List<GenreRef> = box.select(".dsc-genres a").mapNotNull { a: Element ->
            val u: String = a.absUrl("href")
            if (u.isBlank()) null
            else GenreRef(a.text(), slugFromUrl(u), u)
        }

        val slug: String = doc.selectFirst("link[rel=canonical]")
            ?.attr("href")
            ?.let { canonical: String -> slugFromUrl(canonical) }
            ?: slugFromUrl(base)

        return SeriesHero(
            postId = rating?.attr("data-id")?.toIntOrNull() ?: 0,
            slug = slug,
            title = box.selectFirst(".dsc-title")?.text().orEmpty(),
            altTitle = box.selectFirst(".dsc-alt")?.text()?.takeIf { it.isNotBlank() },
            poster = box.selectFirst(".dsc-poster img")
                ?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
                ?: box.selectFirst(".dsc-poster img")?.absUrl("src").orEmpty(),
            score = statByLabel("Score")
                ?.filter { it.isDigit() || it == '.' }
                ?.toDoubleOrNull(),
            views = statByLabel("Views")?.replace(",", "")?.toLongOrNull(),
            episodeCount = statByLabel("Episodes")?.toIntOrNull(),
            duration = statByLabel("Duration"),
            aired = statByLabel("Aired"),
            studios = studios,
            statusChips = box.select(".dsc-chips .dsc-chip").map { it.text() },
            ratingValue = box.selectFirst(".dt_rating_vgs")?.text()?.toDoubleOrNull(),
            voteCount = box.selectFirst(".rating-count")?.text()?.toIntOrNull(),
            genres = genres,
            watchEp1Url = box.selectFirst(".dsc-play")?.absUrl("href"),
            favorites = fav?.selectFirst(".sc-fav-n")
                ?.text()
                ?.replace(",", "")
                ?.toIntOrNull(),
            favoriteNonce = fav?.attr("data-nonce")?.takeIf { it.isNotBlank() },
            shareCount = box.selectFirst("#social_count")?.text()?.toIntOrNull(),
            synopsisHtml = box.selectFirst(".dsc-desc")?.html(),
            synopsisText = box.selectFirst(".dsc-desc p")
                ?.text()
                ?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseEpisodeInfo(doc: Document, base: String): EpisodeInfo? {
        val box: Element = doc.selectFirst("#info.episode-info-card") ?: return null

        fun metaField(label: String): Element? =
            box.selectFirst(
                ".ep-meta-field:has(.ep-meta-label:contains($label)) .ep-meta-value"
            )

        val studioElement: Element? = metaField("Studio")
        val playlistAnchor: Element? = box.selectFirst(".dt-pl-open")

        val previews: List<String> = box.select(".galeria .g-item a[href]")
            .mapNotNull { a: Element ->
                val u: String = a.absUrl("href")
                u.takeIf { it.isNotBlank() }
            }

        val columnsRaw: String = box.selectFirst(".galeria")?.attr("style").orEmpty()
        val columns: Int = Regex("""--gal-cols:\s*(\d+)""")
            .find(columnsRaw)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: 6

        val genres: List<GenreRef> = box.select(".ep-genres a[rel=tag]")
            .mapNotNull { a: Element ->
                val u: String = a.absUrl("href")
                if (u.isBlank()) null else GenreRef(a.text(), slugFromUrl(u), u)
            }

        val slug: String = doc.selectFirst("link[rel=canonical]")
            ?.attr("href")
            ?.let { canonical: String -> slugFromUrl(canonical) }
            ?: slugFromUrl(base)

        val shortSyn: String? = box.selectFirst(".episode-desc > span:first-child")
            ?.text()
            ?.takeIf { it.isNotBlank() }
        val longSyn: String? = box.selectFirst(".ep-desc-more")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return EpisodeInfo(
            postId = box.selectFirst(".starstruck-main")
                ?.attr("data-id")
                ?.toIntOrNull()
                ?: 0,
            slug = slug,
            title = box.selectFirst(".epih1")?.text().orEmpty(),
            seriesUrl = box.selectFirst(".episode-series-img a")?.absUrl("href"),
            seriesPoster = box.selectFirst(".episode-series-img img")
                ?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
                ?: box.selectFirst(".episode-series-img img")?.absUrl("src").orEmpty(),
            rating = box.selectFirst(".dt_rating_vgs")?.text()?.toDoubleOrNull(),
            voteCount = box.selectFirst(".rating-count")?.text()?.toIntOrNull(),
            genres = genres,
            views = metaField("Views")?.text()?.replace(",", "")?.toLongOrNull(),
            studio = studioElement?.text()?.takeIf { it.isNotBlank() },
            studioUrl = studioElement?.selectFirst("a")?.absUrl("href"),
            airedOn = metaField("Aired On")?.text()?.takeIf { it.isNotBlank() },
            addedOn = metaField("Added")?.text()?.takeIf { it.isNotBlank() },
            synopsisShort = shortSyn,
            synopsisLong = longSyn,
            playlistPostId = playlistAnchor?.attr("data-post-id")?.toIntOrNull(),
            playlistTitle = playlistAnchor?.attr("data-title")
                ?.takeIf { it.isNotBlank() },
            shareCount = box.selectFirst("#social_count")?.text()?.toIntOrNull(),
            previewUrls = previews,
            galleryColumns = columns,
        )
    }

    private fun parsePlayerBlock(doc: Document, base: String): PlayerBlock {
        val iframe: Element? = doc.selectFirst("#playex iframe")
            ?: doc.selectFirst("div[id=playex] iframe")
            ?: doc.selectFirst(".playex iframe")
            ?: doc.selectFirst("div[class*=playex] iframe")
            ?: doc.selectFirst("div[class*=player_sist] iframe")
            ?: doc.selectFirst("iframe[data-savepage-src*=\"dt_embed\"]")
            ?: doc.selectFirst("iframe[src*=\"dt_embed\"]")
            ?: doc.selectFirst("iframe[title]")

        val src: String = iframe?.attr("data-savepage-src").orEmpty().ifBlank {
            iframe?.absUrl("src").orEmpty()
        }
        val base64: String? = BASE64_P_REGEX.find(src)?.groupValues?.getOrNull(1)

        val srcdoc: String = unescapeHtml(iframe?.attr("srcdoc"))
        val mp4: String? = MP4_URL_REGEX.find(srcdoc)?.value
            ?: MP4_URL_REGEX.find(base)?.value

        val mirrorsFromTabs: List<Mirror> = doc.select(".dt-mi-tabs li a")
            .map { a: Element ->
                Mirror(
                    label = a.text(),
                    optionId = a.attr("href").removePrefix("#"),
                    isActive = a.hasClass("selected"),
                )
            }
        val mirrors: List<Mirror> = if (mirrorsFromTabs.isNotEmpty()) {
            mirrorsFromTabs
        } else {
            doc.select("select.dt-mi-select option").mapIndexed { idx: Int, opt: Element ->
                Mirror(
                    label = opt.text(),
                    optionId = opt.attr("value")
                        .removePrefix("#")
                        .ifBlank { "option-$idx" },
                    isActive = idx == 0,
                )
            }
        }

        val decodedPath: String? = base64?.let { it: String -> decodeBase64Safely(it) }
        val mp4ByQuality: Map<String, String> = buildQualityMap(mp4, decodedPath)

        Log.d(
            "PlayerParse",
            "iframe=${iframe != null} " +
                    "srcLen=${src.length} " +
                    "srcHead=${src.take(80)} " +
                    "base64=${base64 != null} " +
                    "srcdocLen=${srcdoc.length} " +
                    "mp4=${mp4?.take(80)} " +
                    "mirrors=${mirrors.size} " +
                    "qualityMapKeys=${mp4ByQuality.keys} " +
                    "iframeCount=${doc.select("iframe").size} " +
                    "playexExists=${doc.selectFirst("#playex") != null} " +
                    "playexDivExists=${doc.selectFirst(".playex") != null}"
        )

        return PlayerBlock(
            mirrors = mirrors,
            embedUrl = src.takeIf { it.isNotBlank() },
            embedBase64 = base64,
            playerTitle = iframe?.attr("title")?.takeIf { it.isNotBlank() },
            aspect = doc.selectFirst(".jw-aspect[style]")?.attr("style"),
            duration = doc.selectFirst(".jw-text-duration")
                ?.text()
                ?.takeIf { it.isNotBlank() },
            qualities = doc.select(".dt-ctl-quality-menu button").map { it.text() },
            speeds = doc.select(".dt-ctl-speed-menu button").map { it.text() },
            mp4Url = mp4,
            mp4UrlByQuality = mp4ByQuality,
        )
    }

    private fun buildQualityMap(
        mp4: String?,
        decodedPath: String?,
    ): Map<String, String> {
        val source: String = mp4 ?: decodedPath ?: return emptyMap()
        if (!RESOLUTION_SEGMENT.containsMatchIn(source)) return emptyMap()
        val qualities: List<String> = listOf("1080p", "720p", "480p", "360p")
        val result: LinkedHashMap<String, String> = LinkedHashMap()
        for (q in qualities) {
            val replaced: String = source.replace(RESOLUTION_SEGMENT, "/$q/")
            if (replaced != source || source.contains("/1080p/")) {
                result[q] = replaced
            }
        }
        return result
    }

    private fun decodeBase64Safely(input: String): String? = runCatching {
        val normalized: String = input.replace('-', '+').replace('_', '/')
        val padded: String = when (normalized.length % 4) {
            0 -> normalized
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> return@runCatching null
        }
        String(Base64.getDecoder().decode(padded))
    }.getOrNull()

    private fun parseEpisodeNav(doc: Document, base: String): EpisodeNav {
        val items: List<Element> = doc.select("#pag_episodes .item")
        val prev: String? = items.getOrNull(0)
            ?.selectFirst("a[href]")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        val next: String? = items.getOrNull(2)
            ?.selectFirst("a[href]:not(.nonex)")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        val series: String? = doc.selectFirst("#pag_episodes a[aria-label*=list]")
            ?.absUrl("href")
        return EpisodeNav(prevUrl = prev, seriesUrl = series, nextUrl = next)
    }

    private fun parseEpisodeControls(doc: Document): EpisodeControls = EpisodeControls(
        previewsOpen = doc.selectFirst(".dt-pv-open") != null,
        playlistAdd = doc.selectFirst(".dt-pl-open") != null,
        downloadOpen = doc.selectFirst(".dt-dl-open") != null,
        lightToggle = doc.selectFirst(".lightSwitcher") != null,
        reportOpen = doc.selectFirst(".report-video") != null,
        wideToggle = doc.selectFirst(".wide.reco") != null,
    )

    private fun parseCommentSummary(doc: Document): CommentSummary {
        val count: Int = doc.selectFirst(".wpd-thread-info")
            ?.attr("data-comments-count")
            ?.toIntOrNull()
            ?: doc.selectFirst(".wpdtc")?.text()?.toIntOrNull()
            ?: 0
        val loggedIn: Boolean = doc.selectFirst("#wpdcom.wpdiscuz_auth") != null
        return CommentSummary(count = count, isLoggedIn = loggedIn)
    }

    private fun parseEpisodeListFromRoot(
        doc: Document,
        rootSelector: String,
        base: String,
    ): List<HentaiMamaEpisode> {
        data class Indexed(val index: Int, val episode: HentaiMamaEpisode)

        val indexed: List<Indexed> = doc.select(rootSelector)
            .mapIndexedNotNull { index: Int, element: Element ->
                runCatching {
                    val href: String = element.attr("href")
                    if (href.isBlank()) return@runCatching null
                    val absoluteHref: String = resolveUrl(href, base)
                    val titleText: String =
                        element.selectFirst(".dt-se-title")?.text()
                            ?: element.selectFirst(".dt-se-toprow")?.text()
                            ?: return@runCatching null
                    val badgeText: String? = element.selectFirst(".dt-se-num")?.text()
                    val epNumFromBadge: Float? = badgeText?.let { it: String ->
                        EP_BADGE_REGEX.find(it)
                            ?.groupValues
                            ?.get(1)
                            ?.toFloatOrNull()
                    }
                    val epNumFromTitle: Float? = EPISODE_NUMBER_REGEX.find(titleText)
                        ?.groupValues
                        ?.get(1)
                        ?.toFloatOrNull()
                    val epNum: Float? = epNumFromBadge ?: epNumFromTitle
                    val dateText: String? = element.selectFirst(".dt-se-date")?.text()
                    val dateTs: Long = parseEpisodeDate(dateText)
                    val img: Element? = element.selectFirst(".dt-se-thumb img")
                    val thumb: String = img?.attr("data-savepage-src")
                        ?.takeIf { it.isNotBlank() }
                        ?: img?.absUrl("src").orEmpty()
                    val variants: Map<Int, String> =
                        parseSrcset(img?.attr("data-savepage-srcset"))
                    val rating: Double? = element.selectFirst(".dt-se-rating")
                        ?.ownText()
                        ?.trim()
                        ?.toDoubleOrNull()
                    val synopsis: String? = element.selectFirst(".dt-se-desc")
                        ?.text()
                        ?.takeIf {
                            it.isNotBlank() &&
                                    !it.contains("No synopsis", ignoreCase = true)
                        }
                    val isCurrent: Boolean = element.hasClass("current")

                    Indexed(
                        index = index,
                        episode = HentaiMamaEpisode(
                            title = titleText,
                            url = absoluteHref,
                            slug = slugFromUrl(absoluteHref),
                            date = dateText?.takeIf { it.isNotBlank() },
                            episodeNumber = epNum?.takeIf { it > 0f },
                            dateTimestamp = dateTs,
                            thumb = thumb,
                            thumbVariants = variants,
                            rating = rating,
                            synopsis = synopsis,
                            isCurrent = isCurrent,
                        ),
                    )
                }.getOrNull()
            }

        return indexed
            .sortedWith(
                compareBy<Indexed> { it.episode.episodeNumber ?: Float.MAX_VALUE }
                    .thenBy { it.index }
            )
            .map { it.episode }
    }

    private fun parseEpisodeDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val patterns: List<SimpleDateFormat> =
            listOf(EPISODE_DATE_FORMAT, EPISODE_DATE_FORMAT_LONG)
        for (fmt in patterns) {
            val parsed: Long? = runCatching { fmt.parse(raw)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    private fun parseSimilarCards(
        doc: Document,
        rootSelector: String,
        base: String,
    ): List<SimilarCard> {
        val result: MutableList<SimilarCard> = mutableListOf()
        val elements: List<Element> = doc.select("$rootSelector a.ep-sim-card")
        for (el in elements) {
            val card: SimilarCard? = runCatching {
                val url: String = el.absUrl("href").takeIf { it.isNotBlank() }
                    ?: resolveUrl(el.attr("href"), base)
                if (url.isBlank()) return@runCatching null
                val img: Element? = el.selectFirst(".ep-sim-poster img")
                val variants: Map<Int, String> =
                    parseSrcset(img?.attr("data-savepage-srcset"))
                val metas: List<String> = el.select(".ep-sim-meta > span")
                    .map { it: Element -> it.text() }

                SimilarCard(
                    slug = slugFromUrl(url),
                    url = url,
                    name = el.selectFirst(".ep-sim-name")?.text().orEmpty(),
                    altTitle = el.selectFirst(".ep-sim-alt")
                        ?.text()
                        ?.takeIf { it.isNotBlank() },
                    poster = img?.attr("data-savepage-src")?.takeIf { it.isNotBlank() }
                        ?: img?.absUrl("src").orEmpty(),
                    posterSmall = variants[175] ?: variants[350],
                    rating = el.selectFirst(".ep-sim-rating")
                        ?.ownText()
                        ?.trim()
                        ?.toDoubleOrNull(),
                    synopsis = el.selectFirst(".ep-sim-desc")
                        ?.text()
                        ?.takeIf { it.isNotBlank() },
                    year = metas.getOrNull(0)?.toIntOrNull(),
                    views = metas.getOrNull(1)?.let { it: String -> parseViews(it) },
                    episodeCount = metas.getOrNull(2)
                        ?.substringBefore(' ')
                        ?.toIntOrNull(),
                )
            }.getOrNull()
            if (card != null) result.add(card)
        }
        return result
    }

    fun parseVideoDetail(
        body: String,
        url: String,
    ): VideoLoadingState<HentaiMamaVideoInfo> {
        val result: VideoLoadingState<EpisodeDetailPage> = parseEpisodeDetail(body, url)
        return when (result) {
            is VideoLoadingState.Success -> {
                val page: EpisodeDetailPage = result.info
                val episodeInfo: EpisodeInfo = page.info
                val player: PlayerBlock = page.player
                val sidebar: List<HentaiMamaEpisode> = page.seriesSidebar

                val links: List<HentaiMamaVideoLink> = player.mp4UrlByQuality
                    .map { entry: Map.Entry<String, String> ->
                        val quality: String = entry.key
                        val mp4: String = entry.value
                        HentaiMamaVideoLink(
                            quality = quality.replaceFirstChar(Char::uppercaseChar),
                            url = mp4,
                            type = if (mp4.contains(".m3u8")) "hls" else "mp4",
                            label = quality,
                        )
                    }
                    .ifEmpty {
                        val single: String? = player.mp4Url
                        if (single.isNullOrBlank()) {
                            emptyList()
                        } else {
                            listOf(
                                HentaiMamaVideoLink(
                                    quality = "Default",
                                    url = single,
                                    type = if (single.contains(".m3u8")) "hls" else "mp4",
                                    label = "Default",
                                )
                            )
                        }
                    }

                val related: List<HanimeInfo> = page.similar.map { sim: SimilarCard ->
                    HanimeInfo(
                        title = sim.name,
                        coverUrl = sim.poster,
                        videoCode = sim.slug,
                        itemType = HanimeInfo.NORMAL,
                    )
                }

                VideoLoadingState.Success(
                    HentaiMamaVideoInfo(
                        title = episodeInfo.title,
                        coverUrl = episodeInfo.seriesPoster,
                        videoCode = episodeInfo.slug,
                        url = url,
                        description = listOfNotNull(
                            episodeInfo.synopsisShort,
                            episodeInfo.synopsisLong,
                        ).joinToString(" ").takeIf { it.isNotBlank() },
                        genre = episodeInfo.genres.joinToString(", ") { it.name }
                            .takeIf { it.isNotBlank() },
                        author = episodeInfo.studio,
                        status = page.seriesSidebarStatus,
                        videoUrls = links,
                        episodes = sidebar,
                        relatedVideos = related,
                        page = page,
                    )
                )
            }
            is VideoLoadingState.Error -> result
            is VideoLoadingState.Loading -> result
            is VideoLoadingState.NoContent -> result
        }
    }

    fun videoListParse(
        detailPageBody: String,
        baseUrl: String,
        apiUrl: String,
        optionNumber: Int,
    ): List<HentaiMamaVideoLink> {
        return try {
            val document: Document = Jsoup.parse(detailPageBody, baseUrl)
            val postId: String? = extractPostId(document, detailPageBody)
            if (postId.isNullOrBlank()) {
                Log.e(TAG, "videoListParse: idpost not found")
                return emptyList()
            }
            val body: FormBody = FormBody.Builder()
                .add("action", HentaiMamaConstants.ACTION_PLAYER)
                .add("a", postId)
                .add("i", optionNumber.toString())
                .build()
            val request: Request = Request.Builder()
                .url(apiUrl)
                .post(body)
                .headers(Headers.headersOf("referer", "$baseUrl/"))
                .build()
            val response = httpClient.newCall(request).execute()
            val responseString: String = response.body.string()
            response.close()
            if (responseString.isBlank()) return emptyList()

            val fragments: List<String> = try {
                json.decodeFromString(responseString)
            } catch (_: Exception) {
                listOf(responseString)
            }

            val fragment: String = fragments.getOrNull(optionNumber - 1)
                ?.takeIf { it.isNotBlank() }
                ?: fragments.firstOrNull { it.isNotBlank() }
                ?: return emptyList()

            val iframeSrc: String = extractIframeSrc(fragment, baseUrl)
                ?: return emptyList()
            val playerRequest: Request = Request.Builder()
                .url(iframeSrc)
                .addHeader("Referer", baseUrl)
                .build()
            val playerResponse = httpClient.newCall(playerRequest).execute()
            val playerBody: String = playerResponse.body.string()
            playerResponse.close()
            if (playerBody.isBlank()) return emptyList()

            val sourcesJson: String = SOURCES_ARRAY_REGEX.find(playerBody)
                ?.groupValues
                ?.get(1)
                ?: SOURCES_QUOTED_REGEX.find(playerBody)?.groupValues?.get(1)
                ?: return emptyList()

            val sources: List<HentaiMamaSource> = runCatching {
                json.decodeFromString<List<HentaiMamaSource>>(sourcesJson)
            }.getOrElse {
                Log.e(TAG, "videoListParse: sources decode failed", it)
                return emptyList()
            }

            sources.mapNotNull { source: HentaiMamaSource ->
                val file: String = source.file.replace("\\/", "/")
                if (file.isBlank()) return@mapNotNull null
                val isHls: Boolean = source.type == "hls" || file.contains(".m3u8")
                HentaiMamaVideoLink(
                    quality = source.label ?: if (isHls) "HLS" else "Video",
                    url = file,
                    type = source.type ?: if (isHls) "hls" else "mp4",
                    label = source.label,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "videoListParse error for option=$optionNumber", e)
            emptyList()
        }
    }

    private fun extractPostId(document: Document, body: String): String? {
        val selectors: List<String> = listOf(
            "#post_report input[name=idpost]",
            "input[name=idpost]",
            "input[name=post_id]",
            "#post_id",
            "meta[name=post_id]",
            "input[name=post-id-hidden]",
        )
        for (sel in selectors) {
            val value: String? = document.selectFirst(sel)?.let { el: Element ->
                el.attr("value")
                    .ifBlank { el.attr("content") }
                    .takeIf { it.isNotBlank() }
            }
            if (value != null) return value
        }
        return IDPOST_REGEX.find(body)?.groupValues?.getOrNull(1)
    }

    private fun extractIframeSrc(fragment: String, base: String): String? {
        val parsed: Document = Jsoup.parseBodyFragment(fragment, base)
        val iframe: Element = parsed.selectFirst("iframe") ?: return null
        val src: String? = iframe.absUrl("src").takeIf { it.isNotBlank() }
            ?: iframe.attr("src")
                .let { it: String -> resolveUrl(it, base) }
                .takeIf { it.isNotBlank() }
        return src
    }

    fun hosterTabs(detailPageBody: String): List<Pair<String, Int>> {
        val doc: Document = Jsoup.parse(detailPageBody)
        val tabs: List<Pair<String, Int>> = doc.select(".dt-mi-tabs a")
            .mapNotNull { tab: Element ->
                val optionNumber: String = tab.attr("href").removePrefix("#option-")
                val label: String = tab.text()
                if (optionNumber.isBlank() || label.isBlank()) {
                    null
                } else {
                    label to (optionNumber.toIntOrNull() ?: return@mapNotNull null)
                }
            }
        if (tabs.isNotEmpty()) return tabs
        return doc.select("select.dt-mi-select option")
            .mapIndexedNotNull { idx: Int, opt: Element ->
                val label: String = opt.text()
                val optionNumber: Int = opt.attr("value")
                    .removePrefix("#option-")
                    .ifBlank { opt.attr("value").removePrefix("#") }
                    .toIntOrNull()
                    ?: (idx + 1)
                if (label.isBlank()) null else label to optionNumber
            }
    }

    fun deriveMp4QualityUrls(
        embedBase64: String?,
        fallbackMp4: String?,
    ): Map<String, String> {
        val decoded: String? = embedBase64?.let { it: String -> decodeBase64Safely(it) }
        return buildQualityMap(fallbackMp4, decoded)
    }
}
