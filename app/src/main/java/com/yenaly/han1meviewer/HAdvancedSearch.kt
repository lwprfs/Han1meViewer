package com.yenaly.han1meviewer

import java.io.Serializable

enum class HAdvancedSearch {

    QUERY,

    GENRE,

    SORT,

    YEAR,

    MONTH,

    DURATION,

    TAGS,

    BRANDS
}

typealias AdvancedSearchMap = HashMap<HAdvancedSearch, Serializable>

@Suppress("NOTHING_TO_INLINE")
inline fun advancedSearchMapOf(vararg pairs: Pair<HAdvancedSearch, Serializable>) =
    hashMapOf(*pairs)
