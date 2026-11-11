package com.yenaly.han1meviewer.XHamster.common

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs
import java.net.URLEncoder

object XHamsterConstants {

    val BASE_URL: String
        get() = Preferences.xHamsterBaseUrl.trimEnd('/')

    val HOSTNAME: Array<String>
        get() = SiteNetworkPrefs.knownHosts(
            SiteType.XHAMSTER,
            listOf("xhamster.com"),
        )

    val REFERER: String
        get() = "$BASE_URL/"

    val ORIGIN: String
        get() = BASE_URL

    val QUALITY_LADDER = listOf("2160p", "1440p", "1080p", "720p", "480p", "240p", "144p")

    val QUALITIES = listOf("auto", "1080p", "720p", "480p", "240p")
    const val DEFAULT_QUALITY = "auto"

    data class Tab(val key: String, val title: String, val path: String)

    val TABS: List<Tab> = listOf(
        Tab("trending", "Trending", ""),
        Tab("newest", "Newest", "/newest"),
        Tab("best", "Best", "/best"),
        Tab("best-daily", "Best 1 day", "/best/daily"),
        Tab("best-weekly", "Best 7 days", "/best/weekly"),
        Tab("best-monthly", "Best 30 days", "/best/monthly"),
        Tab("hd", "HD", "/hd"),
        Tab("4k", "4K", "/4k"),
        Tab("full-length", "Full length", "/full-length"),
    )

    data class Category(val key: String, val title: String, val path: String)

    val CATEGORIES: List<Category> = listOf(
        Category("japanese", "Japanese", "/categories/japanese"),
        Category("asian", "Asian", "/categories/asian"),
        Category("teen", "Teen", "/categories/teen"),
        Category("blowjob", "Blowjob", "/categories/blowjob"),
        Category("uncensored", "Uncensored", "/categories/uncensored"),
        Category("amateur", "Amateur", "/categories/amateur"),
        Category("mature", "Mature", "/categories/mature"),
        Category("granny", "Granny", "/categories/granny"),
        Category("creampie", "Creampie", "/categories/creampie"),
        Category("homemade", "Homemade", "/categories/homemade"),
        Category("wife", "Wife", "/categories/wife"),
        Category("jav", "JAV", "/categories/jav"),
        Category("chinese", "Chinese", "/categories/chinese"),
        Category("pussy", "Pussy", "/categories/pussy"),
        Category("american", "American", "/categories/american"),
        Category("vintage", "Vintage", "/categories/vintage"),
        Category("anal", "Anal", "/categories/anal"),
        Category("female-masturbation", "Female Masturbation", "/categories/female-masturbation"),
        Category("indian", "Indian", "/categories/indian"),
        Category("18-year-old", "18 Year Old", "/categories/18-year-old"),
        Category("orgasm", "Orgasm", "/categories/orgasm"),
        Category("russian", "Russian", "/categories/russian"),
        Category("cartoon", "Cartoon", "/categories/cartoon"),
        Category("nepali", "Nepali", "/categories/nepali"),
        Category("big-tits", "Big Tits", "/categories/big-tits"),
        Category("cuckold", "Cuckold", "/categories/cuckold"),
        Category("indonesian", "Indonesian", "/categories/indonesian"),
        Category("cumshot", "Cumshot", "/categories/cumshot"),
        Category("milf", "MILF", "/categories/milf"),
        Category("mom", "Mom", "/categories/mom"),
        Category("eating-pussy", "Eating Pussy", "/categories/eating-pussy"),
        Category("korean", "Korean", "/categories/korean"),
        Category("lesbian", "Lesbian", "/categories/lesbian"),
        Category("bdsm", "BDSM", "/categories/bdsm"),
        Category("massage", "Massage", "/categories/massage"),
        Category("hardcore", "Hardcore", "/categories/hardcore"),
        Category("rough-sex", "Rough Sex", "/categories/rough-sex"),
        Category("nude", "Nude", "/categories/nude"),
        Category("bbw", "BBW", "/categories/bbw"),
        Category("handjob", "Handjob", "/categories/handjob"),
        Category("vietnamese", "Vietnamese", "/categories/vietnamese"),
        Category("housewife", "Housewife", "/categories/housewife"),
        Category("filipina", "Filipina", "/categories/filipina"),
        Category("public-nudity", "Public Nudity", "/categories/public-nudity"),
        Category("hairy", "Hairy", "/categories/hairy"),
        Category("celebrity", "Celebrity", "/categories/celebrity"),
        Category("big-natural-tits", "Big Natural Tits", "/categories/big-natural-tits"),
        Category("threesome", "Threesome", "/categories/threesome"),
        Category("thai", "Thai", "/categories/thai"),
        Category("tits", "Tits", "/categories/tits"),
        Category("cum-in-mouth", "Cum in Mouth", "/categories/cum-in-mouth"),
        Category("european", "European", "/categories/european"),
        Category("skinny", "Skinny", "/categories/skinny"),
        Category("big-cock", "Big Cock", "/categories/big-cock"),
        Category("compilation", "Compilation", "/categories/compilation"),
        Category("voyeur", "Voyeur", "/categories/voyeur"),
        Category("group-sex", "Group Sex", "/categories/group-sex"),
        Category("saggy-tits", "Saggy Tits", "/categories/saggy-tits"),
        Category("pornstar", "Pornstar", "/categories/pornstar"),
        Category("big-ass", "Big Ass", "/categories/big-ass"),
        Category("gangbang", "Gangbang", "/categories/gangbang"),
        Category("squirting", "Squirting", "/categories/squirting"),
        Category("sri-lankan", "Sri Lankan", "/categories/sri-lankan"),
        Category("pissing", "Pissing", "/categories/pissing"),
        Category("top", "Top Rated", "/categories/top"),
        Category("couple", "Couple", "/categories/couple"),
        Category("babe", "Babe", "/categories/babe"),
        Category("wife-sharing", "Wife Sharing", "/categories/wife-sharing"),
        Category("cum-swallowing", "Cum Swallowing", "/categories/cum-swallowing"),
        Category("retro", "Retro", "/categories/retro"),
        Category("small-tits", "Small Tits", "/categories/small-tits"),
        Category("gay", "Gay Porn", "/gay"),
        Category("shemale", "Transgender Porn", "/shemale"),
        Category("hentai", "Hentai", "/categories/hentai"),
        Category("sfw", "SFW", "/categories/sfw"),
    )

    fun listUrl(path: String): String {
        val clean = path.trim()
        return if (clean.isEmpty()) "$BASE_URL/" else absolute(clean)
    }

    fun searchUrl(query: String): String =
        "$BASE_URL/search/" + URLEncoder.encode(query.trim(), "UTF-8")

    fun videoUrl(code: String): String = "$BASE_URL/videos/$code"

    fun absolute(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> BASE_URL + url
        else -> "$BASE_URL/$url"
    }

    private val VIDEO_PATH = Regex("""^(?:(?:https?:)?//[^/]+)?/videos/([^/?#\s]+)""")

    fun codeFromUrl(url: String): String? =
        VIDEO_PATH.find(url)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun parseVideoReference(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("/videos/")) return null
        val normalized = if (text.startsWith("http", ignoreCase = true) || text.startsWith("/")) {
            text
        } else "https://$text"
        return codeFromUrl(normalized)
    }

    fun relativePath(url: String): String {
        if (url.startsWith("/")) return url
        val afterScheme = url.substringAfter("//", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return url
        val slash = afterScheme.indexOf('/')
        return if (slash < 0) "/" else afterScheme.substring(slash)
    }
}
