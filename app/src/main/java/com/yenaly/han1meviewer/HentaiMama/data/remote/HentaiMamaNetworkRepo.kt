package com.yenaly.han1meviewer.HentaiMama.data.remote

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePaginator
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaHomePage
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoLink
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaGenreParser
import com.yenaly.han1meviewer.HentaiMama.data.parser.HentaiMamaParser
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeCategory
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object HentaiMamaNetworkRepo {

    private const val TAG = "HentaiMamaRepo"

    fun getHomePage() = flow {
        emit(WebsiteState.Loading)
        try {
            val popularResp = HentaiMamaNetwork.service.getPopularVideos()
            val popularBody = if (popularResp.isSuccessful) {
                popularResp.body()?.string().orEmpty()
            } else ""

            val latestResp = HentaiMamaNetwork.service.getLatestVideos()
            val latestBody = if (latestResp.isSuccessful) {
                latestResp.body()?.string().orEmpty()
            } else ""

            val popularState = HentaiMamaParser.parseVideoList(popularBody)
            val latestState = HentaiMamaParser.parseVideoList(latestBody)

            val popular = (popularState as? PageLoadingState.Success)?.info ?: emptyList()
            val latest = (latestState as? PageLoadingState.Success)?.info ?: popular

            if (popular.isEmpty() && latest.isEmpty()) {
                emit(WebsiteState.Error(IllegalStateException("No videos found")))
            } else {
                emit(
                    WebsiteState.Success(
                        HentaiMamaHomePage(
                            popularVideos = popular,
                            latestVideos = latest,
                        )
                    )
                )
            }
        } catch (e: Exception) {
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
            val response = HentaiMamaNetwork.service.getVideoDetail(finalUrl)
            if (!response.isSuccessful) {
                Log.w(TAG, "getCategoryVideos HTTP ${response.code()} for ${category.key}")
                return emptyList()
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            when (val state = HentaiMamaParser.parseSearchResults(body, isFilterSearch = true)) {
                is PageLoadingState.Success -> state.info
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCategoryVideos failed for '${category.key}'", e)
            emptyList()
        }
    }

    fun getLatestVideos(page: Int) = flow {
        emit(PageLoadingState.Loading)
        try {
            val response = if (page <= 1) {
                HentaiMamaNetwork.service.getLatestVideos()
            } else {
                HentaiMamaNetwork.service.getLatestVideosPaged(page)
            }
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                emit(HentaiMamaParser.parseVideoList(body))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchVideos(page: Int, query: String, sort: String? = null) = flow {
        emit(PageLoadingState.Loading)
        try {
            val response = HentaiMamaNetwork.service.searchVideos(page, query)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                val state = HentaiMamaParser.parseSearchResults(body, isFilterSearch = false)
                val enriched = enrichWithPagination(state, body)
                emit(enriched)
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Search failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(PageLoadingState.Error(e))
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
            val fullUrl = buildGenreSearchUrl(slug, query, page, sort)
            val response = HentaiMamaNetwork.service.getVideoDetail(fullUrl)
            if (!response.isSuccessful) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            val series = HentaiMamaGenreParser.parseSeriesCards(body, fullUrl)
            val videos = series.map { s ->
                HanimeInfo(
                    title = s.title.ifBlank { s.altTitle.orEmpty() },
                    coverUrl = s.posterFull.ifBlank { s.posterMid.orEmpty() },
                    videoCode = s.slug,
                    itemType = HanimeInfo.NORMAL,
                )
            }
            if (videos.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(PageLoadingState.Success(videos))
            }
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

            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                val state = HentaiMamaParser.parseSearchResults(body, isFilterSearch = true)
                emit(enrichWithPagination(state, body))
            } else {
                emit(PageLoadingState.Error(IllegalStateException("Filter failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "filterVideos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchGenrePage(
        slug: String,
        sort: String? = null,
        page: Int = 1,
        layout: GenreLayout = GenreLayout.DETAILS,
    ): GenrePage? {
        return try {
            val url = HentaiMamaGenreParser.buildGenreUrl(
                baseUrl = HentaiMamaNetwork.baseUrl,
                slug = slug,
                sort = sort,
                layout = layout,
                page = page,
            )
            val response = HentaiMamaNetwork.service.getVideoDetail(url)
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchGenrePage HTTP ${response.code()} for $url")
                return null
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) return null
            HentaiMamaGenreParser.parse(
                body = body,
                baseUrl = url,
                slug = slug,
                sort = sort,
                layout = layout,
            )
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
            val url = HentaiMamaGenreParser.buildGenreUrl(
                baseUrl = HentaiMamaNetwork.baseUrl,
                slug = slug,
                sort = sort,
                layout = layout,
                page = page,
            )
            val response = HentaiMamaNetwork.service.getVideoDetail(url)
            if (!response.isSuccessful) {
                when (response.code()) {
                    404 -> emit(VideoLoadingState.NoContent)
                    else -> emit(
                        VideoLoadingState.Error(
                            IllegalStateException("HTTP ${response.code()}")
                        )
                    )
                }
                return@flow
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                emit(VideoLoadingState.NoContent)
                return@flow
            }
            val parsed: GenrePage = HentaiMamaGenreParser.parse(
                body = body,
                baseUrl = url,
                slug = slug,
                sort = sort,
                layout = layout,
            )
            emit(VideoLoadingState.Success(parsed))
        } catch (e: Exception) {
            Log.e(TAG, "getGenrePageFlow failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchGenreNextPage(nextUrl: String, slug: String): GenrePage? {
        return try {
            val response = HentaiMamaNetwork.service.getVideoDetail(nextUrl)
            if (!response.isSuccessful) return null
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) return null
            HentaiMamaGenreParser.parse(body = body, baseUrl = nextUrl, slug = slug)
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenreNextPage failed", e)
            null
        }
    }

    fun getVideoDetail(url: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
            val response = HentaiMamaNetwork.service.getVideoDetail(full)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                Log.d(TAG, "getVideoDetail: len=${body.length} url=$full")
                emit(HentaiMamaParser.parseVideoDetail(body, full))
            } else {
                when (response.code()) {
                    404 -> emit(VideoLoadingState.NoContent)
                    else -> emit(
                        VideoLoadingState.Error(
                            IllegalStateException("Failed: ${response.code()}")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getSeriesDetail(url: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
            val response = HentaiMamaNetwork.service.getVideoDetail(full)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: EMPTY_STRING
                Log.d(TAG, "getSeriesDetail: len=${body.length} url=$full")
                emit(HentaiMamaParser.parseSeriesDetail(body, full))
            } else {
                when (response.code()) {
                    404 -> emit(VideoLoadingState.NoContent)
                    else -> emit(
                        VideoLoadingState.Error(
                            IllegalStateException("Failed: ${response.code()}")
                        )
                    )
                }
            }
        } catch (e: Exception) {
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
            val doc = org.jsoup.Jsoup.parse(body)
            val next = doc.selectFirst("a.dt-pg-next")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
            val dataPage: Int = doc.selectFirst(".pagination.dt-pg")
                ?.attr("data-page")
                ?.toIntOrNull() ?: 1
            val dataPages: Int = doc.selectFirst(".pagination.dt-pg")
                ?.attr("data-pages")
                ?.toIntOrNull() ?: 1
            next != null || dataPage < dataPages
        } catch (_: Exception) {
            false
        }
    }

    fun extractNextPageUrl(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val doc = org.jsoup.Jsoup.parse(body)
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
            val doc = org.jsoup.Jsoup.parse(body)
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
            val doc = org.jsoup.Jsoup.parse(body, baseUrl)
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
