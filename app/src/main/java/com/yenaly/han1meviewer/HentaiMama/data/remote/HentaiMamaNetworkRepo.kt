package com.yenaly.han1meviewer.HentaiMama.data.remote

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaSeriesRepo
import com.yenaly.han1meviewer.HentaiMama.data.model.AzLink
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePaginator
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaHomePage
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoLink
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaGenreParser
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaParser
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaSeriesCardParser
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeCategory
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response

object HentaiMamaNetworkRepo {

    private const val TAG = "HentaiMamaRepo"
    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 1500L

    private fun isChallengeResponse(code: Int, body: String?): Boolean {
        if (code != 403) return false
        if (body.isNullOrBlank()) return true
        return body.contains("Just a moment", ignoreCase = true) ||
                body.contains("cf-chl", ignoreCase = true) ||
                body.contains("__cf_chl", ignoreCase = true) ||
                body.contains("challenge-platform", ignoreCase = true) ||
                body.contains("cf_clearance", ignoreCase = true) ||
                body.contains("Attention Required", ignoreCase = true) ||
                body.contains("403 Forbidden", ignoreCase = true)
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRY_COUNT,
        operation: suspend () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("Retries exhausted")
    }

    fun getHomePage() = flow {
        emit(WebsiteState.Loading)
        try {
            val result = withRetry {
                val popularResp = HentaiMamaNetwork.service.getPopularVideos()
                val popularBody = if (popularResp.isSuccessful) {
                    popularResp.body()?.string().orEmpty()
                } else {
                    val errBody = runCatching { popularResp.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(popularResp.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on popular videos")
                    }
                    ""
                }

                val latestResp = HentaiMamaNetwork.service.getLatestVideos()
                val latestBody = if (latestResp.isSuccessful) {
                    latestResp.body()?.string().orEmpty()
                } else {
                    val errBody = runCatching { latestResp.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(latestResp.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on latest videos")
                    }
                    ""
                }

                val popularState = HentaiMamaParser.parseVideoList(popularBody)
                val latestState = HentaiMamaParser.parseVideoList(latestBody)

                val popular = (popularState as? PageLoadingState.Success)?.info ?: emptyList()
                val latest = (latestState as? PageLoadingState.Success)?.info ?: popular

                if (popular.isEmpty() && latest.isEmpty()) {
                    throw IllegalStateException("No videos found")
                }

                WebsiteState.Success(
                    HentaiMamaHomePage(
                        popularVideos = popular,
                        latestVideos = latest,
                    )
                )
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getHomePage error", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getCategoryVideos(category: HentaiMamaHomeCategory): List<HanimeInfo> {
        val baseUrl = HentaiMamaNetwork.baseUrl
        val rawPath = category.genrePath.trim()
        val relative = rawPath.trimStart('/')

        if (isGenrePath(relative)) {
            val slug = extractGenreSlug(relative)
            if (slug.isNotBlank()) {
                val page = fetchGenrePage(
                    slug = slug,
                    sort = category.sort?.takeIf { it.isNotBlank() },
                    page = 1,
                    layout = GenreLayout.DETAILS,
                )
                return page?.series?.mapNotNull { series ->
                    runCatching {
                        HanimeInfo(
                            title = series.title.ifBlank { series.altTitle.orEmpty() },
                            coverUrl = series.posterFull.ifBlank { series.posterMid.orEmpty() },
                            videoCode = series.slug,
                            itemType = HanimeInfo.NORMAL,
                        )
                    }.getOrNull()
                }.orEmpty()
            }
            return emptyList()
        }

        val fullUrl = if (relative.startsWith("http")) relative else "$baseUrl/$relative"
        val finalUrl = if (!category.sort.isNullOrBlank() &&
            !fullUrl.contains("filter=", ignoreCase = true)
        ) {
            val sep = if ('?' in fullUrl) "&" else "?"
            "$fullUrl${sep}filter=${category.sort}"
        } else {
            fullUrl
        }

        return try {
            withRetry {
                val response = HentaiMamaNetwork.service.getVideoDetail(finalUrl)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on category ${category.key}")
                    }
                    Log.w(TAG, "getCategoryVideos HTTP ${response.code()} for ${category.key}")
                    return@withRetry emptyList()
                }
                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry emptyList()
                when (val state = HentaiMamaParser.parseSearchResults(body, isFilterSearch = true)) {
                    is PageLoadingState.Success -> state.info
                    else -> emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCategoryVideos failed for '${category.key}'", e)
            emptyList()
        }
    }

    fun getLatestVideos(page: Int) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = withRetry {
                val response = if (page <= 1) {
                    HentaiMamaNetwork.service.getLatestVideos()
                } else {
                    HentaiMamaNetwork.service.getLatestVideosPaged(page)
                }
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: EMPTY_STRING
                    HentaiMamaParser.parseVideoList(body)
                } else {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on latest videos page $page")
                    }
                    PageLoadingState.Error(IllegalStateException("Failed: ${response.code()}"))
                }
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getLatestVideos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchVideos(page: Int, query: String, sort: String? = null) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = withRetry {
                val base = HentaiMamaNetwork.baseUrl.trimEnd('/')
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = if (page <= 1) {
                    "$base/?s=$encoded"
                } else {
                    "$base/page/$page/?s=$encoded"
                }

                val response = HentaiMamaNetwork.service.getVideoDetail(url)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on search page $page")
                    }
                    throw IllegalStateException("Search failed: ${response.code()}")
                }

                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry PageLoadingState.NoMoreData

                val doc = Jsoup.parse(body, url)
                val cards: List<SeriesCard> = HentaiMamaSeriesCardParser.parseCards(doc, url)
                val videos = cards.map { card ->
                    HanimeInfo(
                        title = card.title,
                        coverUrl = card.thumbFull.ifBlank { card.thumbSmall.orEmpty() },
                        videoCode = card.slug,
                        duration = card.episodeCount?.let { "$it eps" },
                        views = card.viewsRaw,
                        uploadTime = card.year?.toString(),
                        genre = card.genres.firstOrNull(),
                        reviews = card.rating?.let { "%.1f".format(it) },
                        currentArtist = card.studios.firstOrNull(),
                        itemType = HanimeInfo.NORMAL,
                    )
                }.ifEmpty {
                    HentaiMamaParser.parseCardList(body, base)
                }

                if (videos.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(videos)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "searchVideos failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchSeriesCards(page: Int, query: String) = flow {
        try {
            val result = withRetry {
                val base = HentaiMamaNetwork.baseUrl.trimEnd('/')
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = if (page <= 1) {
                    "$base/?s=$encoded"
                } else {
                    "$base/page/$page/?s=$encoded"
                }

                val response = HentaiMamaNetwork.service.getVideoDetail(url)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on search cards page $page")
                    }
                    return@withRetry emptyList<SeriesCard>()
                }

                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry emptyList<SeriesCard>()

                HentaiMamaSeriesCardParser.parseCards(Jsoup.parse(body, url), url)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "searchSeriesCards failed", e)
            emit(emptyList<SeriesCard>())
        }
    }.flowOn(Dispatchers.IO)

    fun searchGenreVideos(
        slug: String,
        query: String,
        page: Int,
        sort: String? = null,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = withRetry {
                val fullUrl = buildGenreSearchUrl(slug, query, page, sort)
                val response = HentaiMamaNetwork.service.getVideoDetail(fullUrl)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on genre search page $page")
                    }
                    throw IllegalStateException("HTTP ${response.code()}")
                }
                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry PageLoadingState.NoMoreData
                val doc = Jsoup.parse(body, fullUrl)
                val cards = HentaiMamaSeriesCardParser.parseCards(doc, fullUrl)
                val videos = cards.map { card ->
                    HanimeInfo(
                        title = card.title,
                        coverUrl = card.thumbFull.ifBlank { card.thumbSmall.orEmpty() },
                        videoCode = card.slug,
                        duration = card.episodeCount?.let { "$it eps" },
                        views = card.viewsRaw,
                        uploadTime = card.year?.toString(),
                        genre = card.genres.firstOrNull(),
                        reviews = card.rating?.let { "%.1f".format(it) },
                        currentArtist = card.studios.firstOrNull(),
                        itemType = HanimeInfo.NORMAL,
                    )
                }.ifEmpty {
                    HentaiMamaParser.parseCardList(body, fullUrl)
                }
                if (videos.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(videos)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "searchGenreVideos failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun filterVideos(
        page: Int,
        genre: String?,
        producer: String?,
        year: String? = null,
        order: String? = null,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = withRetry {
                val genres = genre?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val studios = producer?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val years = year?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val filterOrder = order?.takeIf { it.isNotBlank() }

                val response = if (page <= 1) {
                    HentaiMamaNetwork.service.getFilteredVideos(
                        filter = filterOrder,
                        genres = genres,
                        years = years,
                        studios = studios,
                    )
                } else {
                    HentaiMamaNetwork.service.getFilteredVideosPaged(
                        page = page,
                        filter = filterOrder,
                        genres = genres,
                        years = years,
                        studios = studios,
                    )
                }

                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on filter page $page")
                    }
                    throw IllegalStateException("Filter failed: ${response.code()}")
                }

                val body = response.body()?.string() ?: EMPTY_STRING
                val state = HentaiMamaParser.parseSearchResults(body, isFilterSearch = true)
                enrichWithPagination(state, body)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "filterVideos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun filterSeriesCards(
        page: Int,
        genre: String?,
        producer: String?,
        year: String? = null,
        order: String? = null,
    ) = flow {
        try {
            val result = withRetry {
                val genres = genre?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val studios = producer?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val years = year?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val filterOrder = order?.takeIf { it.isNotBlank() }

                val response = if (page <= 1) {
                    HentaiMamaNetwork.service.getFilteredVideos(
                        filter = filterOrder,
                        genres = genres,
                        years = years,
                        studios = studios,
                    )
                } else {
                    HentaiMamaNetwork.service.getFilteredVideosPaged(
                        page = page,
                        filter = filterOrder,
                        genres = genres,
                        years = years,
                        studios = studios,
                    )
                }

                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on filter cards page $page")
                    }
                    return@withRetry emptyList<SeriesCard>()
                }

                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry emptyList<SeriesCard>()

                val base = HentaiMamaNetwork.baseUrl
                HentaiMamaSeriesCardParser.parseCards(Jsoup.parse(body, base), base)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "filterSeriesCards failed", e)
            emit(emptyList<SeriesCard>())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchGenrePage(
        slug: String,
        sort: String? = null,
        page: Int = 1,
        layout: GenreLayout = GenreLayout.DETAILS,
    ): GenrePage? {
        return try {
            withRetry {
                val url = HentaiMamaGenreParser.buildGenreUrl(
                    baseUrl = HentaiMamaNetwork.baseUrl,
                    slug = slug,
                    sort = sort,
                    layout = layout,
                    page = page,
                )
                val response = HentaiMamaNetwork.service.getVideoDetail(url)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on genre page $slug")
                    }
                    Log.w(TAG, "fetchGenrePage HTTP ${response.code()} for $url")
                    return@withRetry null
                }
                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry null
                HentaiMamaGenreParser.parse(
                    body = body,
                    baseUrl = url,
                    slug = slug,
                    sort = sort,
                    layout = layout,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenrePage failed for $slug", e)
            null
        }
    }

    fun getGenrePageFlow(
        slug: String,
        sort: String? = null,
        page: Int = 1,
        layout: GenreLayout = GenreLayout.DETAILS,
    ) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val result = withRetry {
                val url = HentaiMamaGenreParser.buildGenreUrl(
                    baseUrl = HentaiMamaNetwork.baseUrl,
                    slug = slug,
                    sort = sort,
                    layout = layout,
                    page = page,
                )
                val response = HentaiMamaNetwork.service.getVideoDetail(url)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on genre page flow $slug")
                    }
                    return@withRetry when (response.code()) {
                        404 -> VideoLoadingState.NoContent
                        else -> VideoLoadingState.Error(IllegalStateException("HTTP ${response.code()}"))
                    }
                }
                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry VideoLoadingState.NoContent
                val parsed: GenrePage = HentaiMamaGenreParser.parse(
                    body = body,
                    baseUrl = url,
                    slug = slug,
                    sort = sort,
                    layout = layout,
                )
                VideoLoadingState.Success(parsed)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getGenrePageFlow failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchGenreNextPage(nextUrl: String, slug: String): GenrePage? {
        return try {
            withRetry {
                val response = HentaiMamaNetwork.service.getVideoDetail(nextUrl)
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on genre next page")
                    }
                    return@withRetry null
                }
                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry null
                HentaiMamaGenreParser.parse(body = body, baseUrl = nextUrl, slug = slug)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenreNextPage failed", e)
            null
        }
    }

    fun getVideoDetail(url: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val result = withRetry {
                val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
                val response = HentaiMamaNetwork.service.getVideoDetail(full)
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: EMPTY_STRING
                    Log.d(TAG, "getVideoDetail: len=${body.length} url=$full")
                    HentaiMamaParser.parseVideoDetail(body, full)
                } else {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on video detail")
                    }
                    when (response.code()) {
                        404 -> VideoLoadingState.NoContent
                        else -> VideoLoadingState.Error(
                            IllegalStateException("Failed: ${response.code()}")
                        )
                    }
                }
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getVideoDetail failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getSeriesDetail(url: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val result = withRetry {
                val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
                val response = HentaiMamaNetwork.service.getVideoDetail(full)
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: EMPTY_STRING
                    Log.d(TAG, "getSeriesDetail: len=${body.length} url=$full")
                    HentaiMamaParser.parseSeriesDetail(body, full)
                } else {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on series detail")
                    }
                    when (response.code()) {
                        404 -> VideoLoadingState.NoContent
                        else -> VideoLoadingState.Error(
                            IllegalStateException("Failed: ${response.code()}")
                        )
                    }
                }
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getSeriesDetail failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun extractVideoLinks(
        detailPageBody: String,
        optionNumber: Int,
    ): List<HentaiMamaVideoLink> = HentaiMamaParser.videoListParse(
        detailPageBody = detailPageBody,
        baseUrl = HentaiMamaNetwork.baseUrl,
        apiUrl = HentaiMamaNetwork.apiUrl,
        optionNumber = optionNumber,
    )

    suspend fun extractHosterTabs(detailPageBody: String): List<Pair<String, Int>> =
        HentaiMamaParser.hosterTabs(detailPageBody)

    fun parseFullEpisodePage(body: String, url: String): VideoLoadingState<EpisodeDetailPage> =
        HentaiMamaParser.parseEpisodeDetail(body, url)

    fun parseFullSeriesPage(body: String, url: String): VideoLoadingState<SeriesDetailPage> =
        HentaiMamaParser.parseSeriesDetail(body, url)

    fun parseGenrePage(
        body: String,
        url: String,
        slug: String,
        sort: String? = null,
    ): GenrePage = HentaiMamaGenreParser.parse(
        body = body,
        baseUrl = url,
        slug = slug,
        sort = sort,
    )

    suspend fun getAzBar(url: String): List<AzLink> = withContext(Dispatchers.IO) {
        try {
            withRetry {
                val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
                val resp = HentaiMamaNetwork.service.getVideoDetail(full)
                val body = if (resp.isSuccessful) {
                    resp.body()?.string().orEmpty()
                } else {
                    val errBody = runCatching { resp.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(resp.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on AZ bar")
                    }
                    ""
                }
                if (body.isBlank()) emptyList()
                else HentaiMamaSeriesCardParser.parseAzBar(Jsoup.parse(body, full))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAzBar failed", e)
            emptyList()
        }
    }

    suspend fun fetchAdvanceSearch(
        genres: List<String> = emptyList(),
        years: List<Int> = emptyList(),
        studios: List<String> = emptyList(),
        sort: String = "alphabet",
        page: Int = 1,
    ): List<SeriesCard> = withContext(Dispatchers.IO) {
        try {
            withRetry {
                val response = if (page <= 1) {
                    HentaiMamaNetwork.service.getFilteredVideos(
                        filter = sort,
                        genres = genres.takeIf { it.isNotEmpty() },
                        years = years.map { it.toString() }.takeIf { it.isNotEmpty() },
                        studios = studios.takeIf { it.isNotEmpty() },
                    )
                } else {
                    HentaiMamaNetwork.service.getFilteredVideosPaged(
                        page = page,
                        filter = sort,
                        genres = genres.takeIf { it.isNotEmpty() },
                        years = years.map { it.toString() }.takeIf { it.isNotEmpty() },
                        studios = studios.takeIf { it.isNotEmpty() },
                    )
                }
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    if (isChallengeResponse(response.code(), errBody)) {
                        throw IllegalStateException("Cloudflare challenge on advance search")
                    }
                    return@withRetry emptyList()
                }
                val body = response.body()?.string().orEmpty()
                if (body.isBlank()) return@withRetry emptyList()
                val base = HentaiMamaNetwork.baseUrl
                val cards = HentaiMamaSeriesCardParser.parseCards(Jsoup.parse(body, base), base)
                if (cards.isNotEmpty()) {
                    runCatching { HentaiMamaSeriesRepo.upsertAll(cards, "advance") }
                        .onFailure { Log.w(TAG, "advance-search persistence skipped: ${it.message}") }
                }
                cards
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAdvanceSearch failed", e)
            emptyList()
        }
    }

    private fun enrichWithPagination(
        state: PageLoadingState<List<HanimeInfo>>,
        body: String,
    ): PageLoadingState<List<HanimeInfo>> {
        if (state !is PageLoadingState.Success) return state
        val hasNext: Boolean = hasNextPage(body)
        return if (!hasNext && state.info.isEmpty()) {
            PageLoadingState.NoMoreData
        } else {
            state
        }
    }

    fun hasNextPage(body: String): Boolean {
        if (body.isBlank()) return false
        return try {
            val doc = Jsoup.parse(body)
            val next = doc.selectFirst("a.dt-pg-next")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
            val relNext = doc.selectFirst("a[rel=next]")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
            val pg = doc.selectFirst(".pagination.dt-pg")
            val cur = pg?.attr("data-page")?.toIntOrNull() ?: 1
            val total = pg?.attr("data-pages")?.toIntOrNull() ?: 1
            next != null || relNext != null || cur < total
        } catch (_: Exception) {
            false
        }
    }

    fun extractNextPageUrl(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val doc = Jsoup.parse(body)
            doc.selectFirst("a.dt-pg-next")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun extractPaginator(body: String): GenrePaginator? {
        if (body.isBlank()) return null
        return try {
            val doc = Jsoup.parse(body)
            val p = doc.selectFirst(".dt-series-pagination-top .pagination.dt-pg")
                ?: doc.selectFirst(".pagination.dt-pg")
                ?: return null
            GenrePaginator(
                current = p.attr("data-page").toIntOrNull() ?: 1,
                total = p.attr("data-pages").toIntOrNull() ?: 1,
                templateUrl = p.attr("data-tpl").takeIf { it.isNotBlank() },
                nextUrl = p.selectFirst("a.dt-pg-next")
                    ?.absUrl("href")
                    ?.takeIf { it.isNotBlank() },
                lastUrl = p.selectFirst("a.dt-pg-last")
                    ?.absUrl("href")
                    ?.takeIf { it.isNotBlank() },
                prevUrl = p.selectFirst("a.dt-pg-prev")
                    ?.absUrl("href")
                    ?.takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            null
        }
    }

    fun extractGenreHeader(body: String, baseUrl: String, slug: String): GenreHeader? {
        if (body.isBlank()) return null
        return try {
            val doc = Jsoup.parse(body, baseUrl)
            HentaiMamaGenreParser.parseHeader(doc, baseUrl, slug)
        } catch (_: Exception) {
            null
        }
    }

    fun isGenrePath(path: String): Boolean {
        if (path.isBlank()) return false
        return path.contains("/genre/", ignoreCase = true) ||
                path.startsWith("genre/", ignoreCase = true)
    }

    fun extractGenreSlug(path: String): String {
        if (path.isBlank()) return ""
        val afterGenre: String = if (path.contains("/genre/")) {
            path.substringAfter("/genre/", "")
        } else if (path.startsWith("genre/")) {
            path.removePrefix("genre/")
        } else {
            path
        }
        return afterGenre.trimStart('/')
            .substringBefore('/')
            .substringBefore('?')
            .trim()
    }

    private fun buildGenreSearchUrl(
        slug: String,
        query: String,
        page: Int,
        sort: String?,
    ): String {
        val base = HentaiMamaNetwork.baseUrl.trimEnd('/')
        val path = if (page <= 1) {
            "$base/genre/$slug/"
        } else {
            "$base/genre/$slug/page/$page/"
        }
        val params = buildList {
            if (!sort.isNullOrBlank()) add("filter=$sort")
            if (query.isNotBlank()) add("s=${java.net.URLEncoder.encode(query, "UTF-8")}")
        }
        return if (params.isEmpty()) path else "$path?${params.joinToString("&")}"
    }
}
