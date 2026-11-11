package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterAccountSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterChannel
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCreator
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDashboard
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterProfile
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterStream
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterEntityParser
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterParser
import kotlinx.coroutines.Dispatchers
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
}
