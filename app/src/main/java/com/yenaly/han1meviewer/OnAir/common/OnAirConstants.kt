package com.yenaly.han1meviewer.OnAir.common

object OnAirConstants {

    private val HOST_B64 = arrayOf(
        "cnp2ZzNqeTd6eGRldWRzeWluZ3VyN2tjeGJ5dzNrZGlybHMzd3lkaGlkcXhqaWVuemg3b2JtYWQub25pb24=",
        "b25haXJicGtyMnB5b2Z3M3B6NG9yNHJjeGl3Y3FpdXJ1ZG15Nmp5eTNoZmtjcHJrZTRwamtuYWQub25pb24=",
        "b25haXI1cmNzcmNoaDZ6MmZnMjRvYXBwNXh0ZzRpZ3dvdHZvam1yNGc0Y3ZwdHhsYndqaG1icWQub25pb24=",
    )

    private val URL_B64 = arrayOf(
        "aHR0cDovL3J6dmczank3enhkZXVkc3lpbmd1cjdrY3hieXcza2RpcmxzM3d5ZGhpZHF4amllbnpoN29ibWFkLm9uaW9uLw==",
        "aHR0cDovL29uYWlyYnBrcjJweW9mdzNwejRvcjRyY3hpd2NxaXVydWRteTZqeXkzaGZrY3Bya2U0cGprbmFkLm9uaW9uLw==",
        "aHR0cDovL29uYWlyNXJjc3JjaGg2ejJmZzI0b2FwcDV4dGc0aWd3b3R2b2ptcjRnNGN2cHR4bGJ3amhtYnFkLm9uaW9uLw==",
    )

    val ONAIR_HOSTNAME: Array<String> by lazy {
        HOST_B64.map(OnAirEndpointCodec::decodeHost).toTypedArray()
    }

    val ONAIR_URL: Array<String> by lazy {
        URL_B64.map(OnAirEndpointCodec::decodeUrl).toTypedArray()
    }

    const val DEFAULT_PAGE_SIZE = 20

    const val SEARCH_FIELD_TITLE = "title"
    const val SEARCH_FIELD_UPLOADER = "uploader"
    const val SEARCH_FIELD_TAG = "tag"

    const val LENGTH_ANY = "any"
    const val LENGTH_XSHORT = "xshort"
    const val LENGTH_SHORT = "short"
    const val LENGTH_MEDIUM = "medium"
    const val LENGTH_LONG = "long"
    const val LENGTH_XLONG = "xlong"

    const val DATE_ANY = "any"
    const val DATE_DAY = "day"
    const val DATE_WEEK = "week"
    const val DATE_MONTH = "month"
    const val DATE_YEAR = "year"

    const val SORT_NEWEST = "newest"
    const val SORT_OLDEST = "oldest"
    const val SORT_MOST_VIEWED = "most_viewed"
    const val SORT_LEAST_VIEWED = "least_viewed"
    const val SORT_LONGEST = "longest"
    const val SORT_SHORTEST = "shortest"

    fun getPrimaryUrl(): String = OnAirSitePreferences.selectedBaseUrl

    fun getPrimaryHost(): String = OnAirSitePreferences.currentHostname()
}
