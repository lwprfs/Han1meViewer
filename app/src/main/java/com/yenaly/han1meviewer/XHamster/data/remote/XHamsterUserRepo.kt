package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCollection
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCommentResult
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterEngagement
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterFeedItem
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListKind
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterListsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

/**
 * Everything that needs the signed-in session: rating, favourites / playlists, comments,
 * the "my" lists and the subscriptions feed. Request shapes were taken from the captured
 * browser sessions (see per-function notes).
 */
object XHamsterUserRepo {

    const val LOGIN_REQUIRED = "LOGIN_REQUIRED"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val TEXT_PLAIN = "text/plain;charset=UTF-8".toMediaType()

    private fun ajaxHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "X-Requested-With" to "XMLHttpRequest",
        "x-csrf-token" to "1",
        "Referer" to referer,
        "Origin" to XHamsterConstants.ORIGIN,
    )

    private fun requireLogin() {
        if (!XHamsterNetwork.hasAuthCookies()) throw IOException(LOGIN_REQUIRED)
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val response = XHamsterNetwork.service.get(url)
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        response.body()?.string() ?: throw IOException("Empty response")
    }

    private class RawResponse(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    private suspend fun post(url: String, body: String, referer: String): RawResponse =
        withContext(Dispatchers.IO) {
            val response = XHamsterNetwork.service.post(
                url, body.toRequestBody(TEXT_PLAIN), ajaxHeaders(referer),
            )
            val text = (if (response.isSuccessful) response.body()?.string()
            else response.errorBody()?.string()).orEmpty()
            RawResponse(response.code(), text)
        }

    private fun syncExtras(body: String): JsonObject? =
        ((runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray)
            ?.firstOrNull() as? JsonObject)?.get("extras") as? JsonObject

    private fun syncSucceeded(body: String): Boolean {
        val extras = syncExtras(body) ?: return false
        val result = (extras["result"] as? JsonPrimitive)?.booleanOrNull
        if (result != null) return result
        val error = extras["error"]
        return error == null || error is JsonNull
    }

    // ───────────────────────── lists ─────────────────────────

    suspend fun myVideos(url: String, kind: XHamsterListKind): Result<XHamsterListPage> = runCatching {
        requireLogin()
        XHamsterListsParser.parseMyVideos(fetchHtml(url), kind)
    }

    suspend fun collections(): Result<List<XHamsterCollection>> = runCatching {
        requireLogin()
        XHamsterListsParser.parseCollections(fetchHtml(XHamsterConstants.listUrl("/my/favorites/videos")))
    }

    /** Path of the Watch Later collection page for the signed-in user. */
    suspend fun watchLaterPath(): String =
        collections().getOrNull()?.firstOrNull { it.isWatchLater }?.path ?: "/my/watch-later"

    // ───────────────────────── rating ─────────────────────────

    /**
     * POST /api/front/rating/rate — body and `{"result":true}` response as captured from the
     * video page. `isLike=false` is the dislike button.
     */
    suspend fun rate(
        videoId: Long,
        like: Boolean,
        watchId: String?,
        referer: String,
    ): Result<Unit> = runCatching {
        requireLogin()
        val payload = buildJsonObject {
            put("stats", buildJsonObject { put("isLinkingClicked", false) })
            put("watchId", watchId)
            put("entityModel", "videoModel")
            put("entityId", videoId)
            put("isLike", like)
        }.toString()
        val response = post("${XHamsterConstants.BASE_URL}/api/front/rating/rate", payload, referer)
        if (!response.ok) throw IOException("HTTP ${response.code}")
        val result = ((runCatching { json.parseToJsonElement(response.body) }.getOrNull() as? JsonObject)
            ?.get("result") as? JsonPrimitive)?.booleanOrNull
        if (result == false) throw IOException("Rating was rejected")
    }

    // ───────────────────────── favourites ─────────────────────────

    /**
     * POST /api/front/favorite/videos with the full set of collections the video should be in.
     * An empty set removes it from every playlist. Returns the collections the server confirms.
     */
    suspend fun setVideoCollections(
        videoId: Long,
        collectionIds: List<String>,
        watchId: String?,
        referer: String,
    ): Result<List<String>> = runCatching {
        requireLogin()
        val payload = buildJsonObject {
            put("videoId", videoId)
            put("collections", buildJsonArray { collectionIds.forEach { add(JsonPrimitive(it)) } })
            put("stats", buildJsonObject {
                put("isLinkingClicked", false)
                put("favoriteSource", "video")
            })
            put("watchId", watchId)
        }.toString()
        val response = post("${XHamsterConstants.BASE_URL}/api/front/favorite/videos", payload, referer)
        if (!response.ok) throw IOException("HTTP ${response.code}")
        val root = runCatching { json.parseToJsonElement(response.body) }.getOrNull() as? JsonObject
        (root?.get("collections") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: collectionIds
    }

    // ───────────────────────── playlist management ─────────────────────────
    //
    // The captures only show the playlists *page*, not the create / rename / delete requests, so
    // these follow the site's generic "<modelName>Sync" x-api convention (same envelope as the
    // subscription and comment calls). Failures are surfaced to the UI instead of being hidden.

    private suspend fun syncCollection(
        id: String?,
        title: String?,
        itemState: String,
    ): Result<Unit> = runCatching {
        requireLogin()
        val payload = buildJsonArray {
            add(buildJsonObject {
                put("name", "favoriteVideosCollectionModelSync")
                put("requestData", buildJsonObject {
                    put("model", buildJsonObject {
                        put("id", id)
                        put("${'$'}id", UUID.randomUUID().toString())
                        put("modelName", "favoriteVideosCollectionModel")
                        put("contentType", "videos")
                        if (title != null) put("title", title)
                        if (itemState == "created") put("visibility", "private")
                        put("itemState", itemState)
                    })
                    put("trusted", true)
                })
            })
        }.toString()
        val response = post(
            "${XHamsterConstants.BASE_URL}/x-api", payload,
            XHamsterConstants.listUrl("/my/favorites/videos"),
        )
        if (!response.ok || !syncSucceeded(response.body)) {
            val message = (syncExtras(response.body)?.get("errorMessage") as? JsonPrimitive)?.contentOrNull
            throw IOException(message ?: "HTTP ${response.code}")
        }
    }

    suspend fun createCollection(title: String): Result<Unit> =
        syncCollection(null, title.trim(), "created")

    suspend fun renameCollection(id: String, title: String): Result<Unit> =
        syncCollection(id, title.trim(), "changed")

    suspend fun deleteCollection(id: String): Result<Unit> =
        syncCollection(id, null, "removed")

    // ───────────────────────── comments ─────────────────────────

    /**
     * POST /x-api commentModelSync (new comment or reply). A `403` + `showCaptcha` answer means
     * the site wants a reCAPTCHA, which cannot be completed from here; that is reported as
     * [XHamsterCommentResult.CaptchaRequired] so the UI can send the user to the web page.
     */
    suspend fun postComment(
        entityModel: String,
        entityId: Long,
        text: String,
        replyToId: String?,
        replyToName: String?,
        engagement: XHamsterEngagement?,
        referer: String,
    ): XHamsterCommentResult = withContext(Dispatchers.IO) {
        if (!XHamsterNetwork.hasAuthCookies()) return@withContext XHamsterCommentResult.LoginRequired
        val type = when (entityModel) {
            "photoModel" -> "photo"
            else -> "video"
        }
        val payload = buildJsonArray {
            add(buildJsonObject {
                put("name", "commentModelSync")
                put("requestData", buildJsonObject {
                    put("model", buildJsonObject {
                        put("id", JsonNull)
                        put("${'$'}id", UUID.randomUUID().toString())
                        put("modelName", "commentModel")
                        put("itemState", "created")
                        put("author", buildJsonObject {
                            put("id", engagement?.userId)
                        })
                        put("replyToId", replyToId)
                        put("replyToUserName", replyToName)
                        put("text", text)
                        put("created", System.currentTimeMillis() / 1000.0)
                        put("reported", false)
                        put("isEditable", true)
                        put("target", buildJsonObject {
                            put("type", type)
                            put("id", entityId)
                        })
                    })
                    put("entity", buildJsonObject {
                        put("entityModel", entityModel)
                        put("entityID", entityId)
                    })
                    put("watchId", engagement?.watchId)
                    put("stats", buildJsonObject { put("isLinkingClicked", false) })
                })
            })
        }.toString()
        val response = runCatching {
            post("${XHamsterConstants.BASE_URL}/x-api", payload, referer)
        }.getOrElse { return@withContext XHamsterCommentResult.Rejected(it.message) }
        val extras = syncExtras(response.body)
        val success = (extras?.get("result") as? JsonPrimitive)?.booleanOrNull
        when {
            response.ok && success != false -> XHamsterCommentResult.Posted
            (extras?.get("showCaptcha") as? JsonPrimitive)?.booleanOrNull == true ->
                XHamsterCommentResult.CaptchaRequired

            else -> XHamsterCommentResult.Rejected(
                (extras?.get("errorMessage") as? JsonPrimitive)?.contentOrNull
                    ?: "HTTP ${response.code}",
            )
        }
    }

    /** The site's abuse / report form (the only reporting channel present in the captures). */
    fun reportUrl(): String = "${XHamsterConstants.BASE_URL}/info/contact?subject=abuse"

    // ───────────────────────── subscriptions feed ─────────────────────────

    /** GET /x-api?r=[{"name":"newsCollectionFetch"...}] — videos from followed channels/creators. */
    suspend fun newsFeed(): Result<List<XHamsterFeedItem>> = runCatching {
        requireLogin()
        withContext(Dispatchers.IO) {
            val request = buildJsonArray {
                add(buildJsonObject {
                    put("name", "newsCollectionFetch")
                    put("requestData", buildJsonObject { })
                })
            }.toString()
            val url = "${XHamsterConstants.BASE_URL}/x-api?r=" +
                    URLEncoder.encode(request, "UTF-8") + "&_=" + Math.random()
            val response = XHamsterNetwork.service.getJson(
                url, ajaxHeaders(XHamsterConstants.listUrl("/my/subscriptions/news")),
            )
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
            XHamsterListsParser.parseNewsFeed(response.body()?.string().orEmpty())
        }
    }

    // ───────────────────────── search / stars ─────────────────────────

    suspend fun photoSearch(url: String): Result<XHamsterPage<XHamsterGallery>> = runCatching {
        XHamsterListsParser.parsePhotoSearch(fetchHtml(url))
    }

    suspend fun starCountries(): Result<List<XHamsterOption>> = runCatching {
        XHamsterListsParser.parseStarCountries(
            fetchHtml(XHamsterConstants.listUrl("/pornstars/all/countries")),
        )
    }
}
