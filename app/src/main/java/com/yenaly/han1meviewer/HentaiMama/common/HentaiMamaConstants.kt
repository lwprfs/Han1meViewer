package com.yenaly.han1meviewer.HentaiMama.common

object HentaiMamaConstants {
    const val BASE_URL = "https://hentaimama.io"
    const val API_URL = "$BASE_URL/wp-admin/admin-ajax.php"
    const val ACTION_PLAYER = "get_player_contents"

    const val PATH_GENRE = "genre"
    const val PATH_SERIES = "hentai-series"
    const val PATH_TVSHOWS = "tvshows"
    const val PATH_EPISODES = "episodes"
    const val PATH_ADVANCED_SEARCH = "advance-search"
    const val PATH_RECENT_EPISODES = "recent-episodes"
    const val PATH_UPCOMING = "upcoming"
    const val PATH_STUDIO = "studio"

    const val SORT_RECENT = "recent"
    const val SORT_RATING = "rating"
    const val SORT_ALPHABET = "alphabet"
    const val SORT_WEEKLY = "weekly"
    const val SORT_MONTHLY = "monthly"
    const val SORT_ALLTIME = "alltime"

    val ALL_SORTS: List<String> = listOf(
        SORT_RECENT,
        SORT_RATING,
        SORT_ALPHABET,
        SORT_WEEKLY,
        SORT_MONTHLY,
        SORT_ALLTIME,
    )

    const val SORT_WEEK = SORT_WEEKLY
    const val SORT_MONTH = SORT_MONTHLY
    const val SORT_AZ = SORT_ALPHABET

    const val LAYOUT_DETAILS = "details"
    const val LAYOUT_BARE = "bare"

    const val QUERY_FILTER = "filter"
    const val QUERY_LAYOUT = "layout"
    const val QUERY_SEARCH = "s"
}
