package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.data.model.OnAirAnnouncementEntry
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPageInfo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPictureCard
import com.yenaly.han1meviewer.OnAir.data.model.OnAirSearchResult
import com.yenaly.han1meviewer.OnAir.data.model.OnAirTag
import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoDetail
import com.yenaly.han1meviewer.OnAir.data.model.StreamResult
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirListingParser
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirWatchParser
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response

object OnAirNetworkRepo {

    private const val TAG = "OnAirNetworkRepo"
    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 2_000L

    private class OnAirHttpException(
        val httpCode: Int,
        override val message: String,
    ) : RuntimeException(message)

    private suspend fun <T> retryWithBackoff(operation: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                return operation()
            } catch (e: OnAirHttpException) {
                if (e.httpCode == 404 || e.httpCode == 401 || e.httpCode == 403) {
                    throw e
                }
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
            Log.w(TAG, "Attempt ${attempt + 1}/$MAX_RETRY_COUNT failed: ${lastError?.message}")
            if (attempt < MAX_RETRY_COUNT - 1) {
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("All retries failed")
    }

    private fun Response<ResponseBody>.toHtmlOrThrow(): String {
        val code = code()
        val bodyText = body()?.string().orEmpty()
        when {
            isSuccessful -> return bodyText
            code == 429 -> throw OnAirHttpException(
                429,
                "OnAir is rate limiting requests. Wait a minute and try again.",
            )
            code == 401 || code == 403 -> {
                OnAirCookieManager.clearCookies()
                throw OnAirHttpException(code, "Session expired (HTTP $code). Log in again.")
            }
            code == 404 -> throw OnAirHttpException(
                404,
                "Content not found on OnAir (404). Try a different mirror.",
            )
            code in 500..599 -> throw OnAirHttpException(
                code,
                "OnAir server error (HTTP $code). Retry in a moment.",
            )
            else -> throw OnAirHttpException(code, "Unexpected response (HTTP $code).")
        }
    }

    fun getHomePage(
        page: Int = 1,
        query: String? = null,
        field: String? = null,
        length: String? = null,
        date: String? = null,
        sort: String? = null,
    ) = flow {
        emit(WebsiteState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getHomePage(
                    page = page.takeIf { it > 1 },
                    query = query?.takeIf { it.isNotBlank() },
                    field = field?.takeIf { it.isNotBlank() },
                    length = length?.takeIf { it.isNotBlank() && it != OnAirConstants.LENGTH_ANY },
                    date = date?.takeIf { it.isNotBlank() && it != OnAirConstants.DATE_ANY },
                    sort = sort?.takeIf { it.isNotBlank() && it != OnAirConstants.SORT_NEWEST },
                )
                val html = response.toHtmlOrThrow()
                OnAirListingParser.parseHomePage(html, OnAirNetwork.baseUrl)
            }
            result.currentUsername?.let(OnAirProfileCache::setCurrentUsername)
            emit(WebsiteState.Success(result))
        } catch (e: Exception) {
            Log.e(TAG, "getHomePage failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getHomePagePaged(
        page: Int,
        query: String? = null,
        field: String? = null,
        length: String? = null,
        date: String? = null,
        sort: String? = null,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getHomePage(
                    page = page.takeIf { it > 1 },
                    query = query?.takeIf { it.isNotBlank() },
                    field = field?.takeIf { it.isNotBlank() },
                    length = length?.takeIf { it.isNotBlank() && it != OnAirConstants.LENGTH_ANY },
                    date = date?.takeIf { it.isNotBlank() && it != OnAirConstants.DATE_ANY },
                    sort = sort?.takeIf { it.isNotBlank() && it != OnAirConstants.SORT_NEWEST },
                )
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                val cards = OnAirListingParser.parseVideoCards(doc, OnAirNetwork.baseUrl)
                val pageInfo = OnAirListingParser.parsePaginator(doc, OnAirNetwork.baseUrl)
                if (cards.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(cards to pageInfo)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getHomePagePaged failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchVideos(
        page: Int,
        query: String,
        field: String,
        length: String,
        date: String,
        sort: String,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getHomePage(
                    page = page.takeIf { it > 1 },
                    query = query.takeIf { it.isNotBlank() },
                    field = field.takeIf { it.isNotBlank() },
                    length = length.takeIf { it.isNotBlank() && it != OnAirConstants.LENGTH_ANY },
                    date = date.takeIf { it.isNotBlank() && it != OnAirConstants.DATE_ANY },
                    sort = sort.takeIf { it.isNotBlank() && it != OnAirConstants.SORT_NEWEST },
                )
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                val cards = OnAirListingParser.parseVideoCards(doc, OnAirNetwork.baseUrl)
                val pageInfo = OnAirListingParser.parsePaginator(doc, OnAirNetwork.baseUrl)
                val heading = OnAirListingParser.parseHeading(html, OnAirNetwork.baseUrl)
                if (cards.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(
                    OnAirSearchResult(
                        query = query,
                        field = field,
                        pageInfo = pageInfo,
                        cards = cards,
                        headingText = heading,
                    )
                )
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "searchVideos failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getTagPage(
        tagName: String,
        page: Int,
        query: String?,
        field: String?,
        length: String?,
        date: String?,
        sort: String?,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getTagPage(
                    tagName = tagName,
                    page = page.takeIf { it > 1 },
                    query = query?.takeIf { it.isNotBlank() },
                    field = field?.takeIf { it.isNotBlank() },
                    length = length?.takeIf { it.isNotBlank() && it != OnAirConstants.LENGTH_ANY },
                    date = date?.takeIf { it.isNotBlank() && it != OnAirConstants.DATE_ANY },
                    sort = sort?.takeIf { it.isNotBlank() && it != OnAirConstants.SORT_NEWEST },
                )
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                val cards = OnAirListingParser.parseVideoCards(doc, OnAirNetwork.baseUrl)
                val pageInfo = OnAirListingParser.parsePaginator(doc, OnAirNetwork.baseUrl)
                val heading = OnAirListingParser.parseHeading(html, OnAirNetwork.baseUrl)
                if (cards.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(
                    OnAirSearchResult(
                        query = query,
                        field = field ?: OnAirConstants.SEARCH_FIELD_TAG,
                        pageInfo = pageInfo,
                        cards = cards,
                        headingText = heading,
                    )
                )
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getTagPage failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getTagsPage(
        page: Int = 1,
        query: String? = null,
        sort: String? = "popular",
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getTags(
                    page = page.takeIf { it > 1 },
                    query = query?.takeIf { it.isNotBlank() },
                    sort = sort?.takeIf { it.isNotBlank() },
                )
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                val tags = OnAirListingParser.parseTagCloud(doc)
                val pageInfo = OnAirListingParser.parsePaginator(doc, OnAirNetwork.baseUrl)
                if (tags.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(tags to pageInfo)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getTagsPage failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getPicturesPage(
        page: Int = 1,
        query: String? = null,
    ) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getPictures(
                    page = page.takeIf { it > 1 },
                    query = query?.takeIf { it.isNotBlank() },
                )
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                val cards: List<OnAirPictureCard> =
                    OnAirListingParser.parsePictureCards(doc, OnAirNetwork.baseUrl)
                val pageInfo = OnAirListingParser.parsePaginator(doc, OnAirNetwork.baseUrl)
                if (cards.isEmpty()) PageLoadingState.NoMoreData
                else PageLoadingState.Success(cards to pageInfo)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getPicturesPage failed", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getAnnouncements() = flow {
        emit(WebsiteState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getAnnouncements()
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                OnAirListingParser.parseAnnouncementEntries(doc)
            }
            emit(WebsiteState.Success(result))
        } catch (e: Exception) {
            Log.e(TAG, "getAnnouncements failed", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getVideoDetail(videoId: String, quality: String? = null) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val watchUrl = OnAirNetwork.baseUrl.trimEnd('/') + "/watch/" + videoId +
                    (quality?.takeIf { it.isNotBlank() }?.let { "?quality=$it" } ?: "")
            val result = retryWithBackoff {
                val firstDetail = runCatching {
                    val response = OnAirNetwork.service.getWatchPage(
                        videoId = videoId,
                        quality = quality?.takeIf { it.isNotBlank() },
                    )
                    if (response.code() == 404) return@runCatching null
                    val html = response.toHtmlOrThrow()
                    if (OnAirListingParser.isLoginRequiredPage(html)) {
                        Log.w(TAG, "watch page shows login form (attempt 1)")
                        return@runCatching null
                    }
                    OnAirWatchParser.parseWatchPage(html, watchUrl)
                }.getOrNull()

                val detail = firstDetail ?: run {
                    Log.w(
                        TAG,
                        "watch parse failed, retrying once with refreshed cookies. " +
                                "jarChars=${OnAirCookieManager.cookiesRaw.length}, " +
                                "adPass=${OnAirCookieManager.hasAdPass}",
                    )
                    val retryResponse = OnAirNetwork.service.getWatchPage(
                        videoId = videoId,
                        quality = quality?.takeIf { it.isNotBlank() },
                    )
                    if (retryResponse.code() == 404) {
                        return@retryWithBackoff VideoLoadingState.NoContent
                    }
                    val retryHtml = retryResponse.toHtmlOrThrow()
                    if (OnAirListingParser.isLoginRequiredPage(retryHtml)) {
                        return@retryWithBackoff VideoLoadingState.Error(
                            IllegalStateException(
                                "Session expired on this mirror. Sign in again from Settings, " +
                                        "or switch to a different mirror.",
                            ),
                        )
                    }
                    val retryDetail = OnAirWatchParser.parseWatchPage(retryHtml, watchUrl)
                    if (retryDetail == null) {
                        Log.w(
                            TAG,
                            "watch parse failed twice; first 500 chars: " +
                                    retryHtml.take(500).replace('\n', ' '),
                        )
                        return@retryWithBackoff VideoLoadingState.Error(
                            IllegalStateException(
                                "This mirror returned a page without the watch layout twice. " +
                                        "Switch mirror from Cookies settings.",
                            ),
                        )
                    }
                    retryDetail
                }

                val normalized = if (detail.videoId.isBlank()) {
                    detail.copy(videoId = videoId)
                } else {
                    detail
                }
                VideoLoadingState.Success(normalized)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getVideoDetail failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchStreamUrlForQuality(
        videoId: String,
        quality: String,
    ): StreamResult? {
        if (videoId.isBlank()) {
            Log.w(TAG, "fetchStreamUrlForQuality called with blank videoId")
            return null
        }
        return try {
            retryWithBackoff {
                val response = OnAirNetwork.service.getWatchPage(
                    videoId = videoId,
                    quality = quality,
                )
                if (response.code() == 404) return@retryWithBackoff null
                val html = response.toHtmlOrThrow()
                OnAirWatchParser.parseStreamFromPage(html, OnAirNetwork.baseUrl, quality)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchStreamUrlForQuality failed for $quality", e)
            null
        }
    }

    fun getUserProfile(username: String, page: Int = 1) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val result = retryWithBackoff {
                val response = OnAirNetwork.service.getUserProfile(
                    username = username,
                    page = page.takeIf { it > 1 },
                )
                if (response.code() == 404) {
                    return@retryWithBackoff VideoLoadingState.NoContent
                }
                val html = response.toHtmlOrThrow()
                val doc = Jsoup.parse(html, OnAirNetwork.baseUrl)
                val profile = parseUserProfile(doc, OnAirNetwork.baseUrl, username)
                if (profile == null) {
                    VideoLoadingState.Error(IllegalStateException("Could not parse the user profile."))
                } else {
                    VideoLoadingState.Success(profile)
                }
            }
            if (result is VideoLoadingState.Success && result.info.isCurrentUser) {
                OnAirProfileCache.put(result.info.username, result.info)
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile failed", e)
            emit(VideoLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseUserProfile(
        doc: org.jsoup.nodes.Document,
        baseUrl: String,
        requestedUsername: String,
    ): OnAirUserProfile? {
        val header = doc.selectFirst("div.profile-header") ?: return null
        val pageTitleEl = header.selectFirst("h1.page-title") ?: return null
        val displayName = pageTitleEl.text().trim().removePrefix("@").trim()
        if (displayName.isBlank()) return null

        val currentUsername = OnAirListingParser.parseCurrentUsername(doc)
            ?: OnAirProfileCache.getCurrentUsername()
        val isCurrentUser = currentUsername != null &&
                currentUsername.equals(displayName, ignoreCase = true)

        val avatarUrl = OnAirListingParser.parseUserAvatar(doc)
        val bio = OnAirListingParser.parseUserBio(doc)
        val followerCount = OnAirListingParser.parseUserFollowerCount(doc)
        val followingCount = OnAirListingParser.parseUserFollowingCount(doc)
        val videoCount = OnAirListingParser.parseUserVideoCount(doc)
        val pictureCount = OnAirListingParser.parseUserPictureCount(doc)
        val isFollowing = header.selectFirst("form[action*=unfollow]") != null

        val videosRoot = doc.selectFirst("div.video-grid")
        val videos = videosRoot?.let { OnAirListingParser.parseVideoCards(doc, baseUrl) }.orEmpty()
        val pictures = videosRoot?.let { OnAirListingParser.parsePictureCards(doc, baseUrl) }.orEmpty()
        val pageInfo = OnAirListingParser.parsePaginator(doc, baseUrl)

        if (currentUsername == null && isCurrentUser) {
            OnAirProfileCache.setCurrentUsername(displayName)
        }

        Log.d(
            TAG,
            "parseUserProfile: user=$displayName requested=$requestedUsername " +
                    "isCurrentUser=$isCurrentUser videos=${videos.size} " +
                    "followers=$followerCount following=$followingCount",
        )

        return OnAirUserProfile(
            username = displayName,
            displayName = displayName,
            bio = bio,
            avatarUrl = avatarUrl,
            followerCount = followerCount,
            followingCount = followingCount,
            videoCount = videoCount,
            pictureCount = pictureCount,
            isFollowing = isFollowing,
            isCurrentUser = isCurrentUser,
            videos = videos,
            pictures = pictures,
            pageInfo = pageInfo,
        )
    }
}
