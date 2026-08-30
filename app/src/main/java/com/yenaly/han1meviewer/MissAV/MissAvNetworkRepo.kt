package com.yenaly.han1meviewer.MissAV

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MissAvNetworkRepo {

    private const val TAG = "MissAvNetworkRepo"
    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 2000L

    private val recommMap: MutableMap<String, String> = ConcurrentHashMap()
    private val uuid by lazy { Preferences.missAvUuid }

    /**
     * Generic retry function with exponential backoff
     * The onRetry callback is now suspend so it can call delay
     */
    private suspend fun <T> retryWithBackoff(
        operation: suspend () -> T,
        maxRetries: Int = MAX_RETRY_COUNT,
        onRetry: suspend (Int, Throwable) -> Unit = { attempt, _ ->
            Log.w(TAG, "Retry attempt $attempt")
        }
    ): T {
        var lastException: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    val delayMs = RETRY_DELAY_MS * (attempt + 1)
                    Log.d(TAG, "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: IllegalStateException("All retries failed")
    }

    /**
     * Get home page with retry and Cloudflare handling
     */
    fun getHomePage() = flow {
        emit(WebsiteState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.getPopularVideos(1)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        val parseResult = MissAvParser.homePage(body)
                        if (parseResult is WebsiteState.Success) {
                            parseResult
                        } else {
                            throw IllegalStateException("Failed to parse home page")
                        }
                    } else {
                        // Check if it's a Cloudflare issue
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare") ||
                                    errorBody.contains("cf_")) {
                                    Log.w(TAG, "Cloudflare challenge detected")
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                } else {
                                    throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                                }
                            }
                            429 -> {
                                Log.w(TAG, "Rate limited, retrying...")
                                throw IllegalStateException("Rate limited")
                            }
                            500, 502, 503, 504 -> {
                                Log.w(TAG, "Server error ${response.code()}, retrying...")
                                throw IllegalStateException("Server error: ${response.code()}")
                            }
                            else -> {
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                        }
                    }
                },
                onRetry = { attempt, throwable ->
                    Log.w(TAG, "Home page retry $attempt: ${throwable.message}")
                    if (throwable is CloudFlareBlockedException) {
                        // Wait longer for Cloudflare challenges
                        delay(RETRY_DELAY_MS * 2 * (attempt + 1))
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Home page error", e)
            val errorState = when (e) {
                is CloudFlareBlockedException -> WebsiteState.Error(e)
                is java.net.SocketTimeoutException -> WebsiteState.Error(
                    IllegalStateException("Network timeout. The site may be under heavy load. Please try again.")
                )
                is java.net.UnknownHostException -> WebsiteState.Error(
                    IllegalStateException("Cannot reach the server. Please check your internet connection.")
                )
                else -> WebsiteState.Error(e)
            }
            emit(errorState)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get popular videos with retry
     */
    fun getPopularVideos(page: Int) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.getPopularVideos(page)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        MissAvParser.popularPage(body)
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Popular videos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get genre videos with retry (synchronous version)
     */
    suspend fun getGenreVideosSync(
        genrePath: String, 
        page: Int = 1, 
        sort: String? = null, 
        filter: String? = null
    ): List<HanimeInfo> {
        return try {
            retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.getGenreVideos(genrePath, page, sort, filter)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        val document = Jsoup.parse(body)
                        document.select("div.thumbnail").map { element ->
                            MissAvParser.parseVideoCardPublic(element)
                        }
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Genre videos error", e)
            emptyList()
        }
    }

    /**
     * Get genre videos with flow
     */
    fun getGenreVideos(genrePath: String, page: Int = 1, sort: String? = null, filter: String? = null) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.getGenreVideos(genrePath, page, sort, filter)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        MissAvParser.searchResults(body)
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Genre videos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get latest videos with retry
     */
    fun getLatestVideos(page: Int) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.getLatestVideos(page)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        MissAvParser.popularPage(body)
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Latest videos error", e)
            emit(PageLoadingState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Search videos with retry
     */
    fun searchVideos(query: String, page: Int, sort: String? = null, filter: String? = null) = flow {
        emit(PageLoadingState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.searchVideos(query, page, sort, filter)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        val document = Jsoup.parse(body)

                        if (document.selectFirst("div[x-data*=handleRecommendResponse]") != null) {
                            val recommId = recommMap[query]
                            val apiResponse = if (page == 1 || recommId == null) {
                                val searchData = MissAvApiHelper.searchData(query)
                                val url = MissAvApiHelper.searchURL(uuid)
                                MissAvNetwork.missAvService.searchApi(
                                    url,
                                    searchData.toRequestBody("application/json".toMediaType())
                                )
                            } else {
                                val recommData = """{"count":24,"cascadeCreate":true}"""
                                val url = MissAvApiHelper.recommURL(recommId)
                                MissAvNetwork.missAvService.recommendApi(
                                    url,
                                    recommData.toRequestBody("application/json".toMediaType())
                                )
                            }

                            if (apiResponse.isSuccessful) {
                                val apiBody = apiResponse.body()?.string() ?: EMPTY_STRING
                                val data = HJson.decodeFromString<RecommendationsResponse>(apiBody)
                                recommMap[query] = data.recommId
                                MissAvParser.parseApiSearchResults(apiBody)
                            } else {
                                when (apiResponse.code()) {
                                    403, 429 -> throw IllegalStateException("Rate limited or blocked")
                                    else -> PageLoadingState.NoMoreData
                                }
                            }
                        } else {
                            MissAvParser.searchResults(body)
                        }
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Search videos error", e)
            when (e) {
                is CloudFlareBlockedException -> emit(PageLoadingState.Error(e))
                is java.net.SocketTimeoutException -> emit(
                    PageLoadingState.Error(IllegalStateException("Search timed out. Please try again."))
                )
                else -> emit(PageLoadingState.Error(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get video detail with retry
     */
    fun getVideoDetail(path: String) = flow {
        emit(VideoLoadingState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val response = MissAvNetwork.missAvService.getVideoDetail(path)
                    if (response.isSuccessful) {
                        val body = response.body()?.string() ?: EMPTY_STRING
                        MissAvParser.parseVideoDetail(body)
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            404 -> VideoLoadingState.NoContent
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Video detail error", e)
            when (e) {
                is CloudFlareBlockedException -> emit(VideoLoadingState.Error(e))
                is java.net.SocketTimeoutException -> emit(
                    VideoLoadingState.Error(IllegalStateException("Video loading timed out. Please try again."))
                )
                else -> emit(VideoLoadingState.Error(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get related videos with retry
     */
    fun getRelatedVideos(videoId: String) = flow {
        emit(WebsiteState.Loading)
        try {
            val result = retryWithBackoff(
                operation = {
                    val url = MissAvApiHelper.relatedURL()
                    val body = MissAvApiHelper.relatedData(uuid, videoId)
                    val response = MissAvNetwork.missAvService.relatedApi(
                        url,
                        body.toRequestBody("application/json".toMediaType())
                    )

                    if (response.isSuccessful) {
                        val bodyString = response.body()?.string() ?: EMPTY_STRING
                        val relatedVideos = MissAvParser.parseRelatedVideos(bodyString)
                        WebsiteState.Success(relatedVideos)
                    } else {
                        when (response.code()) {
                            403 -> {
                                val errorBody = response.errorBody()?.string() ?: ""
                                if (errorBody.contains("Just a moment") || 
                                    errorBody.contains("Cloudflare")) {
                                    throw CloudFlareBlockedException("Cloudflare challenge detected for MissAV")
                                }
                                throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                            }
                            else -> throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
                        }
                    }
                }
            )
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Related videos error", e)
            emit(WebsiteState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * MissAV API Helper - HMAC signature generation for API requests
 */
object MissAvApiHelper {

    private const val TAG = "MissAvApiHelper"

    fun searchURL(uuid: String): String {
        val path = "/missav-default/search/users/$uuid/items/?frontend_timestamp=${System.currentTimeMillis()}"
        val signedPath = generateHMACSignature(path, MissAvConstants.MISSAV_PUBLIC_TOKEN)
        return "${MissAvConstants.MISSAV_API_URL}$signedPath"
    }

    fun recommURL(recommId: String): String {
        val path = "/missav-default/recomms/next/items/$recommId?frontend_timestamp=${System.currentTimeMillis()}"
        val signedPath = generateHMACSignature(path, MissAvConstants.MISSAV_PUBLIC_TOKEN)
        return "${MissAvConstants.MISSAV_API_URL}$signedPath"
    }

    fun relatedURL(): String {
        val path = "/missav-default/batch/?frontend_timestamp=${System.currentTimeMillis()}"
        val signedPath = generateHMACSignature(path, MissAvConstants.MISSAV_PUBLIC_TOKEN)
        return "${MissAvConstants.MISSAV_API_URL}$signedPath"
    }

    fun searchData(query: String): String = buildJsonObject {
        put("searchQuery", query)
        put("count", 24)
        put("scenario", "search")
        put("returnProperties", true)
        putJsonArray("includedProperties") {
            add("title_en")
            add("dm")
        }
        put("cascadeCreate", true)
    }.toString()

    fun relatedData(uuid: String, entryId: String): String {
        fun buildRequestObject(scenario: String) = buildJsonObject {
            put("method", "POST")
            put("path", "/recomms/items/$entryId/items/")
            putJsonObject("params") {
                put("targetUserId", uuid)
                put("count", 24)
                put("scenario", scenario)
                put("returnProperties", true)
                putJsonArray("includedProperties") {
                    add("title_en")
                    add("dm")
                }
                put("cascadeCreate", true)
            }
        }

        return buildJsonObject {
            putJsonArray("requests") {
                add(buildRequestObject("desktop-watch-next-side"))
                add(buildRequestObject("desktop-watch-next-bottom"))
            }
            put("distinctRecomms", true)
        }.toString()
    }

    private fun generateHMACSignature(data: String, key: String): String = try {
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(secretKeySpec)

        val hashBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }

        "$data&frontend_sign=$hexString"
    } catch (e: Exception) {
        Log.e(TAG, "HMAC signature generation failed", e)
        data
    }
}