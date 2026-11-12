package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterAccountSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterChannel
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCreator
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDashboard
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterEntityProfile
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGalleryPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPhotoDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterShortsPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterProfile
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterStream
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterEntityParser
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterParser
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCommentsPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlinx.coroutines.withContext
import java.io.IOException

object XHamsterRepo {

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val response = XHamsterNetwork.service.get(url)
        if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
        response.body()?.string() ?: throw IOException("Empty response")
    }

    suspend fun list(url: String): Result<XHamsterListPage> = runCatching {
        val html = fetchHtml(url)
        val page = XHamsterParser.parseListPage(html)

        if (page.items.isEmpty()) {
            val scraped = XHamsterEntityParser.parseHtmlVideoList(html)
            if (scraped.items.isNotEmpty()) scraped else page
        } else page
    }

    suspend fun channels(url: String): Result<XHamsterPage<XHamsterChannel>> = runCatching {
        XHamsterEntityParser.parseChannels(fetchHtml(url))
    }

    suspend fun creators(url: String): Result<XHamsterPage<XHamsterCreator>> = runCatching {
        XHamsterEntityParser.parseCreators(fetchHtml(url))
    }

    suspend fun galleries(url: String): Result<XHamsterPage<XHamsterGallery>> = runCatching {
        XHamsterEntityParser.parseGalleries(fetchHtml(url))
    }

    private fun rebase(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) {
            XHamsterConstants.BASE_URL + XHamsterConstants.relativePath(url)
        } else XHamsterConstants.listUrl(url)

    suspend fun galleryPage(url: String): Result<XHamsterGalleryPage> = runCatching {
        XHamsterEntityParser.parseGalleryPage(fetchHtml(rebase(url)))
            ?: throw IOException("Unable to read gallery")
    }

    suspend fun photoDetail(galleryId: Long, photoId: Long): Result<XHamsterPhotoDetail> = runCatching {
        val html = fetchHtml("${XHamsterConstants.BASE_URL}/photos/gallery/$galleryId/$photoId")
        XHamsterEntityParser.parsePhotoPage(html) ?: throw IOException("Unable to read photo")
    }

    suspend fun photoComments(
        galleryId: Long,
        photoId: Long,
        page: Int,
    ): Result<XHamsterCommentsPage> = runCatching {
        withContext(Dispatchers.IO) {
            val referer = "${XHamsterConstants.BASE_URL}/photos/gallery/$galleryId/$photoId"
            val url = "${XHamsterConstants.BASE_URL}/api/front/comment/list" +
                    "?entityModel=photoModel&entityId=$photoId&page=$page" +
                    "&spamFolder=0&checkPrivileges=1"
            val response = XHamsterNetwork.service.getJson(url, ajaxHeaders(referer))
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
            val body = response.body()?.string() ?: throw IOException("Empty response")
            XHamsterParser.parseCommentsPage(body, page)
                ?: throw IOException("Unable to read comments")
        }
    }

    suspend fun entityProfile(path: String): Result<XHamsterEntityProfile> = runCatching {
        val clean = XHamsterConstants.relativePath(path).substringBefore('?').substringBefore('#')
            .trimEnd('/')
        XHamsterEntityParser.parseEntityProfile(fetchHtml(XHamsterConstants.listUrl(clean)), clean)
            ?: throw IOException("Unable to read profile")
    }

    suspend fun shorts(url: String): Result<XHamsterShortsPage> = runCatching {
        XHamsterEntityParser.parseShortsPage(fetchHtml(rebase(url)))
    }

    suspend fun categories(): Result<List<XHamsterOption>> = runCatching {
        XHamsterEntityParser.parseCategories(fetchHtml(XHamsterConstants.listUrl("/categories")))
    }

    suspend fun photoFilters(): Result<List<XHamsterOption>> = runCatching {
        XHamsterEntityParser.parsePhotoFilters(fetchHtml(XHamsterConstants.listUrl("/photos")))
    }

    suspend fun topCreatorPeriods(url: String): Result<List<XHamsterOption>> = runCatching {
        XHamsterEntityParser.parseTopCreatorOptions(fetchHtml(url), "periods")
    }

    suspend fun profile(path: String): Result<XHamsterProfile> = runCatching {
        XHamsterEntityParser.parseProfile(fetchHtml(XHamsterConstants.listUrl(path)))
            ?: throw IOException("Unable to read profile")
    }

    suspend fun dashboard(): Result<XHamsterDashboard> = runCatching {
        XHamsterEntityParser.parseDashboard(fetchHtml(XHamsterConstants.listUrl("/creator")))
            ?: throw IOException("Unable to read dashboard")
    }

    suspend fun accountSettings(): Result<XHamsterAccountSettings> = runCatching {
        val profile = runCatching { fetchHtml(XHamsterConstants.listUrl("/my/edit/profile")) }.getOrNull()
        val privacy = runCatching { fetchHtml(XHamsterConstants.listUrl("/my/edit/privacy")) }.getOrNull()
        if (profile == null && privacy == null) throw IOException("Unable to read settings")
        XHamsterEntityParser.parseAccountSettings(profile, privacy)
    }

    suspend fun playlists(): Result<List<XHamsterLink>> = runCatching {
        XHamsterParser.parsePlaylists(fetchHtml(XHamsterConstants.listUrl("/my/favorites/videos")))
    }

    suspend fun search(query: String): Result<XHamsterListPage> =
        list(XHamsterConstants.searchUrl(query))

    suspend fun detail(code: String): Result<XHamsterDetail> = runCatching {
        val html = fetchHtml(XHamsterConstants.videoUrl(code))
        XHamsterParser.parseDetail(html, code) ?: throw IOException("Unable to read video page")
    }

    suspend fun resolveStreams(detail: XHamsterDetail): List<XHamsterStream> =
        XHamsterStreamResolver.resolve(detail)

    private fun ajaxHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "X-Requested-With" to "XMLHttpRequest",
        "x-csrf-token" to "1",
        "Referer" to referer,
        "Origin" to XHamsterConstants.ORIGIN,
    )

    suspend fun comments(
        videoEntityId: Long,
        videoCode: String,
        page: Int,
    ): Result<XHamsterCommentsPage> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "${XHamsterConstants.BASE_URL}/api/front/comment/list" +
                    "?entityModel=videoModel&entityId=$videoEntityId&page=$page" +
                    "&spamFolder=0&checkPrivileges=1"
            val response = XHamsterNetwork.service.getJson(
                url, ajaxHeaders(XHamsterConstants.videoUrl(videoCode)),
            )
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
            val body = response.body()?.string() ?: throw IOException("Empty response")
            XHamsterParser.parseCommentsPage(body, page)
                ?: throw IOException("Unable to read comments")
        }
    }

    private val sync = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun syncSucceeded(body: String): Boolean {
        val root = runCatching { sync.parseToJsonElement(body) }.getOrNull() as? JsonArray
            ?: return false
        val first = root.firstOrNull() as? JsonObject ?: return false
        val extras = first["extras"] as? JsonObject
        val result = (extras?.get("result") as? JsonPrimitive)?.booleanOrNull
        if (result != null) return result
        val error = extras?.get("error")
        return error == null || error is kotlinx.serialization.json.JsonNull
    }

    suspend fun setSubscribed(
        uploader: XHamsterUploader,
        videoCode: String,
        subscribe: Boolean,
        refererUrl: String? = null,
    ): Result<Boolean> = runCatching {
        withContext(Dispatchers.IO) {
            if (!XHamsterNetwork.hasAuthCookies()) throw IOException("LOGIN_REQUIRED")
            val id = uploader.subscriptionId ?: throw IOException("Missing subscription id")
            val entityModel = uploader.subscriptionModel
                ?: if (uploader.isChannel) "channelModel" else "userModel"
            val payload = buildJsonArray {
                add(buildJsonObject {
                    put("name", "entitySubscriptionModelSync")
                    put("requestData", buildJsonObject {
                        put("model", buildJsonObject {
                            put("id", id)
                            put("${'$'}id", UUID.randomUUID().toString())
                            put("modelName", "entitySubscriptionModel")
                            put("entityModelName", entityModel)
                            put("entityId", id)
                            put("subscribed", subscribe)
                            put("itemState", if (subscribe) "created" else "removed")
                        })
                        put("trusted", true)
                    })
                })
            }.toString()
            val response = XHamsterNetwork.service.post(
                "${XHamsterConstants.BASE_URL}/x-api",
                payload.toRequestBody("text/plain;charset=UTF-8".toMediaType()),
                ajaxHeaders(refererUrl ?: XHamsterConstants.videoUrl(videoCode)),
            )
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
            val body = response.body()?.string().orEmpty()
            if (!syncSucceeded(body)) throw IOException("Subscription was rejected")
            subscribe
        }
    }
}
