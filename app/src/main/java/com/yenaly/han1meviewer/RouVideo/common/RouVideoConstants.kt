package com.yenaly.han1meviewer.RouVideo.common

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs
import com.yenaly.han1meviewer.logic.network.SiteNetworks
import com.yenaly.han1meviewer.util.applicationContext
import java.net.URLEncoder

object RouVideoConstants {

    val BASE_URL: String
        get() = Preferences.rouVideoBaseUrl.trimEnd('/')

    val HOSTNAME: Array<String>
        get() = SiteNetworkPrefs.knownHosts(
            SiteType.ROUVIDEO,
            SiteNetworks.forSite(SiteType.ROUVIDEO).mirrors.map { it.host },
        )

    const val ITEMS_PER_PAGE = 26

    const val TAG_AI_SERIES = "AI短劇"

    val MAIN_TAGS: List<String> = listOf("國產AV", "探花", "自拍流出", "OnlyFans", "日本", "麻豆傳媒")

    enum class Sort(val order: String, val searchSort: String, val labelRes: Int) {
        RELEVANCE("createdAt", "", R.string.rouvideo_sort_relevance),
        NEWEST("createdAt", "createdAt:desc", R.string.rouvideo_sort_newest),
        VIEWS("viewCount", "viewCount:desc", R.string.rouvideo_sort_views),
        LIKES("likeCount", "likeCount:desc", R.string.rouvideo_sort_likes),
    }

    enum class SeriesSort(val query: String, val labelRes: Int) {
        UPDATED("updated", R.string.rouvideo_series_sort_updated),
        HOT("hot", R.string.rouvideo_series_sort_hot),
        LIKES("likes", R.string.rouvideo_series_sort_likes),
        MOST("most", R.string.rouvideo_series_sort_most),
    }

    enum class SeriesFilter(val query: String, val labelRes: Int) {
        ALL("all", R.string.rouvideo_series_filter_all),
        ONGOING("ongoing", R.string.rouvideo_series_filter_ongoing),
        COMPLETED("completed", R.string.rouvideo_series_filter_completed),
        FRESH("fresh", R.string.rouvideo_series_filter_fresh),
    }

    sealed interface TabSource {
        data class Library(val sort: Sort) : TabSource
        data class Tag(val tag: String, val sort: Sort = Sort.NEWEST) : TabSource
        data class Series(val tag: String = "") : TabSource
    }

    data class HomeTab(val key: String, val title: String, val source: TabSource)

    val DEFAULT_TABS: List<HomeTab>
        get() = listOf(
            HomeTab("latest", str(R.string.rouvideo_tab_latest), TabSource.Library(Sort.NEWEST)),
            HomeTab("liked", str(R.string.rouvideo_tab_liked), TabSource.Library(Sort.LIKES)),
            HomeTab("viewed", str(R.string.rouvideo_tab_viewed), TabSource.Library(Sort.VIEWS)),
            HomeTab("tag_cnav", RouVideoTagLabels.label("國產AV"), TabSource.Tag("國產AV")),
            HomeTab("tag_91", RouVideoTagLabels.label("探花"), TabSource.Tag("探花")),
            HomeTab("tag_selfie", RouVideoTagLabels.label("自拍流出"), TabSource.Tag("自拍流出")),
            HomeTab("tag_onlyfans", RouVideoTagLabels.label("OnlyFans"), TabSource.Tag("OnlyFans")),
            HomeTab("tag_jp", RouVideoTagLabels.label("日本"), TabSource.Tag("日本")),
            HomeTab("tag_madou", RouVideoTagLabels.label("麻豆傳媒"), TabSource.Tag("麻豆傳媒")),
            HomeTab("series_ai", RouVideoTagLabels.label(TAG_AI_SERIES), TabSource.Series(TAG_AI_SERIES)),
            HomeTab("series_all", str(R.string.rouvideo_tab_series), TabSource.Series()),
        )

    private fun str(res: Int): String = applicationContext.getString(res)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun query(params: List<Pair<String, String>>): String =
        params.filter { it.second.isNotEmpty() }
            .joinToString("&") { "${it.first}=${enc(it.second)}" }
            .let { if (it.isEmpty()) "" else "?$it" }

    fun libraryUrl(sort: Sort, page: Int): String =
        BASE_URL + "/v" + query(
            listOf(
                "order" to sort.order,
                "page" to if (page > 1) page.toString() else "",
            )
        )

    fun tagUrl(tag: String, sort: Sort, page: Int): String =
        BASE_URL + "/t/" + enc(tag) + query(
            listOf(
                "order" to sort.order,
                "page" to if (page > 1) page.toString() else "",
            )
        )

    fun searchUrl(q: String, tag: String, sort: Sort, page: Int, series: Boolean = false): String =
        BASE_URL + "/search" + query(
            listOf(
                "q" to q,
                "t" to tag,
                "sort" to if (sort == Sort.RELEVANCE) "" else sort.searchSort,
                "tab" to if (series) "series" else "",
                "page" to if (page > 1) page.toString() else "",
            )
        )

    fun seriesUrl(tag: String, filter: SeriesFilter, sort: SeriesSort, page: Int): String =
        BASE_URL + "/series" + query(
            listOf(
                "tag" to tag,
                "f" to if (filter == SeriesFilter.ALL) "" else filter.query,
                "s" to if (sort == SeriesSort.UPDATED) "" else sort.query,
                "page" to if (page > 1) page.toString() else "",
            )
        )

    fun categoriesUrl(): String = "$BASE_URL/cat"

    fun videoPageUrl(id: String): String = "$BASE_URL/v/$id"

    fun hlsApiUrl(id: String): String = "$BASE_URL/api/hls/$id"

    fun playUrl(id: String): String = "$BASE_URL/api/v/$id/play"

    fun absolute(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl
        else BASE_URL + (if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl")

    val REFERER: String
        get() = "$BASE_URL/"

    val ORIGIN: String
        get() = BASE_URL

    private val VIDEO_REF_REGEX = Regex("""/v/([A-Za-z0-9]{10,40})(?:[/?#].*)?$""")

    fun parseVideoReference(input: String): String? {
        val text = input.trim()
        if (text.startsWith("id:", ignoreCase = true)) {
            return text.substring(3).trim().takeIf { id -> id.isNotEmpty() && id.all { it.isLetterOrDigit() } }
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            val host = runCatching { java.net.URI(text).host }.getOrNull().orEmpty().lowercase()
            if (HOSTNAME.none { host == it || host.endsWith(".$it") }) return null
            return VIDEO_REF_REGEX.find(text)?.groupValues?.get(1)
        }
        return null
    }
}
