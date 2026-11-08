package com.yenaly.han1meviewer.SupJav.common

object SupJavConstants {
    /** Follows the mirror / custom mirror chosen in the network settings. */
    val BASE_URL: String
        get() = com.yenaly.han1meviewer.Preferences.supJavBaseUrl.trimEnd('/')

    val HOSTNAME: Array<String>
        get() = com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs.knownHosts(
            com.yenaly.han1meviewer.SiteType.SUPJAV,
            listOf("supjav.com", "supjav.fun"),
        )

    const val PROTECTOR_URL = "https://lk1.supremejav.com"

    data class ServerInfo(val name: String, val title: String, val description: String)

    val SERVERS: List<ServerInfo> = listOf(
        ServerInfo("VAS", "VAS", "StreamWish style player (api/stream, HLS)"),
        ServerInfo("LUC", "LUC", "Lulustream style player (packed HLS)"),
        ServerInfo("FST", "FST", "StreamWish embed (packed HLS)"),
        ServerInfo("ST", "ST", "Streamtape (MP4)"),
        ServerInfo("VOE", "VOE", "VOE (HLS / MP4, best effort)"),
        ServerInfo("TV", "TV", "TV player (HLS)"),
    )

    val SUPPORTED_SERVERS: List<String> = SERVERS.map { it.name }

    val QUALITIES = listOf("1080p", "720p", "480p", "360p")
    const val DEFAULT_QUALITY = "720p"

    val LANGUAGES = listOf(
        "en" to "English",
        "zh" to "Chinese",
        "ja" to "Japanese",
    )

    data class Category(val key: String, val title: String, val path: String)

    val CATEGORIES: List<Category> = listOf(
        Category("latest", "Latest", ""),
        Category("popular", "Popular", "/popular"),
        Category("censored", "Censored", "/category/censored-jav"),
        Category("uncensored", "Uncensored", "/category/uncensored-jav"),
        Category("amateur", "Amateur", "/category/amateur"),
        Category("chinese-sub", "Chinese Sub", "/category/chinese-subtitles"),
        Category("english-sub", "English Sub", "/category/english-subtitles"),
        Category("reducing-mosaic", "Reducing Mosaic", "/category/reducing-mosaic"),
    )

    fun langPath(lang: String): String = if (lang == "en" || lang.isBlank()) "" else "/$lang"

    fun pagedUrl(path: String, page: Int, lang: String = "en"): String {
        val clean = langPath(lang) + path.trimEnd('/')
        return if (page <= 1) "$BASE_URL$clean".let { if (clean.isEmpty()) "$it/" else it }
        else "$BASE_URL$clean/page/$page"
    }

    fun searchUrl(query: String, page: Int, lang: String = "en"): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val lp = langPath(lang)
        return if (page <= 1) "$BASE_URL$lp/?s=$q" else "$BASE_URL$lp/page/$page/?s=$q"
    }

    private val VIDEO_REF_REGEX = Regex("""(?:^|/)(\d{3,})(?:\.html)?/?(?:[?#].*)?$""")

    fun parseVideoReference(input: String): String? {
        val text = input.trim()
        if (text.startsWith("id:", ignoreCase = true)) {
            return text.substring(3).trim().removeSuffix(".html").takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            val host = runCatching { java.net.URI(text).host }.getOrNull().orEmpty().lowercase()
            if (HOSTNAME.none { host == it || host.endsWith(".$it") }) return null
            return VIDEO_REF_REGEX.find(text)?.groupValues?.get(1)
        }
        return null
    }

    fun videoUrl(code: String): String = "$BASE_URL/$code.html"
}
