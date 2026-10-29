package com.yenaly.han1meviewer.FapFolder.data.remote

import android.util.Log
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAlbumPage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAutocompleteSuggestion
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderCommentsPage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedPage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroup
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupMembersPage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderHomeExtras
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLightboxResponse
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchResult
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSuggestedCommunity
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderTrendingTag
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderUserProfile
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderAlbumParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderAutocompleteParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderCommentParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderGroupParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderHomeExtrasParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderHtmlUtils
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderLightboxParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderPostParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderProfileParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderSearchParser
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderSessionParser
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jsoup.Jsoup
import okhttp3.Request
import okhttp3.OkHttpClient

object FapFolderNetworkRepo {

    private const val TAG = "FapFolderNetworkRepo"

    data class FirstPageSnapshot(
        val items: List<FapFolderFeedItem>,
        val hasMore: Boolean,
        val get: String?,
        val filter: String?,
        val id: String?,
        val type: String?,
        val uid: String?,
        val search: String?,
        val hashtag: String?,
    )

    data class HomeExtras(
        val trending: List<FapFolderTrendingTag>,
        val suggested: List<FapFolderSuggestedCommunity>,
    )

    data class HomePageBundle(
        val page: FapFolderFeedPage,
        val extras: HomeExtras,
    )

    fun parseFirstPageFromHtml(
        html: String,
        pageUrl: String,
        filter: String?,
        fallbackId: String?,
    ): FirstPageSnapshot {
        val doc = Jsoup.parse(html, pageUrl)
        val streamRoot = doc.selectFirst("div.js_posts_stream")
        val attrs = FapFolderGroupParser.parseStreamAttrs(doc)
        val uploads = FapFolderSessionCache.uploadsBase()

        val posts = FapFolderPostParser.parsePostsFromList(
            root = streamRoot ?: doc.body(),
            baseUrl = pageUrl,
            uploadsBase = uploads,
            includeComments = false,
        )
        val items: List<FapFolderFeedItem> = posts.map { FapFolderFeedItem.Post(it) }

        val seeMore = doc.selectFirst(".js_see-more-infinite")
            ?: doc.selectFirst(".js_see-more")
        val hasMore = seeMore != null && !seeMore.hasClass("done") && posts.isNotEmpty()

        return FirstPageSnapshot(
            items = items,
            hasMore = hasMore,
            get = attrs?.get,
            filter = attrs?.filter ?: filter,
            id = attrs?.id ?: fallbackId,
            type = attrs?.type,
            uid = attrs?.uid,
            search = attrs?.search,
            hashtag = attrs?.hashtag,
        )
    }

    private suspend fun rateLimit() {
        delay(FapFolderConstants.MIN_GAP_BETWEEN_PAGES_MS)
    }

    private suspend fun rateLimitLightbox() {
        delay(FapFolderConstants.MIN_GAP_BETWEEN_LIGHTBOX_MS)
    }

    private fun basePath(): String =
        FapFolderConstants.BASE_URL.trimEnd('/')

    private fun uploadsBase(): String =
        FapFolderSessionCache.uploadsBase()

    fun getHomePageWithExtras(): Flow<WebsiteState<HomePageBundle>> = flow {
        emit(WebsiteState.Loading)
        val url = basePath() + "/"
        try {
            val response = FapFolderNetwork.service.getByUrl(url)
            val rawBody = response.body()?.string()
            val code = response.code()
            if (!response.isSuccessful || rawBody.isNullOrBlank()) {
                emit(errorState(code, rawBody))
                return@flow
            }

            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(WebsiteState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }

            val session = FapFolderSessionParser.parseAndCache(rawBody, url)
            val doc = Jsoup.parse(rawBody, url)

            val streamRoot = doc.selectFirst("div.js_posts_stream")
            val feedGet = streamRoot?.attr("data-get")?.takeIf { it.isNotBlank() }
                ?: FapFolderConstants.FEED_GET_NEWSFEED
            val feedFilter = streamRoot?.attr("data-filter")?.takeIf { it.isNotBlank() }
                ?: FapFolderConstants.FILTER_ALL
            val feedId = streamRoot?.attr("data-id")?.takeIf { it.isNotBlank() }
            val feedType = streamRoot?.attr("data-type")?.takeIf { it.isNotBlank() }
            val feedUid = streamRoot?.attr("data-uid")?.takeIf { it.isNotBlank() }

            val posts = FapFolderPostParser.parsePostsFromList(
                root = streamRoot ?: doc.body(),
                baseUrl = url,
                uploadsBase = uploadsBase(),
                includeComments = false,
            )

            val seeMore = doc.selectFirst(".js_see-more-infinite")
                ?: doc.selectFirst(".js_see-more")
            val hasMore = seeMore != null && !seeMore.hasClass("done") && posts.isNotEmpty()

            val page = FapFolderFeedPage(
                items = posts.map { FapFolderFeedItem.Post(it) },
                session = session,
                nextOffset = 1,
                hasMore = hasMore,
                feedGet = feedGet,
                feedFilter = feedFilter,
                feedId = feedId,
                feedType = feedType,
                feedUid = feedUid,
            )
            val extras = HomeExtras(
                trending = FapFolderHomeExtrasParser.parseTrending(doc, url),
                suggested = FapFolderHomeExtrasParser.parseSuggested(doc, url),
            )

            emit(WebsiteState.Success(HomePageBundle(page = page, extras = extras)))
        } catch (e: Exception) {
            Log.e(TAG, "getHomePageWithExtras failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getHomePage(): Flow<WebsiteState<FapFolderFeedPage>> = flow {
        emit(WebsiteState.Loading)
        val url = basePath() + "/"
        try {
            val response = FapFolderNetwork.service.getByUrl(url)
            val rawBody = response.body()?.string()
            val code = response.code()
            if (!response.isSuccessful || rawBody.isNullOrBlank()) {
                emit(errorState(code, rawBody))
                return@flow
            }

            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(
                    WebsiteState.Error(
                        IllegalStateException("Cloudflare challenge. Try again later.")
                    )
                )
                return@flow
            }

            val session = FapFolderSessionParser.parseAndCache(rawBody, url)
            val doc = Jsoup.parse(rawBody, url)

            val streamRoot = doc.selectFirst("div.js_posts_stream")
            val feedGet = streamRoot?.attr("data-get")?.takeIf { it.isNotBlank() }
                ?: FapFolderConstants.FEED_GET_NEWSFEED
            val feedFilter = streamRoot?.attr("data-filter")?.takeIf { it.isNotBlank() }
                ?: FapFolderConstants.FILTER_ALL
            val feedId = streamRoot?.attr("data-id")?.takeIf { it.isNotBlank() }
            val feedType = streamRoot?.attr("data-type")?.takeIf { it.isNotBlank() }
            val feedUid = streamRoot?.attr("data-uid")?.takeIf { it.isNotBlank() }

            val posts = FapFolderPostParser.parsePostsFromList(
                root = streamRoot ?: doc.body(),
                baseUrl = url,
                uploadsBase = uploadsBase(),
                includeComments = false,
            )

            val items = posts.map { FapFolderFeedItem.Post(it) }

            val seeMore = doc.selectFirst(".js_see-more-infinite")
            val hasMore = seeMore != null && !seeMore.hasClass("done") && posts.isNotEmpty()

            emit(
                WebsiteState.Success(
                    FapFolderFeedPage(
                        items = items,
                        session = session,
                        nextOffset = 1,
                        hasMore = hasMore,
                        feedGet = feedGet,
                        feedFilter = feedFilter,
                        feedId = feedId,
                        feedType = feedType,
                        feedUid = feedUid,
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getHomePage failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getHomeExtras(): Flow<WebsiteState<HomeExtras>> = flow {
        emit(WebsiteState.Loading)
        val url = basePath() + "/"
        try {
            val response = FapFolderNetwork.service.getByUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(errorState(response.code(), rawBody))
                return@flow
            }
            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(WebsiteState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }
            val doc = Jsoup.parse(rawBody, url)
            val trending = FapFolderHomeExtrasParser.parseTrending(doc, url)
            val suggested = FapFolderHomeExtrasParser.parseSuggested(doc, url)
            emit(WebsiteState.Success(HomeExtras(trending, suggested)))
        } catch (e: Exception) {
            Log.e(TAG, "getHomeExtras failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun loadMore(
        request: FapFolderLoadMoreRequest,
    ): Flow<PageLoadingState<List<FapFolderFeedItem>>> = flow {
        emit(PageLoadingState.Loading)
        try {
            rateLimit()
            val response = FapFolderNetwork.service.loadMore(
                get = request.get,
                offset = request.offset,
                filter = request.filter,
                id = request.id,
                type = request.type,
                uid = request.uid,
                premfeed = request.premfeed,
                time = request.time,
                search = request.search,
                hashtag = request.hashtag,
            )

            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            if (rawBody.isBlank()) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }

            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }

            if (parsed.has("callback") && !parsed.isNull("callback")) {
                FapFolderSessionCache.clear()
                emit(
                    PageLoadingState.Error(
                        IllegalStateException("Session expired. Sign in again.")
                    )
                )
                return@flow
            }

            val dataHtml = parsed.optString("data").takeIf { it.isNotBlank() }
            if (dataHtml == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }

            val items: List<FapFolderFeedItem> = parseLoadMoreItems(
                dataHtml = dataHtml,
                request = request,
            )

            if (items.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(PageLoadingState.Success(items))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMore failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun filterPosts(
        filter: String,
        id: String? = null,
        offset: Int = 1,
    ): Flow<PageLoadingState<List<FapFolderFeedItem>>> = flow {
        emit(PageLoadingState.Loading)
        try {
            rateLimit()
            val response = FapFolderNetwork.service.filterPosts(
                get = FapFolderConstants.FEED_GET_NEWSFEED,
                filter = filter,
                id = id,
                offset = offset.takeIf { it > 1 },
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            if (parsed.has("callback") && !parsed.isNull("callback")) {
                FapFolderSessionCache.clear()
                emit(PageLoadingState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            val postsHtml = parsed.optString("posts").takeIf { it.isNotBlank() }
                ?: parsed.optString("data").takeIf { it.isNotBlank() }
            if (postsHtml == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            val posts = FapFolderPostParser.parsePostsFromHtml(
                html = postsHtml,
                baseUrl = basePath(),
                uploadsBase = uploadsBase(),
                includeComments = false,
            )
            if (posts.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(PageLoadingState.Success(posts.map { FapFolderFeedItem.Post(it) }))
            }
        } catch (e: Exception) {
            Log.e(TAG, "filterPosts failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun loadDedicatedFeed(path: String): Flow<PageLoadingState<List<FapFolderFeedItem>>> = flow {
        emit(PageLoadingState.Loading)
        val url = basePath().trimEnd('/') + path
        try {
            rateLimit()
            val response = FapFolderNetwork.service.getByUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(PageLoadingState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }
            FapFolderSessionParser.parseAndCache(rawBody, url)
            val doc = Jsoup.parse(rawBody, url)
            val streamRoot = doc.selectFirst("div.js_posts_stream") ?: doc.body()
            val posts = FapFolderPostParser.parsePostsFromList(
                root = streamRoot,
                baseUrl = url,
                uploadsBase = uploadsBase(),
                includeComments = false,
            )
            if (posts.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(PageLoadingState.Success(posts.map { FapFolderFeedItem.Post(it) }))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadDedicatedFeed failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseLoadMoreItems(
        dataHtml: String,
        request: FapFolderLoadMoreRequest,
    ): List<FapFolderFeedItem> {
        val get = request.get
        val base = basePath()
        val uploads = uploadsBase()

        return when {
            request.search != null || request.hashtag != null ->
                FapFolderSearchParser.parseLoadMoreHtml(
                    htmlFragment = dataHtml,
                    baseUrl = base,
                    tab = FapFolderSearchTab.POSTS,
                )

            get == FapFolderConstants.FEED_GET_POSTS_PROFILE ||
                get == FapFolderConstants.FEED_GET_POSTS_GROUP -> {
                val posts = FapFolderPostParser.parsePostsFromHtml(
                    html = dataHtml,
                    baseUrl = base,
                    uploadsBase = uploads,
                    includeComments = false,
                )
                posts.map { FapFolderFeedItem.Post(it) }
            }

            get == "members" -> {
                val doc = Jsoup.parseBodyFragment(dataHtml, base)
                FapFolderGroupParser.parseMemberRows(doc, base)
                    .map { FapFolderFeedItem.User(it) }
            }

            get == "profile_groups" -> {
                val doc = Jsoup.parseBodyFragment(dataHtml, base)
                FapFolderGroupParser.parseGroupRows(doc, base)
                    .map { FapFolderFeedItem.Community(it) }
            }

            get == "followers" || get == "following" || get == "users" -> {
                val doc = Jsoup.parseBodyFragment(dataHtml, base)
                FapFolderSearchParser.parseUserRows(doc, base)
                    .map { FapFolderFeedItem.User(it) }
            }

            get == "communities" -> {
                val doc = Jsoup.parseBodyFragment(dataHtml, base)
                FapFolderSearchParser.parseCommunityRows(doc, base)
                    .map { FapFolderFeedItem.Community(it) }
            }

            get == FapFolderConstants.FEED_GET_SEARCH -> {
                FapFolderSearchParser.parseLoadMoreHtml(
                    htmlFragment = dataHtml,
                    baseUrl = base,
                    tab = FapFolderSearchTab.POSTS,
                )
            }

            else -> {
                val posts = FapFolderPostParser.parsePostsFromHtml(
                    html = dataHtml,
                    baseUrl = base,
                    uploadsBase = uploads,
                    includeComments = false,
                )
                posts.map { FapFolderFeedItem.Post(it) }
            }
        }
    }

    fun getSearchPage(
        query: String,
        isHashtag: Boolean,
        tab: FapFolderSearchTab = FapFolderSearchTab.POSTS,
        page: Int = 1,
    ): Flow<WebsiteState<FapFolderSearchResult>> = flow {
        emit(WebsiteState.Loading)
        val path = if (isHashtag) {
            FapFolderUrlUtils.toHashtagUrlPath(query)
        } else {
            FapFolderUrlUtils.toSearchUrlPath(query)
        }
        val url = basePath() + path
        try {
            rateLimit()
            val response = FapFolderNetwork.service.getByUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(errorState(response.code(), rawBody))
                return@flow
            }

            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(WebsiteState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }

            FapFolderSessionParser.parseAndCache(rawBody, url)

            val result = FapFolderSearchParser.parseSearchPage(
                html = rawBody,
                pageUrl = url,
                query = query,
                isHashtag = isHashtag,
                requestedTab = tab,
            )
            emit(WebsiteState.Success(result))
        } catch (e: Exception) {
            Log.e(TAG, "getSearchPage failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun autocomplete(query: String): Flow<WebsiteState<String>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.autocompleteSearch(query)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed == null) {
                emit(WebsiteState.Error(IllegalStateException("Malformed autocomplete response.")))
                return@flow
            }
            if (parsed.has("callback") && !parsed.isNull("callback")) {
                emit(WebsiteState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            val resultsHtml = parsed.optString("results").takeIf { it.isNotBlank() }
            if (resultsHtml == null) {
                emit(WebsiteState.Success(""))
            } else {
                emit(WebsiteState.Success(resultsHtml))
            }
        } catch (e: Exception) {
            Log.e(TAG, "autocomplete failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun autocompleteRows(query: String): Flow<WebsiteState<List<FapFolderAutocompleteSuggestion>>> = flow {
        emit(WebsiteState.Loading)
        val base = basePath()
        try {
            val response = FapFolderNetwork.service.autocompleteSearch(query)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(errorState(response.code(), rawBody))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed == null) {
                emit(WebsiteState.Error(IllegalStateException("Malformed autocomplete response.")))
                return@flow
            }
            if (parsed.has("callback") && !parsed.isNull("callback")) {
                emit(WebsiteState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            val resultsHtml = parsed.optString("results").takeIf { it.isNotBlank() }
            val rows = if (resultsHtml == null) {
                emptyList()
            } else {
                FapFolderAutocompleteParser.parse(resultsHtml, base)
            }
            emit(WebsiteState.Success(rows))
        } catch (e: Exception) {
            Log.e(TAG, "autocompleteRows failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getPostDetail(slugOrId: String): Flow<VideoLoadingState<FapFolderPost>> = flow {
        emit(VideoLoadingState.Loading)
        val slug = slugOrId.trim()
        val url = if (slug.startsWith("http")) slug else basePath() + "/posts/$slug"
        try {
            val response = FapFolderNetwork.service.getPostUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(
                    if (response.code() == 404) VideoLoadingState.NoContent
                    else VideoLoadingState.Error(IllegalStateException("HTTP ${response.code()}"))
                )
                return@flow
            }
            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(VideoLoadingState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }

            FapFolderSessionParser.parseAndCache(rawBody, url)
            val doc = Jsoup.parse(rawBody, url)
            val postEl = doc.selectFirst("div.post[data-id]")
            if (postEl == null) {
                emit(VideoLoadingState.NoContent)
                return@flow
            }
            val post = FapFolderPostParser.parsePost(
                postEl, url, uploadsBase(), parseComments = false
            )
            if (post == null) {
                emit(VideoLoadingState.NoContent)
                return@flow
            }

            val isVideo = post.variants.contains(
                com.yenaly.han1meviewer.FapFolder.data.model.PostVariant.VIDEO
            )
            runCatching {
                FapFolderHistoryRepo.saveProgress(
                    postId = post.postId,
                    title = post.bodyPlain.take(120).ifBlank { "FapFolder post" },
                    coverUrl = post.primaryImage?.fullUrl
                        ?: post.primaryVideo?.posterUrl
                        ?: post.firstGallery?.photos?.firstOrNull()?.fullUrl.orEmpty(),
                    authorName = post.author.displayName,
                    postSlug = post.slug,
                    isVideo = isVideo,
                    positionMs = 0L,
                    durationMs = 0L,
                    isPlaying = false,
                )
            }.onFailure { Log.w(TAG, "saveProgress failed: ${it.message}") }

            emit(VideoLoadingState.Success(post))
        } catch (e: Exception) {
            Log.e(TAG, "getPostDetail failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun resolveVideoPlayUrl(playUrl: String): Flow<WebsiteState<String>> = flow {
        emit(WebsiteState.Loading)
        val cleaned = playUrl
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .trim()
        if (cleaned.isBlank()) {
            emit(WebsiteState.Error(IllegalArgumentException("Empty play URL")))
            return@flow
        }
        try {
            val request = okhttp3.Request.Builder()
                .url(cleaned)
                .get()
                .header("referer", FapFolderConstants.HEADER_REFERER)
                .header("origin", FapFolderConstants.HEADER_ORIGIN)
                .header("accept", "*/*")
                .header("accept-encoding", "identity")
                .header("range", "bytes=0-")
                .header("user-agent", FapFolderConstants.USER_AGENT_FAPFOLDER)
                .build()

            FapFolderNetwork.sharedOkHttpClient
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                .newCall(request)
                .execute()
                .use { resp ->
                    val code = resp.code
                    val location = resp.header("Location")
                    if (code !in 300..399 || location.isNullOrBlank()) {
                        emit(
                            WebsiteState.Error(
                                IllegalStateException(
                                    "No redirect from /video/play (HTTP $code)"
                                )
                            )
                        )
                        return@use
                    }
                    emit(WebsiteState.Success(location))
                }
        } catch (e: Exception) {
            Log.e(TAG, "resolveVideoPlayUrl failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getGroupPage(slug: String): Flow<VideoLoadingState<FapFolderGroup>> = flow {
        emit(VideoLoadingState.Loading)
        val url = basePath() + "/groups/${slug.trim('/')}"
        try {
            val response = FapFolderNetwork.service.getGroupUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(
                    if (response.code() == 404) VideoLoadingState.NoContent
                    else VideoLoadingState.Error(IllegalStateException("HTTP ${response.code()}"))
                )
                return@flow
            }
            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(VideoLoadingState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }
            FapFolderSessionParser.parseAndCache(rawBody, url)
            val group = FapFolderGroupParser.parseGroup(rawBody, url)
            if (group == null) {
                emit(VideoLoadingState.NoContent)
            } else {
                emit(VideoLoadingState.Success(group))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getGroupPage failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getProfilePage(pathOrUsername: String): Flow<VideoLoadingState<FapFolderUserProfile>> = flow {
        emit(VideoLoadingState.Loading)
        val clean = pathOrUsername.trim('/')
        val url = if (clean.startsWith("http")) {
            clean
        } else if (clean.startsWith("groups/")) {
            basePath() + "/$clean"
        } else {
            basePath() + "/$clean"
        }
        try {
            val response = FapFolderNetwork.service.getProfileUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(
                    if (response.code() == 404) VideoLoadingState.NoContent
                    else VideoLoadingState.Error(IllegalStateException("HTTP ${response.code()}"))
                )
                return@flow
            }
            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(VideoLoadingState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }
            FapFolderSessionParser.parseAndCache(rawBody, url)
            val profile = FapFolderProfileParser.parseProfile(rawBody, url)
            if (profile == null) {
                emit(VideoLoadingState.NoContent)
            } else {
                emit(VideoLoadingState.Success(profile))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getProfilePage failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getAlbumPage(albumId: String): Flow<VideoLoadingState<FapFolderAlbumPage>> = flow {
        emit(VideoLoadingState.Loading)
        val url = basePath() + "/photos/${albumId.trim('/')}"
        try {
            val response = FapFolderNetwork.service.getAlbumUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(
                    if (response.code() == 404) VideoLoadingState.NoContent
                    else VideoLoadingState.Error(IllegalStateException("HTTP ${response.code()}"))
                )
                return@flow
            }
            if (FapFolderHtmlUtils.looksLikeCloudflareChallenge(rawBody)) {
                emit(VideoLoadingState.Error(IllegalStateException("Cloudflare challenge.")))
                return@flow
            }
            FapFolderSessionParser.parseAndCache(rawBody, url)
            val album = FapFolderAlbumParser.parseAlbum(rawBody, url, uploadsBase())
            if (album == null) {
                emit(VideoLoadingState.NoContent)
            } else {
                emit(VideoLoadingState.Success(album))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAlbumPage failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun openLightbox(
        photoId: Long,
        context: String,
    ): Flow<WebsiteState<FapFolderLightboxResponse>> = flow {
        emit(WebsiteState.Loading)
        try {
            rateLimitLightbox()
            val response = FapFolderNetwork.service.lightbox(
                photoId = photoId.toString(),
                context = context,
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = FapFolderLightboxParser.parse(
                rawJson = rawBody,
                baseUrl = basePath(),
                uploadsBase = uploadsBase(),
                requestedPhotoId = photoId,
            )
            val err = parsed.error
            if (err != null) {
                emit(WebsiteState.Error(err))
                return@flow
            }
            if (parsed.hasCallback) {
                FapFolderSessionCache.clear()
                emit(
                    WebsiteState.Error(
                        IllegalStateException("Session expired. Sign in again.")
                    )
                )
                return@flow
            }
            val lb = parsed.response
            if (lb == null) {
                emit(WebsiteState.Error(IllegalStateException("Empty lightbox response.")))
            } else {
                val resolved = lb.copy(
                    nextPhotoSource = FapFolderUrlUtils.resolveLightboxSource(
                        lb.nextPhotoSource,
                        uploadsBase(),
                    ),
                    prevPhotoSource = FapFolderUrlUtils.resolveLightboxSource(
                        lb.prevPhotoSource,
                        uploadsBase(),
                    ),
                )
                emit(WebsiteState.Success(resolved))
            }
        } catch (e: Exception) {
            Log.e(TAG, "openLightbox failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getComments(
        postId: Long,
        filter: String = FapFolderCommentParser.defaultFilterValue(),
        page: Int = 1,
    ): Flow<PageLoadingState<FapFolderCommentsPage>> = flow {
        emit(PageLoadingState.Loading)
        try {
            rateLimit()
            val response = FapFolderNetwork.service.filterComments(
                get = filter,
                id = postId.toString(),
                offset = page.takeIf { it > 1 },
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            if (parsed.has("callback") && !parsed.isNull("callback")) {
                FapFolderSessionCache.clear()
                emit(PageLoadingState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            val commentsHtml = parsed.optString("comments").takeIf { it.isNotBlank() }
            if (commentsHtml == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            val comments = FapFolderCommentParser.parseCommentsFromHtml(
                commentsHtml, basePath(), uploadsBase()
            )
            if (comments.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(
                    PageLoadingState.Success(
                        FapFolderCommentsPage(
                            postId = postId,
                            comments = comments,
                            hasMore = true,
                            nextOffset = page + 1,
                            activeFilter = filter,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getComments failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun loadMoreReplies(
        postId: Long,
        commentId: Long,
        offset: Int = 1,
    ): Flow<PageLoadingState<List<FapFolderComment>>> = flow {
        emit(PageLoadingState.Loading)
        try {
            rateLimit()
            val response = FapFolderNetwork.service.filterComments(
                get = FapFolderCommentParser.FILTER_MOST_RECENT,
                id = commentId.toString(),
                offset = offset.takeIf { it > 1 },
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            if (parsed.has("callback") && !parsed.isNull("callback")) {
                emit(PageLoadingState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            val html = parsed.optString("comments").takeIf { it.isNotBlank() }
                ?: parsed.optString("data").takeIf { it.isNotBlank() }
            if (html == null) {
                emit(PageLoadingState.NoMoreData)
                return@flow
            }
            val comments = FapFolderCommentParser.parseCommentsFromHtml(
                html, basePath(), uploadsBase()
            )
            if (comments.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(PageLoadingState.Success(comments))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMoreReplies failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getGroupMembers(
        groupId: Long?,
        slug: String,
        page: Int = 1,
    ): Flow<PageLoadingState<FapFolderGroupMembersPage>> = flow {
        emit(PageLoadingState.Loading)
        val url = basePath() + "/groups/${slug.trim('/')}/members" +
                if (page > 1) "/page/$page/" else ""
        try {
            rateLimit()
            val response = FapFolderNetwork.service.getByUrl(url)
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful || rawBody.isBlank()) {
                emit(PageLoadingState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            FapFolderSessionParser.parseAndCache(rawBody, url)
            val doc = Jsoup.parse(rawBody, url)
            val rows = FapFolderGroupParser.parseMemberRows(doc, url)
            if (rows.isEmpty()) {
                emit(PageLoadingState.NoMoreData)
            } else {
                emit(
                    PageLoadingState.Success(
                        FapFolderGroupMembersPage(
                            groupId = groupId,
                            members = rows,
                            hasMore = rows.size >= 20,
                            nextOffset = page + 1,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getGroupMembers failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun reactToPost(
        postId: Long,
        action: String,
        reaction: String? = null,
    ): Flow<WebsiteState<Unit>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.react(
                action = action,
                id = postId.toString(),
                reaction = reaction,
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed != null && parsed.has("callback") && !parsed.isNull("callback")) {
                emit(WebsiteState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            if (parsed?.optBoolean("error", false) == true) {
                emit(
                    WebsiteState.Error(
                        IllegalStateException(parsed.optString("message", "Action failed."))
                    )
                )
                return@flow
            }
            emit(WebsiteState.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "reactToPost failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun reactToComment(
        commentId: Long,
        action: String,
        reaction: String? = null,
    ): Flow<WebsiteState<Unit>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.react(
                action = action,
                id = commentId.toString(),
                reaction = reaction,
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed != null && parsed.has("callback") && !parsed.isNull("callback")) {
                emit(WebsiteState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            if (parsed?.optBoolean("error", false) == true) {
                emit(
                    WebsiteState.Error(
                        IllegalStateException(parsed.optString("message", "Action failed."))
                    )
                )
                return@flow
            }
            emit(WebsiteState.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "reactToComment failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun joinGroup(groupId: Long): Flow<WebsiteState<Unit>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.connectAction(
                action = FapFolderConstants.GROUP_ACTION_JOIN,
                id = groupId.toString(),
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed?.optBoolean("error", false) == true) {
                emit(
                    WebsiteState.Error(
                        IllegalStateException(parsed.optString("message", "Join failed."))
                    )
                )
                return@flow
            }
            emit(WebsiteState.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "joinGroup failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun leaveGroup(groupId: Long): Flow<WebsiteState<Unit>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.connectAction(
                action = FapFolderConstants.GROUP_ACTION_LEAVE,
                id = groupId.toString(),
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            emit(WebsiteState.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "leaveGroup failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun followUser(uid: Long): Flow<WebsiteState<Unit>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.connectAction(
                action = FapFolderConstants.FOLLOW_ACTION_FOLLOW,
                id = uid.toString(),
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            emit(WebsiteState.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "followUser failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun unfollowUser(uid: Long): Flow<WebsiteState<Unit>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.connectAction(
                action = FapFolderConstants.FOLLOW_ACTION_UNFOLLOW,
                id = uid.toString(),
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            emit(WebsiteState.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "unfollowUser failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun postComment(
        postId: Long,
        message: String,
    ): Flow<WebsiteState<FapFolderComment>> = flow {
        emit(WebsiteState.Loading)
        try {
            val response = FapFolderNetwork.service.postComment(
                handle = "post",
                id = postId.toString(),
                message = message,
            )
            val rawBody = response.body()?.string().orEmpty()
            if (!response.isSuccessful) {
                emit(WebsiteState.Error(IllegalStateException("HTTP ${response.code()}")))
                return@flow
            }
            val parsed = runCatching { org.json.JSONObject(rawBody) }.getOrNull()
            if (parsed != null && parsed.has("callback") && !parsed.isNull("callback")) {
                FapFolderSessionCache.clear()
                emit(WebsiteState.Error(IllegalStateException("Session expired.")))
                return@flow
            }
            val commentHtml = parsed?.optString("comment")?.takeIf { it.isNotBlank() }
            if (commentHtml == null) {
                emit(WebsiteState.Error(IllegalStateException("Empty comment response.")))
                return@flow
            }
            val comment = FapFolderCommentParser.parseCommentsFromHtml(
                commentHtml, basePath(), uploadsBase()
            ).firstOrNull()
            if (comment == null) {
                emit(WebsiteState.Error(IllegalStateException("Empty comment response.")))
            } else {
                emit(WebsiteState.Success(comment))
            }
        } catch (e: Exception) {
            Log.e(TAG, "postComment failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun errorState(code: Int, body: String?): WebsiteState<Nothing> {
        return when {
            code == 403 && FapFolderHtmlUtils.looksLikeCloudflareChallenge(body) ->
                WebsiteState.Error(IllegalStateException("Cloudflare challenge."))
            code == 403 ->
                WebsiteState.Error(IllegalStateException("Forbidden (HTTP 403)."))
            code == 404 ->
                WebsiteState.Error(IllegalStateException("Not found (HTTP 404)."))
            code == 429 ->
                WebsiteState.Error(IllegalStateException("Rate limited (HTTP 429)."))
            code in 500..599 ->
                WebsiteState.Error(IllegalStateException("Server error (HTTP $code)."))
            else ->
                WebsiteState.Error(IllegalStateException("HTTP $code"))
        }
    }
}
